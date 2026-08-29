import wave
import struct
import math

SAMPLE_RATE = 44100
DURATION = 10.0
TOTAL_SAMPLES = int(SAMPLE_RATE * DURATION)

samples = [0.0] * TOTAL_SAMPLES

def add_sine(freq, start_t, dur, amp, fade_in=0.01, fade_out=0.05):
    start_idx = int(start_t * SAMPLE_RATE)
    end_idx = min(TOTAL_SAMPLES, int((start_t + dur) * SAMPLE_RATE))
    for i in range(start_idx, end_idx):
        t = (i - start_idx) / SAMPLE_RATE
        # Envelope
        env = 1.0
        if t < fade_in:
            env = t / fade_in
        elif t > dur - fade_out:
            env = max(0.0, (dur - t) / fade_out)
        val = math.sin(2.0 * math.pi * freq * t) * amp * env
        samples[i] += val

def add_chirp(start_freq, end_freq, start_t, dur, amp):
    start_idx = int(start_t * SAMPLE_RATE)
    end_idx = min(TOTAL_SAMPLES, int((start_t + dur) * SAMPLE_RATE))
    for i in range(start_idx, end_idx):
        t = (i - start_idx) / SAMPLE_RATE
        prog = t / dur
        curr_freq = start_freq + (end_freq - start_freq) * prog
        env = math.sin(prog * math.pi)
        samples[i] += math.sin(2.0 * math.pi * curr_freq * t) * amp * env

def add_noise_whoosh(start_t, dur, amp):
    import random
    start_idx = int(start_t * SAMPLE_RATE)
    end_idx = min(TOTAL_SAMPLES, int((start_t + dur) * SAMPLE_RATE))
    for i in range(start_idx, end_idx):
        t = (i - start_idx) / SAMPLE_RATE
        env = math.sin((t / dur) * math.pi) ** 2
        noise = (random.random() * 2.0 - 1.0) * amp * env
        samples[i] += noise

# 1. Ambient Bass Pad (D Minor: 55Hz, 110Hz, 164.8Hz)
for i in range(TOTAL_SAMPLES):
    t = i / SAMPLE_RATE
    bass = math.sin(2.0 * math.pi * 55.0 * t) * 0.25
    harm1 = math.sin(2.0 * math.pi * 110.0 * t) * 0.12
    harm2 = math.sin(2.0 * math.pi * 164.81 * t + math.sin(t * 4) * 0.5) * 0.08
    # Gentle pulse
    pulse = (math.sin(t * math.pi * 2.0 * 1.5) * 0.15 + 0.85)
    samples[i] += (bass + harm1 + harm2) * pulse

# 2. Scene 1 Riser into drop (0.0s -> 2.8s)
add_chirp(100.0, 800.0, 0.5, 2.3, 0.18)
add_noise_whoosh(1.8, 1.0, 0.15)
# Drop impact at 2.8s
add_sine(45.0, 2.8, 1.2, 0.45, fade_in=0.005, fade_out=1.1)
add_sine(90.0, 2.8, 0.8, 0.25, fade_in=0.005, fade_out=0.7)

# 3. Dashboard UI Typing & Clock Pings (2.8s -> 6.0s)
for ping_t in [3.0, 3.2, 3.4, 3.8, 4.0, 4.2, 4.5, 4.8, 5.2, 5.5, 5.8]:
    add_sine(1760.0, ping_t, 0.06, 0.08, fade_in=0.002, fade_out=0.05)
    add_sine(880.0, ping_t, 0.08, 0.06, fade_in=0.002, fade_out=0.07)

# 4. Feature Card Whooshes (6.0s, 6.7s, 7.4s)
add_noise_whoosh(5.9, 0.6, 0.2)
add_chirp(400.0, 1200.0, 6.0, 0.5, 0.12)
add_noise_whoosh(6.6, 0.6, 0.2)
add_chirp(500.0, 1400.0, 6.7, 0.5, 0.12)
add_noise_whoosh(7.3, 0.6, 0.2)
add_chirp(600.0, 1600.0, 7.4, 0.5, 0.12)

# 5. Grand Climax & CTA Impact (8.4s -> 10.0s)
add_sine(50.0, 8.4, 1.6, 0.40, fade_in=0.005, fade_out=1.5)
add_sine(1046.5, 8.4, 1.5, 0.20, fade_in=0.01, fade_out=1.4)  # C6 Chime
add_sine(1318.5, 8.4, 1.5, 0.18, fade_in=0.01, fade_out=1.4)  # E6 Chime
add_sine(1567.9, 8.4, 1.5, 0.18, fade_in=0.01, fade_out=1.4)  # G6 Chime
add_sine(2093.0, 8.4, 1.5, 0.15, fade_in=0.01, fade_out=1.4)  # C7 Chime

# Master Limiter & Normalize
max_val = max(abs(s) for s in samples) or 1.0
norm_factor = 0.90 / max_val

with wave.open("/data/data/com.termux/files/home/saas_soundtrack.wav", "w") as wav_file:
    wav_file.setnchannels(2)
    wav_file.setsampwidth(2)
    wav_file.setframerate(SAMPLE_RATE)
    
    for s in samples:
        val = int(max(-32767, min(32767, s * norm_factor * 32767)))
        # Stereo frame (L, R)
        wav_file.writeframes(struct.pack("<hh", val, val))

print("✅ SaaS Soundtrack Synthesized: 10.0s Stereo 44.1kHz")
