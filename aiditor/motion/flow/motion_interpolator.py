"""
Optical Flow Motion Interpolation Engine (MCI / Optical Vectors)
================================================================
Synthesizes true intermediate video frames using bidirectional block motion
estimation (EPZS / AOBMC) instead of repeating static frames.
"""

import os
import subprocess
import json
import time
from typing import Dict, Any, Optional


class OpticalFlowInterpolator:
    """
    Interpolates video framerates using real optical flow motion estimation.
    """

    @staticmethod
    def interpolate(
        input_path: str,
        output_path: str,
        target_fps: int = 60,
        mode: str = "mci",
        scd_threshold: float = 10.0,
        bitrate: str = "12M",
        color_grade: bool = True
    ) -> Dict[str, Any]:
        """
        Runs true optical flow frame synthesis.
        
        Args:
            input_path: Path to input video file.
            output_path: Path to rendered output MP4.
            target_fps: Desired output framerate (default 60).
            mode: 'mci' (Motion Compensated Interpolation) or 'blend' (Sub-frame Blending).
            scd_threshold: Scene Change Detection threshold (0-100) to prevent cross-cut warping.
            bitrate: Output video bitrate.
            color_grade: Whether to apply automotive dynamic contrast and vibrance grading.
        """
        if not os.path.isfile(input_path):
            raise FileNotFoundError(f"Input video not found: {input_path}")

        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)

        # Build minterpolate filter
        if mode.lower() == "mci":
            # Motion Compensated Interpolation with EPZS bidirectional vectors & scene cut protection
            flow_filter = (
                f"minterpolate=fps={target_fps}:mi_mode=mci:mc_mode=aobmc:"
                f"me_mode=bidir:me=epzs:mb_size=16:search_param=16:scd=fdiff:scd_threshold={scd_threshold}"
            )
        elif mode.lower() == "blend":
            flow_filter = f"minterpolate=fps={target_fps}:mi_mode=blend:scd=fdiff:scd_threshold={scd_threshold}"
        else:
            flow_filter = f"fps={target_fps}"

        # Standardize resolution to 1080x1920 9:16 vertical
        scale_filter = "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:black"

        filters = [flow_filter, scale_filter]

        if color_grade:
            grade_filter = "eq=contrast=1.16:brightness=0.02:saturation=1.26:gamma=1.03,unsharp=5:5:0.6:5:5:0.0"
            filters.append(grade_filter)

        vf_chain = ",".join(filters)

        cmd = [
            "ffmpeg", "-hide_banner", "-y",
            "-i", input_path,
            "-vf", vf_chain,
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-threads", "8",
            "-b:v", bitrate,
            "-maxrate", "14M",
            "-bufsize", "24M",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart",
            output_path
        ]

        start_time = time.time()
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        elapsed = time.time() - start_time

        if proc.returncode != 0:
            raise RuntimeError(f"Optical flow synthesis failed: {proc.stderr}")

        file_size = os.path.getsize(output_path) if os.path.exists(output_path) else 0

        return {
            "status": "ok",
            "input": input_path,
            "output": os.path.abspath(output_path),
            "target_fps": target_fps,
            "mode": mode,
            "scd_threshold": scd_threshold,
            "render_time_seconds": round(elapsed, 2),
            "file_size_bytes": file_size,
            "file_size_mb": round(file_size / (1024 * 1024), 2)
        }
