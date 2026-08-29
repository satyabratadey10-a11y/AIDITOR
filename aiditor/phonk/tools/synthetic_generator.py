"""
Synthetic Media Asset Generator
===============================
Generates procedural test car video footage and phonk audio tracks using FFmpeg & pure Python.
"""

import subprocess
import os
import wave
import struct
import math


class SyntheticAssetGenerator:
    """Generates synthetic test videos and phonk beats for verification."""

    @staticmethod
    def generate_phonk_audio(output_wav: str, duration_sec: float = 10.0, bpm: float = 135.0) -> str:
        """
        Synthesizes a Phonk beat with 808 sub-bass kicks, snare hits, and melodic cowbells in pure Python!
        """
        sample_rate = 44100
        total_samples = int(duration_sec * sample_rate)
        beat_interval = 60.0 / bpm
        beat_samples = int(beat_interval * sample_rate)

        # Melodic cowbell frequencies (Phonk scale: C minor pentatonic - C5, Eb5, F5, G5, Bb5)
        cowbell_freqs = [523.25, 622.25, 698.46, 783.99, 932.33, 1046.50]

        samples = [0.0] * total_samples

        # 1. 808 Kicks and Snare hits
        for b in range(int(duration_sec / beat_interval)):
            start_s = b * beat_samples
            # 808 Kick on beats 0, 2 (or every beat)
            # Pitch drops from 150 Hz to 45 Hz with exponential decay
            kick_dur = int(0.35 * sample_rate)
            for i in range(min(kick_dur, total_samples - start_s)):
                t = i / sample_rate
                f = 140.0 * math.exp(-12.0 * t) + 42.0
                env = math.exp(-5.0 * t)
                kick_val = 0.8 * math.sin(2.0 * math.pi * f * t) * env
                # soft distortion
                kick_val = math.tanh(kick_val * 1.5)
                samples[start_s + i] += kick_val

            # Snare on beats 1, 3
            if b % 2 == 1:
                snare_dur = int(0.20 * sample_rate)
                for i in range(min(snare_dur, total_samples - start_s)):
                    t = i / sample_rate
                    # Noise burst + tone
                    noise = (hash(str(i + start_s)) % 1000) / 500.0 - 1.0
                    tone = math.sin(2.0 * math.pi * 220.0 * t)
                    env = math.exp(-18.0 * t)
                    samples[start_s + i] += 0.45 * (0.7 * noise + 0.3 * tone) * env

            # Melodic Phonk Cowbell (syncopated 16th notes)
            for sub in [0, int(0.5 * beat_samples), int(0.75 * beat_samples)]:
                cb_start = start_s + sub
                if cb_start < total_samples:
                    cb_freq = cowbell_freqs[(b * 3 + sub) % len(cowbell_freqs)]
                    cb_dur = int(0.25 * sample_rate)
                    for i in range(min(cb_dur, total_samples - cb_start)):
                        t = i / sample_rate
                        # Cowbell dual square/metallic timbre
                        cb_val = (
                            0.6 * math.sin(2.0 * math.pi * cb_freq * t) +
                            0.4 * math.sin(2.0 * math.pi * cb_freq * 1.48 * t)
                        )
                        # Add high harmonics
                        cb_val = math.tanh(cb_val * 2.0)
                        env = math.exp(-14.0 * t)
                        samples[cb_start + i] += 0.4 * cb_val * env

        # Master clamp and export to 16-bit PCM WAV
        max_val = max(abs(s) for s in samples) or 1.0
        norm_factor = 32000.0 / max_val

        with wave.open(output_wav, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(sample_rate)
            raw_data = struct.pack(f"<{len(samples)}h", *[int(s * norm_factor) for s in samples])
            wf.writeframes(raw_data)

        return output_wav

    @staticmethod
    def generate_car_drift_video(output_mp4: str, duration_sec: float = 10.0) -> str:
        """
        Generates procedural high-action car footage with drift camera dynamics,
        moving headlights, neon city background, and road perspective in FFmpeg.
        """
        # Build synthetic procedural video using testsrc + geq/drawbox/drawtext filters
        filter_str = (
            "testsrc2=size=1920x1080:rate=60,"
            "drawbox=x=0:y=600:w=1920:h=480:color=black@0.9:t=fill,"
            # Neon Grid lines on road
            "drawgrid=width=120:height=60:thickness=2:color=cyan@0.4,"
            # Car body (Red Drift Supercar box) moving horizontally & oscillating
            "drawbox=x='(w/2 - 200 + 350*sin(t*2.5))':y='(680 + 30*cos(t*4))':w=400:h=160:color=red@1.0:t=fill,"
            # Car windshield
            "drawbox=x='(w/2 - 120 + 350*sin(t*2.5))':y='(690 + 30*cos(t*4))':w=240:h=60:color=0x112233@1.0:t=fill,"
            # Headlights (Electric Yellow/White glow)
            "drawbox=x='(w/2 + 180 + 350*sin(t*2.5))':y='(760 + 30*cos(t*4))':w=40:h=30:color=yellow@1.0:t=fill,"
            # Tail lights (Glowing Crimson)
            "drawbox=x='(w/2 - 220 + 350*sin(t*2.5))':y='(760 + 30*cos(t*4))':w=40:h=30:color=0xFF0033@1.0:t=fill,"
            # Velocity / HUD text
            "drawtext=text='PHONK DRIFT APEX // SPEED %{eif\\:180+60*sin(t*2)\\:d} KM/H':x=80:y=120:fontsize=48:fontcolor=cyan:shadowcolor=black:shadowx=3:shadowy=3"
        )

        cmd = [
            "ffmpeg", "-hide_banner", "-y",
            "-f", "lavfi",
            "-i", filter_str,
            "-t", str(duration_sec),
            "-c:v", "libx264",
            "-preset", "ultrafast",
            "-pix_fmt", "yuv420p",
            "-r", "60",
            output_mp4
        ]

        subprocess.run(cmd, capture_output=True, text=True, check=True)
        return output_mp4
