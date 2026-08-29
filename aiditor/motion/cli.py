"""
Tracker Motion Studio CLI (AI-Operable VFX Core 2.0)
====================================================
Declarative, agent-controllable command-line interface for:
1. .axproj Project State Management (init, status, plan).
2. Video Content Analysis (VCA) scene intelligence.
3. 3D Camera Tracking & Matchmoving.
4. 2D Motion Tracking & HUD Telemetry Badges.
5. Neural & Differential Rotoscoping (Behind-Subject 3D text).
6. VFX Interchange Exporters (Blender .py, Nuke .chan/.nk, After Effects .json/.jsx).
"""

import argparse
import sys
import json
import time
import os
from typing import Dict, Any, Optional

from .api import TrackerMotionStudio, CameraMotionSolver, PointTracker
from .presets import TrackerPreset
from .project import ProjectManager
from .exporters import BlenderExporter, NukeExporter, AfterEffectsExporter


def print_banner():
    banner = r"""
========================================================================
  ______ _____            _____ _  ________ _____    __  __  ____ _______ _____ ____  _   _ 
 |__  / |_   _|          |_   _| |/ /  ____|  __ \  |  \/  |/ __ \__   __|_   _/ __ \| \ | |
   / /    | |  _ __ __ _   | | | ' /| |__  | |__) | | \  / | |  | | | |    | || |  | |  \| |
  / /     | | | '__/ _` |  | | |  < |  __| |  _  /  | |\/| | |  | | | |    | || |  | | . ` |
 / /__   _| |_| | | (_| |  | | | . \| |____| | \ \  | |  | | |__| | | |   _| || |__| | |\  |
/_____| |_____|_|  \__,_|  |_| |_|\_\______|_|  \_\ |_|  |_|\____/  |_|  |_____\____/|_| \_|
                                                                                            
   ROTO-MOTION, CAMERA TRACKING & OBJECT PINNING SUITE (AI-OPERABLE VFX CORE)
========================================================================
"""
    print(banner, file=sys.stderr)


def cmd_init(args):
    """Initializes a new .axproj workspace."""
    pm = ProjectManager.create(args.video, project_dir=args.project)
    res = {
        "status": "ok",
        "action": "init_project",
        "project_dir": pm.project_dir,
        "video_path": os.path.abspath(args.video),
        "manifest": pm.get_manifest()
    }
    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print(f"✨ Initialized .axproj project at: {pm.project_dir}")


def cmd_status(args):
    """Reports .axproj lifecycle status."""
    pm = ProjectManager(args.project)
    status = pm.get_status()
    if args.json_output:
        print(json.dumps(status, indent=2))
    else:
        print("\n📊 Project Lifecycle Status:")
        print(f"  Project Dir: {status['project_dir']}")
        print(f"  Video Path: {status['video_path']}")
        print(f"  Stages: {json.dumps(status['stages'], indent=4)}")


def cmd_plan(args):
    """Generates an AI-executable multi-stage workflow plan."""
    studio = TrackerMotionStudio()
    vca = studio.inspect_real_objects(args.video, sample_fps=2)
    summary = vca.get("summary", {})
    obj_type = summary.get("detected_object_type", "subject")

    plan = {
        "status": "ok",
        "video_path": os.path.abspath(args.video),
        "detected_entity": obj_type,
        "average_speed_kmh": summary.get("average_speed_kmh", 0.0),
        "suggested_pipeline": [
            {
                "stage": 1,
                "command": "analyze",
                "purpose": "Extract scene graph, bounding box trajectories, and dominant color palettes."
            },
            {
                "stage": 2,
                "command": "track",
                "purpose": f"Lock tracking crosshairs and HUD badges to {obj_type} centroid."
            },
            {
                "stage": 3,
                "command": "roto",
                "purpose": f"Extract alpha matte to place 3D typography BEHIND {obj_type}."
            },
            {
                "stage": 4,
                "command": "camera",
                "purpose": "Solve camera trajectory and apply camera lock-on centering."
            },
            {
                "stage": 5,
                "command": "export",
                "purpose": "Export 3D Camera to Blender (.py), Nuke (.chan/.nk), or After Effects (.json)."
            }
        ]
    }
    if args.json_output:
        print(json.dumps(plan, indent=2))
    else:
        print("\n📋 AI Suggested Workflow Plan:")
        for step in plan["suggested_pipeline"]:
            print(f"  [{step['stage']}] {step['command'].upper()}: {step['purpose']}")


