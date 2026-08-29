"""
Color Grading & Aesthetic Look Engine
=====================================
Professional Hollywood & Phonk LUT emulation, split toning, curves, and vignette for car edits.
"""

from enum import Enum
from typing import List


class EditStyle(str, Enum):
    TOKYO_MIDNIGHT = "tokyo_midnight"
    CYBER_DRIFT = "cyber_drift"
    HIGH_CONTRAST_DRIFT = "high_contrast_drift"
    MONOCHROME_ACID = "monochrome_acid"
    GOLDEN_HEAT = "golden_heat"
    CLEAN_NATURAL = "clean_natural"


class ColorGradeFX:
    """Builds professional color grading filter chains."""

    @staticmethod
    def build_color_grade_filter(style: EditStyle = EditStyle.TOKYO_MIDNIGHT, vignette_strength: float = 0.4) -> str:
        filters: List[str] = []

        if style == EditStyle.TOKYO_MIDNIGHT:
            # Crushed blacks, cyan/teal shadows, crimson highlights, intense contrast
            filters.append("eq=contrast=1.28:brightness=-0.04:saturation=1.35")
            filters.append("colorbalance=rs=-0.06:gs=0.04:bs=0.12:rm=0.08:gm=-0.02:bm=0.10:rh=0.15:gh=-0.05:bh=0.08")
            filters.append("curves=preset=strong_contrast")

        elif style == EditStyle.CYBER_DRIFT:
            # Electric magenta, purple shadows, cyan highlights
            filters.append("eq=contrast=1.32:brightness=-0.03:saturation=1.45")
            filters.append("colorbalance=rs=0.12:gs=-0.08:bs=0.18:rm=0.15:gm=-0.05:bm=0.10:rh=-0.05:gh=0.10:bh=0.15")

        elif style == EditStyle.HIGH_CONTRAST_DRIFT:
            # Aggressive punch, ultra sharp, metallic sheen
            filters.append("eq=contrast=1.40:brightness=-0.02:saturation=1.25")
            filters.append("colorbalance=rs=0.05:gs=0.0:bs=-0.05:rm=0.08:gm=0.04:bm=-0.04")

        elif style == EditStyle.MONOCHROME_ACID:
            # Gritty high-contrast B&W
            filters.append("eq=contrast=1.60:brightness=-0.05:saturation=0.0")
            filters.append("curves=preset=darker")

        elif style == EditStyle.GOLDEN_HEAT:
            # Amber dusk, golden reflections
            filters.append("eq=contrast=1.25:brightness=0.01:saturation=1.30")
            filters.append("colorbalance=rs=0.18:gs=0.08:bs=-0.12:rm=0.12:gm=0.05:bm=-0.08:rh=0.15:gh=0.05:bh=-0.10")

        else:  # CLEAN_NATURAL
            filters.append("eq=contrast=1.10:saturation=1.15")

        # Edge clarity / unsharp mask
        filters.append("unsharp=3:3:0.8:3:3:0.4")

        # Cinematic lens vignette
        if vignette_strength > 0.0:
            angle = 0.5 + (0.5 * vignette_strength)
            filters.append(f"vignette=angle={angle:.2f}:aspect=16/9")

        return ",".join(filters)
