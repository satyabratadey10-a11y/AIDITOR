"""
Video Content & Object Analyzer Suite
"""

from typing import List, Dict, Any
from .scene_detector import SceneDetector
from .motion_analyzer import MotionAnalyzer
from .saliency_tracker import SaliencyTracker
from .visual_stats import VisualStatsAnalyzer
from ..tools.media_probe import MediaProbe


class VideoContentAnalyzer:
    """
    Unified Pro Video Content Analyzer.
    Extracts scenes, optical flow & kinetic energy, object tracking, and lighting atmosphere.
    """

    def __init__(self, video_path: str):
        self.video_path = video_path
        self.probe_info = MediaProbe.get_video_info(video_path)
        self.scene_detector = SceneDetector(video_path)
        self.motion_analyzer = MotionAnalyzer(video_path)
        self.saliency_tracker = SaliencyTracker(video_path)
        self.visual_analyzer = VisualStatsAnalyzer(video_path)

    def analyze_full(self) -> Dict[str, Any]:
        """
        Executes complete multi-dimensional video analysis.
        """
        # 1. Detect scene cuts
        raw_scenes = self.scene_detector.detect_scenes()

        # 2. Enrich each scene with motion, saliency, and visual stats
        analyzed_scenes = []
        for s in raw_scenes:
            motion = self.motion_analyzer.analyze_segment_motion(s["start_time"], s["duration"])
            saliency = self.saliency_tracker.track_subject_focal_point(s["start_time"], s["duration"])
            visuals = self.visual_analyzer.analyze_scene_visuals(s["start_time"], s["duration"])

            enriched = dict(s)
            enriched.update(motion)
            enriched["saliency"] = saliency
            enriched["visuals"] = visuals
            analyzed_scenes.append(enriched)

        # 3. Overall video profile
        total_motion = sum(s["motion_score"] for s in analyzed_scenes) / max(1, len(analyzed_scenes))
        high_action_scenes = [s for s in analyzed_scenes if s["motion_score"] > 0.5]

        return {
            "file": self.video_path,
            "info": self.probe_info,
            "scene_count": len(analyzed_scenes),
            "scenes": analyzed_scenes,
            "overall_motion_score": round(total_motion, 3),
            "high_action_ratio": round(len(high_action_scenes) / max(1, len(analyzed_scenes)), 2),
            "recommended_edit_style": "AGGRESSIVE_DRIFT" if total_motion > 0.4 else "AESTHETIC_STANCE"
        }
