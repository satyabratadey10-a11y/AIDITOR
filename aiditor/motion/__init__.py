"""
Tracker Motion Studio - Python Roto-Motion & Camera/Object Tracking Suite
========================================================================
Pro-grade video rotoscoping, camera tracking, point tracking, and motion pinning
built for stock Python 3 standard library and FFmpeg.
"""

from .api import (
    TrackerMotionStudio,
    RotoMotionEngine,
    CameraTracker,
    PointTracker,
    RealVisionAnalyzer,
    TrackerPreset,
    TrackingConfig
)

__version__ = "1.0.0"
__all__ = [
    "TrackerMotionStudio",
    "RotoMotionEngine",
    "CameraTracker",
    "PointTracker",
    "RealVisionAnalyzer",
    "TrackerPreset",
    "TrackingConfig"
]