def cmd_analyze(args):
    """Performs deep VCA scene analysis."""
    t0 = time.time()
    studio = TrackerMotionStudio()
    report = studio.inspect_real_objects(video_path=args.video, sample_fps=args.fps)
    dt = time.time() - t0

    if args.project:
        pm = ProjectManager(args.project)
        pm.save_scene_analysis(report)

    res = {
        "status": "ok",
        "action": "analyze",
        "duration_seconds": round(dt, 2),
        "summary": report.get("summary", {}),
        "sample_keyframes": report.get("timeline", [])[:3]
    }

    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print(f"\n⏱️ Analysis completed in {dt:.2f}s")
        print(f"  Object: {res['summary'].get('detected_object_type')}")
        print(f"  Speed: {res['summary'].get('average_speed_kmh')} KM/H")
        print(f"  Confidence: {res['summary'].get('tracking_confidence')}")


def cmd_track(args):
    """Performs motion tracking or renders HUD badges."""
    t0 = time.time()
    studio = TrackerMotionStudio(default_resolution=args.resolution)

    def on_progress(msg):
        if not args.json_output:
            print(f"  ⚡ {msg}", file=sys.stderr)

    out = studio.apply_hud_callout(
        video_path=args.video,
        output_path=args.output,
        title=args.title,
        subtitle=args.subtitle,
        color=args.color,
        resolution=args.resolution,
        progress_callback=on_progress
    )
    dt = time.time() - t0

    res = {
        "status": "ok",
        "action": "track",
        "output_file": os.path.abspath(out),
        "render_time_seconds": round(dt, 2),
        "resolution": args.resolution
    }

    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print(f"\n✨ Tracked video rendered in {dt:.2f}s -> {out}")


def cmd_roto(args):
    """Performs rotoscoping / behind-subject compositing."""
    t0 = time.time()
    studio = TrackerMotionStudio(default_resolution=args.resolution)

    def on_progress(msg):
        if not args.json_output:
            print(f"  ⚡ {msg}", file=sys.stderr)

    if args.preset == "behind_text":
        out = studio.apply_behind_subject_text(
            video_path=args.video,
            output_path=args.output,
            text=args.text,
            resolution=args.resolution,
            progress_callback=on_progress
        )
    elif args.preset == "dual_tone":
        out = studio.apply_dual_tone_roto(
            video_path=args.video,
            output_path=args.output,
            resolution=args.resolution,
            progress_callback=on_progress
        )
    elif args.preset == "neon_saber":
        out = studio.apply_neon_saber_outline(
            video_path=args.video,
            output_path=args.output,
            color=args.color_name,
            resolution=args.resolution,
            progress_callback=on_progress
        )
    else:
        out = studio.apply_behind_subject_text(
            video_path=args.video,
            output_path=args.output,
            text=args.text,
            resolution=args.resolution,
            progress_callback=on_progress
        )

    dt = time.time() - t0
    res = {
        "status": "ok",
        "action": "roto",
        "preset": args.preset,
        "output_file": os.path.abspath(out),
        "render_time_seconds": round(dt, 2)
    }

    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print(f"\n✨ Roto video rendered in {dt:.2f}s -> {out}")


