import wave
import array
import math
import random
import time

t0 = time.time()
SAMPLE_RATE = 48000
DURATION = 25.0
TOTAL_SAMPLES = int(SAMPLE_RATE * DURATION)

samples_L = [0.0] * TOTAL_SAMPLES
samples_R = [0.0] * TOTAL_SAMPLES

# 1. Precompute 1 second of warm D-major ambient pad and tile it
PAD_LEN = SAMPLE_RATE # 1 second cycle
pad_table = [0.0] * PAD_LEN
for i in range(PAD_LEN):
    t = i / SAMPLE_RATE
    w1 = math.sin(2.0 * math.pi * 146.83 * t) * 0.08 # D3
    w2 = math.sin(2.0 * math.pi * 220.00 * t) * 0.05 # A3
    w3 = math.sin(2.0 * math.pi * 185.00 * t) * 0.04 # F#3
    pad_table[i] = w1 + w2 + w3

for i in range(TOTAL_SAMPLES):
    t = i / SAMPLE_RATE
    pulse = 0.85 + 0.15 * math.sin(2.0 * math.pi * 0.25 * t)
    val = pad_table[i % PAD_LEN] * pulse
    samples_L[i] = val
    samples_R[i] = val

def add_tone(freq, start_t, dur, amp, pan=0.0, attack=0.002, decay=0.04):
    start_idx = int(start_t * SAMPLE_RATE)
    num_samples = int(dur * SAMPLE_RATE)
    end_idx = min(TOTAL_SAMPLES, start_idx + num_samples)
    actual_len = end_idx - start_idx
    if actual_len <= 0:
        return
    
    gain_L = math.cos((pan + 1.0) * math.pi / 4.0)
    gain_R = math.sin((pan + 1.0) * math.pi / 4.0)
    
    att_samples = int(attack * SAMPLE_RATE)
    dec_samples = int(decay * SAMPLE_RATE)
    
    for idx in range(actual_len):
        t = idx / SAMPLE_RATE
        env = 1.0
        if idx < att_samples and att_samples > 0:
            env = idx / att_samples
        elif idx > actual_len - dec_samples and dec_samples > 0:
            env = max(0.0, (actual_len - idx) / dec_samples)
        
        val = math.sin(2.0 * math.pi * freq * t) * amp * env
        samples_L[start_idx + idx] += val * gain_L
        samples_R[start_idx + idx] += val * gain_R

def add_click(start_t, amp=0.18):
    start_idx = int(start_t * SAMPLE_RATE)
    dur = 0.02
    actual_len = min(TOTAL_SAMPLES - start_idx, int(dur * SAMPLE_RATE))
    if actual_len <= 0:
        return
    for idx in range(actual_len):
        t = idx / SAMPLE_RATE
        env = math.exp(-t * 260.0)
        val = (math.sin(2.0 * math.pi * 2600.0 * t) + 0.5 * math.sin(2.0 * math.pi * 4400.0 * t)) * amp * env
        samples_L[start_idx + idx] += val
        samples_R[start_idx + idx] += val

def add_whoosh(start_t, dur, amp=0.18, pan_start=-0.6, pan_end=0.6):
    start_idx = int(start_t * SAMPLE_RATE)
    actual_len = min(TOTAL_SAMPLES - start_idx, int(dur * SAMPLE_RATE))
    if actual_len <= 0:
        return
    for idx in range(actual_len):
        prog = idx / actual_len
        env = math.sin(prog * math.pi) ** 2
        pan = pan_start + (pan_end - pan_start) * prog
        gain_L = math.cos((pan + 1.0) * math.pi / 4.0)
        gain_R = math.sin((pan + 1.0) * math.pi / 4.0)
        noise = (random.random() * 2.0 - 1.0) * amp * env
        samples_L[start_idx + idx] += noise * gain_L
        samples_R[start_idx + idx] += noise * gain_R

def add_riser(start_t, dur, start_freq, end_freq, amp=0.22):
    start_idx = int(start_t * SAMPLE_RATE)
    actual_len = min(TOTAL_SAMPLES - start_idx, int(dur * SAMPLE_RATE))
    if actual_len <= 0:
        return
    phase = 0.0
    for idx in range(actual_len):
        prog = idx / actual_len
        curr_freq = start_freq + (end_freq - start_freq) * (prog ** 1.8)
        phase += 2.0 * math.pi * curr_freq / SAMPLE_RATE
        env = (math.sin(prog * math.pi * 0.5) ** 1.5) * amp
        val = math.sin(phase) * env
        samples_L[start_idx + idx] += val
        samples_R[start_idx + idx] += val

