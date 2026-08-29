"""
Phonk Video Studio CLI
======================
Command-line interface for analyzing video content and rendering studio-grade car edits.
"""

import argparse
import sys
import os
import json
import time

from .api import PhonkCarEditor, VideoAnalyzer, PhonkAudioAnalyzer, EditConfig, EditStyle, AspectRatio
from .tools.synthetic_generator import SyntheticAssetGenerator


def print_banner():
    banner = r"""
========================================================================
   ____  __  ______  _   ____ __     _    ___________  __________  
  / __ \/ / / / __ \/ | / / //_/    | |  / /  _/ __ \/ ____/ __ \ 
 / /_/ / /_/ / / / /  |/ / ,<       | | / // // / / / __/ / / / / 
/ ____/ __  / /_/ / /|  / /| |      | |/ // // /_/ / /___/ /_/ /  
/_/   /_/ /_/\____/_/ |_/_/ |_|      |___/___/_____/_____/\____/   
                                                                   
      PRO AUTOMATED VIDEO ANALYSIS & PHONK CAR EDITING PIPELINE    
========================================================================
"""
    print(banner)


def cmd_analyze(args):
    print(f"\n🔍 [ANALYZER] Inspecting video content in: {args.video}")
    t0 = time.time()
    analyzer = VideoAnalyzer(args.video)
    report = analyzer.analyze()
    dt = time.time() - t0

    print(f"⏱️ Analysis completed in {dt:.2f}s")
    print(f"📊 Video Format: {report['info']['width']}x{report['info']['height']} @ {report['info']['fps']} FPS ({report['info']['duration']:.2f}s)")
    print(f"🎬 Total Scenes Detected: {report['scene_count']}")
    print(f"⚡ Overall Motion Energy Score: {report['overall_motion_score']} (High-action ratio: {report['high_action_ratio']*100:.1f}%)")
    print(f"🎨 Recommended Style: {report['recommended_edit_style']}")

    print("\n--- Scene Breakdown ---")
    for s in report["scenes"]:
        print(f"  • Scene #{s['scene_id']}: {s['start_time']:.2f}s - {s['end_time']:.2f}s ({s['duration']:.2f}s) | "
              f"Motion: {s['motion_type']} (Score: {s['motion_score']}) | Mood: {s['visuals']['lighting_mood']}")

    if args.json:
        with open(args.json, "w") as f:
            json.dump(report, f, indent=2)
        print(f"\n💾 Full analysis report saved to: {args.json}")


def cmd_analyze_audio(args):
    print(f"\n🎵 [AUDIO ANALYZER] Extracting Phonk transients from: {args.audio}")
    analyzer = PhonkAudioAnalyzer(args.audio)
    report = analyzer.analyze()

    print(f"🥁 Estimated Tempo: {report.get('bpm')} BPM")
    print(f"⏱️ Duration: {report.get('duration'):.2f}s")
    print(f"⚡ Total Detected Beats / Transients: {len(report.get('beats', []))}")
    print(f"💥 Detected Bass Drop: {report.get('drop_timestamp'):.2f}s")

    if args.json:
        with open(args.json, "w") as f:
            json.dump(report, f, indent=2)
        print(f"\n💾 Audio analysis saved to: {args.json}")


def cmd_edit(args):
    print("\n🚀 [RENDER ENGINE] Initializing Phonk Car Edit Pipeline...")

    style_map = {
        "tokyo_midnight": EditStyle.TOKYO_MIDNIGHT,
        "cyber_drift": EditStyle.CYBER_DRIFT,
        "high_contrast_drift": EditStyle.HIGH_CONTRAST_DRIFT,
        "monochrome_acid": EditStyle.MONOCHROME_ACID,
        "golden_heat": EditStyle.GOLDEN_HEAT,
        "clean_natural": EditStyle.CLEAN_NATURAL
    }

    aspect_map = {
        "9:16": AspectRatio.VERTICAL_9_16,
        "16:9": AspectRatio.HORIZONTAL_16_9,
        "2.35:1": AspectRatio.ANAMORPHIC_2_35,
        "1:1": AspectRatio.SQUARE_1_1
    }

    config = EditConfig(
        style=style_map.get(args.style, EditStyle.TOKYO_MIDNIGHT),
        aspect_ratio=aspect_map.get(args.aspect, AspectRatio.VERTICAL_9_16),
        resolution=args.resolution,
        shake_intensity=args.shake,
        rgb_split_intensity=args.rgb_split,
        flash_intensity=args.flash,
        bass_boost_db=args.bass_boost,
        output_fps=args.fps,
        preset=args.preset,
        target_duration=args.duration,
        cached_video_analysis=args.video_analysis,
        cached_audio_analysis=args.audio_analysis
    )

    editor = PhonkCarEditor(config)

    def on_progress(msg: str):
        print(f"  ⚡ {msg}")

    t0 = time.time()
    out_file = editor.create_phonk_car_edit(
        video_sources=args.videos,
        audio_source=args.audio,
        output_file=args.output,
        config=config,
        progress_callback=on_progress
    )
    dt = time.time() - t0

    print(f"\n✨ SUCCESS! Phonk car edit rendered in {dt:.2f}s -> {out_file}")


