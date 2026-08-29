"""
Action Camera Video Stabilizer
==============================
Applies multi-axis camera motion stabilization (translation, rotation, rolling shutter reduction).
"""

import subprocess
import tempfile
import os
from typing import Tuple


class CameraStabilizer:
    """Stabilizes handheld and action camera shakes."""

    def __init__(self, video_path: str):
        self.video_path = video_path

    def build_stabilize_filter(
        self,
        smoothing: int = 30,
        shakiness: int = 6,
        accuracy: int = 8,
        max_angle: float = 0.05,
        zoom: int = 0
    ) -> Tuple[str, str]:
        """
        Creates two-pass stabilization transforms.
        Returns (temp_trf_path, vidstabtransform_filter_str).
        """
        tf = tempfile.NamedTemporaryFile(suffix=".trf", delete=False)
        temp_trf = tf.name
        tf.close()

        detect_cmd = [
            "ffmpeg", "-hide_banner", "-y",
            "-i", self.video_path,
            "-vf", f"scale=480:-2,vidstabdetect=stepsize=6:shakiness={shakiness}:accuracy={accuracy}:result={temp_trf}",
            "-f", "null",
            "-"
        ]
        subprocess.run(detect_cmd, capture_output=True, text=False, check=True)

        transform_filter = f"vidstabtransform=input={temp_trf}:smoothing={smoothing}:optzoom=1:zoom={zoom}:maxangle={max_angle}:interpol=bicubic"
        return temp_trf, transform_filter
