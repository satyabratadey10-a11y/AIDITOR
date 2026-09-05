"""
AIDITOR Project Manager
=======================
Manages project lifecycles, thumbnail extraction via FFmpeg, file size tracking,
creation & modification dates, and metadata persistence.
"""

import os
import json
import time
import subprocess
from typing import List, Dict, Any, Optional
from .models import ProjectMetadata
from ..phonk.tools.media_probe import MediaProbe


class ProjectManager:
    """Manages project persistence, thumbnails, and file size tracking."""

    def __init__(self, storage_dir: Optional[str] = None):
        if storage_dir is None:
            # Dedicated projects directory under AIDITOR root
            base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
            self.storage_dir = os.path.join(base_dir, "projects_data")
        else:
            self.storage_dir = storage_dir

        self.thumbnails_dir = os.path.join(self.storage_dir, "thumbnails")
        self.db_path = os.path.join(self.storage_dir, "projects.json")

        os.makedirs(self.storage_dir, exist_ok=True)
        os.makedirs(self.thumbnails_dir, exist_ok=True)

        self._ensure_sample_projects()

    def _ensure_sample_projects(self):
        """Ensures projects database file exists without placeholder projects."""
        if not os.path.exists(self.db_path) or os.path.getsize(self.db_path) == 0:
            self._save_projects([])

    def _generate_placeholder_thumbnail(self, path: str, title: str):
        """Creates a minimal valid JPEG placeholder thumbnail instantaneously."""
        if os.path.exists(path):
            return
        minimal_jpg = (
            b'\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00H\x00H\x00\x00\xff\xdb\x00C\x00'
            b'\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19'
            b'\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.\' \",#\x1c\x1c(7),01444\x1f\'9=82<.342'
            b'\xff\xc0\x00\x0b\x08\x00\x01\x00\x01\x01\x01\x11\x00\xff\xc4\x00\x1f\x00\x00\x01'
            b'\x05\x01\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x01\x02\x03\x04\x05'
            b'\x06\x07\x08\t\n\x0b\xff\xda\x00\x08\x01\x01\x00\x00?\x00\xbf\x00\xff\xd9'
        )
        try:
            with open(path, "wb") as f:
                f.write(minimal_jpg)
        except Exception:
            pass

    def _read_projects(self) -> List[Dict[str, Any]]:
        try:
            with open(self.db_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return []

    def _save_projects(self, projects: List[Dict[str, Any]]):
        with open(self.db_path, "w", encoding="utf-8") as f:
            json.dump(projects, f, indent=2)

    def list_projects(self) -> List[Dict[str, Any]]:
        """Returns all projects sorted by last modified date descending."""
        projects = self._read_projects()
        return sorted(projects, key=lambda p: p.get("modified_at", ""), reverse=True)

    def get_project(self, project_id: str) -> Optional[Dict[str, Any]]:
        projects = self._read_projects()
        for p in projects:
            if p["id"] == project_id:
                return p
        return None

    def create_project(self, name: str, video_path: str = "") -> Dict[str, Any]:
        """Creates a new project record, probes the video, and extracts its cover thumbnail."""
        project_id = f"proj_{int(time.time())}"
        now_str = time.strftime("%Y-%m-%d %H:%M:%S")

        width = 1920
        height = 1080
        fps = 30.0
        duration = 10.0
        file_size_bytes = 0

        # Probe video if path provided and exists
        if video_path and os.path.exists(video_path):
            try:
                info = MediaProbe.get_video_info(video_path)
                width = info.get("width", 1920) or 1920
                height = info.get("height", 1080) or 1080
                fps = info.get("fps", 30.0) or 30.0
                duration = info.get("duration", 10.0) or 10.0
                file_size_bytes = os.path.getsize(video_path)
            except Exception:
                file_size_bytes = 1024 * 1024 * 25  # 25 MB default

        if file_size_bytes == 0:
            file_size_bytes = 1024 * 1024 * 35

        file_size_mb = file_size_bytes / (1024 * 1024)
        file_size_formatted = f"{file_size_mb:.1f} MB"

        thumbnail_path = os.path.join(self.thumbnails_dir, f"{project_id}.jpg")

        if video_path and os.path.exists(video_path):
            # Extract actual video thumbnail
            cmd = [
                "ffmpeg", "-hide_banner", "-y",
                "-ss", "00:00:01",
                "-i", video_path,
                "-vframes", "1",
                "-vf", "scale=320:180:force_original_aspect_ratio=decrease,pad=320:180:(ow-iw)/2:(oh-ih)/2:black",
                thumbnail_path
            ]
            try:
                subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
            except Exception:
                self._generate_placeholder_thumbnail(thumbnail_path, name)
        else:
            self._generate_placeholder_thumbnail(thumbnail_path, name)

        new_project = {
            "id": project_id,
            "name": name,
            "video_path": video_path or os.path.join(self.storage_dir, f"{project_id}.mp4"),
            "thumbnail_path": thumbnail_path,
            "file_size_bytes": file_size_bytes,
            "file_size_formatted": file_size_formatted,
            "duration_seconds": duration,
            "width": width,
            "height": height,
            "fps": fps,
            "created_at": now_str,
            "modified_at": now_str,
            "applied_tools": [],
            "timeline_markers": []
        }

        projects = self._read_projects()
        projects.insert(0, new_project)
        self._save_projects(projects)
        return new_project

    def update_project(self, project_id: str, updates: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        projects = self._read_projects()
        for p in projects:
            if p["id"] == project_id:
                for k, v in updates.items():
                    if k not in ["id", "created_at"]:
                        p[k] = v
                p["modified_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
                self._save_projects(projects)
                return p
        return None

    def delete_project(self, project_id: str) -> bool:
        projects = self._read_projects()
        filtered = [p for p in projects if p["id"] != project_id]
        if len(filtered) != len(projects):
            self._save_projects(filtered)
            return True
        return False
