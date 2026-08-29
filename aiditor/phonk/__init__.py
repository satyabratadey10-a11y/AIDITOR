"""
Phonk Video Studio (ApexPhonkStudio)
=====================================
A pro-grade automated video content analyzer and After Effects-level video editing pipeline.
Built with zero external dependencies (Stock Python 3 + FFmpeg/FFprobe).
"""

from .api import PhonkCarEditor, VideoAnalyzer, PhonkAudioAnalyzer, EditConfig, EditStyle, AspectRatio

__version__ = "2.0.0"
__all__ = [
    "PhonkCarEditor",
    "VideoAnalyzer",
    "PhonkAudioAnalyzer",
    "EditConfig",
    "EditStyle",
    "AspectRatio",
]
