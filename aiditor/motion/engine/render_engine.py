"""
Render Engine for Tracker Motion Studio
=======================================
Executes single-pass multi-threaded FFmpeg rendering for motion tracking, rotoscoping, and camera solves.
"""

import subprocess
import os
from typing import Optional, Callable
from ..presets import TrackingConfig
from .filtergraph_compiler import TrackerFiltergraphCompiler
from ..tracker import PointTracker


class TrackerRenderEngine:
    """Executes FFmpeg tracking and rotoscoping render passes."""

    def __init__(self, config: Optional[TrackingConfig] = None):
        self.config = config or TrackingConfig()

    def render(
        self,
        video_path: str,
        output_path: str,
        config: Optional[TrackingConfig] = None,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """
        Renders the tracked/rotoscoped video in a single pass.
        """
        cfg = config or self.config

        if progress_callback:
            progress_callback(f"Analyzing motion and compiling {cfg.preset.value} filtergraph...")

        vf, fc, temp_trf = TrackerFiltergraphCompiler.compile(
            video_path=video_path,
            config=cfg
        )

        cmd = ["ffmpeg", "-hide_banner", "-y", "-i", video_path]

        if fc:
            cmd.extend(["-filter_complex", fc])
        elif vf:
            cmd.extend(["-vf", vf])

        cmd.extend([
            "-c:v", "libx264",
            "-preset", cfg.preset_ffmpeg,
            "-crf", str(cfg.crf),
            "-pix_fmt", "yuv420p",
            "-r", str(cfg.fps),
            "-threads", "8",
            "-c:a", "copy",
            "-movflags", "+faststart",
            output_path
        ])

        try:
            if progress_callback:
                progress_callback(f"Rendering {cfg.preset.value} to {output_path}...")

            proc = subprocess.run(cmd, capture_output=True, text=False)

            # Fallback if audio copy fails (e.g. format requires aac)
            if proc.returncode != 0 and "-c:a copy" in " ".join(cmd):
                cmd[cmd.index("-c:a") + 1] = "aac"
                proc = subprocess.run(cmd, capture_output=True, text=False)

            if proc.returncode != 0:
                err_msg = proc.stderr.decode("utf-8", errors="replace")
                raise RuntimeError(f"Tracker rendering failed:\n{err_msg}")

            if progress_callback:
                progress_callback("Render completed successfully!")

            return output_path
        finally:
            if temp_trf and os.path.exists(temp_trf):
                os.remove(temp_trf)
