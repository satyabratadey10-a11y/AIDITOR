"""
High-Level Python API for Phonk Video Studio
=============================================
Provides clean, intuitive Python interfaces for ultra-fast video content analysis and automated After Effects-grade editing.
"""

from dataclasses import dataclass, field
from typing import List, Dict, Any, Optional, Union
import os
import json

from .analyzer import VideoContentAnalyzer
from .audio import PhonkAudioSuite
from .fx import EditStyle, AspectRatio
from .engine import SequencePlanner, RenderPipeline
from .tools import MediaProbe, SyntheticAssetGenerator


@dataclass
class EditConfig:
    """Configuration options for automated Phonk editing."""
    style: EditStyle = EditStyle.TOKYO_MIDNIGHT
    aspect_ratio: AspectRatio = AspectRatio.VERTICAL_9_16
    resolution: str = "480p"  # "480p", "720p", "1080p"
    target_w: int = 480
    target_h: int = 854
    shake_intensity: float = 0.85
    rgb_split_intensity: float = 1.0
    flash_intensity: float = 0.35
    bass_boost_db: float = 5.0
    enable_optical_glow: bool = True
    output_fps: int = 60
    crf: int = 20
    preset: str = "ultrafast"
    target_duration: Optional[float] = None
    cached_video_analysis: Optional[Union[str, Dict[str, Any]]] = None
    cached_audio_analysis: Optional[Union[str, Dict[str, Any]]] = None

    def __post_init__(self):
        # Auto-compute target_w and target_h from resolution string if needed
        res = self.resolution.lower()
        if self.aspect_ratio == AspectRatio.VERTICAL_9_16:
            if "1080" in res:
                self.target_w, self.target_h = 1080, 1920
            elif "720" in res:
                self.target_w, self.target_h = 720, 1280
            else:  # default 480p
                self.target_w, self.target_h = 480, 854
        elif self.aspect_ratio == AspectRatio.HORIZONTAL_16_9:
            if "1080" in res:
                self.target_w, self.target_h = 1920, 1080
            elif "720" in res:
                self.target_w, self.target_h = 1280, 720
            else:
                self.target_w, self.target_h = 854, 480


class VideoAnalyzer:
    """High-level analyzer for visual scenes, motion dynamics, and car framing."""

    def __init__(self, video_path: str):
        self.video_path = video_path
        self._analyzer = VideoContentAnalyzer(video_path)

    def analyze(self) -> Dict[str, Any]:
        return self._analyzer.analyze_full()

    def get_scenes(self) -> List[Dict[str, Any]]:
        return self._analyzer.scene_detector.detect_scenes()

    def get_motion_profile(self) -> List[Dict[str, Any]]:
        scenes = self.get_scenes()
        return self._analyzer.motion_analyzer.analyze_full_video(scenes)

    def export_report_json(self, output_json: str) -> str:
        report = self.analyze()
        with open(output_json, "w") as f:
            json.dump(report, f, indent=2)
        return output_json


class PhonkAudioAnalyzer:
    """High-level analyzer for BPM, transients, beat drops, and energy curves."""

    def __init__(self, audio_path: str):
        self.audio_path = audio_path
        self._suite = PhonkAudioSuite(audio_path)

    def analyze(self) -> Dict[str, Any]:
        return self._suite.analyze()

    def get_bpm(self) -> float:
        res = self.analyze()
        return res.get("bpm", 130.0)

    def get_beat_timestamps(self) -> List[float]:
        res = self.analyze()
        return res.get("beat_timestamps", [])


class PhonkCarEditor:
    """
    Main Studio Editor Class.
    Combines high-precision beat extraction, subject protection, and ultra-fast single-pass rendering.
    """

    def __init__(self, config: Optional[EditConfig] = None):
        self.config = config or EditConfig()
        self.renderer = RenderPipeline(
            output_fps=self.config.output_fps,
            crf=self.config.crf,
            preset=self.config.preset,
            target_w=self.config.target_w,
            target_h=self.config.target_h
        )

    def create_phonk_car_edit(
        self,
        video_sources: Union[str, List[str]],
        audio_source: str,
        output_file: str,
        config: Optional[EditConfig] = None,
        progress_callback: Optional[callable] = None
    ) -> str:
        """
        Main pipeline method to produce an After Effects-grade phonk car edit in a single fast pass.
        """
        cfg = config or self.config

        if isinstance(video_sources, str):
            video_sources = [video_sources]

        # 1. High-precision Multi-band Beat Extraction
        if cfg.cached_audio_analysis:
            if isinstance(cfg.cached_audio_analysis, str) and os.path.exists(cfg.cached_audio_analysis):
                with open(cfg.cached_audio_analysis, "r") as f:
                    beat_data = json.load(f)
            else:
                beat_data = cfg.cached_audio_analysis
            if progress_callback:
                progress_callback(f"Loaded audio analysis: {beat_data.get('bpm')} BPM ({len(beat_data.get('beat_timestamps', []))} beats)")
        else:
            if progress_callback:
                progress_callback(f"Analyzing audio multi-band beats from: {audio_source}...")
            audio_suite = PhonkAudioSuite(audio_source)
            beat_data = audio_suite.analyze()
            if progress_callback:
                progress_callback(f"Detected BPM: {beat_data.get('bpm')} | Total beats: {len(beat_data.get('beat_timestamps', []))}")

        beat_timestamps = beat_data.get("beat_timestamps", [])

        # 2. Fast Saliency Focal Point Extraction
        saliency = {"center_x": 0.5, "center_y": 0.5}
        try:
            from .analyzer.saliency_tracker import SaliencyTracker
            tracker = SaliencyTracker(video_sources[0])
            saliency = tracker.track_subject_focal_point(start_time=0.0, duration=3.0)
        except Exception:
            pass

        # 3. Streamlined Fast Single-Pass Master Render
        if progress_callback:
            progress_callback(f"Rendering Phonk Edit ({cfg.target_w}x{cfg.target_h} @ {cfg.output_fps}fps) in single-pass...")

        return self.renderer.render_single_source_edit(
            video_path=video_sources[0],
            audio_path=audio_source,
            output_path=output_file,
            beat_timestamps=beat_timestamps,
            target_aspect=cfg.aspect_ratio,
            style=cfg.style,
            saliency_center=saliency,
            duration=cfg.target_duration,
            shake_intensity=cfg.shake_intensity,
            rgb_split_intensity=cfg.rgb_split_intensity,
            flash_intensity=cfg.flash_intensity,
            bass_boost_db=cfg.bass_boost_db,
            target_w=cfg.target_w,
            target_h=cfg.target_h,
            progress_callback=progress_callback
        )


# Unified Alias
PhonkStudioAPI = PhonkCarEditor

