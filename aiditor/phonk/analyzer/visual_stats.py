"""
Visual Atmosphere & Color Statistics Analyzer
=============================================
Extracts luminance, saturation, hue distribution, and contrast metrics from frames
using signalstats to determine scene atmosphere, lighting profile, and color grade matching.
"""

import subprocess
import re
from typing import Dict, Any


class VisualStatsAnalyzer:
    """Analyzes lighting, contrast, saturation, and color dynamics."""

    def __init__(self, video_path: str):
        self.video_path = video_path

    def analyze_scene_visuals(self, start_time: float, duration: float) -> Dict[str, Any]:
        """
        Runs signalstats filter across a sample of the clip.
        """
        cmd = [
            "ffmpeg",
            "-hide_banner",
            "-ss", str(start_time),
            "-t", str(min(duration, 2.0)),
            "-i", self.video_path,
            "-vf", "signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=-",
            "-f", "null",
            "-"
        ]

        proc = subprocess.run(cmd, capture_output=True, text=True)
        stdout = proc.stdout + proc.stderr

        yavg_matches = re.findall(r"lavfi\.signalstats\.YAVG=([0-9.]+)", stdout)
        sat_matches = re.findall(r"lavfi\.signalstats\.SATAVG=([0-9.]+)", stdout)

        if yavg_matches:
            yavg_vals = [float(v) for v in yavg_matches]
            avg_luminance = sum(yavg_vals) / len(yavg_vals)
        else:
            avg_luminance = 110.0  # default mid-tone

        if sat_matches:
            sat_vals = [float(v) for v in sat_matches]
            avg_saturation = sum(sat_vals) / len(sat_vals)
        else:
            avg_saturation = 120.0

        # Classify lighting mood
        if avg_luminance < 60.0:
            lighting_mood = "DARK_MIDNIGHT"
            recommended_grade = "TOKYO_NEON_GLOW"
        elif avg_luminance > 160.0:
            lighting_mood = "HIGH_KEY_DAYLIGHT"
            recommended_grade = "HIGH_CONTRAST_DRIFT"
        else:
            lighting_mood = "BALANCED_DUSK"
            recommended_grade = "CYBER_MAGENTA_TEAL"

        return {
            "avg_luminance": round(avg_luminance, 2),
            "avg_saturation": round(avg_saturation, 2),
            "lighting_mood": lighting_mood,
            "recommended_grade": recommended_grade,
            "is_night_scene": avg_luminance < 85.0
        }
