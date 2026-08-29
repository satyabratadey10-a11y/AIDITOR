"""
Intelligent Video Sequence & Beat Synchronization Planner
=========================================================
Matches analyzed video clips/scenes to phonk beat grids, tempo, and audio sections.
"""

from typing import List, Dict, Any


class SequencePlanner:
    """Synchronizes scene selections and cuts with phonk beat grids."""

    @staticmethod
    def plan_edit_timeline(
        scenes: List[Dict[str, Any]],
        beat_data: Dict[str, Any],
        target_duration: float = None
    ) -> List[Dict[str, Any]]:
        """
        Assigns scenes to beat intervals.
        - High-motion scenes are prioritized during the drop/chorus.
        - Low-motion hero shots are assigned during the intro/build-up.
        """
        beats = beat_data.get("beats", [])
        drop_time = beat_data.get("drop_timestamp", 0.0)
        total_audio_duration = beat_data.get("duration", 30.0)

        edit_duration = min(total_audio_duration, target_duration) if target_duration else total_audio_duration

        if not scenes:
            return []

        # Sort scenes by motion score
        sorted_by_motion = sorted(scenes, key=lambda s: s.get("motion_score", 0.0), reverse=True)
        high_motion_scenes = [s for s in sorted_by_motion if s.get("motion_score", 0.0) >= 0.35]
        low_motion_scenes = [s for s in sorted_by_motion if s.get("motion_score", 0.0) < 0.35]

        if not high_motion_scenes:
            high_motion_scenes = sorted_by_motion
        if not low_motion_scenes:
            low_motion_scenes = sorted_by_motion

        # Create cut points at major beats (every 2nd or 4th beat, or heavy hits)
        cut_points = [0.0]
        for i, b in enumerate(beats):
            t = b["timestamp"]
            if t > edit_duration:
                break
            # Cut on bar start (every 4 beats) or heavy bass hits, minimum 0.7s per cut
            if (b["is_bar_start"] or b["is_heavy_hit"]) and (t - cut_points[-1] >= 0.7):
                cut_points.append(t)

        if cut_points[-1] < edit_duration:
            cut_points.append(edit_duration)

        timeline_segments = []
        high_idx = 0
        low_idx = 0

        for i in range(len(cut_points) - 1):
            t_start = cut_points[i]
            t_end = cut_points[i + 1]
            seg_dur = t_end - t_start

            # Determine whether this segment is before or after the drop
            is_in_drop = t_start >= drop_time

            if is_in_drop:
                # Pick high-motion drift scene
                scene_template = high_motion_scenes[high_idx % len(high_motion_scenes)]
                high_idx += 1
                energy_level = "MAXIMUM"
            else:
                # Pick hero static/stance scene
                scene_template = low_motion_scenes[low_idx % len(low_motion_scenes)]
                low_idx += 1
                energy_level = "BUILDUP"

            timeline_segments.append({
                "segment_index": i,
                "timeline_start": t_start,
                "timeline_end": t_end,
                "timeline_duration": seg_dur,
                "source_file": scene_template.get("source_file", scene_template.get("path")),
                "source_start": scene_template.get("start_time", 0.0),
                "source_duration": scene_template.get("duration", seg_dur),
                "motion_score": scene_template.get("motion_score", 0.5),
                "saliency": scene_template.get("saliency", {"center_x": 0.5, "center_y": 0.5}),
                "energy_level": energy_level,
                "is_drop_zone": is_in_drop
            })

        return timeline_segments