def cmd_camera(args):
    """Performs camera solving, lock-on, and face lock."""
    t0 = time.time()
    studio = TrackerMotionStudio(default_resolution=args.resolution)

    def on_progress(msg):
        if not args.json_output:
            print(f"  ⚡ {msg}", file=sys.stderr)

    if args.mode == "face_lock":
        out = studio.apply_face_tracking(
            video_path=args.video,
            output_path=args.output,
            resolution=args.resolution,
            progress_callback=on_progress
        )
    elif args.mode == "lock_on":
        out = studio.apply_lock_on_camera(
            video_path=args.video,
            output_path=args.output,
            resolution=args.resolution,
            progress_callback=on_progress
        )
    else:
        out = studio.stabilize(
            video_path=args.video,
            output_path=args.output,
            smoothing=args.smoothing,
            resolution=args.resolution,
            progress_callback=on_progress
        )

    dt = time.time() - t0
    res = {
        "status": "ok",
        "action": "camera",
        "mode": args.mode,
        "output_file": os.path.abspath(out),
        "render_time_seconds": round(dt, 2)
    }

    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print(f"\n✨ Camera solved video rendered in {dt:.2f}s -> {out}")


def cmd_export(args):
    """Exports solved camera/tracks to Blender, Nuke, or After Effects."""
    solver = CameraMotionSolver(args.video)
    trajectory = solver.solve_trajectory()

    exported_files = {}

    if args.format in ["blender", "all"]:
        out_blender = (args.output or "camera_solve") + ".py" if not (args.output and args.output.endswith(".py")) else args.output
        BlenderExporter.export_camera_script(trajectory, out_blender, focal_length_mm=args.focal_length)
        exported_files["blender_py"] = os.path.abspath(out_blender)

    if args.format in ["nuke", "all"]:
        out_chan = (args.output or "camera_solve") + ".chan"
        NukeExporter.export_chan(trajectory, out_chan, focal_length_mm=args.focal_length)
        exported_files["nuke_chan"] = os.path.abspath(out_chan)

    if args.format in ["aftereffects", "all"]:
        out_ae = (args.output or "tracks_ae") + ".json"
        AfterEffectsExporter.export_json(trajectory, out_ae)
        exported_files["ae_json"] = os.path.abspath(out_ae)

    res = {
        "status": "ok",
        "action": "export",
        "format": args.format,
        "exported_files": exported_files,
        "keyframe_count": len(trajectory)
    }

    if args.json_output:
        print(json.dumps(res, indent=2))
    else:
        print("\n✅ Successfully exported VFX tracking files:")
        for k, v in exported_files.items():
            print(f"  • [{k}]: {v}")


