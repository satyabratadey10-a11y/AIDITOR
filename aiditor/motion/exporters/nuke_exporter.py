"""
Foundry Nuke .chan & .nk Script Exporter
=======================================
Exports 3D camera transforms to standard Nuke .chan files and generates
native Nuke .nk node scripts with animated curves.
"""

import os
from typing import List, Dict, Any


class NukeExporter:
    """Exports camera tracking and 2D point tracks to Foundry Nuke formats."""

    @staticmethod
    def export_chan(
        trajectory: List[Dict[str, Any]],
        output_chan_path: str,
        focal_length_mm: float = 35.0,
        sensor_width_mm: float = 36.0
    ) -> str:
        """
        Exports standard Nuke 8-column .chan file:
        frame  tx  ty  tz  rx  ry  rz  focal
        """
        lines = []
        for pt in trajectory:
            frame = pt.get("frame", 1)
            dx = pt.get("dx", 0.0)
            dy = pt.get("dy", 0.0)
            da = pt.get("da", 0.0)
            zoom = pt.get("zoom", 1.0)

            tx = dx * 0.1
            ty = -dy * 0.1
            tz = (zoom - 1.0) * 100.0
            rx = 0.0
            ry = 0.0
            rz = da * 57.2958 # radians to degrees
            focal = focal_length_mm * zoom

            lines.append(f"{frame}\t{tx:.4f}\t{ty:.4f}\t{tz:.4f}\t{rx:.4f}\t{ry:.4f}\t{rz:.4f}\t{focal:.4f}")

        os.makedirs(os.path.dirname(os.path.abspath(output_chan_path)), exist_ok=True)
        with open(output_chan_path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")

        return output_chan_path

    @staticmethod
    def export_nuke_tracker_script(
        tracks: List[Dict[str, Any]],
        output_nk_path: str,
        image_w: int = 1920,
        image_h: int = 1080
    ) -> str:
        """
        Generates a Nuke .nk script with an animated Tracker4 node.
        """
        track_anim_x = []
        track_anim_y = []

        for pt in tracks:
            f = pt.get("frame", pt.get("t", 0))
            x = pt.get("x", 0.5) * image_w
            y = (1.0 - pt.get("y", 0.5)) * image_h # Nuke Y is bottom-left
            track_anim_x.append(f"x{f} {x:.2f}")
            track_anim_y.append(f"x{f} {y:.2f}")

        curve_x = " ".join(track_anim_x)
        curve_y = " ".join(track_anim_y)

        nk_script = f"""#! /usr/local/Nuke -nx
version 14.0
Root {{
 format "{image_w} {image_h} 0 0 {image_w} {image_h} 1.0"
}}
Tracker4 {{
 tracks {{ {{ 1 0 0 }}
   {{ "Track1" {{curve {curve_x}}} {{curve {curve_y}}} 1 1 1 0 0 0 }}
 }}
 name Tracker1
 selected true
 xpos 0
 ypos 0
}}
"""
        os.makedirs(os.path.dirname(os.path.abspath(output_nk_path)), exist_ok=True)
        with open(output_nk_path, "w", encoding="utf-8") as f:
            f.write(nk_script)

        return output_nk_path
