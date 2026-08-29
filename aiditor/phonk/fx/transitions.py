"""
Transition FX Generator
=======================
Builds high-impact cinematic transitions (whip pans, zoom glitches, radial wipes) between video clips.
"""

from enum import Enum
import random
from typing import List


class TransitionType(str, Enum):
    RADIAL = "radial"
    WHIP_LEFT = "wipeleft"
    WHIP_RIGHT = "wiperight"
    CIRCLE_CROP = "circlecrop"
    DISSOLVE = "dissolve"
    PIXELIZE = "pixelize"
    HBLUR = "hblur"
    FADE_WHITE = "fadewhite"
    ZOOM_IN = "zoomin"


class TransitionFX:
    """Selects and parameterizes transitions between clips."""

    PHONK_HOT_TRANSITIONS = [
        TransitionType.WHIP_LEFT,
        TransitionType.WHIP_RIGHT,
        TransitionType.RADIAL,
        TransitionType.CIRCLE_CROP,
        TransitionType.HBLUR,
        TransitionType.FADE_WHITE
    ]

    @classmethod
    def get_random_phonk_transition(cls) -> TransitionType:
        return random.choice(cls.PHONK_HOT_TRANSITIONS)
