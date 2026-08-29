"""
Flicker-Free, Zero-Dropout Face Lock Camera Tracker
===================================================
Features:
1. Base-Anchored Smooth Fallback (eliminates 1-frame 0-drop glitch).
2. YUV420p Even Pixel Alignment (trunc(.../2)*2 removes chroma jitter).
3. Continuous Gapless Interpolation with timestamp delta tolerance.
4. Motion-Differential Skin Filtering for stable subject lock.
"""

import subprocess
import struct
import math
from typing import List, Dict, Any, Tuple, Optional


class FaceCameraTracker:
    """Tracks facial coordinates across time and compiles flicker-free Face Lock camera transforms."""

    def __init__(self, video_path: str, sample_w: int = 180, sample_h: int = 320):
        self.video_path = video_path
        self.w = sample_w
        self.h = sample_h
        self.frame_bytes = sample_w * sample_h * 3

    def track_dense_face_trajectory(self, sample_fps: int = 8) -> List[Dict[str, Any]]:
        """
        Extracts motion-filtered face & subject centroid coordinates (x, y) across video frames.
        """
        cmd = [
            "ffmpeg", "-hide_banner",
            "-i", self.video_path,
            "-vf", f"fps={sample_fps},scale={self.w}:{self.h}",
            "-pix_fmt", "rgb24",
            "-f", "rawvideo",
            "-"
        ]
        proc = subprocess.run(cmd, capture_output=True)
        raw = proc.stdout
        num_frames = len(raw) // self.frame_bytes

        raw_traj = []
        prev_frame = None

        for f_idx in range(num_frames):
            t = f_idx / sample_fps
            offset = f_idx * self.frame_bytes
            frame = raw[offset:offset + self.frame_bytes]

            if prev_frame:
                active_pixels = []
                for y in range(int(self.h * 0.05), int(self.h * 0.55)):
                    for x in range(self.w):
                        idx = (y * self.w + x) * 3
                        r2, g2, b2 = frame[idx], frame[idx + 1], frame[idx + 2]
                        r1, g1, b1 = prev_frame[idx], prev_frame[idx + 1], prev_frame[idx + 2]
                        diff = abs(r2 - r1) + abs(g2 - g1) + abs(b2 - b1)

                        y_val = 0.299 * r2 + 0.587 * g2 + 0.114 * b2
                        cb = 128 - 0.168736 * r2 - 0.331264 * g2 + 0.5 * b2
                        cr = 128 + 0.5 * r2 - 0.418688 * g2 - 0.081312 * b2

                        is_skin = (77 <= cb <= 127 and 133 <= cr <= 173 and y_val > 45)
                        is_moving = (diff > 25)

                        if is_moving and (is_skin or y < self.h * 0.40):
                            active_pixels.append((x, y))

                if len(active_pixels) > 40:
                    cx = sum(p[0] for p in active_pixels) / len(active_pixels)
                    cy = sum(p[1] for p in active_pixels) / len(active_pixels)
                else:
                    cx = raw_traj[-1]["x"] * self.w if raw_traj else self.w * 0.55
                    cy = raw_traj[-1]["y"] * self.h if raw_traj else self.h * 0.35
            else:
                cx = self.w * 0.55
                cy = self.h * 0.35

            prev_frame = frame
            raw_traj.append({
                "t": round(t, 2),
                "x": round(cx / self.w, 3),
                "y": round(cy / self.h, 3)
            })

        if not raw_traj:
            return []

        # Snappy exponential moving average smoothing (alpha = 0.65)
        smooth_traj = []
        cur_x = raw_traj[0]["x"]
        cur_y = raw_traj[0]["y"]
        alpha = 0.65

        for pt in raw_traj:
            cur_x = alpha * pt["x"] + (1 - alpha) * cur_x
            cur_y = alpha * pt["y"] + (1 - alpha) * cur_y
            smooth_traj.append({
                "t": pt["t"],
                "x": round(cur_x, 4),
                "y": round(cur_y, 4)
            })

        # Apply second-order Gaussian smoothing curve for cinematic stability
        from ..curves.speed_graph import SpeedGraph
        return SpeedGraph.smooth_trajectory(smooth_traj, keys=["x", "y"], window_size=7, passes=2)

    def build_face_lock_filter(
        self,
        target_w: int = 480,
        target_h: int = 854,
        zoom_factor: float = 0.52
    ) -> str:
        """
        Compiles a flicker-free, zero-dropout Face Lock-On camera tracking filter.
        Uses baseline-anchored delta evaluation and even integer truncation (trunc(.../2)*2)
        to completely eliminate 1-frame 0-dropouts and chroma tearing.
        """
        smooth_traj = self.track_dense_face_trajectory(sample_fps=8)

        if not smooth_traj:
            return f"setpts=PTS-STARTPTS,crop=w=iw*{zoom_factor}:h=ih*{zoom_factor}:x='(in_w-out_w)/2':y='(in_h-out_h)*0.35',scale={target_w}:{target_h}"

        step = max(1, len(smooth_traj) // 70)
        sampled = smooth_traj[::step]
        if smooth_traj[-1] not in sampled:
            sampled.append(smooth_traj[-1])

        mean_x = sum(p["x"] for p in sampled) / len(sampled)
        mean_y = sum(p["y"] for p in sampled) / len(sampled)

        # Baseline reference coordinate (prevents any drop to 0 if a floating gap occurs)
        base_x_expr = f"(in_w*{mean_x:.3f}-out_w/2)"
        base_y_expr = f"(in_h*{mean_y:.3f}-out_h*0.35)"

        x_terms = []
        y_terms = []

        for i in range(len(sampled) - 1):
            t1 = sampled[i]["t"]
            # Small overlap (+0.03s) ensures 0 gap between knots
            t2 = sampled[i + 1]["t"] + 0.03
            dt = max(0.01, sampled[i + 1]["t"] - sampled[i]["t"])
            x1 = sampled[i]["x"]
            x2 = sampled[i + 1]["x"]
            y1 = sampled[i]["y"]
            y2 = sampled[i + 1]["y"]

            cond = f"between(t\\,{t1:.2f}\\,{t2:.2f})"
            interp = f"(t-{t1:.2f})/{dt:.2f}"

            # Delta from baseline
            target_x_piece = f"(in_w*{x1:.3f}+(in_w*({x2:.3f}-{x1:.3f}))*{interp}-out_w/2)"
            target_y_piece = f"(in_h*{y1:.3f}+(in_h*({y2:.3f}-{y1:.3f}))*{interp}-out_h*0.35)"

            x_terms.append(f"({cond}*({target_x_piece}-{base_x_expr}))")
            y_terms.append(f"({cond}*({target_y_piece}-{base_y_expr}))")

        first_t = sampled[0]["t"]
        last_t = sampled[-1]["t"]
        first_x = sampled[0]["x"]
        last_x = sampled[-1]["x"]
        first_y = sampled[0]["y"]
        last_y = sampled[-1]["y"]

        edge_x = f"(lt(t\\,{first_t:.2f})*((in_w*{first_x:.3f}-out_w/2)-{base_x_expr})+gt(t\\,{last_t:.2f})*((in_w*{last_x:.3f}-out_w/2)-{base_x_expr}))"
        edge_y = f"(lt(t\\,{first_t:.2f})*((in_h*{first_y:.3f}-out_h*0.35)-{base_y_expr})+gt(t\\,{last_t:.2f})*((in_h*{last_y:.3f}-out_h*0.35)-{base_y_expr}))"

        # Safe continuous evaluation
        x_full = f"({base_x_expr} + {edge_x} + " + "+".join(x_terms) + ")"
        y_full = f"({base_y_expr} + {edge_y} + " + "+".join(y_terms) + ")"

        # Bound coordinates safely inside frame borders and force EVEN integer alignment
        x_expr = f"trunc(min(max(0\\,{x_full})\\,in_w-out_w)/2)*2"
        y_expr = f"trunc(min(max(0\\,{y_full})\\,in_h-out_h)/2)*2"

        filter_str = (
            f"setpts=PTS-STARTPTS,"
            f"crop=w='trunc(in_w*{zoom_factor:.2f}/2)*2':h='trunc(in_h*{zoom_factor:.2f}/2)*2':"
            f"x='{x_expr}':"
            f"y='{y_expr}',"
            f"scale={target_w}:{target_h}"
        )
        return filter_str
