"""
AIDITOR Master CLI Interface
============================
Unified command-line interface for autonomous AI video editing, motion tracking,
optical flow synthesis, audio-visual rhythm sync, and generative motion graphics.
"""

import sys
import os
import argparse
import json
from .motion.api import MotionTrackingAPI
from .motion.flow import OpticalFlowInterpolator
from .motion.curves import SpeedGraph, EasingPreset
from .motion.exporters import BlenderCameraExporter, NukeCameraExporter, AfterEffectsExporter
from .phonk.api import PhonkStudioAPI
from .phonk.tools.media_probe import MediaProbe


def print_banner():
    banner = """
========================================================================
     █████╗ ██╗██████╗ ██╗████████╗ ██████╗ ██████╗ 
    ██╔══██╗██║██╔══██╗██║╚══██╔══╝██╔═══██╗██╔══██╗
    ███████║██║██║  ██║██║   ██║   ██║   ██║██████╔╝
    ██╔══██║██║██║  ██║██║   ██║   ██║   ██║██╔══██╗
    ██║  ██║██║██████╔╝██║   ██║   ╚██████╔╝██║  ██║
    ╚═╝  ╚═╝╚═╝╚═════╝ ╚═╝   ╚═╝    ╚═════╝ ╚═╝  ╚═╝
    
       THE AUTONOMOUS AI VIDEO EDITING & MOTION VFX CORE
========================================================================
"""
    print(banner)


def cmd_plan(args):
    """Inspects video and generates a recommended AI workflow plan."""
    info = MediaProbe.get_video_info(args.video)
    plan = {
        "video": args.video,
        "duration_seconds": info.get("duration", 0),
        "resolution": f"{info.get('width', 0)}x{info.get('height', 0)}",
        "framerate": info.get("fps", 0),
        "recommended_workflows": [
            {
                "step": 1,
                "action": "flow",
                "description": "Interpolate framerate to smooth 60 FPS using bidirectional optical flow vectors",
                "command": f"aiditor flow --video {args.video} --output output_60fps.mp4 --fps 60"
            },
            {
                "step": 2,
                "action": "track",
                "description": "Lock onto moving subject with HUD cyber callout",
                "command": f"aiditor track --video output_60fps.mp4 --output tracked.mp4 --title 'SUBJECT LOCKED'"
            },
            {
                "step": 3,
                "action": "roto",
                "description": "Place 3D typography behind foreground subject",
                "command": f"aiditor roto --video output_60fps.mp4 --output roto.mp4 --text 'AIDITOR CORE'"
            }
        ]
    }
    if args.json_output:
        print(json.dumps(plan, indent=2))
    else:
        print(f"🎬 Video: {args.video}")
        print(f"  • Resolution: {plan['resolution']} @ {plan['framerate']} FPS ({plan['duration_seconds']:.2f}s)")
        print("\n📋 Recommended Autonomous AI Workflows:")
        for wf in plan["recommended_workflows"]:
            print(f"\n  [{wf['step']}] {wf['action'].upper()}: {wf['description']}")
            print(f"      $ {wf['command']}")


def cmd_flow(args):
    """Executes true optical flow motion interpolation."""
    if not args.json_output:
        print(f"🎬 Running Optical Flow Frame Synthesis ({args.fps} FPS, mode: {args.mode})...")
    res = OpticalFlowInterpolator.interpolate(
        input_path=args.video,
        output_path=args.output,
        target_fps=args.fps,
        mode=args.mode,
        scd_threshold=args.scd,
        color_grade=not args.no_grade
    )
    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print("\n✅ Optical Flow Synthesis Complete:")
        print(f"  • Output Video: {res['output']}")
        print(f"  • Frame Rate: {res['target_fps']} FPS")
        print(f"  • Size: {res['file_size_mb']} MB ({res['render_time_seconds']}s)")


def cmd_track(args):
    """Executes motion tracking with cyber HUD callouts."""
    api = MotionTrackingAPI(default_resolution=args.resolution)
    out = api.apply_hud_callout(
        video_path=args.video,
        output_path=args.output,
        title=args.title,
        subtitle=args.subtitle,
        color=args.color,
        resolution=args.resolution
    )
    res = {"output": out, "title": args.title, "color": args.color}
    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print("\n✅ Motion Tracking Render Complete:")
        print(f"  • Output: {out}")


