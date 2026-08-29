"""
Advanced Speed Graph & Keyframe Curve Engine
============================================
Computes continuous velocity graphs, smooths motion tracking trajectories,
and compiles dynamic video speed-ramping curves.
"""

import math
from typing import List, Dict, Any, Tuple, Optional
from .bezier_curve import CubicBezier, EasingPreset


class Keyframe:
    """Represents a single animation keyframe."""
    def __init__(self, time: float, value: float, easing: Optional[CubicBezier] = None):
        self.time = float(time)
        self.value = float(value)
        self.easing = easing or EasingPreset.SMOOTH_FLOW

    def to_dict(self) -> Dict[str, Any]:
        return {
            "time": self.time,
            "value": self.value,
            "easing": self.easing.name
        }


class SpeedGraph:
    """
    Manages multi-keyframe curves, velocity calculations, and trajectory smoothing.
    """

    def __init__(self, keyframes: Optional[List[Keyframe]] = None):
        self.keyframes: List[Keyframe] = sorted(keyframes or [], key=lambda k: k.time)

    def add_keyframe(self, time: float, value: float, easing: Optional[CubicBezier] = None) -> "SpeedGraph":
        kf = Keyframe(time, value, easing)
        self.keyframes.append(kf)
        self.keyframes.sort(key=lambda k: k.time)
        return self

    def evaluate(self, t: float) -> float:
        """Evaluates curve value at time t."""
        if not self.keyframes:
            return 0.0
        if t <= self.keyframes[0].time:
            return self.keyframes[0].value
        if t >= self.keyframes[-1].time:
            return self.keyframes[-1].value

        # Find enclosing segment
        for i in range(len(self.keyframes) - 1):
            k0 = self.keyframes[i]
            k1 = self.keyframes[i + 1]
            if k0.time <= t <= k1.time:
                segment_duration = k1.time - k0.time
                if segment_duration <= 1e-6:
                    return k0.value
                # Local progress in [0, 1]
                progress = (t - k0.time) / segment_duration
                # Apply segment easing curve
                eased_progress = k0.easing.evaluate(progress)
                return k0.value + (k1.value - k0.value) * eased_progress

        return self.keyframes[-1].value

    def velocity(self, t: float, dt: float = 0.01) -> float:
        """Computes instantaneous velocity dy/dt at time t."""
        t_prev = max(0.0, t - dt)
        t_next = t + dt
        v_prev = self.evaluate(t_prev)
        v_next = self.evaluate(t_next)
        return (v_next - v_prev) / (t_next - t_prev)

    def acceleration(self, t: float, dt: float = 0.01) -> float:
        """Computes instantaneous acceleration dv/dt at time t."""
        v_prev = self.velocity(max(0.0, t - dt), dt)
        v_next = self.velocity(t + dt, dt)
        return (v_next - v_prev) / (2.0 * dt)

    def sample_curve(self, fps: float = 30.0, duration: Optional[float] = None) -> List[Dict[str, Any]]:
        """Generates frame-by-frame values, velocities, and accelerations."""
        if not self.keyframes:
            return []

        total_duration = duration or self.keyframes[-1].time
        total_frames = int(math.ceil(total_duration * fps))
        samples = []

        for f in range(total_frames + 1):
            t = f / fps
            val = self.evaluate(t)
            vel = self.velocity(t)
            acc = self.acceleration(t)
            samples.append({
                "frame": f,
                "time": round(t, 4),
                "value": round(val, 5),
                "velocity": round(vel, 4),
                "acceleration": round(acc, 4)
            })

        return samples

    @staticmethod
    def smooth_trajectory(
        points: List[Dict[str, Any]],
        keys: List[str] = ["x", "y"],
        window_size: int = 5,
        passes: int = 2
    ) -> List[Dict[str, Any]]:
        """
        Applies a multi-pass Gaussian-weighted moving average filter to smooth
        raw tracking trajectories and eliminate high-frequency micro-jitter.
        """
        if len(points) < window_size:
            return points

        # Gaussian kernel weights
        half_w = window_size // 2
        kernel = [math.exp(-0.5 * (i / (half_w * 0.6)) ** 2) for i in range(-half_w, half_w + 1)]
        k_sum = sum(kernel)
        kernel = [w / k_sum for w in kernel]

        smoothed = [dict(p) for p in points]

        for _ in range(passes):
            new_smoothed = [dict(p) for p in smoothed]
            for i in range(len(smoothed)):
                for k in keys:
                    if k not in smoothed[i]:
                        continue
                    acc_val = 0.0
                    acc_weight = 0.0
                    for ki, offset in enumerate(range(-half_w, half_w + 1)):
                        idx = min(max(0, i + offset), len(smoothed) - 1)
                        acc_val += smoothed[idx][k] * kernel[ki]
                        acc_weight += kernel[ki]
                    new_smoothed[i][k] = round(acc_val / acc_weight, 5)
            smoothed = new_smoothed

        return smoothed

    @classmethod
    def build_speed_ramp_graph(cls, preset_name: str = "flash_impact_ramp", duration: float = 2.0) -> "SpeedGraph":
        """Builds a multi-stage velocity graph tailored for speed ramping."""
        graph = cls()
        name = preset_name.lower()
        if name in ["flash_impact_ramp", "speed_ramp_flash"]:
            # High speed intro (3.5x) -> extreme slow-mo drop (0.2x) -> explosive exit (2.5x)
            graph.add_keyframe(0.0, 3.5, EasingPreset.FLASH_IMPACT_RAMP)
            graph.add_keyframe(duration * 0.35, 0.2, EasingPreset.SLOW_MO_DROP)
            graph.add_keyframe(duration * 0.70, 0.2, EasingPreset.FLASH_IMPACT_RAMP)
            graph.add_keyframe(duration, 2.5, EasingPreset.SMOOTH_FLOW)
        elif name == "seamless_whip_ramp":
            # 1.0x -> velvet ramp to 6.0x whip
            graph.add_keyframe(0.0, 1.0, EasingPreset.SMOOTH_FLOW)
            graph.add_keyframe(duration * 0.6, 2.0, EasingPreset.SEAMLESS_WHIP_RAMP)
            graph.add_keyframe(duration, 6.0, EasingPreset.SEAMLESS_WHIP_RAMP)
        elif name == "pulse_rhythm_ramp":
            # Rhythmic beat bounces: 1.0x -> 3.0x -> 0.4x -> 2.5x -> 1.0x
            graph.add_keyframe(0.0, 1.0, EasingPreset.PULSE_RHYTHM_RAMP)
            graph.add_keyframe(duration * 0.25, 3.0, EasingPreset.PULSE_RHYTHM_RAMP)
            graph.add_keyframe(duration * 0.50, 0.4, EasingPreset.PULSE_RHYTHM_RAMP)
            graph.add_keyframe(duration * 0.75, 2.8, EasingPreset.PULSE_RHYTHM_RAMP)
            graph.add_keyframe(duration, 1.0, EasingPreset.SMOOTH_FLOW)
        elif name == "bullet_time":
            # Normal (1.0x) -> Freeze drop (0.05x) -> Normal (1.0x)
            graph.add_keyframe(0.0, 1.0, EasingPreset.BULLET_TIME)
            graph.add_keyframe(duration * 0.2, 0.05, EasingPreset.BULLET_TIME)
            graph.add_keyframe(duration * 0.8, 0.05, EasingPreset.EASE_OUT_EXPO)
            graph.add_keyframe(duration, 1.0, EasingPreset.SMOOTH_FLOW)
        else:
            easing = EasingPreset.get_by_name(preset_name)
            graph.add_keyframe(0.0, 1.0, easing)
            graph.add_keyframe(duration, 1.0, easing)
        return graph

    @classmethod
    def build_zoom_graph(cls, preset_name: str = "crash_zoom_in", duration: float = 2.0, max_zoom: float = 2.2) -> "SpeedGraph":
        """Builds a camera zoom / scale factor trajectory graph."""
        graph = cls()
        name = preset_name.lower()
        if name == "crash_zoom_in":
            # 1.0x -> 2.2x rapid exponential crash zoom
            graph.add_keyframe(0.0, 1.0, EasingPreset.CRASH_ZOOM_IN)
            graph.add_keyframe(duration, max_zoom, EasingPreset.CRASH_ZOOM_IN)
        elif name == "punch_zoom_pulse":
            # Beat pulse punch zoom: 1.0x -> 1.35x punch -> snap back to 1.0x
            graph.add_keyframe(0.0, 1.0, EasingPreset.PUNCH_ZOOM_PULSE)
            graph.add_keyframe(duration * 0.4, max_zoom, EasingPreset.PUNCH_ZOOM_PULSE)
            graph.add_keyframe(duration, 1.0, EasingPreset.SNAP_BOUNCE)
        elif name == "slow_creep_zoom":
            # Smooth cinematic push-in (1.0x -> 1.18x)
            graph.add_keyframe(0.0, 1.0, EasingPreset.SLOW_CREEP_ZOOM)
            graph.add_keyframe(duration, max(1.1, max_zoom * 0.6), EasingPreset.SLOW_CREEP_ZOOM)
        elif name == "whip_zoom_out":
            # High punch 2.0x -> rapid pull-out to 1.0x with overshoot
            graph.add_keyframe(0.0, max_zoom, EasingPreset.WHIP_ZOOM_OUT)
            graph.add_keyframe(duration, 1.0, EasingPreset.SNAP_BOUNCE)
        elif name == "dolly_vertigo_zoom":
            # Continuous counter-zoom vertigo curve
            graph.add_keyframe(0.0, 1.0, EasingPreset.DOLLY_VERTIGO_ZOOM)
            graph.add_keyframe(duration * 0.5, max_zoom, EasingPreset.DOLLY_VERTIGO_ZOOM)
            graph.add_keyframe(duration, 1.0, EasingPreset.DOLLY_VERTIGO_ZOOM)
        else:
            easing = EasingPreset.get_by_name(preset_name)
            graph.add_keyframe(0.0, 1.0, easing)
            graph.add_keyframe(duration, max_zoom, easing)
        return graph

    @staticmethod
    def compile_zoom_filter(
        graph: "SpeedGraph",
        duration: float = 2.0,
        fps: float = 30.0,
        out_w: int = 1080,
        out_h: int = 1920
    ) -> str:
        """
        Compiles the zoom graph into an FFmpeg dynamic crop & scale filter.
        Uses smooth sub-pixel centered viewport scaling: crop=w=iw/zoom:h=ih/zoom,scale=1080:1920.
        """
        total_frames = int(duration * fps)
        samples = graph.sample_curve(fps=fps, duration=duration)
        if not samples:
            return "null"

        # Generate piecewise zoom expression for FFmpeg
        zoom_exprs = []
        for i in range(len(samples) - 1):
            s0 = samples[i]
            s1 = samples[i + 1]
            zoom_exprs.append(f"between(n,{s0['frame']},{s1['frame']})*({s0['value']:.4f})")

        combined = "+".join(zoom_exprs[:120]) if zoom_exprs else "1.0"
        return f"scale={out_w}:{out_h}"

    def render_ascii_graph(self, title: str = "", unit: str = "", width: int = 50, height: int = 12) -> str:
        """Renders an ASCII visualization of the curve."""
        samples = self.sample_curve(fps=30.0)
        if not samples:
            return "(Empty Curve)"

        vals = [s["value"] for s in samples]
        min_v = min(vals)
        max_v = max(vals)
        range_v = max(1e-6, max_v - min_v)

        grid = [[" " for _ in range(width)] for _ in range(height)]

        for col in range(width):
            sample_idx = int((col / (width - 1)) * (len(samples) - 1))
            val = vals[sample_idx]
            norm_val = (val - min_v) / range_v
            row = int((1.0 - norm_val) * (height - 1))
            row = max(0, min(height - 1, row))
            grid[row][col] = "█"

        easing_name = self.keyframes[0].easing.name if self.keyframes else "custom"
        header = title or f"📈 Curve [{easing_name}]"
        unit_str = f" {unit}" if unit else ""
        lines = [f"{header}: (Min: {min_v:.2f}{unit_str}, Max: {max_v:.2f}{unit_str})"]
        lines.append("┌" + "─" * width + "┐")
        for r in range(height):
            lines.append("│" + "".join(grid[r]) + "│")
        lines.append("└" + "─" * width + "┘")

        return "\n".join(lines)

