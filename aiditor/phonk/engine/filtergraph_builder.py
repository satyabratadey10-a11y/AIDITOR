"""
FFmpeg Filtergraph Compiler & Pipeline Builder
==============================================
Constructs optimized multi-stage filtergraphs for glitch-free After Effects-style rendering.
"""

from typing import List, Dict, Any, Optional
from ..fx.framing import FramingFX, AspectRatio
from ..fx.color_grade import ColorGradeFX, EditStyle
from ..fx.screen_shake import ScreenShakeFX
from ..fx.chromatic import ChromaticAberrationFX
from ..fx.glow_flash import GlowFlashFX
from ..audio.audio_effects import AudioMasteringEngine


class FiltergraphBuilder:
    """Builds comprehensive video and audio filtergraph commands."""

    @staticmethod
    def build_full_edit_filter(
        target_aspect: AspectRatio = AspectRatio.VERTICAL_9_16,
        style: EditStyle = EditStyle.TOKYO_MIDNIGHT,
        beat_timestamps: List[float] = None,
        saliency_center: Dict[str, float] = None,
        shake_intensity: float = 1.0,
        rgb_split_intensity: float = 1.0,
        flash_intensity: float = 0.35,
        enable_glow: bool = True,
        vignette_strength: float = 0.35,
        target_w: int = 480,
        target_h: int = 854,
        source_w: Optional[int] = None,
        source_h: Optional[int] = None
    ) -> str:
        beats = beat_timestamps or []
        filters: List[str] = []

        # 1. Framing & Saliency Aspect Ratio (Single-pass protection)
        framing_f = FramingFX.build_framing_filter(
            target_aspect=target_aspect,
            saliency_center=saliency_center,
            target_w=target_w,
            target_h=target_h,
            source_w=source_w,
            source_h=source_h
        )
        if framing_f:
            filters.append(framing_f)

        # 2. Color Grading Profile
        color_f = ColorGradeFX.build_color_grade_filter(style, vignette_strength)
        if color_f:
            filters.append(color_f)

        # 3. Beat-synced Camera Shake (S-Shake)
        if beats and shake_intensity > 0:
            shake_f = ScreenShakeFX.build_beat_shake_filter(
                beats,
                intensity=shake_intensity,
                target_w=target_w,
                target_h=target_h
            )
            if shake_f and shake_f != "null":
                filters.append(shake_f)

        # 4. Beat-synced Chromatic Aberration / RGB Split
        if beats and rgb_split_intensity > 0:
            rgb_f = ChromaticAberrationFX.build_beat_rgb_split(beats, intensity=rgb_split_intensity)
            if rgb_f and rgb_f != "null":
                filters.append(rgb_f)

        # 5. Beat-synced Exposure Pop & Flash
        if beats and flash_intensity > 0:
            flash_f = GlowFlashFX.build_beat_flash_filter(beats, flash_strength=flash_intensity)
            if flash_f and flash_f != "null":
                filters.append(flash_f)

        # 6. Optical Glow / Bloom
        if enable_glow:
            glow_f = GlowFlashFX.build_neon_bloom_filter()
            if glow_f:
                filters.append(glow_f)

        return ",".join(filters)

    @staticmethod
    def build_audio_filter(bass_boost_db: float = 5.0, treble_boost_db: float = 2.5) -> str:
        return AudioMasteringEngine.build_phonk_master_filter(bass_boost_db, treble_boost_db)
