"""
AIDITOR Server Models
=====================
Data models for projects, tool configurations, visualizer data, and FFmpeg render jobs.
"""

import os
import json
import time
from typing import Dict, Any, List, Optional
from dataclasses import dataclass, field, asdict


@dataclass
class ProjectMetadata:
    id: str
    name: str
    video_path: str
    thumbnail_path: str
    file_size_bytes: int
    file_size_formatted: str
    duration_seconds: float
    width: int
    height: int
    fps: float
    created_at: str
    modified_at: str
    applied_tools: List[Dict[str, Any]] = field(default_factory=list)
    timeline_markers: List[Dict[str, Any]] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ProjectMetadata":
        return cls(
            id=data.get("id", ""),
            name=data.get("name", "Untitled Project"),
            video_path=data.get("video_path", ""),
            thumbnail_path=data.get("thumbnail_path", ""),
            file_size_bytes=data.get("file_size_bytes", 0),
            file_size_formatted=data.get("file_size_formatted", "0 MB"),
            duration_seconds=data.get("duration_seconds", 0.0),
            width=data.get("width", 1920),
            height=data.get("height", 1080),
            fps=data.get("fps", 30.0),
            created_at=data.get("created_at", time.strftime("%Y-%m-%d %H:%M:%S")),
            modified_at=data.get("modified_at", time.strftime("%Y-%m-%d %H:%M:%S")),
            applied_tools=data.get("applied_tools", []),
            timeline_markers=data.get("timeline_markers", [])
        )


@dataclass
class ToolInputConfig:
    """Configures input modification parameters for any tool."""
    source_path: str
    in_point_seconds: float = 0.0
    out_point_seconds: Optional[float] = None
    stream_index: int = 0
    mute_audio: bool = False
    loop: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class ToolMiddleConfig:
    """Configures middle processing algorithm parameters for any tool."""
    # Optical Flow params
    target_fps: int = 60
    flow_mode: str = "mci"  # "mci" or "blend"
    scd_threshold: float = 10.0
    color_grade: bool = True

    # Beat Sync params
    vibe: str = "aggressive_drift"  # "aggressive_drift", "chill_neon", "speed_ramp_chaos", "dark_gritty"
    beat_sensitivity: float = 0.8
    audio_path: Optional[str] = None
    cut_frequency: str = "medium"  # "fast", "medium", "slow"

    # Motion Tracking params
    tracking_mode: str = "hud_callout"  # "hud_callout", "point_track", "face_lock", "stabilize"
    target_x: float = 0.5  # Normalized 0..1
    target_y: float = 0.5  # Normalized 0..1
    hud_title: str = "TARGET LOCKED"
    hud_subtitle: str = "TRACKING ACTIVE"
    hud_color: str = "0xFFFFFF"

    # Speed Ramp params
    ramp_preset: str = "flash_impact_ramp"  # "flash_impact_ramp", "smooth_flow", "crash_zoom_in"
    speed_curve_points: List[Dict[str, float]] = field(default_factory=list)
    duration_seconds: float = 2.0
    max_speed_multiplier: float = 2.5

    # Rotoscope params
    roto_preset: str = "behind_text"  # "behind_text", "dual_tone", "neon_saber"
    text_content: str = "AIDITOR"
    neon_color: str = "white"
    mask_feather: float = 3.0

    # Color Grade params
    lut_preset: str = "monochrome_cinema"  # "monochrome_cinema", "high_contrast_bw", "cyber_noir"
    exposure: float = 0.0  # -2.0 to +2.0
    contrast: float = 1.2  # 0.5 to 2.5
    brightness: float = 0.0  # -1.0 to 1.0
    saturation: float = 0.0  # 0.0 for pure black & white
    gamma: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class ToolOutputConfig:
    """Configures output render & delivery parameters for any tool."""
    output_path: str
    resolution: str = "1080p"  # "480p", "720p", "1080p", "4k"
    fps: int = 60
    codec: str = "libx264"
    crf: int = 18
    bitrate: str = "12M"
    container: str = "mp4"

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class VisualizerData:
    """Carries real visualizer data generated for UI display."""
    tool_type: str
    timestamp: float
    data: Dict[str, Any]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "tool_type": self.tool_type,
            "timestamp": self.timestamp,
            "data": self.data
        }