def cmd_generate_demo(args):
    print("\n🛠️ [DEMO GENERATOR] Synthesizing procedural test media assets...")
    out_dir = args.out_dir
    os.makedirs(out_dir, exist_ok=True)

    v_path = os.path.join(out_dir, "demo_car_drift.mp4")
    a_path = os.path.join(out_dir, "demo_phonk_beat.wav")

    print("  • Generating synthetic 60FPS drifting supercar footage...")
    SyntheticAssetGenerator.generate_car_drift_video(v_path, duration_sec=args.duration)
    print(f"    ✓ Video created: {v_path}")

    print("  • Synthesizing 808 sub-bass, cowbells & drums phonk track...")
    SyntheticAssetGenerator.generate_phonk_audio(a_path, duration_sec=args.duration, bpm=args.bpm)
    print(f"    ✓ Audio created: {a_path}")

    print(f"\n🎉 Test assets ready in '{out_dir}'!")
    print(f"To run an instant test edit, execute:")
    print(f"  python3 -m phonk_video_studio edit --videos {v_path} --audio {a_path} --output {out_dir}/demo_phonk_edit.mp4 --style tokyo_midnight --aspect 9:16")


def main():
    print_banner()
    parser = argparse.ArgumentParser(description="Pro Video Content Analyzer & Phonk Video Editing Pipeline")
    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # Command: analyze
    p_analyze = subparsers.add_parser("analyze", help="Analyze video scenes, motion vectors, and lighting")
    p_analyze.add_argument("--video", required=True, help="Path to input video file")
    p_analyze.add_argument("--json", help="Optional path to save JSON report")

    # Command: analyze-audio
    p_audio = subparsers.add_parser("analyze-audio", help="Analyze phonk audio BPM, beats, and bass drops")
    p_audio.add_argument("--audio", required=True, help="Path to input audio file")
    p_audio.add_argument("--json", help="Optional path to save JSON report")

    # Command: edit
    p_edit = subparsers.add_parser("edit", help="Render pro Phonk car edit")
    p_edit.add_argument("--videos", nargs="+", required=True, help="One or more input video files")
    p_edit.add_argument("--audio", required=True, help="Input Phonk audio track")
    p_edit.add_argument("--output", required=True, help="Output video file path (.mp4)")
    p_edit.add_argument("--style", default="tokyo_midnight",
                        choices=["tokyo_midnight", "cyber_drift", "high_contrast_drift", "monochrome_acid", "golden_heat", "clean_natural"])
    p_edit.add_argument("--aspect", default="9:16", choices=["9:16", "16:9", "2.35:1", "1:1"])
    p_edit.add_argument("--resolution", default="480p", choices=["480p", "720p", "1080p"], help="Output resolution (default: 480p)")
    p_edit.add_argument("--shake", type=float, default=0.85, help="S-Shake intensity (0.0 to 2.0)")
    p_edit.add_argument("--rgb-split", type=float, default=1.0, help="Chromatic aberration intensity (0.0 to 2.0)")
    p_edit.add_argument("--flash", type=float, default=0.35, help="Beat flash strength (0.0 to 1.0)")
    p_edit.add_argument("--bass-boost", type=float, default=6.0, help="Audio sub-bass boost in dB")
    p_edit.add_argument("--fps", type=int, default=60, help="Target FPS (default: 60)")
    p_edit.add_argument("--preset", default="ultrafast", choices=["ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow"])
    p_edit.add_argument("--duration", type=float, help="Optional duration cap in seconds")
    p_edit.add_argument("--video-analysis", help="Path to cached video analysis JSON")
    p_edit.add_argument("--audio-analysis", help="Path to cached audio analysis JSON")

    # Command: demo
    p_demo = subparsers.add_parser("generate-demo", help="Generate procedural test car video and phonk audio")
    p_demo.add_argument("--out-dir", default="./demo_assets", help="Directory to save generated demo assets")
    p_demo.add_argument("--duration", type=float, default=8.0, help="Demo duration in seconds")
    p_demo.add_argument("--bpm", type=float, default=135.0, help="Demo phonk BPM")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(0)

    if args.command == "analyze":
        cmd_analyze(args)
    elif args.command == "analyze-audio":
        cmd_analyze_audio(args)
    elif args.command == "edit":
        cmd_edit(args)
    elif args.command == "generate-demo":
        cmd_generate_demo(args)


if __name__ == "__main__":
    main()
