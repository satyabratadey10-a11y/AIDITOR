"""
Verification & Test Suite for Phonk Video Studio
================================================
Tests all modules: video analysis, audio beat detection, FX filters, and end-to-end rendering.
"""

import unittest
import os
import sys
import shutil
import tempfile

# Add parent path
sys.path.insert(0, "/data/data/com.termux/files/home")

from phonk_video_studio.api import PhonkCarEditor, VideoAnalyzer, PhonkAudioAnalyzer, EditConfig, EditStyle, AspectRatio
from phonk_video_studio.tools.synthetic_generator import SyntheticAssetGenerator
from phonk_video_studio.tools.media_probe import MediaProbe


class TestPhonkVideoStudio(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.test_dir = tempfile.mkdtemp(prefix="phonk_test_")
        cls.demo_video = os.path.join(cls.test_dir, "test_drift.mp4")
        cls.demo_audio = os.path.join(cls.test_dir, "test_phonk.wav")

        print("\n[Setup] Generating synthetic test assets...")
        SyntheticAssetGenerator.generate_car_drift_video(cls.demo_video, duration_sec=4.0)
        SyntheticAssetGenerator.generate_phonk_audio(cls.demo_audio, duration_sec=4.0, bpm=135.0)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.test_dir, ignore_errors=True)

    def test_01_media_probe(self):
        print("\n[Test 1] Testing MediaProbe...")
        v_info = MediaProbe.get_video_info(self.demo_video)
        self.assertEqual(v_info["width"], 1920)
        self.assertEqual(v_info["height"], 1080)
        self.assertGreater(v_info["fps"], 0)

        a_info = MediaProbe.get_audio_info(self.demo_audio)
        self.assertEqual(a_info["sample_rate"], 44100)
        print("  ✓ MediaProbe passed!")

    def test_02_video_content_analyzer(self):
        print("\n[Test 2] Testing VideoContentAnalyzer...")
        analyzer = VideoAnalyzer(self.demo_video)
        report = analyzer.analyze()

        self.assertIn("scenes", report)
        self.assertGreater(len(report["scenes"]), 0)
        self.assertIn("overall_motion_score", report)
        print(f"  ✓ VideoAnalyzer passed! Detected motion score: {report['overall_motion_score']}")

    def test_03_audio_beat_analyzer(self):
        print("\n[Test 3] Testing PhonkAudioAnalyzer...")
        analyzer = PhonkAudioAnalyzer(self.demo_audio)
        report = analyzer.analyze()

        self.assertGreater(report["bpm"], 80.0)
        self.assertGreater(len(report["beats"]), 0)
        print(f"  ✓ PhonkAudioAnalyzer passed! Detected BPM: {report['bpm']}, Beats: {len(report['beats'])}")

    def test_04_end_to_end_car_edit_render(self):
        print("\n[Test 4] Testing End-to-End Render (9:16 Tokyo Midnight Phonk Edit)...")
        out_video = os.path.join(self.test_dir, "rendered_car_edit.mp4")

        config = EditConfig(
            style=EditStyle.TOKYO_MIDNIGHT,
            aspect_ratio=AspectRatio.VERTICAL_9_16,
            shake_intensity=1.0,
            rgb_split_intensity=1.0,
            flash_intensity=0.35,
            bass_boost_db=6.0,
            output_fps=60,
            preset="ultrafast",
            target_duration=3.5
        )

        editor = PhonkCarEditor(config)
        rendered_file = editor.create_phonk_car_edit(
            video_sources=[self.demo_video],
            audio_source=self.demo_audio,
            output_file=out_video,
            config=config
        )

        self.assertTrue(os.path.exists(rendered_file))
        self.assertGreater(os.path.getsize(rendered_file), 10000)

        # Probe output video to verify vertical 1080x1920 60FPS
        out_probe = MediaProbe.get_video_info(rendered_file)
        self.assertEqual(out_probe["width"], 1080)
        self.assertEqual(out_probe["height"], 1920)
        print(f"  ✓ End-to-End Render passed! Output: {out_probe['width']}x{out_probe['height']} @ {out_probe['fps']} FPS ({os.path.getsize(rendered_file)} bytes)")


if __name__ == "__main__":
    unittest.main()
