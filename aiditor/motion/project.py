"""
Project State & Cache Management (.axproj)
==========================================
Provides stateless, idempotent, and resumable project directory management
for autonomous AI agents and CLI pipelines.
"""

import os
import json
import time
import shutil
from typing import Dict, Any, Optional, List


class ProjectManager:
    """Manages .axproj project structure, manifests, and cached stages."""

    def __init__(self, project_dir: str):
        self.project_dir = os.path.abspath(project_dir)
        self.manifest_path = os.path.join(self.project_dir, "manifest.json")
        self.scene_path = os.path.join(self.project_dir, "scene.json")
        self.tracks_path = os.path.join(self.project_dir, "tracks.json")
        self.camera_path = os.path.join(self.project_dir, "camera.json")
        self.report_path = os.path.join(self.project_dir, "report.json")
        self.masks_dir = os.path.join(self.project_dir, "masks")
        self.exports_dir = os.path.join(self.project_dir, "exports")
        self.cache_dir = os.path.join(self.project_dir, "cache")

    @classmethod
    def create(cls, video_path: str, project_dir: Optional[str] = None) -> "ProjectManager":
        """Initializes a new .axproj project directory."""
        if not project_dir:
            base_name = os.path.splitext(os.path.basename(video_path))[0]
            parent_dir = os.path.dirname(os.path.abspath(video_path))
            project_dir = os.path.join(parent_dir, f"{base_name}.axproj")

        os.makedirs(project_dir, exist_ok=True)
        pm = cls(project_dir)
        os.makedirs(pm.masks_dir, exist_ok=True)
        os.makedirs(pm.exports_dir, exist_ok=True)
        os.makedirs(pm.cache_dir, exist_ok=True)

        # Write initial manifest
        if not os.path.exists(pm.manifest_path):
            manifest = {
                "project_version": "2.0.0",
                "video_path": os.path.abspath(video_path),
                "created_at": time.time(),
                "updated_at": time.time(),
                "status": "initialized",
                "stages": {
                    "ingest": "completed",
                    "analyze": "pending",
                    "track": "pending",
                    "solve": "pending",
                    "roto": "pending",
                    "export": "pending"
                }
            }
            pm._write_json(pm.manifest_path, manifest)

        return pm

    def _write_json(self, path: str, data: Dict[str, Any]) -> None:
        """Atomically writes JSON to disk."""
        tmp = f"{path}.tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        shutil.move(tmp, path)

    def _read_json(self, path: str) -> Optional[Dict[str, Any]]:
        """Reads JSON if file exists."""
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        return None

    def get_manifest(self) -> Dict[str, Any]:
        return self._read_json(self.manifest_path) or {}

    def update_stage(self, stage: str, status: str) -> None:
        manifest = self.get_manifest()
        if "stages" not in manifest:
            manifest["stages"] = {}
        manifest["stages"][stage] = status
        manifest["updated_at"] = time.time()
        self._write_json(self.manifest_path, manifest)

    def save_scene_analysis(self, scene_data: Dict[str, Any]) -> str:
        """Saves VCA scene analysis and updates stage status."""
        self._write_json(self.scene_path, scene_data)
        self.update_stage("analyze", "completed")
        return self.scene_path

    def get_scene_analysis(self) -> Optional[Dict[str, Any]]:
        return self._read_json(self.scene_path)

    def save_tracks(self, tracks_data: Dict[str, Any]) -> str:
        """Saves 2D tracking trajectories and confidence metrics."""
        self._write_json(self.tracks_path, tracks_data)
        self.update_stage("track", "completed")
        return self.tracks_path

    def get_tracks(self) -> Optional[Dict[str, Any]]:
        return self._read_json(self.tracks_path)

    def save_camera_solve(self, camera_data: Dict[str, Any]) -> str:
        """Saves 3D/2D camera motion vectors and calibration."""
        self._write_json(self.camera_path, camera_data)
        self.update_stage("solve", "completed")
        return self.camera_path

    def get_camera_solve(self) -> Optional[Dict[str, Any]]:
        return self._read_json(self.camera_path)

    def save_report(self, report_data: Dict[str, Any]) -> str:
        """Saves unified canonical report."""
        self._write_json(self.report_path, report_data)
        return self.report_path

    def get_status(self) -> Dict[str, Any]:
        """Returns complete project lifecycle state for AI Agent queries."""
        manifest = self.get_manifest()
        return {
            "project_dir": self.project_dir,
            "video_path": manifest.get("video_path"),
            "stages": manifest.get("stages", {}),
            "has_scene_analysis": os.path.exists(self.scene_path),
            "has_tracks": os.path.exists(self.tracks_path),
            "has_camera_solve": os.path.exists(self.camera_path),
            "has_report": os.path.exists(self.report_path),
            "masks_count": len(os.listdir(self.masks_dir)) if os.path.exists(self.masks_dir) else 0,
            "exports_count": len(os.listdir(self.exports_dir)) if os.path.exists(self.exports_dir) else 0
        }
