"""
AIDITOR: The Autonomous AI Video Editing & Motion VFX Core
==========================================================
Zero-dependency, high-throughput video processing, optical flow interpolation,
motion tracking, audio-visual rhythm sync, and programmatic motion graphics
engineered for AI Agents and CLI workflows.
"""

__version__ = "3.0.0"
__author__ = "Google DeepMind / Antigravity Team"
__license__ = "Apache-2.0"

from .motion.api import MotionTrackingAPI
from .motion.flow import OpticalFlowInterpolator
from .motion.curves import SpeedGraph, CubicBezier, EasingPreset
from .phonk.api import PhonkStudioAPI
from .phonk.tools.media_probe import MediaProbe

__all__ = [
    "MotionTrackingAPI",
    "OpticalFlowInterpolator",
    "SpeedGraph",
    "CubicBezier",
    "EasingPreset",
    "PhonkStudioAPI",
    "MediaProbe",
]
