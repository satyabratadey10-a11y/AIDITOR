"""
Phonk Audio Processing & Beat Engine
"""

from typing import Dict, Any
from .beat_detector import BeatDetector
from .transient_analyzer import TransientAnalyzer
from .audio_effects import AudioMasteringEngine


class PhonkAudioSuite:
    """Unified Phonk Audio Analysis & Processing Suite."""

    def __init__(self, audio_path: str):
        self.audio_path = audio_path
        self.beat_detector = BeatDetector(audio_path)

    def analyze(self) -> Dict[str, Any]:
        beats_info = self.beat_detector.detect_beats()
        sections_info = TransientAnalyzer.identify_drop_and_sections(beats_info)
        beats_info.update(sections_info)
        return beats_info
