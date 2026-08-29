"""
High-Level Python API for Tracker Motion Studio
================================================
Provides intuitive, professional Python classes for video rotoscoping,
camera solves, point tracking, and mobile-style motion graphics pinning.
"""

from typing import Optional, Callable, List, Dict, Any
from .presets import TrackerPreset, TrackingConfig
from .engine.render_engine import TrackerRenderEngine
from .analyzer.real_vision_analyzer import RealVisionAnalyzer
from .camera.motion_solver import CameraMotionSolver
from .camera.lock_on import LockOnCameraTracker
from .camera.stabilizer import CameraStabilizer
from .tracker.point_tracker import PointTracker
from .roto.layer_compositor import LayerCompositor


class RotoMotionEngine:
    """Engine for subject rotoscoping, alpha matting, and layered graphics."""

    def __init__(self, video_path: str):
        self.video_path = video_path

    def render_behind_text(
        self,
        text: str,
        output_path: str,
        resolution: str = "480p",
        font_size: int = 48,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Composites 3D typography BEHIND the rotoscoped moving vehicle."""
        cfg = TrackingConfig(
            preset=TrackerPreset.BEHIND_SUBJECT_TEXT,
            target_text=text,
            resolution=resolution
        )
        engine = TrackerRenderEngine(cfg)
        return engine.render(self.video_path, output_path, config=cfg, progress_callback=progress_callback)

    def render_dual_tone(
        self,
        output_path: str,
        resolution: str = "480p",
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Applies B&W/Cyber background separation with hyper-saturated vehicle."""
        cfg = TrackingConfig(
            preset=TrackerPreset.DUAL_TONE_ROTO,
            resolution=resolution
        )
        engine = TrackerRenderEngine(cfg)
        return engine.render(self.video_path, output_path, config=cfg, progress_callback=progress_callback)

    def render_neon_saber(
        self,
        output_path: str,
        resolution: str = "480p",
        color: str = "cyan",
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Applies electric neon contour outlines around the subject."""
        cfg = TrackingConfig(
            preset=TrackerPreset.NEON_EDGE_SABER,
            resolution=resolution
        )
        engine = TrackerRenderEngine(cfg)
        return engine.render(self.video_path, output_path, config=cfg, progress_callback=progress_callback)


class CameraTracker:
    """Solves 2D/3D camera trajectories, stabilization, and lock-on effects."""

    def __init__(self, video_path: str):
        self.video_path = video_path

    def solve_trajectory(self) -> List[Dict[str, Any]]:
        solver = CameraMotionSolver(self.video_path)
        return solver.solve_trajectory()

    def lock_on(
        self,
        output_path: str,
        resolution: str = "480p",
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Pins camera perspective to the moving car."""
        cfg = TrackingConfig(
            preset=TrackerPreset.LOCK_ON_CAMERA,
            resolution=resolution
        )
        engine = TrackerRenderEngine(cfg)
        return engine.render(self.video_path, output_path, config=cfg, progress_callback=progress_callback)

    def stabilize(
        self,
        output_path: str,
        smoothing: int = 30,
        resolution: str = "480p",
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Stabilizes handheld or action camera shake."""
        cfg = TrackingConfig(
            preset=TrackerPreset.ACTION_STABILIZE,
            smoothing=smoothing,
            resolution=resolution
        )
        engine = TrackerRenderEngine(cfg)
        return engine.render(self.video_path, output_path, config=cfg, progress_callback=progress_callback)


class TrackerMotionStudio:
    """
    Main Studio Class for Tracker Motion Effects.
    Combines rotoscoping, camera tracking, and motion callout graphics in one clean interface.
    """

    def __init__(self, default_resolution: str = "480p"):
        self.resolution = default_resolution

    def apply_hud_callout(
        self,
        video_path: str,
        output_path: str,
        title: str = "TRACKED TARGET",
        subtitle: str = "SYSTEM LOCKED",
        color: str = "0x00FFCC",
        resolution: Optional[str] = None,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Attaches a cyberpunk target lock reticle and speed telemetry HUD badge."""
        cfg = TrackingConfig(
            preset=TrackerPreset.HUD_CYBER_CALLOUT,
            target_text=title,
            subtitle_text=subtitle,
            color=color,
            resolution=resolution or self.resolution
        )
        engine = TrackerRenderEngine(cfg)
        return engine.render(video_path, output_path, config=cfg, progress_callback=progress_callback)

    def apply_behind_subject_text(
        self,
        video_path: str,
        output_path: str,
        text: str = "DRIFT KING",
        resolution: Optional[str] = None,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Rotoscopes vehicle and places 3D typography BEHIND the car."""
        roto = RotoMotionEngine(video_path)
        return roto.render_behind_text(
            text=text,
            output_path=output_path,
            resolution=resolution or self.resolution,
            progress_callback=progress_callback
        )

    def apply_lock_on_camera(
        self,
        video_path: str,
        output_path: str,
        resolution: Optional[str] = None,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Locks camera angle directly onto the moving subject/vehicle."""
        cam = CameraTracker(video_path)
        return cam.lock_on(
            output_path=output_path,
            resolution=resolution or self.resolution,
            progress_callback=progress_callback
        )

    def apply_face_tracking(
        self,
        video_path: str,
        output_path: str,
        resolution: Optional[str] = None,
        zoom_factor: float = 0.72,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Locks camera perspective onto the face/head across the video."""
        cfg = TrackingConfig(
            preset=TrackerPreset.FACE_LOCK_CAMERA,
            resolution=resolution or self.resolution
        )
        engine = TrackerRenderEngine(cfg)
        return engine.render(video_path, output_path, config=cfg, progress_callback=progress_callback)

    def apply_dual_tone_roto(
        self,
        video_path: str,
        output_path: str,
        resolution: Optional[str] = None,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """B&W background separation with saturated glowing subject."""
        roto = RotoMotionEngine(video_path)
        return roto.render_dual_tone(
            output_path=output_path,
            resolution=resolution or self.resolution,
            progress_callback=progress_callback
        )

    def apply_neon_saber_outline(
        self,
        video_path: str,
        output_path: str,
        color: str = "cyan",
        resolution: Optional[str] = None,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Generates electric neon contour outline around subject."""
        roto = RotoMotionEngine(video_path)
        return roto.render_neon_saber(
            output_path=output_path,
            resolution=resolution or self.resolution,
            color=color,
            progress_callback=progress_callback
        )

    def inspect_real_objects(
        self,
        video_path: str,
        sample_fps: int = 3
    ) -> Dict[str, Any]:
        """Runs real computer vision object detection and returns 4-point lock-in anchors."""
        analyzer = RealVisionAnalyzer(video_path)
        return analyzer.analyze_real_objects(sample_fps=sample_fps)

    def stabilize(
        self,
        video_path: str,
        output_path: str,
        smoothing: int = 30,
        resolution: Optional[str] = None,
        progress_callback: Optional[Callable[[str], None]] = None
    ) -> str:
        """Smoothes camera jitter and shakiness."""
        cam = CameraTracker(video_path)
        return cam.stabilize(
            output_path=output_path,
            smoothing=smoothing,
            resolution=resolution or self.resolution,
            progress_callback=progress_callback
        )


# Unified Alias
MotionTrackingAPI = TrackerMotionStudio