# 2. Scene 1 Events (0.0s -> 5.5s)
add_tone(55.0, 0.35, 1.5, amp=0.35, attack=0.01, decay=1.2)
add_tone(1174.66, 0.35, 1.2, amp=0.12, pan=-0.3, attack=0.002, decay=1.0)
add_tone(1479.98, 0.40, 1.2, amp=0.10, pan=0.3, attack=0.002, decay=1.0)
add_tone(1760.00, 0.45, 1.2, amp=0.10, pan=0.0, attack=0.002, decay=1.0)
add_click(2.00, amp=0.12)
add_tone(880.0, 2.00, 0.12, amp=0.08, attack=0.002, decay=0.10)

# 3. Scene 2 Events (Dashboard Entrance & Typing: 5.5s -> 11.5s)
add_whoosh(5.40, 0.55, amp=0.18, pan_start=-0.7, pan_end=0.2)
add_tone(65.41, 5.50, 1.0, amp=0.30, attack=0.005, decay=0.9)
add_tone(523.25, 5.50, 0.6, amp=0.15, attack=0.002, decay=0.5)

# Synchronized Typewriter Clicks
type_times = [6.20, 6.45, 6.70, 6.95, 7.20, 7.45, 7.70]
for idx, tt in enumerate(type_times):
    add_click(tt, amp=0.12 + 0.02 * (idx % 2))

# Terminal outputs
add_tone(783.99, 8.20, 0.25, amp=0.14, pan=-0.2, attack=0.002, decay=0.20)
add_click(8.20, amp=0.10)
add_tone(1046.50, 9.20, 0.25, amp=0.15, pan=0.2, attack=0.002, decay=0.20)
add_click(9.20, amp=0.10)
add_tone(1318.51, 10.20, 0.35, amp=0.18, pan=0.0, attack=0.002, decay=0.30)
add_click(10.20, amp=0.12)

# 4. Scene 3 Events (Feature Cards: 11.5s -> 17.5s)
add_whoosh(11.85, 0.45, amp=0.18, pan_start=-0.8, pan_end=-0.1)
add_click(12.00, amp=0.15)
add_tone(587.33, 12.00, 0.3, amp=0.14, pan=-0.3, attack=0.002, decay=0.25)

add_whoosh(13.45, 0.45, amp=0.18, pan_start=0.1, pan_end=0.8)
add_click(13.60, amp=0.15)
add_tone(739.99, 13.60, 0.3, amp=0.14, pan=0.3, attack=0.002, decay=0.25)

add_whoosh(15.05, 0.45, amp=0.18, pan_start=-0.5, pan_end=0.5)
add_click(15.20, amp=0.15)
add_tone(880.00, 15.20, 0.35, amp=0.16, pan=0.0, attack=0.002, decay=0.30)

# 5. Scene 4 Events (Wave Crescendo & Riser: 17.5s -> 22.0s)
add_riser(17.50, 3.50, start_freq=180.0, end_freq=1100.0, amp=0.22)
add_whoosh(19.50, 1.50, amp=0.22, pan_start=-0.8, pan_end=0.8)
add_tone(45.0, 21.00, 1.8, amp=0.45, attack=0.003, decay=1.7)
add_tone(90.0, 21.00, 1.2, amp=0.25, attack=0.003, decay=1.1)
add_tone(880.0, 21.00, 0.8, amp=0.18, attack=0.002, decay=0.7)

# 6. Scene 5 Events (Grand Finale & CTA: 22.0s -> 25.0s)
add_tone(587.33, 22.00, 3.0, amp=0.18, pan=-0.3, attack=0.003, decay=2.8)
add_tone(739.99, 22.00, 3.0, amp=0.16, pan=0.3, attack=0.003, decay=2.8)
add_tone(880.00, 22.00, 3.0, amp=0.15, pan=-0.1, attack=0.003, decay=2.8)
add_tone(1174.66, 22.00, 3.0, amp=0.14, pan=0.1, attack=0.003, decay=2.8)
add_tone(1760.00, 22.00, 2.5, amp=0.10, pan=0.0, attack=0.003, decay=2.3)

# Master Limiter & Peak Normalization
peak = max(max(abs(s) for s in samples_L), max(abs(s) for s in samples_R))
gain = (0.88 / peak) if peak > 0 else 1.0

# Write with array.array for super-fast C serialization
interleaved = array.array('h', [0] * (TOTAL_SAMPLES * 2))
for i in range(TOTAL_SAMPLES):
    val_l = int(max(-32767, min(32767, samples_L[i] * gain * 32767)))
    val_r = int(max(-32767, min(32767, samples_R[i] * gain * 32767)))
    interleaved[i * 2] = val_l
    interleaved[i * 2 + 1] = val_r

output_wav = "/data/data/com.termux/files/home/wave_saas_soundtrack.wav"
with wave.open(output_wav, "wb") as wav_file:
    wav_file.setnchannels(2)
    wav_file.setsampwidth(2)
    wav_file.setframerate(SAMPLE_RATE)
    wav_file.writeframes(interleaved.tobytes())

print(f"✅ Generated {DURATION}s Wave Audio in {time.time() - t0:.2f}s: {output_wav}")