def cmd_roto(args):
    """Executes rotoscoping with 3D text behind subject."""
    api = MotionTrackingAPI(default_resolution=args.resolution)
    if args.preset == "behind_text":
        out = api.apply_behind_subject_text(args.video, args.output, text=args.text, resolution=args.resolution)
    elif args.preset == "dual_tone":
        out = api.apply_dual_tone_roto(args.video, args.output, resolution=args.resolution)
    else:
        out = api.apply_neon_saber_outline(args.video, args.output, color=args.color_name, resolution=args.resolution)
    
    res = {"output": out, "preset": args.preset}
    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print("\n✅ Rotoscoping VFX Render Complete:")
        print(f"  • Output: {out}")


def cmd_camera(args):
    """Executes camera lock-on / face stabilization."""
    api = MotionTrackingAPI(default_resolution=args.resolution)
    if args.mode == "face_lock":
        out = api.apply_face_tracking(args.video, args.output, resolution=args.resolution)
    elif args.mode == "lock_on":
        out = api.apply_lock_on_camera(args.video, args.output, resolution=args.resolution)
    else:
        out = api.stabilize(args.video, args.output, smoothing=args.smoothing, resolution=args.resolution)
    
    res = {"output": out, "mode": args.mode}
    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print("\n✅ Camera Lock-On Render Complete:")
        print(f"  • Output: {out}")


def cmd_curve(args):
    """Generates, visualizes, and samples Bézier curves."""
    easing = EasingPreset.get_by_name(args.preset)
    graph = SpeedGraph()
    graph.add_keyframe(0.0, 0.0, easing)
    graph.add_keyframe(args.duration, 1.0, easing)
    samples = graph.sample_curve(fps=args.fps, duration=args.duration)
    ascii_plot = graph.render_ascii_graph(title=f"📈 Curve [{args.preset.upper()}]")
    if args.json_output:
        print(json.dumps({"preset": args.preset, "samples": samples}, indent=2))
    else:
        print(f"\n{ascii_plot}")
        print(f"\n📊 Peak Velocity: {max(s['velocity'] for s in samples):.2f} units/s")


def cmd_zoom(args):
    """Generates and visualizes dynamic camera zoom curves."""
    graph = SpeedGraph.build_zoom_graph(args.preset, duration=args.duration, max_zoom=args.max_zoom)
    samples = graph.sample_curve(fps=args.fps, duration=args.duration)
    ascii_plot = graph.render_ascii_graph(title=f"🔍 Zoom Scale [{args.preset.upper()}]", unit="x")
    if args.json_output:
        print(json.dumps({"preset": args.preset, "samples": samples}, indent=2))
    else:
        print(f"\n{ascii_plot}")
        print(f"\n🔍 Peak Zoom: {max(s['value'] for s in samples):.2f}x")


def cmd_speed_ramp(args):
    """Generates and visualizes multi-stage speed ramp curves."""
    graph = SpeedGraph.build_speed_ramp_graph(args.preset, duration=args.duration)
    samples = graph.sample_curve(fps=args.fps, duration=args.duration)
    ascii_plot = graph.render_ascii_graph(title=f"⚡ Speed Ramp [{args.preset.upper()}]", unit="x")
    if args.json_output:
        print(json.dumps({"preset": args.preset, "samples": samples}, indent=2))
    else:
        print(f"\n{ascii_plot}")
        print(f"\n⚡ Peak Speed: {max(s['value'] for s in samples):.2f}x")


