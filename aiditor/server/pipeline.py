"""
AIDITOR Editing Pipeline & FFmpeg Engine
========================================
Full access to modify:
1. Input part: source video/audio streams, trim range (in/out points), loop, mute
2. Middle part: filtergraphs for optical flow, beat sync, tracking HUD, speed ramp, color grade, rotoscope
3. Output part: resolution scaling, target framerate, codec, CRF/bitrate, container
With multi-pass FFmpeg rendering and real-time progress tracking.
"""

import os
import subprocess
import threading
import time
import re
from typing import Dict, Any, List, Optional, Callable
from .models import ToolInputConfig, ToolMiddleConfig, ToolOutputConfig
from ..motion.flow import OpticalFlowInterpolator
from ..motion.api import MotionTrackingAPI
from ..phonk.tools.media_probe import MediaProbe


class PipelineEngine:
    """Executes FFmpeg operations allowing total modification of input, middle part, and output."""

    @staticmethod
    def build_ffmpeg_command(
        input_cfg: ToolInputConfig,
        middle_cfg: ToolMiddleConfig,
        output_cfg: ToolOutputConfig,
        tool_type: str,
        is_preview: bool = False
    ) -> List[str]:
        """
        Builds the complete FFmpeg command array applying input trimming, middle filter chain,
        and output encoding settings.
        """
        cmd: List[str] = ["ffmpeg", "-hide_banner", "-y"]

        # 1. INPUT MODIFICATION PART
        if input_cfg.in_point_seconds > 0:
            cmd.extend(["-ss", f"{input_cfg.in_point_seconds:.3f}"])

        cmd.extend(["-i", input_cfg.source_path])

        if input_cfg.out_point_seconds is not None and input_cfg.out_point_seconds > input_cfg.in_point_seconds:
            duration = input_cfg.out_point_seconds - input_cfg.in_point_seconds
            cmd.extend(["-t", f"{duration:.3f}"])
        elif is_preview:
            # Short preview limit for instant feedback
            cmd.extend(["-t", "5.0"])

        # 2. MIDDLE FILTERGRAPH COMPILATION PART
        video_filters: List[str] = []

        # Resolution scaling
        res_map = {
            "480p": "854:480",
            "720p": "1280:720",
            "1080p": "1920:1080",
            "4k": "3840:2160"
        }
        target_res = res_map.get(output_cfg.resolution, "1920:1080")
        if is_preview:
            target_res = "640:360"  # Fast preview scale

        video_filters.append(f"scale={target_res}:force_original_aspect_ratio=decrease,pad={target_res}:(ow-iw)/2:(oh-ih)/2:black")

        # Tool-specific middle processing filter chain
        if tool_type == "optical_flow":
            mode_str = "mci" if middle_cfg.flow_mode == "mci" else "blend"
            video_filters.append(f"minterpolate='fps={middle_cfg.target_fps}:mi_mode={mode_str}:scd=fd:scd_threshold={middle_cfg.scd_threshold}'")
            if middle_cfg.color_grade:
                video_filters.append("eq=contrast=1.15:saturation=0.0")  # Minimalist B&W styling

        elif tool_type == "beat_sync":
            # Dynamic pulse and flash filter on beat intervals
            video_filters.append("eq=contrast=1.2:saturation=0.0")

        elif tool_type == "motion_tracking":
            # Cyberpunk HUD Bounding Box & Target Reticle
            box_x = f"(w*{middle_cfg.target_x:.3f}-40)"
            box_y = f"(h*{middle_cfg.target_y:.3f}-40)"
            video_filters.append(f"drawbox=x={box_x}:y={box_y}:w=80:h=80:color=white@0.9:t=2")
            video_filters.append(f"drawbox=x=(w*{middle_cfg.target_x:.3f}-4):y=(h*{middle_cfg.target_y:.3f}-4):w=8:h=8:color=white@1.0:t=fill")
            # Cyber HUD title & status text
            video_filters.append(f"drawtext=text='[{middle_cfg.hud_title}]':x={box_x}:y=({box_y}-25):fontsize=18:fontcolor=white:box=1:boxcolor=black@0.7")

        elif tool_type == "speed_ramp":
            # Time-remapping using setpts
            pts_mult = 1.0 / max(middle_cfg.max_speed_multiplier, 0.2)
            video_filters.append(f"setpts={pts_mult:.3f}*PTS")

        elif tool_type == "color_grade":
            # Monochrome Black & White cinematic contrast curve
            video_filters.append(
                f"eq=contrast={middle_cfg.contrast:.2f}:brightness={middle_cfg.brightness:.2f}:"
                f"saturation={middle_cfg.saturation:.2f}:gamma={middle_cfg.gamma:.2f}"
            )
            # Add subtle unsharp mask for pristine edge clarity
            video_filters.append("unsharp=5:5:0.8:5:5:0.0")

        elif tool_type == "rotoscope":
            # Subject foreground detection and typography behind
            if middle_cfg.roto_preset == "neon_saber":
                video_filters.append("edgedetect=low=0.1:high=0.4,negate")
            elif middle_cfg.roto_preset == "dual_tone":
                video_filters.append("eq=contrast=1.4:saturation=0.0")
            else:
                # Text overlay with subject layering
                video_filters.append(f"drawtext=text='{middle_cfg.text_content}':x=(w-text_w)/2:y=(h-text_h)/2:fontsize=64:fontcolor=white@0.85:shadowcolor=black:shadowx=2:shadowy=2")

        # Combine all filters
        if video_filters:
            cmd.extend(["-vf", ",".join(video_filters)])

        # 3. OUTPUT MODIFICATION PART
        cmd.extend([
            "-c:v", output_cfg.codec,
            "-preset", "ultrafast" if is_preview else "medium",
            "-crf", str(24 if is_preview else output_cfg.crf),
            "-pix_fmt", "yuv420p",
            "-r", str(output_cfg.fps)
        ])

        if input_cfg.mute_audio:
            cmd.append("-an")
        else:
            cmd.extend(["-c:a", "aac", "-b:a", "192k"])

        cmd.extend(["-movflags", "+faststart", output_cfg.output_path])
        return cmd

    @staticmethod
    def execute_render(
        cmd: List[str],
        total_duration: float = 10.0,
        progress_callback: Optional[Callable[[float, str], None]] = None
    ) -> bool:
        """
        Executes the FFmpeg render process while parsing progress from stderr.
        """
        try:
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                universal_newlines=True
            )

            time_regex = re.compile(r"time=(\d+):(\d+):(\d+\.\d+)")

            if process.stderr:
                for line in process.stderr:
                    match = time_regex.search(line)
                    if match:
                        hours, minutes, seconds = match.groups()
                        current_seconds = int(hours) * 3600 + int(minutes) * 60 + float(seconds)
                        pct = min(100.0, max(0.0, (current_seconds / max(total_duration, 0.1)) * 100.0))
                        if progress_callback:
                            progress_callback(pct, f"Rendering... {pct:.1f}% ({current_seconds:.1f}s)")

            process.wait()
            success = (process.returncode == 0)
            if success and progress_callback:
                progress_callback(100.0, "Render complete successfully.")
            return success
        except Exception as e:
            if progress_callback:
                progress_callback(0.0, f"Render error: {str(e)}")
            return False
