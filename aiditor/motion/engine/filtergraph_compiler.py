"""
Filtergraph Compiler for Tracker Motion Studio
==============================================
Compiles modular video tracking, rotoscoping, and camera transformation graphs.
"""

from typing import Dict, Any, List, Optional, Tuple
from ..presets import TrackerPreset, TrackingConfig
from ..roto import LayerCompositor, NeonOutlineFX
from ..tracker import PointTracker, HUDCalloutGenerator, TextPinningGenerator
from ..camera import LockOnCameraTracker, CameraStabilizer


class TrackerFiltergraphCompiler:
    """Compiles FFmpeg filterchains and filtercomplexes for tracking effects."""

    @staticmethod
    def compile(
        video_path: str,
        config: TrackingConfig,
        trajectory: Optional[List[Dict[str, Any]]] = None
    ) -> Tuple[Optional[str], Optional[str], Optional[str]]:
        """
        Compiles the appropriate filter structure.
        Returns: (vf_filter_str, filter_complex_str, temp_trf_path)
        """
        w, h = config.target_w, config.target_h

        # 1. BEHIND_SUBJECT_TEXT (Roto-motion)
        if config.preset == TrackerPreset.BEHIND_SUBJECT_TEXT:
            fc = LayerCompositor.build_behind_text_filtercomplex(
                text=config.target_text,
                target_w=w,
                target_h=h,
                font_size=max(24, int(w * 0.09)),
                enable_neon_glow=True
            )
            return None, fc, None

        # 2. DUAL_TONE_ROTO
        elif config.preset == TrackerPreset.DUAL_TONE_ROTO:
            fc = LayerCompositor.build_dual_tone_roto_filtercomplex(
                target_w=w,
                target_h=h,
                bg_mood="monochrome"
            )
            return None, fc, None

        # 3. NEON_EDGE_SABER
        elif config.preset == TrackerPreset.NEON_EDGE_SABER:
            fc = NeonOutlineFX.build_neon_saber_filtercomplex(
                target_w=w,
                target_h=h,
                neon_color="cyan"
            )
            return None, fc, None

        # 4. LOCK_ON_CAMERA
        elif config.preset == TrackerPreset.LOCK_ON_CAMERA:
            tracker = LockOnCameraTracker(video_path)
            temp_trf, transform_f = tracker.build_lock_on_filter(smoothing=2)
            vf = f"scale={w}:{h},{transform_f}"
            return vf, None, temp_trf

        # 5. FACE_LOCK_CAMERA
        elif config.preset == TrackerPreset.FACE_LOCK_CAMERA:
            from ..camera.face_tracker import FaceCameraTracker
            face_tracker = FaceCameraTracker(video_path)
            vf = face_tracker.build_face_lock_filter(target_w=w, target_h=h, zoom_factor=0.50)
            return vf, None, None

        # 6. ACTION_STABILIZE
        elif config.preset == TrackerPreset.ACTION_STABILIZE:
            stabilizer = CameraStabilizer(video_path)
            temp_trf, transform_f = stabilizer.build_stabilize_filter(smoothing=config.smoothing)
            vf = f"scale={w}:{h},{transform_f}"
            return vf, None, temp_trf

        # 6. HUD_CYBER_CALLOUT (Default Point Tracking)
        else:
            if not trajectory:
                pt_tracker = PointTracker(video_path)
                trajectory = pt_tracker.track_object_trajectory()

            hud_f = HUDCalloutGenerator.build_cyber_callout_filter(
                trajectory=trajectory,
                title=config.target_text,
                subtitle=config.subtitle_text,
                target_w=w,
                target_h=h,
                color=config.color
            )
            vf = f"scale={w}:{h},{hud_f}"
            return vf, None, None
