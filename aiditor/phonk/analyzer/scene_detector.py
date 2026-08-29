"""
Scene & Shot Boundary Detector
==============================
Analyzes video stream to detect shot transitions, scene boundaries, cuts, and clip durations.
"""

import subprocess
import re
import os
from typing import List, Dict, Any
from ..tools.media_probe import MediaProbe


class SceneDetector:
    """Detects scene cuts, shot durations, and visual transition boundaries."""

    def __init__(self, video_path: str, threshold: float = 0.35):
        self.video_path = video_path
        self.threshold = threshold
        self.video_info = MediaProbe.get_video_info(video_path)

    def detect_scenes(self) -> List[Dict[str, Any]]:
        """
        Executes FFmpeg scene change filter to find timestamp markers of each cut.
        Returns a list of scene segments with start, end, duration, and frame indices.
        """
        duration = self.video_info["duration"]
        fps = self.video_info["fps"]

        # Run ffmpeg with select filter and showinfo to capture scene score
        cmd = [
            "ffmpeg",
            "-hide_banner",
            "-i", self.video_path,
            "-filter_complex",
            f"select='gt(scene,{self.threshold})',showinfo",
            "-f", "null",
            "-"
        ]

        proc = subprocess.run(cmd, capture_output=True, text=True)
        stderr_output = proc.stderr

        # Parse timestamps from showinfo output: pts_time: 12.345
        pts_matches = re.findall(r"pts_time:([0-9.]+)", stderr_output)
        timestamps = [0.0]
        for pts_str in pts_matches:
            t = float(pts_str)
            if t > timestamps[-1] + 0.2:  # debounce cuts closer than 200ms
                timestamps.append(t)

        if duration > timestamps[-1]:
            timestamps.append(duration)

        # Build scene segment objects
        scenes = []
        for i in range(len(timestamps) - 1):
            start = round(timestamps[i], 3)
            end = round(timestamps[i + 1], 3)
            seg_duration = round(end - start, 3)
            if seg_duration <= 0.05:
                continue

            scenes.append({
                "scene_id": i + 1,
                "start_time": start,
                "end_time": end,
                "duration": seg_duration,
                "start_frame": int(start * fps),
                "end_frame": int(end * fps),
                "frame_count": int(seg_duration * fps)
            })

        # If no cuts found or single shot, return entire video as one scene
        if not scenes:
            scenes = [{
                "scene_id": 1,
                "start_time": 0.0,
                "end_time": duration,
                "duration": duration,
                "start_frame": 0,
                "end_frame": int(duration * fps),
                "frame_count": int(duration * fps)
            }]

        return scenes
