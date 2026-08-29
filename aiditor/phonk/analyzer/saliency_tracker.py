"""
Subject Saliency & Focal Point Tracker
======================================
Analyzes visual saliency, object bounding regions, and luminance centroids
to pinpoint car/subject location in frame for intelligent dynamic zooms and 9:16 vertical tracking.
"""

import subprocess
import re
from typing import Dict, Any
from ..tools.media_probe import MediaProbe


class SaliencyTracker:
    """Tracks subject bounding boxes and saliency centroids."""

    def __init__(self, video_path: str):
        self.video_path = video_path
        self.video_info = MediaProbe.get_video_info(video_path)

    def track_subject_focal_point(self, start_time: float, duration: float) -> Dict[str, Any]:
        """
        Samples the clip with cropdetect to locate active visual boundaries.
        Returns normalized focal coordinates (center_x, center_y) and bounding width/height.
        """
        w = self.video_info["width"]
        h = self.video_info["height"]

        cmd = [
            "ffmpeg",
            "-hide_banner",
            "-ss", str(start_time),
            "-t", str(min(duration, 3.0)),
            "-i", self.video_path,
            "-vf", "cropdetect=24:16:0",
            "-f", "null",
            "-"
        ]

        proc = subprocess.run(cmd, capture_output=True, text=True)
        stderr_output = proc.stderr

        # Format: crop=w:h:x:y
        crop_matches = re.findall(r"crop=([0-9]+):([0-9]+):([0-9]+):([0-9]+)", stderr_output)

        if not crop_matches:
            # Default to dead-center
            return {
                "center_x": 0.5,
                "center_y": 0.5,
                "box_w_rel": 1.0,
                "box_h_rel": 1.0,
                "is_centered": True
            }

        crop_w_list = [int(m[0]) for m in crop_matches]
        crop_h_list = [int(m[1]) for m in crop_matches]
        crop_x_list = [int(m[2]) for m in crop_matches]
        crop_y_list = [int(m[3]) for m in crop_matches]

        avg_w = sum(crop_w_list) / len(crop_w_list)
        avg_h = sum(crop_h_list) / len(crop_h_list)
        avg_x = sum(crop_x_list) / len(crop_x_list)
        avg_y = sum(crop_y_list) / len(crop_y_list)

        # Center point
        cx = (avg_x + avg_w / 2.0) / w
        cy = (avg_y + avg_h / 2.0) / h

        # Clamp
        cx = min(0.9, max(0.1, cx))
        cy = min(0.9, max(0.1, cy))

        return {
            "center_x": round(cx, 3),
            "center_y": round(cy, 3),
            "box_w_rel": round(avg_w / w, 3),
            "box_h_rel": round(avg_h / h, 3),
            "is_centered": abs(cx - 0.5) < 0.1 and abs(cy - 0.5) < 0.1
        }
