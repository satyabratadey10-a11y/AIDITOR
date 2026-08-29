"""
Motion & Velocity Dynamics Analyzer
===================================
Analyzes optical flow, camera motion vectors, translation/rotation energy,
and classifies scenes into high-speed drift, flyby, smooth pan, or hero static shots.
"""

import subprocess
import tempfile
import os
import math
from typing import List, Dict, Any
from ..tools.media_probe import MediaProbe


class MotionAnalyzer:
    """Extracts optical flow, camera trajectory, and dynamic motion energy per segment."""

    def __init__(self, video_path: str):
        self.video_path = video_path
        self.video_info = MediaProbe.get_video_info(video_path)

    def analyze_segment_motion(self, start_time: float, duration: float) -> Dict[str, Any]:
        """
        Runs fast vidstabdetect pass to extract camera transform coordinates (dx, dy, da, zoom).
        Downsamples to 480p for ultra-fast motion estimation.
        """
        temp_trf = tempfile.mktemp(suffix=".trf")
        try:
            # Downscale for ultra-fast analysis speed
            cmd = [
                "ffmpeg",
                "-hide_banner",
                "-ss", str(start_time),
                "-t", str(min(duration, 5.0)),
                "-i", self.video_path,
                "-vf", f"scale=480:-2,vidstabdetect=stepsize=8:shakiness=6:accuracy=6:result={temp_trf}",
                "-f", "null",
                "-"
            ]
            subprocess.run(cmd, capture_output=True, text=True)

            if not os.path.exists(temp_trf) or os.path.getsize(temp_trf) == 0:
                return self._fallback_motion_stats()

            dx_list = []
            dy_list = []
            da_list = []
            zoom_list = []
            energy_list = []

            with open(temp_trf, "r") as f:
                for line in f:
                    parts = line.strip().split()
                    if len(parts) >= 5:
                        try:
                            dx = float(parts[1])
                            dy = float(parts[2])
                            da = float(parts[3])
                            zoom = float(parts[4])

                            # Motion magnitude scaled to original resolution
                            scale_mult = self.video_info["width"] / 480.0
                            linear_speed = math.sqrt(dx * dx + dy * dy) * scale_mult
                            angular_speed = abs(da) * 100.0
                            zoom_speed = abs(zoom - 1.0) * 150.0
                            total_energy = linear_speed + angular_speed + zoom_speed

                            dx_list.append(dx * scale_mult)
                            dy_list.append(dy * scale_mult)
                            da_list.append(da)
                            zoom_list.append(zoom)
                            energy_list.append(total_energy)
                        except ValueError:
                            continue

            if not energy_list:
                return self._fallback_motion_stats()

            avg_energy = sum(energy_list) / len(energy_list)
            max_energy = max(energy_list)
            avg_dx = sum(abs(x) for x in dx_list) / len(dx_list)
            avg_dy = sum(abs(y) for y in dy_list) / len(dy_list)
            avg_da = sum(abs(a) for a in da_list) / len(da_list)

            # Classify motion pattern
            if max_energy > 45.0 or (avg_energy > 20.0 and avg_da > 0.08):
                motion_type = "DRIFT_AGGRESSIVE"
            elif avg_energy > 15.0:
                motion_type = "HIGH_SPEED_ACTION"
            elif avg_energy > 5.0:
                motion_type = "SMOOTH_PAN"
            else:
                motion_type = "HERO_STATIC"

            # Normalize motion score [0.0 - 1.0]
            normalized_score = min(1.0, max(0.0, avg_energy / 35.0))

            return {
                "motion_type": motion_type,
                "motion_score": round(normalized_score, 3),
                "avg_energy": round(avg_energy, 2),
                "max_energy": round(max_energy, 2),
                "lateral_drift_factor": round(avg_dx, 2),
                "vertical_bump_factor": round(avg_dy, 2),
                "rotation_drift_factor": round(avg_da, 3),
                "sample_points": len(energy_list)
            }

        finally:
            if os.path.exists(temp_trf):
                os.remove(temp_trf)

    def _fallback_motion_stats(self) -> Dict[str, Any]:
        return {
            "motion_type": "HERO_STATIC",
            "motion_score": 0.2,
            "avg_energy": 3.0,
            "max_energy": 5.0,
            "lateral_drift_factor": 1.0,
            "vertical_bump_factor": 1.0,
            "rotation_drift_factor": 0.0,
            "sample_points": 0
        }

    def analyze_full_video(self, scenes: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        annotated_scenes = []
        for scene in scenes:
            m_stats = self.analyze_segment_motion(scene["start_time"], scene["duration"])
            annotated = dict(scene)
            annotated.update(m_stats)
            annotated_scenes.append(annotated)
        return annotated_scenes
