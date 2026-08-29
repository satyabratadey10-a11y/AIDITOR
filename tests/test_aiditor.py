"""
AIDITOR Automated Test Suite
"""

import unittest
import os
import sys

# Add parent directory to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from aiditor import CubicBezier, EasingPreset, SpeedGraph, MediaProbe, PhonkStudioAPI


class TestAiditorCurves(unittest.TestCase):

    def test_cubic_bezier_linear(self):
        cb = EasingPreset.LINEAR
        self.assertAlmostEqual(cb.evaluate(0.0), 0.0, places=3)
        self.assertAlmostEqual(cb.evaluate(0.5), 0.5, places=3)
        self.assertAlmostEqual(cb.evaluate(1.0), 1.0, places=3)

    def test_cubic_bezier_ease_out(self):
        cb = EasingPreset.EASE_OUT_EXPO
        self.assertAlmostEqual(cb.evaluate(0.0), 0.0, places=3)
        self.assertGreater(cb.evaluate(0.5), 0.5)
        self.assertAlmostEqual(cb.evaluate(1.0), 1.0, places=3)

    def test_speed_graph_sampling(self):
        graph = SpeedGraph()
        graph.add_keyframe(0.0, 1.0, EasingPreset.SMOOTH_FLOW)
        graph.add_keyframe(1.0, 3.0, EasingPreset.SMOOTH_FLOW)
        
        samples = graph.sample_curve(fps=30.0, duration=1.0)
        self.assertEqual(len(samples), 31)
        self.assertAlmostEqual(samples[0]["value"], 1.0, places=2)
        self.assertAlmostEqual(samples[-1]["value"], 3.0, places=2)

    def test_zoom_graph_builder(self):
        graph = SpeedGraph.build_zoom_graph("crash_zoom_in", duration=2.0, max_zoom=2.2)
        samples = graph.sample_curve(fps=30.0, duration=2.0)
        self.assertAlmostEqual(samples[0]["value"], 1.0, places=2)
        self.assertAlmostEqual(samples[-1]["value"], 2.2, places=2)

    def test_speed_ramp_graph_builder(self):
        graph = SpeedGraph.build_speed_ramp_graph("flash_impact_ramp", duration=2.0)
        samples = graph.sample_curve(fps=30.0, duration=2.0)
        self.assertGreater(max(s["value"] for s in samples), 2.0)


class TestMediaProbe(unittest.TestCase):

    def test_nonexistent_file_handling(self):
        with self.assertRaises(FileNotFoundError):
            MediaProbe.get_video_info("/nonexistent/video.mp4")


if __name__ == "__main__":
    unittest.main()
