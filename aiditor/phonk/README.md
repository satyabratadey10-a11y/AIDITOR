# 🏎️ Phonk Video Studio (ApexPhonkStudio)

A pro-grade automated video content analyzer and Adobe After Effects-level video editing pipeline built in **100% Stock Python 3 and FFmpeg/FFprobe** (zero external pip packages required).

---

## ⚡ Key Capabilities

### 1. 🔍 Deep Video Content & Object Analysis
* **Scene & Shot Cut Detection**: Uses FFmpeg scene score evaluation and frame delta metrics to detect scene transitions, shot timestamps, and durations.
* **Optical Flow & Motion Vector Dynamics**: Analyzes camera trajectories ($dx, dy, da$, zoom), drift energy, and kinetic velocity to classify shots into `DRIFT_AGGRESSIVE`, `HIGH_SPEED_ACTION`, `SMOOTH_PAN`, and `HERO_STATIC`.
* **Object Saliency & Car Tracking**: Tracks vehicle bounding boxes and luminance centroids to center dynamic crops and zooms on the subject.
* **Atmosphere & Lighting Analysis**: Analyzes average luminance, saturation, contrast, and highlights to dynamically match the aesthetic color grading.

### 2. 🎵 Phonk Audio Beat & Drop Analysis
* **BPM & Beat Detection**: Pure Python Short-Time Energy (STE) and transient onset detection to extract exact beat timestamps and tempo (e.g. 130–160 BPM).
* **808 Sub-Bass & Drop Locator**: Detects build-up risers and heavy 808 sub-bass drops to map high-speed drift sequences to the music climax.
* **Audio Mastering & Bass Boost**: Multi-band sub-bass punch boost, stereo compressor, and true-peak limiter.

### 3. ✨ After Effects-Grade Visual FX Suite
* **Velocity Ramping (Twixtor Curves)**: Slow-mo build-up easing into snap acceleration on kicks and drops.
* **S-Shake (Camera Impact Jitter)**: Directional screen shakes with harmonic trigonometric decay ($A \cdot e^{-\gamma t} \sin(\omega t)$).
* **Chromatic Aberration (RGB Split)**: Beat-synced red/blue channel split with edge smear.
* **Exposure Pops & Optical Bloom**: Rhythmic brightness/contrast pops and neon headlight bloom.
* **Tokyo Midnight Color Grading**: Crushed blacks, cyan/teal shadows, electric crimson highlights, and anamorphic vignette.
* **Multi-Format Framing**: 9:16 Vertical (TikTok/Reels/Shorts), 16:9 Widescreen (YouTube), 2.35:1 Anamorphic Cinema.

---

## 🚀 Quick Start (CLI)

### 1. Generate Procedural Test Footage & Phonk Beat
```bash
python3 -m phonk_video_studio.cli generate-demo --out-dir ./demo_assets --duration 8.0 --bpm 135.0
```

### 2. Analyze Video Content
```bash
python3 -m phonk_video_studio.cli analyze --video ./demo_assets/demo_car_drift.mp4 --json video_report.json
```

### 3. Analyze Audio Beats & Drops
```bash
python3 -m phonk_video_studio.cli analyze-audio --audio ./demo_assets/demo_phonk_beat.wav --json audio_report.json
```

### 4. Render an After Effects-Grade Phonk Car Edit
```bash
python3 -m phonk_video_studio.cli edit \
  --videos ./demo_assets/demo_car_drift.mp4 \
  --audio ./demo_assets/demo_phonk_beat.wav \
  --output final_phonk_car_edit.mp4 \
  --style tokyo_midnight \
  --aspect 9:16 \
  --shake 1.0 \
  --rgb-split 1.0 \
  --flash 0.35 \
  --fps 60
```

---

## 💻 Python Programmatic API

```python
from phonk_video_studio import (
    PhonkCarEditor,
    VideoAnalyzer,
    PhonkAudioAnalyzer,
    EditConfig,
    EditStyle,
    AspectRatio,
)

# 1. Analyze Video Content
analyzer = VideoAnalyzer("car_footage.mp4")
video_report = analyzer.analyze()
print("Detected Scenes:", video_report["scene_count"])
print("Motion Score:", video_report["overall_motion_score"])

# 2. Analyze Phonk Music Track
audio_analyzer = PhonkAudioAnalyzer("phonk_track.mp3")
audio_report = audio_analyzer.analyze()
print("Tempo:", audio_report["bpm"], "BPM")
print("Drop timestamp:", audio_report["drop_timestamp"])

# 3. Create Full After Effects Phonk Car Edit
config = EditConfig(
    style=EditStyle.TOKYO_MIDNIGHT,      # Tokyo Midnight, Cyber Drift, Golden Heat, etc.
    aspect_ratio=AspectRatio.VERTICAL_9_16, # 9:16, 16:9, 2.35:1
    shake_intensity=1.0,                 # S-Shake intensity
    rgb_split_intensity=1.0,             # Chromatic Aberration
    flash_intensity=0.35,                # Exposure Pops
    bass_boost_db=6.0,                   # Sub-bass mastering
    output_fps=60,
    preset="veryfast"
)

editor = PhonkCarEditor(config)
editor.create_phonk_car_edit(
    video_sources=["clip1.mp4", "clip2.mp4"],
    audio_source="phonk_track.mp3",
    output_file="final_edit.mp4"
)
```
