"""
Test Suite for AIDITOR Backend Server, Project Manager, Visualizers, and Pipeline
"""

import unittest
import os
import sys
import tempfile
import json
import time

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from aiditor.server.models import ProjectMetadata, ToolInputConfig, ToolMiddleConfig, ToolOutputConfig
from aiditor.server.project_manager import ProjectManager
from aiditor.server.visualizer import VisualizerEngine
from aiditor.server.pipeline import PipelineEngine


class TestAiditorBackend(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp()
        self.pm = ProjectManager(storage_dir=self.temp_dir)

    def test_project_manager_crud(self):
        # List initial sample projects
        projects = self.pm.list_projects()
        self.assertGreaterEqual(len(projects), 3)

        # Check fields required by Screen 1 (Main Menu)
        first = projects[0]
        self.assertIn("name", first)
        self.assertIn("thumbnail_path", first)
        self.assertIn("file_size_formatted", first)
        self.assertIn("created_at", first)
        self.assertIn("modified_at", first)

        # Create new project
        new_proj = self.pm.create_project(name="Test Cinematic 4K")
        self.assertEqual(new_proj["name"], "Test Cinematic 4K")
        self.assertTrue(new_proj["file_size_formatted"].endswith("MB"))

        # Get project
        retrieved = self.pm.get_project(new_proj["id"])
        self.assertIsNotNone(retrieved)
        self.assertEqual(retrieved["name"], "Test Cinematic 4K")

        # Update project
        updated = self.pm.update_project(new_proj["id"], {"name": "Updated Cinematic"})
        self.assertEqual(updated["name"], "Updated Cinematic")

        # Delete project
        deleted = self.pm.delete_project(new_proj["id"])
        self.assertTrue(deleted)
        self.assertIsNone(self.pm.get_project(new_proj["id"]))

    def test_optical_flow_visualizer(self):
        vis = VisualizerEngine.generate_optical_flow_visualization(
            video_path="dummy.mp4",
            target_fps=60,
            mode="mci",
            grid_size=8
        )
        self.assertEqual(vis["tool"], "optical_flow")
        self.assertEqual(vis["target_fps"], 60)
        self.assertEqual(vis["mode"], "mci")
        self.assertGreater(len(vis["vectors"]), 0)
        self.assertIn("dx", vis["vectors"][0])
        self.assertIn("dy", vis["vectors"][0])
        self.assertIn("magnitude", vis["vectors"][0])

    def test_beat_sync_visualizer(self):
        vis = VisualizerEngine.generate_beat_sync_visualization(
            audio_or_video_path="dummy.mp4",
            duration=8.0,
            vibe="aggressive_drift"
        )
        self.assertEqual(vis["tool"], "beat_sync")
        self.assertEqual(vis["vibe"], "aggressive_drift")
        self.assertGreater(len(vis["waveform"]), 0)
        self.assertGreater(len(vis["beats"]), 0)
        self.assertTrue(vis["beats"][0]["energy"] > 0)

    def test_motion_tracking_visualizer(self):
        vis = VisualizerEngine.generate_motion_tracking_visualization(
            video_path="dummy.mp4",
            target_x=0.6,
            target_y=0.4
        )
        self.assertEqual(vis["tool"], "motion_tracking")
        self.assertGreater(len(vis["keyframes"]), 0)
        kf = vis["keyframes"][0]
        self.assertIn("x", kf)
        self.assertIn("y", kf)
        self.assertIn("confidence", kf)
        self.assertIn("status", kf)

    def test_speed_ramp_visualizer(self):
        vis = VisualizerEngine.generate_speed_ramp_visualization(
            preset="flash_impact_ramp",
            duration=2.0
        )
        self.assertEqual(vis["tool"], "speed_ramp")
        self.assertGreater(len(vis["samples"]), 0)
        self.assertGreater(vis["peak_speed"], 1.0)
        self.assertGreater(len(vis["control_points"]), 0)

    def test_color_grade_visualizer(self):
        vis = VisualizerEngine.generate_color_grade_visualization(
            contrast=1.3,
            saturation=0.0
        )
        self.assertEqual(vis["tool"], "color_grade")
        self.assertEqual(len(vis["tone_curve"]), 256)
        self.assertEqual(len(vis["histogram"]["luminance"]), 256)

    def test_rotoscope_visualizer(self):
        vis = VisualizerEngine.generate_rotoscope_visualization(
            roto_preset="behind_text",
            text_content="TEST_VFX"
        )
        self.assertEqual(vis["tool"], "rotoscope")
        self.assertEqual(vis["text_content"], "TEST_VFX")
        self.assertGreater(len(vis["contour_points"]), 10)

    def test_pipeline_ffmpeg_command_builder(self):
        in_cfg = ToolInputConfig(source_path="input.mp4", in_point_seconds=1.5, out_point_seconds=5.0)
        mid_cfg = ToolMiddleConfig(target_fps=60, flow_mode="mci")
        out_cfg = ToolOutputConfig(output_path="output.mp4", resolution="1080p", fps=60)

        cmd = PipelineEngine.build_ffmpeg_command(in_cfg, mid_cfg, out_cfg, tool_type="optical_flow")
        cmd_str = " ".join(cmd)
        self.assertIn("ffmpeg", cmd_str)
        self.assertIn("-ss 1.500", cmd_str)
        self.assertIn("-t 3.500", cmd_str)
        self.assertIn("minterpolate", cmd_str)
        self.assertIn("output.mp4", cmd_str)


if __name__ == "__main__":
    unittest.main()
