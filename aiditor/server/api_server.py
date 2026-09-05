"""
AIDITOR REST API & Backend Server
=================================
High-performance standard library HTTP server providing complete RESTful video editing APIs:
- Project lifecycle (Create, List, Update, Delete, Probing, Thumbnails)
- Real-time Visualizers for all 6 core tools with full input & middle parameter modification
- FFmpeg multi-stage preview & final export rendering pipeline
- Zero external pip dependencies, 100% resilient across Termux, Android, and Linux.
"""

import os
import json
import time
import uuid
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn
from typing import Dict, Any, Optional

from .project_manager import ProjectManager
from .visualizer import VisualizerEngine
from .pipeline import PipelineEngine
from .models import ToolInputConfig, ToolMiddleConfig, ToolOutputConfig


class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True


# In-memory export jobs tracking
EXPORT_JOBS: Dict[str, Dict[str, Any]] = {}


class AiditorRequestHandler(BaseHTTPRequestHandler):
    project_manager: Optional[ProjectManager] = None

    def _set_headers(self, status_code: int = 200, content_type: str = "application/json"):
        self.send_response(status_code)
        self.send_header("Content-Type", content_type)
        # Enable CORS for Android / WebView / Localhost
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_OPTIONS(self):
        self._set_headers(204)

    def _read_json_body(self) -> Dict[str, Any]:
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length > 0:
            body = self.rfile.read(content_length).decode("utf-8")
            try:
                return json.loads(body)
            except Exception:
                return {}
        return {}

    def _send_json(self, data: Any, status_code: int = 200):
        self._set_headers(status_code, "application/json")
        self.wfile.write(json.dumps(data, indent=2).encode("utf-8"))

    def _send_error(self, message: str, status_code: int = 400):
        self._send_json({"error": message, "status": "error"}, status_code)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        if not AiditorRequestHandler.project_manager:
            AiditorRequestHandler.project_manager = ProjectManager()

        pm = AiditorRequestHandler.project_manager

        # Health check
        if path == "/" or path == "/health":
            self._send_json({
                "status": "online",
                "app": "AIDITOR Autonomous AI Video Editor Core",
                "version": "3.0.0",
                "design": "Monochrome Minimalist Black & White",
                "engine": "FFmpeg 8.x + Jetpack Compose 1.12.0 Bridge"
            })
            return

        # List all projects (for Main Menu Screen)
        if path == "/api/projects":
            projects = pm.list_projects()
            self._send_json({"projects": projects, "count": len(projects)})
            return

        # Get single project
        if path.startswith("/api/projects/"):
            proj_id = path.split("/")[-1]
            proj = pm.get_project(proj_id)
            if proj:
                self._send_json(proj)
            else:
                self._send_error(f"Project '{proj_id}' not found", 404)
            return

        # Check export job progress
        if path.startswith("/api/render/progress/"):
            job_id = path.split("/")[-1]
            if job_id in EXPORT_JOBS:
                self._send_json(EXPORT_JOBS[job_id])
            else:
                self._send_error(f"Job '{job_id}' not found", 404)
            return

        # Serve thumbnail file directly
        if path.startswith("/api/media/thumbnail/"):
            filename = path.split("/")[-1]
            thumb_path = os.path.join(pm.thumbnails_dir, filename)
            if os.path.exists(thumb_path):
                self._set_headers(200, "image/jpeg")
                with open(thumb_path, "rb") as f:
                    self.wfile.write(f.read())
            else:
                self._send_error("Thumbnail image not found", 404)
            return

        self._send_error("Endpoint not found", 404)

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        body = self._read_json_body()

        if not AiditorRequestHandler.project_manager:
            AiditorRequestHandler.project_manager = ProjectManager()

        pm = AiditorRequestHandler.project_manager

        # Create new project
        if path == "/api/projects":
            name = body.get("name", "Untitled Project")
            video_path = body.get("video_path", "")
            new_proj = pm.create_project(name=name, video_path=video_path)
            self._send_json(new_proj, 201)
            return

        # Generate Real Visualizer Data for any tool with input and middle modification
        if path == "/api/tools/visualize":
            tool_type = body.get("tool_type", "optical_flow")
            input_part = body.get("input", {})
            middle_part = body.get("middle", {})

            source_video = input_part.get("source_path", "sample_input.mp4")

            result_data = {}
            if tool_type == "optical_flow":
                result_data = VisualizerEngine.generate_optical_flow_visualization(
                    video_path=source_video,
                    target_fps=int(middle_part.get("target_fps", 60)),
                    mode=middle_part.get("flow_mode", "mci"),
                    scd_threshold=float(middle_part.get("scd_threshold", 10.0)),
                    sample_time=float(middle_part.get("sample_time", 1.0))
                )
            elif tool_type == "beat_sync":
                result_data = VisualizerEngine.generate_beat_sync_visualization(
                    audio_or_video_path=source_video,
                    duration=float(input_part.get("duration", 10.0)),
                    vibe=middle_part.get("vibe", "aggressive_drift"),
                    beat_sensitivity=float(middle_part.get("beat_sensitivity", 0.8))
                )
            elif tool_type == "motion_tracking":
                result_data = VisualizerEngine.generate_motion_tracking_visualization(
                    video_path=source_video,
                    target_x=float(middle_part.get("target_x", 0.5)),
                    target_y=float(middle_part.get("target_y", 0.5)),
                    tracking_mode=middle_part.get("tracking_mode", "hud_callout")
                )
            elif tool_type == "speed_ramp":
                result_data = VisualizerEngine.generate_speed_ramp_visualization(
                    preset=middle_part.get("ramp_preset", "flash_impact_ramp"),
                    duration=float(middle_part.get("duration_seconds", 2.0)),
                    custom_points=middle_part.get("speed_curve_points")
                )
            elif tool_type == "color_grade":
                result_data = VisualizerEngine.generate_color_grade_visualization(
                    contrast=float(middle_part.get("contrast", 1.2)),
                    exposure=float(middle_part.get("exposure", 0.0)),
                    saturation=float(middle_part.get("saturation", 0.0)),
                    brightness=float(middle_part.get("brightness", 0.0)),
                    gamma=float(middle_part.get("gamma", 1.0)),
                    lut_preset=middle_part.get("lut_preset", "monochrome_cinema")
                )
            elif tool_type == "rotoscope":
                result_data = VisualizerEngine.generate_rotoscope_visualization(
                    roto_preset=middle_part.get("roto_preset", "behind_text"),
                    text_content=middle_part.get("text_content", "AIDITOR"),
                    neon_color=middle_part.get("neon_color", "white"),
                    mask_feather=float(middle_part.get("mask_feather", 3.0))
                )
            else:
                self._send_error(f"Unknown tool type: {tool_type}", 400)
                return

            self._send_json({
                "tool_type": tool_type,
                "input_applied": input_part,
                "middle_applied": middle_part,
                "visualizer": result_data,
                "timestamp": time.time()
            })
            return

        # Trigger Video Export (Full Render Job with FFmpeg)
        if path == "/api/render/export":
            job_id = f"export_{uuid.uuid4().hex[:8]}"
            tool_type = body.get("tool_type", "color_grade")
            input_dict = body.get("input", {})
            middle_dict = body.get("middle", {})
            output_dict = body.get("output", {})

            input_cfg = ToolInputConfig(
                source_path=input_dict.get("source_path", "input.mp4"),
                in_point_seconds=float(input_dict.get("in_point_seconds", 0.0)),
                out_point_seconds=float(input_dict.get("out_point_seconds", 5.0)) if input_dict.get("out_point_seconds") else None,
                mute_audio=bool(input_dict.get("mute_audio", False))
            )

            middle_cfg = ToolMiddleConfig(
                target_fps=int(middle_dict.get("target_fps", 60)),
                flow_mode=middle_dict.get("flow_mode", "mci"),
                vibe=middle_dict.get("vibe", "aggressive_drift"),
                target_x=float(middle_dict.get("target_x", 0.5)),
                target_y=float(middle_dict.get("target_y", 0.5)),
                hud_title=middle_dict.get("hud_title", "TARGET LOCKED"),
                contrast=float(middle_dict.get("contrast", 1.2)),
                saturation=float(middle_dict.get("saturation", 0.0)),
                text_content=middle_dict.get("text_content", "AIDITOR")
            )

            out_dir = os.path.join(pm.storage_dir, "exports")
            os.makedirs(out_dir, exist_ok=True)
            out_file = output_dict.get("output_path") or os.path.join(out_dir, f"{job_id}.mp4")

            output_cfg = ToolOutputConfig(
                output_path=out_file,
                resolution=output_dict.get("resolution", "1080p"),
                fps=int(output_dict.get("fps", 60)),
                codec=output_dict.get("codec", "libx264"),
                crf=int(output_dict.get("crf", 18))
            )

            EXPORT_JOBS[job_id] = {
                "job_id": job_id,
                "status": "PROCESSING",
                "progress_percentage": 0.0,
                "message": "Initializing FFmpeg render pipeline...",
                "output_path": out_file,
                "started_at": time.time()
            }

            # Run in thread
            import threading
            def render_thread():
                cmd = PipelineEngine.build_ffmpeg_command(input_cfg, middle_cfg, output_cfg, tool_type, is_preview=False)
                def on_progress(pct: float, msg: str):
                    EXPORT_JOBS[job_id]["progress_percentage"] = pct
                    EXPORT_JOBS[job_id]["message"] = msg

                success = PipelineEngine.execute_render(cmd, total_duration=5.0, progress_callback=on_progress)
                EXPORT_JOBS[job_id]["status"] = "COMPLETED" if success else "FAILED"
                EXPORT_JOBS[job_id]["completed_at"] = time.time()

            t = threading.Thread(target=render_thread)
            t.daemon = True
            t.start()

            self._send_json(EXPORT_JOBS[job_id], 202)
            return

        self._send_error("Endpoint not found", 404)

    def do_PUT(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        body = self._read_json_body()

        if not AiditorRequestHandler.project_manager:
            AiditorRequestHandler.project_manager = ProjectManager()

        if path.startswith("/api/projects/"):
            proj_id = path.split("/")[-1]
            updated = AiditorRequestHandler.project_manager.update_project(proj_id, body)
            if updated:
                self._send_json(updated)
            else:
                self._send_error(f"Project '{proj_id}' not found", 404)
            return

        self._send_error("Endpoint not found", 404)

    def do_DELETE(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if not AiditorRequestHandler.project_manager:
            AiditorRequestHandler.project_manager = ProjectManager()

        if path.startswith("/api/projects/"):
            proj_id = path.split("/")[-1]
            success = AiditorRequestHandler.project_manager.delete_project(proj_id)
            if success:
                self._send_json({"deleted": True, "id": proj_id})
            else:
                self._send_error(f"Project '{proj_id}' not found", 404)
            return

        self._send_error("Endpoint not found", 404)


def start_server(port: int = 8080, host: str = "0.0.0.0") -> ThreadedHTTPServer:
    """Starts the Aiditor HTTP Backend Server."""
    server = ThreadedHTTPServer((host, port), AiditorRequestHandler)
    print(f"🎬 AIDITOR REST API Server listening on http://{host}:{port}")
    return server


if __name__ == "__main__":
    import sys
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    srv = start_server(port)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        srv.server_close()
