"""
Floating 3D Motion Text & Element Pinning
========================================
Pins typography, speed badges, and brand tags to moving objects.
"""

from typing import List, Dict, Any, Optional
from .point_tracker import PointTracker


class TextPinningGenerator:
    """Generates motion-pinned typography filters."""

    @staticmethod
    def build_pinned_text_filter(
        trajectory: List[Dict[str, Any]],
        text: str,
        target_w: int = 480,
        target_h: int = 854,
        offset_y: int = -80,
        font_size: int = 28,
        color: str = "0xFFD700",  # Golden Neon
        enable_box: bool = False
    ) -> str:
        """
        Pins glowing 3D typography directly above or on the tracked vehicle.
        """
        x_expr, y_expr = PointTracker.build_tracking_expression(
            trajectory=trajectory,
            target_w=target_w,
            target_h=target_h,
            offset_x=0,
            offset_y=offset_y
        )

        box_str = ":box=1:boxcolor=black@0.65:boxborderw=6" if enable_box else ""

        filter_str = (
            f"drawtext=text='{text}':"
            f"fontcolor={color}:"
            f"fontsize={font_size}:"
            f"x='{x_expr}-text_w/2':"
            f"y='{y_expr}':"
            f"shadowcolor=0x000000@0.9:shadowx=4:shadowy=4"
            f"{box_str}"
        )
        return filter_str
