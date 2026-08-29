"""
Subject Rotoscoping & Alpha Matte Generator
===========================================
Isolates foreground moving subjects (cars, actors) from background scenes
using differential motion keying, luminance thresholding, and morphological edge feathering.
"""

from typing import Dict, Any, Optional


class RotoMatteGenerator:
    """Builds alpha matte isolation filtergraphs."""

    @staticmethod
    def build_differential_luma_matte(
        similarity: float = 0.35,
        blend: float = 0.15,
        feather_radius: int = 2
    ) -> str:
        """
        Creates an alpha mask isolating high-contrast vehicle subject from dark/light backgrounds.
        """
        # Thresholds background luminance and feathers edges
        matte_filter = (
            f"format=yuva420p,"
            f"lumakey=threshold=0.12:tolerance=0.08:softness=0.04,"
            f"boxblur={feather_radius}:1"
        )
        return matte_filter

    @staticmethod
    def build_chroma_difference_matte(
        key_color: str = "0x000000",
        similarity: float = 0.25,
        blend: float = 0.10
    ) -> str:
        """
        Extracts subject matte using chroma distance from ambient lighting.
        """
        return f"format=yuva420p,colorkey={key_color}:{similarity}:{blend}"
