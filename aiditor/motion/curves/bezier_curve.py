"""
Cubic Bézier Easing & Motion Curve Solver
==========================================
Provides sub-pixel accurate, mathematically continuous C^1/C^2 easing curves
matching After Effects Graph Editor, CSS Cubic-Bezier, and Blender F-Curves.
"""

import math
from typing import Tuple, Dict, List


class CubicBezier:
    """
    Evaluates a cubic Bézier curve defined by control points:
    P0 = (0, 0), P1 = (x1, y1), P2 = (x2, y2), P3 = (1, 1).
    Uses Newton-Raphson iteration with bisection fallback to solve for t given x.
    """

    def __init__(self, x1: float, y1: float, x2: float, y2: float, name: str = "custom"):
        self.x1 = max(0.0, min(1.0, float(x1)))
        self.y1 = float(y1)
        self.x2 = max(0.0, min(1.0, float(x2)))
        self.y2 = float(y2)
        self.name = name

    def _sample_curve_x(self, t: float) -> float:
        # P(t) = 3*t*(1-t)^2*x1 + 3*t^2*(1-t)*x2 + t^3
        return ((1.0 - 3.0 * self.x2 + 3.0 * self.x1) * t + (3.0 * self.x2 - 6.0 * self.x1)) * t * t + 3.0 * self.x1 * t

    def _sample_curve_y(self, t: float) -> float:
        return ((1.0 - 3.0 * self.y2 + 3.0 * self.y1) * t + (3.0 * self.y2 - 6.0 * self.y1)) * t * t + 3.0 * self.y1 * t

    def _sample_curve_derivative_x(self, t: float) -> float:
        return (3.0 * (1.0 - 3.0 * self.x2 + 3.0 * self.x1) * t + 2.0 * (3.0 * self.x2 - 6.0 * self.x1)) * t + 3.0 * self.x1

    def _solve_t_for_x(self, x: float, epsilon: float = 1e-6) -> float:
        # Clamp x
        if x <= 0.0:
            return 0.0
        if x >= 1.0:
            return 1.0

        # Newton-Raphson iteration
        t = x
        for _ in range(8):
            current_x = self._sample_curve_x(t) - x
            if abs(current_x) < epsilon:
                return t
            d_x = self._sample_curve_derivative_x(t)
            if abs(d_x) < 1e-6:
                break
            t -= current_x / d_x

        # Bisection fallback
        t0, t1 = 0.0, 1.0
        t = x
        while t0 < t1:
            current_x = self._sample_curve_x(t)
            if abs(current_x - x) < epsilon:
                return t
            if x > current_x:
                t0 = t
            else:
                t1 = t
            t = (t1 + t0) * 0.5

        return t

    def evaluate(self, x: float) -> float:
        """Evaluates output value Y for input progress X in range [0, 1]."""
        if x <= 0.0:
            return 0.0
        if x >= 1.0:
            return 1.0
        t = self._solve_t_for_x(x)
        return self._sample_curve_y(t)

    def velocity(self, x: float, delta: float = 0.001) -> float:
        """Calculates instantaneous velocity (dY/dX)."""
        x_prev = max(0.0, x - delta)
        x_next = min(1.0, x + delta)
        return (self.evaluate(x_next) - self.evaluate(x_prev)) / (x_next - x_prev)


class EasingPreset:
    """Standardized professional speed, zoom, and easing curves."""

    LINEAR = CubicBezier(0.0, 0.0, 1.0, 1.0, "linear")
    EASE_IN = CubicBezier(0.42, 0.0, 1.0, 1.0, "ease_in")
    EASE_OUT = CubicBezier(0.0, 0.0, 0.58, 1.0, "ease_out")
    EASE_IN_OUT = CubicBezier(0.42, 0.0, 0.58, 1.0, "ease_in_out")
    
    # Advanced Motion & VFX Curves
    EASE_OUT_EXPO = CubicBezier(0.16, 1.0, 0.3, 1.0, "ease_out_expo")
    EASE_IN_OUT_QUINT = CubicBezier(0.83, 0.0, 0.17, 1.0, "ease_in_out_quint")
    SMOOTH_FLOW = CubicBezier(0.25, 0.1, 0.25, 1.0, "smooth_flow")
    
    # Speed Ramping Profiles (for fast whip zooms, phonk impact ramps, slow-mo drop)
    SPEED_RAMP_FLASH = CubicBezier(0.05, 0.9, 0.1, 1.0, "speed_ramp_flash")
    FLASH_IMPACT_RAMP = CubicBezier(0.08, 0.82, 0.17, 1.0, "flash_impact_ramp")
    SEAMLESS_WHIP_RAMP = CubicBezier(0.65, 0.0, 0.35, 1.0, "seamless_whip_ramp")
    PULSE_RHYTHM_RAMP = CubicBezier(0.4, 0.0, 0.2, 1.0, "pulse_rhythm_ramp")
    SLOW_MO_DROP = CubicBezier(0.7, 0.0, 0.3, 1.0, "slow_mo_drop")
    BULLET_TIME = CubicBezier(0.85, 0.0, 0.15, 1.0, "bullet_time")
    SNAP_BOUNCE = CubicBezier(0.34, 1.56, 0.64, 1.0, "snap_bounce")

    # Camera Zoom & Scale Graph Profiles
    CRASH_ZOOM_IN = CubicBezier(0.12, 0.98, 0.24, 1.0, "crash_zoom_in")
    PUNCH_ZOOM_PULSE = CubicBezier(0.2, 1.35, 0.4, 1.0, "punch_zoom_pulse")
    SLOW_CREEP_ZOOM = CubicBezier(0.3, 0.1, 0.3, 1.0, "slow_creep_zoom")
    WHIP_ZOOM_OUT = CubicBezier(0.7, -0.2, 0.3, 1.0, "whip_zoom_out")
    DOLLY_VERTIGO_ZOOM = CubicBezier(0.77, 0.0, 0.175, 1.0, "dolly_vertigo_zoom")

    @classmethod
    def get_by_name(cls, name: str) -> CubicBezier:
        lookup = {
            "linear": cls.LINEAR,
            "ease_in": cls.EASE_IN,
            "ease_out": cls.EASE_OUT,
            "ease_in_out": cls.EASE_IN_OUT,
            "ease_out_expo": cls.EASE_OUT_EXPO,
            "ease_in_out_quint": cls.EASE_IN_OUT_QUINT,
            "smooth_flow": cls.SMOOTH_FLOW,
            "speed_ramp_flash": cls.SPEED_RAMP_FLASH,
            "flash_impact_ramp": cls.FLASH_IMPACT_RAMP,
            "seamless_whip_ramp": cls.SEAMLESS_WHIP_RAMP,
            "pulse_rhythm_ramp": cls.PULSE_RHYTHM_RAMP,
            "slow_mo_drop": cls.SLOW_MO_DROP,
            "bullet_time": cls.BULLET_TIME,
            "snap_bounce": cls.SNAP_BOUNCE,
            "crash_zoom_in": cls.CRASH_ZOOM_IN,
            "punch_zoom_pulse": cls.PUNCH_ZOOM_PULSE,
            "slow_creep_zoom": cls.SLOW_CREEP_ZOOM,
            "whip_zoom_out": cls.WHIP_ZOOM_OUT,
            "dolly_vertigo_zoom": cls.DOLLY_VERTIGO_ZOOM
        }
        return lookup.get(name.lower(), cls.SMOOTH_FLOW)

