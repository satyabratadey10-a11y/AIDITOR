"""
Cyberpunk HUD & Motion Callout Badges
====================================
Generates animated futuristic target lock brackets, velocity telemetry,
and cyber callout badges pinned to the tracked object.
"""

from typing import List, Dict, Any, Optional
from .point_tracker import PointTracker


class HUDCalloutGenerator:
    """Generates motion-pinned HUD callout filters."""

    @staticmethod
    def build_cyber_callout_filter(
        trajectory: List[Dict[str, Any]],
        title: str = "TRACKED TARGET",
        subtitle: str = "SYSTEM LOCKED",
        target_w: int = 480,
        target_h: int = 854,
        color: str = "0x00FFCC",  # Cyber Neon Cyan
        font_size: int = 16
    ) -> str:
        """
        Creates motion-tracked HUD badge, crosshairs, and telemetry overlay filters.
        """
        # Clean title & subtitle of unescaped special characters
        clean_title = title.replace(":", "\\:").replace("'", "").upper()
        clean_sub = subtitle.replace(":", "\\:").replace("'", "").upper()

        x_expr, y_expr = PointTracker.build_tracking_expression(
            trajectory=trajectory,
            target_w=target_w,
            target_h=target_h,
            offset_x=-int(target_w * 0.12),
            offset_y=-int(target_h * 0.14)
        )

        cx_expr, cy_expr = PointTracker.build_tracking_expression(
            trajectory=trajectory,
            target_w=target_w,
            target_h=target_h,
            offset_x=0,
            offset_y=0
        )

        filters = []

        # 1. Target Lock Crosshair: [ + ]
        reticle_filter = (
            f"drawtext=text='[ + ]':"
            f"fontcolor={color}:"
            f"fontsize={font_size + 8}:"
            f"x='{cx_expr}-text_w/2':"
            f"y='{cy_expr}-text_h/2':"
            f"shadowcolor=black:shadowx=2:shadowy=2"
        )
        filters.append(reticle_filter)

        # 2. Main Title Callout Badge (e.g. "[ NISSAN GT-R R34 ]")
        badge_filter = (
            f"drawtext=text='{clean_title}':"
            f"fontcolor=white:"
            f"fontsize={font_size}:"
            f"box=1:boxcolor=0x0a1018@0.85:boxborderw=5:"
            f"x='{x_expr}':"
            f"y='{y_expr}':"
            f"shadowcolor={color}:shadowx=1:shadowy=1"
        )
        filters.append(badge_filter)

        # 3. Telemetry Subtitle (e.g. "STATUS // SPD 145 KM/H")
        telemetry_filter = (
            f"drawtext=text='{clean_sub} // SPD 142 KM/H':"
            f"fontcolor={color}:"
            f"fontsize={max(10, font_size - 4)}:"
            f"box=1:boxcolor=0x000000@0.60:boxborderw=3:"
            f"x='{x_expr}':"
            f"y='{y_expr}+{font_size + 8}'"
        )
        filters.append(telemetry_filter)

        return ",".join(filters)
