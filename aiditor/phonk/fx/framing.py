"""
Smart Framing, Aspect Ratio & Subject Protection FX
===================================================
Formats video for TikTok/Reels/Shorts (9:16), YouTube (16:9), or Cinema (2.35:1).
Protects the main subject / car by avoiding redundant multi-pass crops and centering dynamically.
"""

from enum import Enum
from typing import Dict, Any, Optional


class AspectRatio(str, Enum):
    VERTICAL_9_16 = "9:16"       # TikTok / Instagram Reels / YouTube Shorts
    HORIZONTAL_16_9 = "16:9"     # Standard Widescreen
    ANAMORPHIC_2_35 = "2.35:1"   # Cinematic Letterbox
    SQUARE_1_1 = "1:1"           # Square (1:1)


class FramingFX:
    """Builds intelligent scaling and cropping filters without cutting off or covering the subject."""

    @staticmethod
    def build_framing_filter(
        target_aspect: AspectRatio = AspectRatio.VERTICAL_9_16,
        saliency_center: Optional[Dict[str, Any]] = None,
        target_w: int = 480,
        target_h: int = 854,
        source_w: Optional[int] = None,
        source_h: Optional[int] = None
    ) -> str:
        """
        Builds a single-pass framing filter.
        If source is already matching the target aspect ratio (e.g. vertical 9:16),
        it preserves the entire subject character without horizontal crop loss.
        """
        saliency = saliency_center or {"center_x": 0.5, "center_y": 0.5}
        cx = max(0.15, min(0.85, float(saliency.get("center_x", 0.5))))

        # Detect if source is already vertical (aspect < 0.65)
        is_source_vertical = False
        if source_w and source_h and source_h > 0:
            aspect = source_w / source_h
            if 0.48 <= aspect <= 0.65:
                is_source_vertical = True

        if target_aspect == AspectRatio.VERTICAL_9_16:
            if is_source_vertical:
                # Video is already vertical! Preserve all subject pixels with direct scale
                return f"scale={target_w}:{target_h}"
            else:
                # Widescreen source -> 9:16 crop centered on subject focal point cx
                return f"crop=w=ih*9/16:h=ih:x='min(max(0\\,(iw-ow)*{cx:.2f})\\,iw-ow)':y=0,scale={target_w}:{target_h}"

        elif target_aspect == AspectRatio.HORIZONTAL_16_9:
            return f"scale={target_w}:{target_h}:force_original_aspect_ratio=increase,crop={target_w}:{target_h}"

        elif target_aspect == AspectRatio.ANAMORPHIC_2_35:
            matte_h = int(target_w / 2.35)
            pad_y = (target_h - matte_h) // 2
            return f"scale={target_w}:{matte_h},pad={target_w}:{target_h}:0:{pad_y}:color=black"

        elif target_aspect == AspectRatio.SQUARE_1_1:
            return f"crop=w='min(iw\\,ih)':h='min(iw\\,ih)':x='(iw-ow)/2':y='(ih-oh)/2',scale={target_w}:{target_h}"

        return f"scale={target_w}:{target_h}"