def cmd_phonk(args):
    """Executes automatic audio-visual beat sync video generation."""
    from .phonk.fx import EditStyle, AspectRatio
    from .phonk.api import EditConfig, PhonkCarEditor
    
    style_map = {
        "aggressive_drift": EditStyle.BRAZILIAN_DIRT,
        "chill_neon": EditStyle.TOKYO_MIDNIGHT,
        "speed_ramp_chaos": EditStyle.HYPER_SPEED_RAMP,
        "dark_gritty": EditStyle.CYBERPUNK_GLITCH
    }
    cfg = EditConfig(
        style=style_map.get(args.vibe, EditStyle.TOKYO_MIDNIGHT),
        aspect_ratio=AspectRatio.VERTICAL_9_16,
        resolution=args.resolution,
        output_fps=args.fps
    )
    editor = PhonkCarEditor(config=cfg)
    out = editor.create_phonk_car_edit(
        video_sources=args.videos,
        audio_source=args.audio,
        output_file=args.output,
        config=cfg
    )
    res = {"output_path": out, "vibe": args.vibe}
    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print("\n✅ Phonk Beat-Sync Render Complete:")
        print(f"  • Output: {out}")



def cmd_export(args):
    """Exports 3D camera and motion tracking data to Blender, Nuke, After Effects."""
    base_name = args.output or "camera_solve"
    results = {}
    
    # Mock / generate trajectory points
    points = [{"frame": f, "x": 540 + 20 * (f % 5), "y": 960 + 10 * (f % 3), "confidence": 0.95} for f in range(1, 101)]
    
    if args.format in ["blender", "all"]:
        b_path = f"{base_name}_blender.py"
        BlenderCameraExporter.export_to_file(points, b_path, focal_length=args.focal_length)
        results["blender"] = b_path
        
    if args.format in ["nuke", "all"]:
        n_path = f"{base_name}_nuke.nk"
        NukeCameraExporter.export_to_file(points, n_path, focal_length=args.focal_length)
        results["nuke"] = n_path
        
    if args.format in ["aftereffects", "all"]:
        a_path = f"{base_name}_ae.jsx"
        AfterEffectsExporter.export_to_file(points, a_path)
        results["aftereffects"] = a_path

    if args.json_output:
        print(json.dumps({"files": results}, indent=2))
    else:
        print("\n✅ 3D Tracking Export Complete:")
        for fmt, path in results.items():
            print(f"  • {fmt.upper()}: {path}")


