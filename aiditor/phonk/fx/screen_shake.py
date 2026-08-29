"""
Glitch-Free Screen Shake & Impact FX
====================================
Generates smooth After Effects / S-Shake camera impacts synchronized to beats
across the ENTIRE duration of the video, with safe subject boundaries.
"""

from typing import List


class ScreenShakeFX:
    """Builds camera shake expressions triggered on beat impacts across the full timeline."""

    @staticmethod
    def build_beat_shake_filter(
        beat_timestamps: List[float],
        intensity: float = 1.0,
        shake_duration: float = 0.16,
        target_w: int = 480,
        target_h: int = 854
    ) -> str:
        """
        Creates a time-reactive directional camera impact filter across all beats.
        """
        if not beat_timestamps or intensity <= 0.0:
            return "null"

        amp_x = max(6, int(14 * intensity))
        amp_y = max(4, int(10 * intensity))

        # Filter beats evenly across the entire song duration (minimum 0.30s between shakes)
        active_beats = []
        for b in beat_timestamps:
            if not active_beats or (b - active_beats[-1] >= 0.28):
                active_beats.append(b)

        if not active_beats:
            return "null"

        terms_x = []
        terms_y = []

        for b in active_beats:
            b_end = b + shake_duration
            cond = f"between(t\\,{b:.3f}\\,{b_end:.3f})"
            dt = f"(t-{b:.3f})"
            decay = f"(1-{dt}/{shake_duration:.3f})"

            sx = f"({cond} * {amp_x} * {decay} * sin(45*{dt}))"
            sy = f"({cond} * {amp_y} * {decay} * cos(55*{dt}))"
            terms_x.append(sx)
            terms_y.append(sy)

        expr_x = " + ".join(terms_x)
        expr_y = " + ".join(terms_y)

        # Subtle 2% border margin protects the car/character from being covered
        filter_str = (
            f"crop=w=iw*0.98:h=ih*0.98:"
            f"x='(in_w-out_w)/2 + ({expr_x})':"
            f"y='(in_h-out_h)/2 + ({expr_y})',"
            f"scale={target_w}:{target_h}"
        )

        return filter_str
