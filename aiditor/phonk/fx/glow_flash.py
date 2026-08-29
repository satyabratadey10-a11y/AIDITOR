"""
Glow, Flash & Optical Bloom FX
==============================
Rhythmic exposure pops, headlight neon bloom, and beat flash transitions across the full timeline.
"""

from typing import List


class GlowFlashFX:
    """Builds beat-reactive exposure flashes and neon bloom filters."""

    @staticmethod
    def build_beat_flash_filter(
        beat_timestamps: List[float],
        flash_strength: float = 0.35,
        flash_duration: float = 0.10
    ) -> str:
        """
        Creates sudden brightness and contrast spikes on beat timestamps across the whole song.
        """
        if not beat_timestamps or flash_strength <= 0.0:
            return "null"

        brightness_val = round(min(0.35, flash_strength * 0.7), 2)
        contrast_val = round(1.0 + flash_strength * 0.4, 2)

        # Sample beats with minimal 0.25s interval so flashes are rhythmic, not blinding
        active_beats = []
        for b in beat_timestamps:
            if not active_beats or (b - active_beats[-1] >= 0.25):
                active_beats.append(b)

        if not active_beats:
            return "null"

        cond_list = []
        for b in active_beats:
            b_end = b + flash_duration
            cond_list.append(f"between(t\\,{b:.3f}\\,{b_end:.3f})")

        timeline_expr = " + ".join(cond_list)
        return f"eq=brightness={brightness_val}:contrast={contrast_val}:enable='{timeline_expr}'"

    @staticmethod
    def build_neon_bloom_filter() -> str:
        """
        Enhances edge glow and headlight halos cleanly without oversaturating.
        """
        return "unsharp=luma_msize_x=5:luma_msize_y=5:luma_amount=1.2:chroma_amount=0.6"