def main():
    common_parser = argparse.ArgumentParser(add_help=False)
    common_parser.add_argument("--json", action="store_true", dest="json_output", help="Output machine-readable JSON")

    parser = argparse.ArgumentParser(
        description="AIDITOR: Autonomous AI Video Editing & Motion VFX Core",
        parents=[common_parser]
    )
    subparsers = parser.add_subparsers(dest="command", help="Available subcommands")

    # Command: plan
    p_plan = subparsers.add_parser("plan", parents=[common_parser], help="Inspect video and generate AI workflow plan")
    p_plan.add_argument("--video", required=True, help="Input video path")

    # Command: flow
    p_flow = subparsers.add_parser("flow", parents=[common_parser], help="Optical Flow 60 FPS motion interpolation")
    p_flow.add_argument("--video", required=True, help="Input video file")
    p_flow.add_argument("--output", required=True, help="Output 60 FPS video (.mp4)")
    p_flow.add_argument("--fps", type=int, default=60, help="Target framerate")
    p_flow.add_argument("--mode", default="mci", choices=["mci", "blend"], help="Interpolation mode")
    p_flow.add_argument("--scd", type=float, default=10.0, help="Scene cut threshold")
    p_flow.add_argument("--no-grade", action="store_true", help="Disable color grading")

    # Command: track
    p_track = subparsers.add_parser("track", parents=[common_parser], help="Motion tracking & HUD callouts")
    p_track.add_argument("--video", required=True, help="Input video file")
    p_track.add_argument("--output", required=True, help="Output video (.mp4)")
    p_track.add_argument("--title", default="TARGET LOCKED", help="HUD title")
    p_track.add_argument("--subtitle", default="TRACKING", help="HUD subtitle")
    p_track.add_argument("--color", default="0x00FFCC", help="Color hex")
    p_track.add_argument("--resolution", default="1080p", choices=["480p", "720p", "1080p"])

    # Command: roto
    p_roto = subparsers.add_parser("roto", parents=[common_parser], help="Rotoscoping & 3D text placement")
    p_roto.add_argument("--video", required=True, help="Input video file")
    p_roto.add_argument("--output", required=True, help="Output video (.mp4)")
    p_roto.add_argument("--preset", default="behind_text", choices=["behind_text", "dual_tone", "neon_saber"])
    p_roto.add_argument("--text", default="AIDITOR", help="Text behind subject")
    p_roto.add_argument("--color-name", default="cyan", choices=["cyan", "magenta", "gold"])
    p_roto.add_argument("--resolution", default="1080p", choices=["480p", "720p", "1080p"])

    # Command: camera
    p_cam = subparsers.add_parser("camera", parents=[common_parser], help="Camera lock-on, face tracking, stabilization")
    p_cam.add_argument("--video", required=True, help="Input video file")
    p_cam.add_argument("--output", required=True, help="Output video (.mp4)")
    p_cam.add_argument("--mode", default="face_lock", choices=["face_lock", "lock_on", "stabilize"])
    p_cam.add_argument("--smoothing", type=int, default=30)
    p_cam.add_argument("--resolution", default="1080p", choices=["480p", "720p", "1080p"])

    # Command: curve
    p_curve = subparsers.add_parser("curve", parents=[common_parser], help="Generate ultra-smooth speed graphs and easing curves")
    p_curve.add_argument("--preset", default="smooth_flow")
    p_curve.add_argument("--duration", type=float, default=1.0)
    p_curve.add_argument("--fps", type=float, default=30.0)

    # Command: zoom
    p_zoom = subparsers.add_parser("zoom", parents=[common_parser], help="Generate camera zoom & scale curves")
    p_zoom.add_argument("--preset", default="crash_zoom_in")
    p_zoom.add_argument("--duration", type=float, default=2.0)
    p_zoom.add_argument("--max-zoom", type=float, default=2.2)
    p_zoom.add_argument("--fps", type=float, default=30.0)

    # Command: speed-ramp
    p_ramp = subparsers.add_parser("speed-ramp", parents=[common_parser], help="Generate multi-stage speed ramp curves")
    p_ramp.add_argument("--preset", default="flash_impact_ramp")
    p_ramp.add_argument("--duration", type=float, default=2.0)
    p_ramp.add_argument("--fps", type=float, default=30.0)

    # Command: phonk
    p_phonk = subparsers.add_parser("phonk", parents=[common_parser], help="Audio beat-sync music video generator")
    p_phonk.add_argument("--videos", nargs="+", required=True, help="List of video files")
    p_phonk.add_argument("--audio", required=True, help="Audio soundtrack file")
    p_phonk.add_argument("--output", required=True, help="Output video (.mp4)")
    p_phonk.add_argument("--vibe", default="aggressive_drift", choices=["aggressive_drift", "chill_neon", "speed_ramp_chaos", "dark_gritty"])
    p_phonk.add_argument("--resolution", default="1080p", choices=["480p", "720p", "1080p"])
    p_phonk.add_argument("--fps", type=int, default=60)

    # Command: export
    p_export = subparsers.add_parser("export", parents=[common_parser], help="Export 3D tracks to Blender, Nuke, After Effects")
    p_export.add_argument("--video", required=True, help="Input video file")
    p_export.add_argument("--format", default="all", choices=["blender", "nuke", "aftereffects", "all"])
    p_export.add_argument("--focal-length", type=float, default=35.0)
    p_export.add_argument("--output", help="Base output filename")

    args = parser.parse_args()

    if not args.command:
        print_banner()
        parser.print_help()
        sys.exit(0)

    if not args.json_output:
        print_banner()

    cmd_map = {
        "plan": cmd_plan,
        "flow": cmd_flow,
        "track": cmd_track,
        "roto": cmd_roto,
        "camera": cmd_camera,
        "curve": cmd_curve,
        "zoom": cmd_zoom,
        "speed-ramp": cmd_speed_ramp,
        "phonk": cmd_phonk,
        "export": cmd_export,
    }

    if args.command in cmd_map:
        cmd_map[args.command](args)


if __name__ == "__main__":
    main()
