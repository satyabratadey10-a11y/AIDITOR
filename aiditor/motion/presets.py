"""
Tracker Motion App Style Presets
================================
Pre-configured motion tracking, rotoscoping, and camera solves matching mobile tracking apps.
"""

from enum import Enum
from dataclasses import dataclass
from typing import Optional, Dict, Any


class TrackerPreset(str, Enum):
    HUD_CYBER_CALLOUT = "hud_callout"        # Target lock reticle + speed telemetry badge
    BEHIND_SUBJECT_TEXT = "behind_text"      # 3D bold text layered BEHIND the rotoscoped car
    LOCK_ON_CAMERA = "lock_on"              # Camera locked directly onto the moving car
    FACE_LOCK_CAMERA = "face_lock"          # Camera locked directly onto the face
    DUAL_TONE_ROTO = "dual_tone"            # B&W cyber background + saturated glowing vehicle
    NEON_EDGE_SABER = "neon_saber"          # Cyberpunk electric neon contour outline
    ACTION_STABILIZE = "stabilize"          # Action Cam high-performance camera stabilizer


@dataclass
class TrackingConfig:
    """Configuration options for tracking and rotoscoping."""
    preset: TrackerPreset = TrackerPreset.HUD_CYBER_CALLOUT
    target_text: str = "TRACKED TARGET"
    subtitle_text: str = "SYSTEM LOCKED"
    color: str = "0x00FFCC"                  # Neon Cyan / Gold / Magenta
    resolution: str = "480p"                 # "480p", "720p", "1080p"
    target_w: int = 480
    target_h: int = 854
    fps: int = 60
    smoothing: int = 25
    preset_ffmpeg: str = "ultrafast"
    crf: int = 20

    def __post_init__(self):
        res = self.resolution.lower()
        if "1080" in res:
            self.target_w, self.target_h = 1080, 1920
        elif "720" in res:
            self.target_w, self.target_h = 720, 1280
        else:
            self.target_w, self.target_h = 480, 854
