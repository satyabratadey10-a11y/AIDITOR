"""
High-Performance Video Render Pipeline
======================================
Executes streamlined, lightning-fast FFmpeg encoding in a single pass.
Zero redundant disk writes, multi-threaded CPU acceleration, and clean After Effects FX.
"""

import subprocess
import os
from typing import List, Dict, Any, Optional, Callable
from .filtergraph_builder import FiltergraphBuilder
from ..fx.framing import AspectRatio
from ..fx.color_grade import EditStyle
from ..tools.media_probe import MediaProbe


class RenderPipeline:
    """Orchestrates video rendering and encoding passes with maximum performance."""

    def __init__(
        self,
        output_fps: int = 60,
        crf: int = 20,
        preset: str = "ultrafast",
        target_w: int = 480,
        target_h: int = 854
    ):
        self.output_fps = output_fps
        self.crf = crf
        self.preset = preset
        self.target_w = target_w
        self.target_h = target_h

    def render_single_source_edit(
        self,
        video_path: str,
        audio_path: str,
        output_path: str,
        beat_timestamps: List[float],
        target_aspect: AspectRatio = AspectRatio.VERTICAL_9_16,
        style: EditStyle = EditStyle.TOKYO_MIDNIGHT,
        saliency_center: Dict[str, float] = None,
        duration: Optional[float] = None,
        shake_intensity: float = 1.0,
        rgb_split_intensity: float = 1.0,
        flash_intensity: float = 0.35,
        bass_boost_db: float = 5.0,
        target_w: Optional[int] = None,
        target_h: Optional[int] = None,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """
        Renders a full-featured Phonk car edit in a single fast FFmpeg pass.
        """
        w = target_w or self.target_w
        h = target_h or self.target_h

        # Probe source dimensions to avoid unwanted cropping of vertical videos
        src_w, src_h = None, None
        try:
            v_info = MediaProbe.get_video_info(video_path)
            src_w = v_info.get("width")
            src_h = v_info.get("height")
        except Exception:
            pass

        if progress_callback:
            progress_callback(f"Building single-pass filtergraph ({w}x{h} @ {self.output_fps}fps)...")

        video_filter = FiltergraphBuilder.build_full_edit_filter(
            target_aspect=target_aspect,
            style=style,
            beat_timestamps=beat_timestamps,
            saliency_center=saliency_center,
            shake_intensity=shake_intensity,
            rgb_split_intensity=rgb_split_intensity,
            flash_intensity=flash_intensity,
            enable_glow=True,
            vignette_strength=0.35,
            target_w=w,
            target_h=h,
            source_w=src_w,
            source_h=src_h
        )

        audio_filter = FiltergraphBuilder.build_audio_filter(bass_boost_db=bass_boost_db)

        # Build FFmpeg command
        cmd = ["ffmpeg", "-hide_banner", "-y"]

        # Video input
        cmd.extend(["-i", video_path])

        # Audio input
        cmd.extend(["-i", audio_path])

        # Video filter
        if video_filter:
            cmd.extend(["-vf", video_filter])

        # Audio filter
        if audio_filter:
            cmd.extend(["-af", audio_filter])

        # Duration limit if specified
        if duration and duration > 0:
            cmd.extend(["-t", str(duration)])
        else:
            cmd.append("-shortest")

        # Multi-threaded fast encoding
        cmd.extend([
            "-c:v", "libx264",
            "-preset", self.preset,
            "-crf", str(self.crf),
            "-pix_fmt", "yuv420p",
            "-r", str(self.output_fps),
            "-threads", "8",
            "-c:a", "aac",
            "-b:a", "320k",
            "-movflags", "+faststart",
            output_path
        ])

        if progress_callback:
            progress_callback(f"Executing fast single-pass render to {output_path}...")

        proc = subprocess.run(cmd, capture_output=True, text=True)

        if proc.returncode != 0:
            raise RuntimeError(f"FFmpeg rendering failed:\n{proc.stderr}")

        if progress_callback:
            progress_callback("Render successfully completed!")

        return output_path
