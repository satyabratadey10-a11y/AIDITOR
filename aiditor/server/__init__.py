"""
AIDITOR Server & Bridge Module
==============================
Backend services, REST APIs, and real visualizer data generators for mobile and desktop interfaces.
"""

from .models import ProjectMetadata, ToolInputConfig, ToolMiddleConfig, ToolOutputConfig, VisualizerData
from .project_manager import ProjectManager
from .visualizer import VisualizerEngine
from .pipeline import PipelineEngine
from .api_server import start_server, AiditorRequestHandler

__all__ = [
    "ProjectMetadata",
    "ToolInputConfig",
    "ToolMiddleConfig",
    "ToolOutputConfig",
    "VisualizerData",
    "ProjectManager",
    "VisualizerEngine",
    "PipelineEngine",
    "start_server",
    "AiditorRequestHandler"
]
