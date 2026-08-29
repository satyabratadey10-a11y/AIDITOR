"""
Multi-Layer Roto Compositor
===========================
Composites 3D text, graphical elements, or particle fields BEHIND the rotoscoped moving subject,
and implements dual-tone background/subject separation grading.
"""

from typing import Dict, Any, Optional
from .matte_generator import RotoMatteGenerator


class LayerCompositor:
    """Builds complex multi-layer filtergraphs for rotoscoped compositing."""

    @staticmethod
    def build_behind_text_filtercomplex(
        text: str,
        target_w: int = 480,
        target_h: int = 854,
        font_size: int = 48,
        color: str = "white",
        text_y_rel: float = 0.40,
        enable_neon_glow: bool = True
    ) -> str:
        """
        Creates a 3-layer composite filtergraph:
        Layer 1 (Bottom): Background video with color toning.
        Layer 2 (Middle): Giant 3D bold typography / title graphics.
        Layer 3 (Top): Rotoscoped foreground car with alpha cutout.
        Result: The car drifts in front of the floating text, creating true 3D spatial depth!
        """
        y_pos = int(target_h * text_y_rel)
        glow_str = ":shadowcolor=0x00FFCC@0.8:shadowx=0:shadowy=0" if enable_neon_glow else ""

        # Filtergraph architecture:
        # [0:v] split=2 [bg_raw][fg_raw];
        # [bg_raw] scale={target_w}:{target_h},eq=brightness=-0.05:contrast=1.10,drawtext=text='{text}':fontsize={font_size}:fontcolor={color}:x='(w-text_w)/2':y='{y_pos}'{glow_str} [bg_with_text];
        # [fg_raw] scale={target_w}:{target_h},format=yuva420p,lumakey=threshold=0.15:tolerance=0.10:softness=0.05 [fg_matte];
        # [bg_with_text][fg_matte] overlay=shortest=1:format=auto
        filter_complex = (
            f"[0:v]split=2[bg_raw][fg_raw];"
            f"[bg_raw]scale={target_w}:{target_h},"
            f"eq=brightness=-0.04:contrast=1.12,"
            f"drawtext=text='{text.upper()}':"
            f"fontsize={font_size}:"
            f"fontcolor={color}:"
            f"x='(w-text_w)/2':"
            f"y='{y_pos}'"
            f"{glow_str}[bg_with_text];"
            f"[fg_raw]scale={target_w}:{target_h},"
            f"format=yuva420p,"
            f"lumakey=threshold=0.15:tolerance=0.10:softness=0.05[fg_matte];"
            f"[bg_with_text][fg_matte]overlay=shortest=1:format=auto"
        )
        return filter_complex

    @staticmethod
    def build_dual_tone_roto_filtercomplex(
        target_w: int = 480,
        target_h: int = 854,
        bg_mood: str = "monochrome"  # "monochrome" or "cyber_blue"
    ) -> str:
        """
        Separates background and subject:
        Background is converted to deep high-contrast B&W or cold cyan,
        while the foreground car retains full glowing RGB saturation.
        """
        if bg_mood == "monochrome":
            bg_grade = "hue=s=0,curves=preset=strong_contrast"
        else:
            bg_grade = "colorbalance=rs=-0.1:gs=0.05:bs=0.20:rm=0.0:gm=-0.05:bm=0.15,hue=s=0.4"

        filter_complex = (
            f"[0:v]split=2[bg_raw][fg_raw];"
            f"[bg_raw]scale={target_w}:{target_h},{bg_grade}[bg_graded];"
            f"[fg_raw]scale={target_w}:{target_h},"
            f"eq=saturation=1.40:contrast=1.20,"
            f"format=yuva420p,"
            f"lumakey=threshold=0.16:tolerance=0.12:softness=0.06[fg_matte];"
            f"[bg_graded][fg_matte]overlay=shortest=1:format=auto"
        )
        return filter_complex
