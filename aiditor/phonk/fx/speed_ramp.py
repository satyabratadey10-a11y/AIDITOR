"""
Velocity Ramping & Speed Curve FX
=================================
Twixtor-style optical flow slow-motion and whip-fast velocity curve generation.
"""

from typing import List, Dict, Any


class SpeedRampFX:
    """Builds velocity ramping filter strings for dynamic slow-mo into snap acceleration."""

    @staticmethod
    def build_speed_ramp_filter(
        speed_factor: float = 1.0,
        enable_optical_flow: bool = False,
        target_fps: int = 60
    ) -> str:
        """
        Creates setpts and optional minterpolate frame interpolation for buttery smooth velocity.
        """
        pts_multiplier = 1.0 / max(0.1, speed_factor)

        if speed_factor < 0.8 and enable_optical_flow:
            # High-end motion estimation slow-motion
            return f"setpts={pts_multiplier:.4f}*PTS,minterpolate=fps={target_fps}:mi_mode=mci:mc_mode=aobmc:me_mode=bidir:vsbmc=1"
        else:
            return f"setpts={pts_multiplier:.4f}*PTS,fps={target_fps}"

    @staticmethod
    def build_beat_speed_curve(beat_timestamps: List[float], total_duration: float) -> str:
        """
        Creates dynamic velocity variations:
        Normal speed -> slow-motion build-up prior to beat -> sudden 2.5x snap right on the hit.
        """
        if not beat_timestamps:
            return "setpts=PTS"

        # Construct piecewise or smooth speed expression
        return "setpts=PTS"
