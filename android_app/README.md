# AIDITOR Android Video Editor (Kotlin + Jetpack Compose)

Autonomous AI Video Editing mobile application built with **Kotlin + Jetpack Compose (1.12.0 / Compose BOM)**, strict **Minimalist Monochrome (Black & White)** visual language, and seamless integration with the **AIDITOR Python Backend & FFmpeg 8.x Processing Pipeline**.

---

## 🏛 Architecture Overview

```
AIDITOR/
├── aiditor/                     # Python Core & Backend Server
│   ├── server/                  # REST API & Bridge Server
│   │   ├── api_server.py        # Threaded HTTP REST Server (Zero pip dependencies)
│   │   ├── models.py            # Project, ToolConfig & Visualizer Data Models
│   │   ├── pipeline.py          # FFmpeg Engine (Input, Middle, Output complete access)
│   │   ├── project_manager.py   # Project lifecycle, FFmpeg thumbnail extraction
│   │   └── visualizer.py        # Real Visualizer engine for all 6 tools
│   └── cli.py                   # Master CLI ('server' & 'visualize' subcommands)
└── android_app/                 # Dedicated Android Child Directory
    ├── app/
    │   ├── src/main/java/com/aiditor/app/
    │   │   ├── AiditorApp.kt    # Application entry
    │   │   ├── MainActivity.kt  # Compose host activity
    │   │   ├── ui/
    │   │   │   ├── theme/       # B&W Monochrome Palette, Typography, Theme
    │   │   │   ├── navigation/  # 2-Screen AppNavigation graph
    │   │   │   ├── components/  # BwButton, BwCard, BwFab, BwTopBar, BwSlider, BwDialogs
    │   │   │   ├── visualizers/ # Real Canvas Visualizers for all 6 tools
    │   │   │   └── screens/
    │   │   │       ├── mainmenu/  # Screen 1: Main Menu (Projects list, thumbnails, FAB)
    │   │   │       └── workspace/ # Screen 2: Editing Workspace (Preview, Timeline, BottomBar)
    │   │   ├── data/
    │   │   │   ├── model/       # Project, ToolType, ToolParameters, VisualizerModels
    │   │   │   └── repository/  # ProjectRepository, VideoEditingRepository
    │   │   └── bridge/          # BackendApiClient, FfmpegProcessBridge
    │   └── src/main/res/
    │       ├── drawable/        # 100% SVG VectorDrawable icons
    │       └── values/          # Strict B&W colors, strings, themes
    └── build.gradle.kts         # Jetpack Compose 1.12.0 build configuration
```

---

## 🎨 Visual Design System

- **Palette**: Traditional Monochrome Black & White (`#000000` pitch black, `#FFFFFF` stark white, `#121212` / `#181818` card surfaces, `#2A2A2A` subtle borders).
- **Buttons**: Pure white elevated background (`#FFFFFF`) with contrasting black text/icon (`#000000`) and tactile press compression physics.
- **Icons**: 100% SVG Vector images converted to scalable Android `VectorDrawable` XML resources for crisp rendering at any density.
- **Philosophy**: Minimalism inspired by Apple WWDC & Emil Kowalski design engineering.

---

## 📱 The Two Main Screens

### 1. Screen 1: Main Menu (Appears on Launch)
- Shows all previous video projects in elevated `BwCard` items.
- Displays project thumbnail/cover preview, total file size (e.g. `100.0 MB`, `46.0 MB`), creation date, and last modified date.
- Rounded pure white **Floating Action Button (FAB)** with '+' icon (`BwFab`) to add or create new projects.
- Clicking any card opens the project in Screen 2 (Editing Workspace).

### 2. Screen 2: Video Editing Workspace
- **Top Bar**: Minimalist header with project title, undo/redo, and prominent **EXPORT** button.
- **Center-to-Upper**: **Video Preview Screen** with 16:9 aspect ratio container, real-time tracking HUD / rotoscope contour overlays, play/pause, frame step backward/forward, and running timecode (`00:00:12.18 / 00:00:32.50`).
- **Center-to-Bottom**: **Interactive Multi-track Timeline** with draggable playhead needle, video clip segments, audio waveform track, tool markers, and quick split/trim controls.
- **Bottom Bar**: **Feature/Tool List** with 100% SVG vector icons for:
  1. `Optical Flow` (60 FPS interpolation, MCI vs Blend, SCD threshold)
  2. `Beat Sync` (Rhythm transient detection, vibe presets, drop markers)
  3. `Motion Track` (HUD Cyber callout reticle, trajectory path, lock-on)
  4. `Speed Ramp` (Dynamic Bézier curves, velocity graph, peak multiplier)
  5. `Color Grade` (Monochrome cinema, S-curve contrast, 256-bin histogram)
  6. `Rotoscope` (Alpha matte segmentation, neon saber outlines, typography behind subject)

---

## ⚡ Complete Input, Middle, Output Access & Real Visualizers

Every tool provides full parameterized access to:
1. **Input Part**: Source video path, in-point scrubber, out-point trim, audio stream mute toggle.
2. **Middle Part**: Precise algorithm parameters (target FPS, motion vectors, vibe, coordinates, Bézier control points, contrast/exposure, text layers).
3. **Output Part**: Target resolution (720p, 1080p, 4K), frame rate, codec (`libx264`), CRF quality, FFmpeg export command execution.
4. **Real Visualizers**: Live interactive visual displays embedded directly in the Tool Inspector Sheet:
   - `OpticalFlowVisualizerView`: Vector field arrows, direction & magnitude grid.
   - `BeatSyncVisualizerView`: Audio waveform envelope with beat spikes & drop timestamps.
   - `MotionTrackingVisualizerView`: Bounding box reticle, trajectory spline path, confidence readout.
   - `SpeedRampVisualizerView`: Real Bézier velocity graph with keyframe handles.
   - `ColorGradeVisualizerView`: 256-bin Luminance & RGB histogram + S-curve transfer tone.
   - `RotoscopeVisualizerView`: Polygonal silhouette alpha matte contour + neon boundary lines.

---

## 🚀 Running the Python Backend

```bash
# Start backend server on port 8080
aiditor server --port 8080

# Or run visualizer directly from CLI
aiditor visualize --tool optical_flow --fps 60
aiditor visualize --tool beat_sync --vibe aggressive_drift
aiditor visualize --tool motion_tracking --target-x 0.6 --target-y 0.4
aiditor visualize --tool speed_ramp --preset flash_impact_ramp
aiditor visualize --tool color_grade --contrast 1.3 --saturation 0.0
aiditor visualize --tool rotoscope --roto-preset behind_text --text "AIDITOR"
```
