"""
Media Probe Utility
===================
Uses ffprobe to extract rich technical and structural metadata from video and audio files.
"""

import json
import subprocess
import os
from typing import Dict, Any, Optional, Tuple


class MediaProbe:
    """Extracts media metadata using standard ffprobe."""

    @staticmethod
    def probe(file_path: str) -> Dict[str, Any]:
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"Media file not found: {file_path}")

        cmd = [
            "ffprobe",
            "-v", "quiet",
            "-print_format", "json",
            "-show_format",
            "-show_streams",
            file_path
        ]

        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return json.loads(result.stdout)

    @staticmethod
    def get_video_info(file_path: str) -> Dict[str, Any]:
        data = MediaProbe.probe(file_path)
        video_stream = None
        for s in data.get("streams", []):
            if s.get("codec_type") == "video":
                video_stream = s
                break

        if not video_stream:
            raise ValueError(f"No video stream found in: {file_path}")

        # Parse frame rate
        r_frame_rate = video_stream.get("r_frame_rate", "30/1")
        try:
            num, den = map(int, r_frame_rate.split("/"))
            fps = num / den if den != 0 else 30.0
        except Exception:
            fps = 30.0

        # Duration
        duration_str = video_stream.get("duration") or data.get("format", {}).get("duration", "0")
        try:
            duration = float(duration_str)
        except Exception:
            duration = 0.0

        width = int(video_stream.get("width", 1920))
        height = int(video_stream.get("height", 1080))
        pix_fmt = video_stream.get("pix_fmt", "yuv420p")
        codec = video_stream.get("codec_name", "h264")

        return {
            "path": file_path,
            "width": width,
            "height": height,
            "aspect_ratio": width / height if height > 0 else 1.777,
            "fps": fps,
            "duration": duration,
            "total_frames": int(duration * fps),
            "pix_fmt": pix_fmt,
            "codec": codec,
            "bitrate": int(data.get("format", {}).get("bit_rate", 0))
        }

    @staticmethod
    def get_audio_info(file_path: str) -> Optional[Dict[str, Any]]:
        data = MediaProbe.probe(file_path)
        audio_stream = None
        for s in data.get("streams", []):
            if s.get("codec_type") == "audio":
                audio_stream = s
                break

        if not audio_stream:
            return None

        duration_str = audio_stream.get("duration") or data.get("format", {}).get("duration", "0")
        try:
            duration = float(duration_str)
        except Exception:
            duration = 0.0

        sample_rate = int(audio_stream.get("sample_rate", 44100))
        channels = int(audio_stream.get("channels", 2))

        return {
            "path": file_path,
            "duration": duration,
            "sample_rate": sample_rate,
            "channels": channels,
            "codec": audio_stream.get("codec_name", "aac"),
            "bitrate": int(audio_stream.get("bit_rate", 0) or data.get("format", {}).get("bit_rate", 0))
        }
