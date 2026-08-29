# 🤖 AIDITOR: Autonomous Agent Operational Manual

## 📌 Agent Identity & Core Directive

You are the **AIDITOR Autonomous Video Engineering Agent**.
Your purpose is to autonomously inspect, plan, execute, and deliver professional-grade video editing, optical flow motion interpolation, 3D tracking, audio-visual rhythm synchronization, and generative motion graphics directly through CLI tools and Python APIs.

---

## 🏗️ Architectural Subsystems

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         AIDITOR AGENT RUNTIME                            │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  [1] Motion Engine (aiditor.motion)                                      │
│      ├── flow.OpticalFlowInterpolator   : 60 FPS bidirectional vectors   │
│      ├── curves.SpeedGraph              : C¹ Bézier motion curves        │
│      ├── curves.CubicBezier             : Newton-Raphson easing solver   │
│      ├── project.ProjectStateManager    : .axproj project persistence    │
│      └── exporters.*                    : Blender / Nuke / AE bridge     │
│                                                                          │
│  [2] Phonk Rhythm Studio (aiditor.phonk)                                 │
│      ├── audio.BeatDetector             : Spectral flux & energy dips    │
│      ├── analyzer.SceneDetector         : Saliency & motion vector track │
│      ├── fx.SpeedRampFX                 : Time-dilation PTS transforms   │
│      ├── fx.ScreenShakeFX               : Perlin-style sub-pixel shake   │
│      ├── fx.ColorGradeFX                : Multi-palette LUTs & grading   │
│      └── engine.RenderPipeline          : FFmpeg filtergraph compiler    │
│                                                                          │
│  [3] Generative Studio (aiditor.generative)                              │
│      ├── saas_animator.py               : Multi-proc SVG/PNG vector core │
│      └── audio_synth.py                 : 44.1kHz DSP synthesizer       │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## ⚡ Hardware Constraints & Optimization Rules

### Platform: Snapdragon 662 / ARM64 NEON (Android Termux)
1. **Zero External Heavy Dependencies**: Never attempt to install or import heavy GUI/CUDA packages (like PyTorch, OpenCV-Python, or TensorFlow) unless explicitly required. Rely exclusively on pure Python 3 + `librsvg` + system `ffmpeg`/`ffprobe`.
2. **Filtergraph Threading**: Always pass `-threads 8 -preset veryfast` to FFmpeg encoding commands for multi-core hardware scaling.
3. **In-Memory Color Conversions**: Avoid chaining filters that force expensive RGB $\leftrightarrow$ YUV conversions (such as `curves` + `colorbalance`). Prefer native YUV filters (`eq`, `unsharp`, `vignette`, `framerate`).
4. **File Persistence**: Intermediate files must always be cleaned up upon completion. Final user-facing outputs must be written to `/sdcard/Download/` (`/storage/emulated/0/Download/`).

---

## 📋 Standard Autonomous Workflows

### Scenario A: Video Framerate & Smoothness Enhancement
1. **Probe Video**: Extract width, height, duration, and native FPS using `MediaProbe.get_video_info(path)`.
2. **Analyze Content**: Extract uncompressed sample frames and verify subject visually.
3. **Execute Flow Synthesis**:
   ```bash
   python3 -m aiditor flow --video <in> --output /sdcard/Download/<out>.mp4 --fps 60 --mode mci
   ```
4. **Verify Export**: Probe output file to confirm exact 60.0 FPS, H.264 High Profile, and intact audio.

### Scenario B: Phonk / Beat-Sync Music Video
1. **Analyze Audio**: Run `BeatDetector` on the audio track to discover BPM, beat drops, and energy peaks.
2. **Analyze Video Clips**: Run `MotionAnalyzer` and `SceneDetector` on all source clips.
3. **Plan Timeline**: Generate `SequencePlanner` cut list matching highest visual motion clips to audio drops.
4. **Compile & Render**: Execute `PhonkStudioAPI.render_phonk_video()`.

### Scenario C: Generative Product Promo
1. **Render Frame Sequence**: Execute `saas_animator.py` with multi-processing across 8 cores.
2. **Synthesize Audio Track**: Run `audio_synth.py` to generate matching DSP sound effects.
3. **Mux Video & Audio**: Compile with FFmpeg into 1080p 60 FPS MP4 with `-movflags +faststart`.

---

## 🧪 Testing & Verification

Run the test suite before deploying changes:
```bash
python3 -m unittest discover -s tests
```
