"""
Lock-On Camera Tracker & Object Pinning
=======================================
Pins camera perspective directly onto the tracked subject / car,
cancelling relative movement so the subject stays locked in frame while the background spins.
"""

import subprocess
import tempfile
import os
from typing import Dict, Any, Optional, Tuple


class LockOnCameraTracker:
    """Pins camera viewpoint to the moving vehicle/subject."""

    def __init__(self, video_path: str):
        self.video_path = video_path

    def build_lock_on_filter(
        self,
        shakiness: int = 8,
        accuracy: int = 8,
        smoothing: int = 2,
        zoom_compensation: float = 0.05
    ) -> Tuple[str, str]:
        """
        Generates dual-pass transform files for object lock-on tracking.
        Returns (temp_trf_path, vidstabtransform_filter_str).
        """
        tf = tempfile.NamedTemporaryFile(suffix=".trf", delete=False)
        temp_trf = tf.name
        tf.close()

        # Step 1: Detect motion vectors
        detect_cmd = [
            "ffmpeg", "-hide_banner", "-y",
            "-i", self.video_path,
            "-vf", f"scale=480:-2,vidstabdetect=stepsize=6:shakiness={shakiness}:accuracy={accuracy}:result={temp_trf}",
            "-f", "null",
            "-"
        ]
        subprocess.run(detect_cmd, capture_output=True, text=False, check=True)

        # Step 2: Inverse transform filter
        # smoothing=1 locks tightly to the object
        transform_filter = f"vidstabtransform=input={temp_trf}:smoothing={smoothing}:optzoom=2:zoom={int(zoom_compensation*100)}:interpol=bicubic"
        return temp_trf, transform_filter
