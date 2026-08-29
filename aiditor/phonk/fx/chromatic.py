"""
Chromatic Aberration & RGB Split FX
===================================
Simulates lens dispersion and lateral RGB channel splitting on beats across the full track.
"""

from typing import List


class ChromaticAberrationFX:
    """Builds RGB channel split filter chains with timeline enable across all beats."""

    @staticmethod
    def build_beat_rgb_split(
        beat_timestamps: List[float],
        intensity: float = 1.0,
        pulse_duration: float = 0.10
    ) -> str:
        """
        Creates RGB channel offsets during beat pulses across the whole song.
        """
        if not beat_timestamps or intensity <= 0.0:
            return "null"

        shift_amount = max(3, int(10 * intensity))

        # Sample beats with minimal 0.22s interval to avoid constant blur
        active_beats = []
        for b in beat_timestamps:
            if not active_beats or (b - active_beats[-1] >= 0.22):
                active_beats.append(b)

        if not active_beats:
            return "null"

        cond_list = []
        for b in active_beats:
            b_end = b + pulse_duration
            cond_list.append(f"between(t\\,{b:.3f}\\,{b_end:.3f})")

        timeline_expr = " + ".join(cond_list)
        return f"rgbashift=rh={shift_amount}:bh=-{shift_amount}:edge=smear:enable='{timeline_expr}'"