def cmd_curve(args):
    """Generates, visualizes, and evaluates smooth speed graphs & keyframe curves."""
    from .curves import SpeedGraph, Keyframe, EasingPreset

    easing = EasingPreset.get_by_name(args.preset)
    graph = SpeedGraph()

    if args.keyframes:
        # Format: "t0:v0, t1:v1, t2:v2"
        for item in args.keyframes.split(","):
            parts = item.strip().split(":")
            if len(parts) == 2:
                graph.add_keyframe(float(parts[0]), float(parts[1]), easing)
    else:
        # Default 0.0 -> 1.0 curve over specified duration
        graph.add_keyframe(0.0, 0.0, easing)
        graph.add_keyframe(args.duration, 1.0, easing)

    samples = graph.sample_curve(fps=args.fps, duration=args.duration)
    ascii_plot = graph.render_ascii_graph()

    res = {
        "status": "ok",
        "action": "curve",
        "preset": args.preset,
        "duration": args.duration,
        "sample_count": len(samples),
        "keyframes": [k.to_dict() for k in graph.keyframes],
        "samples": samples[:10]  # First 10 samples preview
    }

    if args.json_output:
        res["all_samples"] = samples
        print(json.dumps(res, indent=2))
    else:
        print(f"\n{ascii_plot}")
        print(f"\n📊 Speed Graph [{args.preset.upper()}] Details:")
        print(f"  • Duration: {args.duration}s ({len(samples)} frames @ {args.fps} FPS)")
        print(f"  • Peak Velocity: {max(s['velocity'] for s in samples):.2f} units/sec")
        print(f"  • Min Velocity: {min(s['velocity'] for s in samples):.2f} units/sec")
        print("\n  Sample Points:")
        for s in samples[::max(1, len(samples) // 6)]:
            print(f"    Frame {s['frame']:3d} ({s['time']:.2f}s): Val = {s['value']:.3f} | Vel = {s['velocity']:+6.2f} | Acc = {s['acceleration']:+6.2f}")


def cmd_flow(args):
    """Executes true optical flow motion interpolation."""
    from .flow import OpticalFlowInterpolator

    if not args.json_output:
        print(f"🎬 Starting Optical Flow Motion Interpolation ({args.fps} FPS, mode: {args.mode})...")
        print(f"  • Input: {args.video}")
        print(f"  • Output: {args.output}")

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
        print("\n✅ Optical Flow Frame Interpolation Complete:")
        print(f"  • Output Video: {res['output']}")
        print(f"  • Frame Rate: {res['target_fps']} FPS (Real Synthesized Frames)")
        print(f"  • File Size: {res['file_size_mb']} MB")
        print(f"  • Render Time: {res['render_time_seconds']}s")


def cmd_zoom(args):
    """Generates, visualizes, and evaluates camera zoom and scale trajectory graphs."""
    from .curves import SpeedGraph

    graph = SpeedGraph.build_zoom_graph(args.preset, duration=args.duration, max_zoom=args.max_zoom)
    samples = graph.sample_curve(fps=args.fps, duration=args.duration)
    ascii_plot = graph.render_ascii_graph(title=f"🔍 Zoom Scale Graph [{args.preset.upper()}]", unit="x")

    res = {
        "status": "ok",
        "action": "zoom_graph",
        "preset": args.preset,
        "duration": args.duration,
        "max_zoom": args.max_zoom,
        "sample_count": len(samples),
        "keyframes": [k.to_dict() for k in graph.keyframes],
        "samples": samples[:10]
    }

    if args.json_output:
        res["all_samples"] = samples
        print(json.dumps(res, indent=2))
    else:
        print(f"\n{ascii_plot}")
        print(f"\n🔍 Camera Zoom Graph [{args.preset.upper()}] Details:")
        print(f"  • Duration: {args.duration}s ({len(samples)} frames @ {args.fps} FPS)")
        print(f"  • Peak Scale: {max(s['value'] for s in samples):.2f}x")
        print(f"  • Min Scale: {min(s['value'] for s in samples):.2f}x")
        print("\n  Sample Points:")
        for s in samples[::max(1, len(samples) // 6)]:
            print(f"    Frame {s['frame']:3d} ({s['time']:.2f}s): Zoom = {s['value']:.3f}x | Rate = {s['velocity']:+6.2f}x/s")


def cmd_speed_ramp(args):
    """Generates, visualizes, and evaluates multi-stage speed ramp curves."""
    from .curves import SpeedGraph

    graph = SpeedGraph.build_speed_ramp_graph(args.preset, duration=args.duration)
    samples = graph.sample_curve(fps=args.fps, duration=args.duration)
    ascii_plot = graph.render_ascii_graph(title=f"⚡ Speed Ramp Graph [{args.preset.upper()}]", unit="x")

    res = {
        "status": "ok",
        "action": "speed_ramp_graph",
        "preset": args.preset,
        "duration": args.duration,
        "sample_count": len(samples),
        "keyframes": [k.to_dict() for k in graph.keyframes],
        "samples": samples[:10]
    }

    if args.json_output:
        res["all_samples"] = samples
        print(json.dumps(res, indent=2))
    else:
        print(f"\n{ascii_plot}")
        print(f"\n⚡ Speed Ramp Graph [{args.preset.upper()}] Details:")
        print(f"  • Duration: {args.duration}s ({len(samples)} frames @ {args.fps} FPS)")
        print(f"  • Peak Speed: {max(s['value'] for s in samples):.2f}x")
        print(f"  • Min Speed: {min(s['value'] for s in samples):.2f}x")
        print("\n  Sample Points:")
        for s in samples[::max(1, len(samples) // 6)]:
            print(f"    Frame {s['frame']:3d} ({s['time']:.2f}s): Speed = {s['value']:.3f}x | Accel = {s['velocity']:+6.2f}")


def main():
    common_parser = argparse.ArgumentParser(add_help=False)
    common_parser.add_argument("--json", action="store_true", dest="json_output", help="Output machine-readable JSON")

    parser = argparse.ArgumentParser(
        description="Tracker Motion Studio (AI-Operable VFX Core)",
        parents=[common_parser]
    )
    subparsers = parser.add_subparsers(dest="command", help="Available subcommands")

    # Command: init
    p_init = subparsers.add_parser("init", parents=[common_parser], help="Initialize .axproj project state")
    p_init.add_argument("--video", required=True, help="Path to video file")
    p_init.add_argument("--project", help="Optional project directory path")

    # Command: status
    p_status = subparsers.add_parser("status", parents=[common_parser], help="Get project status")
    p_status.add_argument("--project", required=True, help="Path to .axproj directory")

    # Command: plan
    p_plan = subparsers.add_parser("plan", parents=[common_parser], help="Inspect video and generate AI workflow plan")
    p_plan.add_argument("--video", required=True, help="Path to video file")

    # Command: analyze
    p_analyze = subparsers.add_parser("analyze", parents=[common_parser], help="Video Content Analysis & object discovery")
    p_analyze.add_argument("--video", required=True, help="Path to video file")
    p_analyze.add_argument("--fps", type=int, default=3, help="Sampling FPS")
    p_analyze.add_argument("--project", help="Optional .axproj directory")

    # Command: track
    p_track = subparsers.add_parser("track", parents=[common_parser], help="Motion tracking & HUD callouts")
    p_track.add_argument("--video", required=True, help="Input video file")
    p_track.add_argument("--output", required=True, help="Output video (.mp4)")
    p_track.add_argument("--title", default="TARGET LOCKED", help="HUD title")
    p_track.add_argument("--subtitle", default="TRACKING", help="HUD subtitle")
    p_track.add_argument("--color", default="0x00FFCC", help="Color hex")
    p_track.add_argument("--resolution", default="480p", choices=["480p", "720p", "1080p"])

    # Command: roto
    p_roto = subparsers.add_parser("roto", parents=[common_parser], help="Rotoscoping & 3D text placement")
    p_roto.add_argument("--video", required=True, help="Input video file")
    p_roto.add_argument("--output", required=True, help="Output video (.mp4)")
    p_roto.add_argument("--preset", default="behind_text", choices=["behind_text", "dual_tone", "neon_saber"])
    p_roto.add_argument("--text", default="VFX CORE", help="Text behind subject")
    p_roto.add_argument("--color-name", default="cyan", choices=["cyan", "magenta", "gold"])
    p_roto.add_argument("--resolution", default="480p", choices=["480p", "720p", "1080p"])

    # Command: camera
    p_cam = subparsers.add_parser("camera", parents=[common_parser], help="Camera lock-on, face tracking, stabilization")
    p_cam.add_argument("--video", required=True, help="Input video file")
    p_cam.add_argument("--output", required=True, help="Output video (.mp4)")
    p_cam.add_argument("--mode", default="face_lock", choices=["face_lock", "lock_on", "stabilize"])
    p_cam.add_argument("--smoothing", type=int, default=30)
    p_cam.add_argument("--resolution", default="480p", choices=["480p", "720p", "1080p"])

    # Command: export
    p_export = subparsers.add_parser("export", parents=[common_parser], help="Export to Blender, Nuke, After Effects")
    p_export.add_argument("--video", required=True, help="Input video file")
    p_export.add_argument("--format", default="all", choices=["blender", "nuke", "aftereffects", "all"])
    p_export.add_argument("--focal-length", type=float, default=35.0)
    p_export.add_argument("--output", help="Base output filename")

    # Command: curve
    p_curve = subparsers.add_parser("curve", parents=[common_parser], help="Generate ultra-smooth speed graphs and keyframe easing curves")
    p_curve.add_argument("--preset", default="smooth_flow", choices=[
        "linear", "ease_in", "ease_out", "ease_in_out", "ease_out_expo",
        "ease_in_out_quint", "smooth_flow", "speed_ramp_flash", "flash_impact_ramp",
        "seamless_whip_ramp", "pulse_rhythm_ramp", "slow_mo_drop", "bullet_time",
        "snap_bounce", "crash_zoom_in", "punch_zoom_pulse", "slow_creep_zoom",
        "whip_zoom_out", "dolly_vertigo_zoom"
    ])
    p_curve.add_argument("--duration", type=float, default=1.0, help="Curve duration in seconds")
    p_curve.add_argument("--fps", type=float, default=30.0, help="Sampling framerate")
    p_curve.add_argument("--keyframes", help="Custom keyframe pairs: 't0:v0, t1:v1, t2:v2'")

    # Command: flow (Optical Flow Motion Interpolator)
    p_flow = subparsers.add_parser("flow", parents=[common_parser], help="True Optical Flow motion vector frame synthesis (60 FPS)")
    p_flow.add_argument("--video", required=True, help="Input video file")
    p_flow.add_argument("--output", required=True, help="Output 60 FPS video (.mp4)")
    p_flow.add_argument("--fps", type=int, default=60, help="Target frame rate")
    p_flow.add_argument("--mode", default="mci", choices=["mci", "blend"], help="Interpolation mode (mci=Motion Vectors, blend=Frame Blending)")
    p_flow.add_argument("--scd", type=float, default=10.0, help="Scene change detection threshold")
    p_flow.add_argument("--no-grade", action="store_true", help="Disable automotive color grading")

    # Command: zoom (Camera Zoom / Scale Graph)
    p_zoom = subparsers.add_parser("zoom", parents=[common_parser], help="Generate & visualize dynamic camera zoom & scale curves")
    p_zoom.add_argument("--preset", default="crash_zoom_in", choices=[
        "crash_zoom_in", "punch_zoom_pulse", "slow_creep_zoom", "whip_zoom_out", "dolly_vertigo_zoom"
    ])
    p_zoom.add_argument("--duration", type=float, default=2.0, help="Zoom duration in seconds")
    p_zoom.add_argument("--max-zoom", type=float, default=2.2, help="Maximum zoom scale multiplier")
    p_zoom.add_argument("--fps", type=float, default=30.0, help="Sampling framerate")

    # Command: speed-ramp (Multi-stage Speed Ramp Curves)
    p_ramp = subparsers.add_parser("speed-ramp", parents=[common_parser], help="Generate & visualize multi-stage speed ramp curves")
    p_ramp.add_argument("--preset", default="flash_impact_ramp", choices=[
        "flash_impact_ramp", "seamless_whip_ramp", "pulse_rhythm_ramp", "bullet_time"
    ])
    p_ramp.add_argument("--duration", type=float, default=2.0, help="Speed ramp duration in seconds")
    p_ramp.add_argument("--fps", type=float, default=30.0, help="Sampling framerate")

    args = parser.parse_args()

    if not args.command:
        print_banner()
        parser.print_help()
        sys.exit(0)

    if not args.json_output:
        print_banner()

    if args.command == "init":
        cmd_init(args)
    elif args.command == "status":
        cmd_status(args)
    elif args.command == "plan":
        cmd_plan(args)
    elif args.command == "analyze":
        cmd_analyze(args)
    elif args.command == "track":
        cmd_track(args)
    elif args.command == "roto":
        cmd_roto(args)
    elif args.command == "camera":
        cmd_camera(args)
    elif args.command == "export":
        cmd_export(args)
    elif args.command == "curve":
        cmd_curve(args)
    elif args.command == "flow":
        cmd_flow(args)
    elif args.command == "zoom":
        cmd_zoom(args)
    elif args.command == "speed-ramp":
        cmd_speed_ramp(args)


if __name__ == "__main__":
    main()


