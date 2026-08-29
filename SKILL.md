---
name: aiditor-vfx-studio
description: Master AI Agent Skill for Autonomous Video Editing, True Optical Flow 60 FPS Interpolation, Motion Tracking, Rotoscoping, Bézier Speed Ramping, Audio Beat-Sync, and Generative SaaS Motion Graphics.
---

# ⚡ AIDITOR AI Agent Skill Specification

## 🎯 When to Activate This Skill
Activate this skill whenever the user asks to:
1. **Interpolate Framerates to 60 FPS**: Synthesize real intermediate non-static motion frames using bidirectional optical vectors (`flow` command).
2. **Track Moving Objects / HUD Pinning**: Apply cyber HUD callout bounding boxes to cars, faces, or objects (`track` command).
3. **Place 3D Typography Behind Foreground Subjects**: Rotoscope foreground subjects and insert text between layers (`roto` command).
4. **Stabilize Camera or Lock Onto Faces**: Smooth shaky footage or crop/center onto human faces or subjects (`camera` command).
5. **Compute & Visualize Speed Graphs / Zoom Curves**: Solve Bézier easing curves, render ASCII speed plots, or calculate speed ramps (`curve`, `zoom`, `speed-ramp` commands).
6. **Generate Audio-Visual Beat-Synced Music Videos**: Auto-cut videos to phonk or music beat drops with dynamic speed ramping, shake, and glow (`phonk` command).
7. **Create Programmatic SaaS Animations From Scratch**: Render high-tech dark-mode glassmorphic product promo videos with synthesized audio (`saas` engine).
8. **Export 3D Tracking Data to VFX DCC Software**: Generate Blender Python, Foundry Nuke, or After Effects scripts (`export` command).

---

## 🛠️ CLI Command Reference for AI Agents

### 1. Optical Flow Motion Interpolation
```bash
python3 -m aiditor flow \
  --video <input_video> \
  --output <output_mp4> \
  --fps 60 \
  --mode mci \
  --scd 10.0
```
* **Modes**: `mci` (Motion Compensated Interpolation - bidirectional vectors) or `blend` (Sub-frame blending).
* **Scene Cut Protection**: `--scd 10.0` prevents ghosting artifacts across hard cuts and transitions.

### 2. Motion Tracking & HUD Pinning
```bash
python3 -m aiditor track \
  --video <input_video> \
  --output <output_mp4> \
  --title "TARGET LOCKED" \
  --subtitle "TRACKING" \
  --color 0x00FFCC \
  --resolution 1080p
```

### 3. Rotoscoping & 3D Typography
```bash
python3 -m aiditor roto \
  --video <input_video> \
  --output <output_mp4> \
  --text "AIDITOR" \
  --color-name cyan \
  --preset behind_text \
  --resolution 1080p
```

### 4. Camera Lock-On & Face Stabilization
```bash
python3 -m aiditor camera \
  --video <input_video> \
  --output <output_mp4> \
  --mode face_lock \
  --smoothing 30 \
  --resolution 1080p
```

### 5. Bézier Speed Graph & Dynamic Zoom
```bash
# Evaluate Speed Ramp
python3 -m aiditor speed-ramp --preset flash_impact_ramp --duration 2.0 --json

# Evaluate Camera Punch Zoom
python3 -m aiditor zoom --preset punch_zoom_pulse --duration 1.5 --max-zoom 2.2 --json
```

### 6. Phonk Audio-Visual Beat Sync
```bash
python3 -m aiditor phonk \
  --videos clip1.mp4 clip2.mp4 clip3.mp4 \
  --audio soundtrack.mp3 \
  --output final_edit.mp4 \
  --vibe aggressive_drift \
  --resolution 1080p \
  --fps 60
```

### 7. Export 3D Tracking Data to VFX DCC Software
```bash
python3 -m aiditor export \
  --video <input_video> \
  --format all \
  --focal-length 35.0 \
  --output camera_solve
```

---

## 🧠 Autonomous Execution Protocol

When an AI Agent receives a video editing or VFX request:
1. **Analyze Video First**: Run `MediaProbe.get_video_info(path)` or extract sample keyframes at timestamps `1s, 5s, 10s` and inspect them with `view_file` to understand the genuine subject, lighting, and resolution.
2. **Select Optimized Pipeline**:
   - For fast action/cars: Apply optical flow + automotive color grading (`eq=contrast=1.18:saturation=1.24:gamma=1.03,unsharp=5:5:0.6`).
   - For beat sync: Run transient beat detection and map drops to speed ramp flash curves.
3. **Execute Non-Blocking Render**: Launch FFmpeg / Python tasks with appropriate threading (`-threads 8 -preset veryfast`).
4. **Deliver Output to Download**: Always export final user-facing videos to `/sdcard/Download/` (`/storage/emulated/0/Download/`) for instant accessibility in the Android Gallery.
