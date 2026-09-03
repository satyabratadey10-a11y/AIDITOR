import os
import math
import subprocess
from concurrent.futures import ProcessPoolExecutor

WIDTH = 1080
HEIGHT = 1920
FPS = 60
DURATION = 25.0
TOTAL_FRAMES = int(FPS * DURATION)
OUT_DIR = "/data/data/com.termux/files/home/wave_frames"

os.makedirs(OUT_DIR, exist_ok=True)

def clamp(v, mn=0.0, mx=1.0):
    return max(mn, min(mx, float(v)))

def ease_out_expo(t):
    return 1.0 if t >= 1.0 else 1.0 - math.pow(2.0, -10.0 * t)

def ease_in_out_quad(t):
    return 2.0 * t * t if t < 0.5 else 1.0 - math.pow(-2.0 * t + 2.0, 2.0) / 2.0

def spring(t):
    if t <= 0.0: return 0.0
    if t >= 1.0: return 1.0
    return 1.0 - math.exp(-7.0 * t) * math.cos(9.0 * t)

def build_wave_path(cy, amp1, freq1, speed1, amp2, freq2, speed2, t, phase_offset=0.0, steps=54):
    pts = []
    for i in range(steps + 1):
        x = (i / float(steps)) * WIDTH
        y = cy + amp1 * math.sin(freq1 * x + speed1 * t + phase_offset) + amp2 * math.cos(freq2 * x - speed2 * t)
        pts.append((x, y))
    d_line = f"M {pts[0][0]:.1f} {pts[0][1]:.1f} " + " ".join(f"L {p[0]:.1f} {p[1]:.1f}" for p in pts[1:])
    return pts, d_line

def render_frame_svg(f):
    t = f / float(FPS)
    
    # Breathing background ambient glows (White / Pearlescent + Hero Blue & Golden Yellow)
    glow_x1 = 540 + int(80 * math.sin(t * 1.2))
    glow_y1 = 450 + int(60 * math.cos(t * 0.9))
    glow_x2 = 620 + int(90 * math.cos(t * 1.1))
    glow_y2 = 1450 + int(70 * math.sin(t * 1.3))
    
    svg_parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">',
        '<defs>',
        # Background Pearlescent Gradient (Strictly light/white)
        '  <linearGradient id="bgGrad" x1="0%" y1="0%" x2="0%" y2="100%">',
        '    <stop offset="0%" stop-color="#FFFFFF"/>',
        '    <stop offset="45%" stop-color="#F8FAFC"/>',
        '    <stop offset="100%" stop-color="#EFF6FF"/>',
        '  </linearGradient>',
        # Hero Blue Gradients
        '  <linearGradient id="blueGrad" x1="0%" y1="0%" x2="100%" y2="0%">',
        '    <stop offset="0%" stop-color="#1D4ED8"/>',
        '    <stop offset="50%" stop-color="#2563EB"/>',
        '    <stop offset="100%" stop-color="#0284C7"/>',
        '  </linearGradient>',
        # Yellow / Gold Gradients
        '  <linearGradient id="yellowGrad" x1="0%" y1="0%" x2="100%" y2="0%">',
        '    <stop offset="0%" stop-color="#D97706"/>',
        '    <stop offset="50%" stop-color="#F59E0B"/>',
        '    <stop offset="100%" stop-color="#FBBF24"/>',
        '  </linearGradient>',
        # CTA Button Gradient: Hero Blue to Vibrant Amber
        '  <linearGradient id="ctaGrad" x1="0%" y1="0%" x2="100%" y2="0%">',
        '    <stop offset="0%" stop-color="#1D4ED8"/>',
        '    <stop offset="60%" stop-color="#2563EB"/>',
        '    <stop offset="100%" stop-color="#F59E0B"/>',
        '  </linearGradient>',
        # Glass Card Fill
        '  <linearGradient id="glassFill" x1="0%" y1="0%" x2="0%" y2="100%">',
        '    <stop offset="0%" stop-color="#FFFFFF" stop-opacity="0.94"/>',
        '    <stop offset="100%" stop-color="#F8FAFC" stop-opacity="0.86"/>',
        '  </linearGradient>',
        # Wave Area Fills
        '  <linearGradient id="waveFillBlue" x1="0%" y1="0%" x2="0%" y2="100%">',
        '    <stop offset="0%" stop-color="#2563EB" stop-opacity="0.18"/>',
        '    <stop offset="100%" stop-color="#2563EB" stop-opacity="0.0"/>',
        '  </linearGradient>',
        '  <linearGradient id="waveFillYellow" x1="0%" y1="0%" x2="0%" y2="100%">',
        '    <stop offset="0%" stop-color="#F59E0B" stop-opacity="0.15"/>',
        '    <stop offset="100%" stop-color="#F59E0B" stop-opacity="0.0"/>',
        '  </linearGradient>',
        # Soft Radial Ambient Diffusion (Hero Blue)
        '  <radialGradient id="ambientBlue" cx="50%" cy="50%" r="50%">',
        '    <stop offset="0%" stop-color="#3B82F6" stop-opacity="0.14"/>',
        '    <stop offset="100%" stop-color="#3B82F6" stop-opacity="0.0"/>',
        '  </radialGradient>',
        # Soft Radial Ambient Diffusion (Warm Yellow)
        '  <radialGradient id="ambientYellow" cx="50%" cy="50%" r="50%">',
        '    <stop offset="0%" stop-color="#F59E0B" stop-opacity="0.10"/>',
        '    <stop offset="100%" stop-color="#F59E0B" stop-opacity="0.0"/>',
        '  </radialGradient>',
        '</defs>',
        # Base Canvas
        f'<rect width="{WIDTH}" height="{HEIGHT}" fill="url(#bgGrad)"/>',
        f'<circle cx="{glow_x1}" cy="{glow_y1}" r="650" fill="url(#ambientBlue)"/>',
        f'<circle cx="{glow_x2}" cy="{glow_y2}" r="700" fill="url(#ambientYellow)"/>',
    ]

    # Minimalist Grid Lines (Light slate, high elegance)
    for gy in range(120, HEIGHT, 160):
        svg_parts.append(f'<line x1="0" y1="{gy}" x2="{WIDTH}" y2="{gy}" stroke="#2563EB" stroke-opacity="0.04" stroke-width="1"/>')
    for gx in range(90, WIDTH, 180):
        svg_parts.append(f'<line x1="{gx}" y1="0" x2="{gx}" y2="{HEIGHT}" stroke="#2563EB" stroke-opacity="0.04" stroke-width="1"/>')

    # =========================================================================
    # SCENE 1 (0.0s – 5.5s): THE WAVE GENESIS & HERO IDENTITY
    # =========================================================================
    if t < 5.7:
        alpha1 = clamp(t / 0.5) * (1.0 - clamp((t - 5.0) / 0.5))
        scale1 = 0.88 + 0.12 * ease_out_expo(clamp(t / 1.0))
        
        if alpha1 > 0.001:
            svg_parts.append(f'<g opacity="{alpha1:.3f}" transform="translate(540, 960) scale({scale1:.4f}) translate(-540, -960)">')
            
            # 1. Top Badge (Clean Glass Pill) - Y: 380, H: 50
            svg_parts.append('<rect x="330" y="380" width="420" height="50" rx="25" fill="#EFF6FF" stroke="#93C5FD" stroke-width="1.5"/>')
            svg_parts.append('<text x="540" y="413" font-family="sans-serif" font-weight="700" font-size="19" fill="#1D4ED8" text-anchor="middle" letter-spacing="2">✦ AUTONOMOUS WAVE ENGINE 3.0 ✦</text>')
            
            # 2. Central Wave Graphics Cluster - Y: 520 to 760 (Centered at 640)
            pts_b, d_b = build_wave_path(630, 48, 0.007, 3.2, 22, 0.015, 1.8, t, 0.0)
            pts_y, d_y = build_wave_path(650, 42, 0.006, 2.7, 26, 0.012, 2.2, t, 1.6)
            
            # Area fills
            area_b = f"{d_b} L {WIDTH} 780 L 0 780 Z"
            area_y = f"{d_y} L {WIDTH} 780 L 0 780 Z"
            svg_parts.append(f'<path d="{area_y}" fill="url(#waveFillYellow)"/>')
            svg_parts.append(f'<path d="{area_b}" fill="url(#waveFillBlue)"/>')
            svg_parts.append(f'<path d="{d_y}" fill="none" stroke="#F59E0B" stroke-width="3.5" stroke-linecap="round"/>')
            svg_parts.append(f'<path d="{d_b}" fill="none" stroke="#2563EB" stroke-width="4.5" stroke-linecap="round"/>')
            
            # Floating Wave Nodes
            for idx in [12, 27, 42]:
                nx, ny = pts_b[idx]
                svg_parts.append(f'<circle cx="{nx:.1f}" cy="{ny:.1f}" r="14" fill="#2563EB" fill-opacity="0.2"/>')
                svg_parts.append(f'<circle cx="{nx:.1f}" cy="{ny:.1f}" r="6" fill="#1D4ED8"/>')
            
            # 3. Hero Typography - Y: 870 & 940 (Strict bounds, 0 overlap)
            svg_parts.append('<text x="540" y="870" font-family="sans-serif" font-weight="900" font-size="76" fill="#0F172A" text-anchor="middle" letter-spacing="3">NEURAL WAVE</text>')
            svg_parts.append('<text x="540" y="938" font-family="sans-serif" font-weight="600" font-size="28" fill="#475569" text-anchor="middle">Real-Time Signal &amp; Audio-Visual Intelligence</text>')
            
            # 4. Feature Badges - Y: 1040, H: 46 (Zero collision with Y: 938)
            pills = [
                ("⚡ 60 FPS Fluid Wave", "#1D4ED8", "#DBEAFE", 140),
                ("🎯 Zero-Delay SFX", "#D97706", "#FEF3C7", 420),
                ("🔒 Local Hardware Sync", "#0F172A", "#F1F5F9", 700)
            ]
            for p_text, p_col, p_bg, px in pills:
                svg_parts.append(f'<rect x="{px}" y="1040" width="240" height="46" rx="23" fill="{p_bg}" stroke="{p_col}" stroke-opacity="0.3" stroke-width="1.5"/>')
                svg_parts.append(f'<text x="{px+120}" y="1070" font-family="sans-serif" font-weight="700" font-size="16" fill="{p_col}" text-anchor="middle">{p_text}</text>')
                
            svg_parts.append('</g>')

    # =========================================================================
    # SCENE 2 (5.5s – 11.5s): LIVE INTERACTIVE GLASS DASHBOARD
    # =========================================================================
    if 5.4 <= t <= 11.7:
        p2 = (t - 5.5) / 6.0
        alpha2 = clamp((t - 5.5) / 0.4) * (1.0 - clamp((t - 11.0) / 0.4))
        card_scale = 0.90 + 0.10 * spring(clamp((t - 5.5) / 0.7))
        
        if alpha2 > 0.001:
            svg_parts.append(f'<g opacity="{alpha2:.3f}" transform="translate(540, 960) scale({card_scale:.4f}) translate(-540, -960)">')
            
            # Modal Glass Container - X: 90, Y: 300, W: 900, H: 1260
            cx, cy, cw, ch = 90, 300, 900, 1260
            svg_parts.append(f'<rect x="{cx}" y="{cy}" width="{cw}" height="{ch}" rx="32" fill="url(#glassFill)" stroke="#93C5FD" stroke-opacity="0.6" stroke-width="2"/>')
            
            # Header Titlebar - Y: 300 to 365
            svg_parts.append(f'<line x1="{cx}" y1="{cy+65}" x2="{cx+cw}" y2="{cy+65}" stroke="#E2E8F0" stroke-width="1.5"/>')
            svg_parts.append(f'<circle cx="{cx+45}" cy="{cy+33}" r="8" fill="#EF4444"/>')
            svg_parts.append(f'<circle cx="{cx+75}" cy="{cy+33}" r="8" fill="#F59E0B"/>')
            svg_parts.append(f'<circle cx="{cx+105}" cy="{cy+33}" r="8" fill="#10B981"/>')
            svg_parts.append(f'<text x="{cx+cw/2}" y="{cy+40}" font-family="sans-serif" font-weight="700" font-size="18" fill="#64748B" text-anchor="middle" letter-spacing="2">WAVE ENGINE // REAL-TIME SPECTRAL RUNTIME</text>')
            
            # Inner Chart Window - X: 135, Y: 395, W: 810, H: 330
            chart_x, chart_y, chart_w, chart_h = cx + 45, cy + 95, cw - 90, 330
            svg_parts.append(f'<rect x="{chart_x}" y="{chart_y}" width="{chart_w}" height="{chart_h}" rx="20" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="1.5"/>')
            
            # Chart Header Bar
            svg_parts.append(f'<text x="{chart_x+30}" y="{chart_y+42}" font-family="sans-serif" font-weight="800" font-size="24" fill="#0F172A">Spectral Waveform Live Stream</text>')
            svg_parts.append(f'<rect x="{chart_x+chart_w-190}" y="{chart_y+18}" width="160" height="34" rx="17" fill="#ECFDF5" stroke="#10B981" stroke-width="1.2"/>')
            svg_parts.append(f'<text x="{chart_x+chart_w-110}" y="{chart_y+41}" font-family="sans-serif" font-weight="700" font-size="15" fill="#059669" text-anchor="middle">● 60 FPS ACTIVE</text>')
            
            # Live undulating waves inside chart
            c_pts = []
            steps = 42
            chart_base_y = chart_y + chart_h / 2.0 + 20
            for i in range(steps + 1):
                px = chart_x + (i / float(steps)) * chart_w
                py = chart_base_y + math.sin(i / 3.8 + t * 4.2) * 50.0 + math.cos(i / 5.2 - t * 2.8) * 25.0
                c_pts.append((px, py))
            
            path_c = f"M {c_pts[0][0]:.1f} {c_pts[0][1]:.1f} " + " ".join(f"L {p[0]:.1f} {p[1]:.1f}" for p in c_pts[1:])
            area_c = f"{path_c} L {c_pts[-1][0]:.1f} {chart_y+chart_h-10} L {c_pts[0][0]:.1f} {chart_y+chart_h-10} Z"
            svg_parts.append(f'<path d="{area_c}" fill="url(#waveFillBlue)"/>')
            svg_parts.append(f'<path d="{path_c}" fill="none" stroke="#2563EB" stroke-width="4" stroke-linecap="round"/>')
            
            # Yellow Harmonics Line
            pts_cy = []
            for i in range(steps + 1):
                px = chart_x + (i / float(steps)) * chart_w
                py = chart_base_y + math.cos(i / 4.2 - t * 3.5) * 38.0
                pts_cy.append((px, py))
            path_cy = f"M {pts_cy[0][0]:.1f} {pts_cy[0][1]:.1f} " + " ".join(f"L {p[0]:.1f} {p[1]:.1f}" for p in pts_cy[1:])
            svg_parts.append(f'<path d="{path_cy}" fill="none" stroke="#F59E0B" stroke-width="3" stroke-linecap="round"/>')
            
            # Wave Head Pulsar
            hx, hy = c_pts[int((steps) * clamp((t - 5.5) / 2.0))]
            svg_parts.append(f'<circle cx="{hx:.1f}" cy="{hy:.1f}" r="16" fill="#2563EB" fill-opacity="0.25"/>')
            svg_parts.append(f'<circle cx="{hx:.1f}" cy="{hy:.1f}" r="7" fill="#1D4ED8"/>')
            
            # 3 Metrics Tiles - Y: 760, H: 125 (Clean spacing, 0 overlap)
            tile_y = chart_y + chart_h + 35
            tile_w = 250
            tile_h = 125
            
            # Tile 1: Frequency
            svg_parts.append(f'<rect x="{chart_x}" y="{tile_y}" width="{tile_w}" height="{tile_h}" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="{chart_x+24}" y="{tile_y+34}" font-family="sans-serif" font-weight="600" font-size="15" fill="#64748B">SAMPLE RATE</text>')
            svg_parts.append(f'<text x="{chart_x+24}" y="{tile_y+76}" font-family="sans-serif" font-weight="900" font-size="30" fill="#1D4ED8">48.0 kHz</text>')
            svg_parts.append(f'<text x="{chart_x+24}" y="{tile_y+105}" font-family="sans-serif" font-weight="700" font-size="14" fill="#2563EB">▲ Pro Studio Grade</text>')

            # Tile 2: Latency
            tx2 = chart_x + tile_w + 30
            svg_parts.append(f'<rect x="{tx2}" y="{tile_y}" width="{tile_w}" height="{tile_h}" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="{tx2+24}" y="{tile_y+34}" font-family="sans-serif" font-weight="600" font-size="15" fill="#64748B">LATENCY</text>')
            svg_parts.append(f'<text x="{tx2+24}" y="{tile_y+76}" font-family="sans-serif" font-weight="900" font-size="30" fill="#059669">1.8 ms</text>')
            svg_parts.append(f'<text x="{tx2+24}" y="{tile_y+105}" font-family="sans-serif" font-weight="700" font-size="14" fill="#059669">⚡ Zero-Drift Locked</text>')

            # Tile 3: Bandwidth
            tx3 = chart_x + (tile_w + 30) * 2
            svg_parts.append(f'<rect x="{tx3}" y="{tile_y}" width="{tile_w}" height="{tile_h}" rx="18" fill="#FFFFFF" stroke="#E2E8F0" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="{tx3+24}" y="{tile_y+34}" font-family="sans-serif" font-weight="600" font-size="15" fill="#64748B">BANDWIDTH</text>')
            svg_parts.append(f'<text x="{tx3+24}" y="{tile_y+76}" font-family="sans-serif" font-weight="900" font-size="30" fill="#D97706">14.2 GB/s</text>')
            svg_parts.append(f'<text x="{tx3+24}" y="{tile_y+105}" font-family="sans-serif" font-weight="700" font-size="14" fill="#D97706">▲ NEON Vector Core</text>')

            # Terminal Shell - Y: 920, H: 330
            term_y = tile_y + tile_h + 35
            term_h = 320
            svg_parts.append(f'<rect x="{chart_x}" y="{term_y}" width="{chart_w}" height="{term_h}" rx="20" fill="#0F172A" stroke="#1E293B" stroke-width="1.5"/>')
            
            # Synchronized Typewriter CLI
            cmd_full = "$ wave-stream --device arm64 --zero-lag-sfx"
            type_len = int(len(cmd_full) * clamp((t - 6.0) / 1.8))
            cmd_curr = cmd_full[:type_len]
            cursor = "█" if int(t * 4) % 2 == 0 else ""
            svg_parts.append(f'<text x="{chart_x+30}" y="{term_y+55}" font-family="monospace" font-weight="700" font-size="22" fill="#F8FAFC">{cmd_curr}{cursor}</text>')
            
            if t >= 8.2:
                svg_parts.append(f'<text x="{chart_x+30}" y="{term_y+115}" font-family="monospace" font-size="18" fill="#38BDF8">[AUDIO-SYNC] Hardware audio clock phase locked (0.00ms)</text>')
            if t >= 9.2:
                svg_parts.append(f'<text x="{chart_x+30}" y="{term_y+165}" font-family="monospace" font-weight="700" font-size="19" fill="#4ADE80">✔ [SUCCESS] Real-time wave synthesis active @ 60.0 FPS</text>')
            if t >= 10.2:
                svg_parts.append(f'<text x="{chart_x+30}" y="{term_y+215}" font-family="monospace" font-size="18" fill="#FBBF24">[METRICS] 100% Zero-latency audio-visual sync established</text>')
                svg_parts.append(f'<rect x="{chart_x+30}" y="{term_y+245}" width="420" height="38" rx="10" fill="#1E293B"/>')
                svg_parts.append(f'<text x="{chart_x+45}" y="{term_y+270}" font-family="monospace" font-size="16" fill="#F8FAFC">⚡ Pipeline: https://engine.neuralwave.ai/v3</text>')

            svg_parts.append('</g>')

    # =========================================================================
    # SCENE 3 (11.5s – 17.5s): HIGH-SPEED ARCHITECTURAL FEATURE GRID
    # =========================================================================
    if 11.4 <= t <= 17.7:
        alpha3 = clamp((t - 11.5) / 0.4) * (1.0 - clamp((t - 17.0) / 0.4))
        
        if alpha3 > 0.001:
            svg_parts.append(f'<g opacity="{alpha3:.3f}">')
            
            # Title & Subtitle (Y: 340 & 400, strict bounds)
            svg_parts.append('<text x="540" y="340" font-family="sans-serif" font-weight="900" font-size="44" fill="#0F172A" text-anchor="middle">ENGINEERED FOR ZERO LATENCY</text>')
            svg_parts.append('<text x="540" y="395" font-family="sans-serif" font-weight="600" font-size="24" fill="#64748B" text-anchor="middle">Mathematically synchronized audio transients &amp; sub-pixel motion</text>')
            
            # 3 Staggered Feature Cards (Non-overlapping, 40px margin)
            # Card 1: Y 460, H 220
            # Card 2: Y 720, H 220
            # Card 3: Y 980, H 220
            cards = [
                ("🌊 MULTI-HARMONIC SPLINE WAVES", "Continuous C² Bézier vector calculations rendered with sub-pixel fluid motion.", "#1D4ED8", "#EFF6FF", 11.8, 460),
                ("⚡ HARDWARE SAMPLE LOCKING", "Sound events mapped directly to video frame indices for absolute zero audio delay.", "#D97706", "#FFFBEB", 13.4, 720),
                ("🔒 HIGH-THROUGHPUT ARM64 CORE", "Multi-process vector synthesis running natively without external cloud dependencies.", "#0F172A", "#F8FAFC", 15.0, 980)
            ]
            
            for ci, (c_title, c_desc, c_col, c_bg, start_t, card_y) in enumerate(cards):
                card_t = clamp((t - start_t) / 0.5)
                card_slide = (1.0 - spring(card_t)) * 100.0
                
                svg_parts.append(f'<g transform="translate(0, {card_slide:.2f})">')
                svg_parts.append(f'<rect x="90" y="{card_y}" width="900" height="220" rx="24" fill="{c_bg}" stroke="{c_col}" stroke-opacity="0.3" stroke-width="2"/>')
                svg_parts.append(f'<circle cx="160" cy="{card_y+65}" r="30" fill="{c_col}" fill-opacity="0.15"/>')
                svg_parts.append(f'<text x="160" y="{card_y+75}" font-family="sans-serif" font-weight="800" font-size="26" fill="{c_col}" text-anchor="middle">0{ci+1}</text>')
                svg_parts.append(f'<text x="215" y="{card_y+75}" font-family="sans-serif" font-weight="800" font-size="30" fill="#0F172A">{c_title}</text>')
                svg_parts.append(f'<text x="215" y="{card_y+135}" font-family="sans-serif" font-weight="500" font-size="21" fill="#475569">{c_desc}</text>')
                svg_parts.append('</g>')

            # Gentle ambient wave underneath cards (Y: 1320)
            _, d_sub = build_wave_path(1320, 35, 0.007, 2.5, 18, 0.014, 1.8, t, 0.8)
            svg_parts.append(f'<path d="{d_sub}" fill="none" stroke="#2563EB" stroke-opacity="0.4" stroke-width="3"/>')
            
            svg_parts.append('</g>')

    # =========================================================================
    # SCENE 4 (17.5s – 22.0s): WAVE CRESCENDO & THROUGHPUT PEAK
    # =========================================================================
    if 17.4 <= t <= 22.2:
        alpha4 = clamp((t - 17.5) / 0.4) * (1.0 - clamp((t - 21.6) / 0.4))
        
        if alpha4 > 0.001:
            svg_parts.append(f'<g opacity="{alpha4:.3f}">')
            
            # Header Badge - Y: 360
            svg_parts.append('<rect x="330" y="340" width="420" height="48" rx="24" fill="#FEF3C7" stroke="#F59E0B" stroke-width="1.5"/>')
            svg_parts.append('<text x="540" y="372" font-family="sans-serif" font-weight="800" font-size="18" fill="#B45309" text-anchor="middle" letter-spacing="2">✦ PEAK PROCESSING VELOCITY ✦</text>')
            
            # Hero Throughput Number - Y: 470
            prog4 = clamp((t - 17.5) / 3.5)
            curr_ops = int(1000000 + (prog4 ** 1.5) * 9000000)
            svg_parts.append(f'<text x="540" y="470" font-family="sans-serif" font-weight="900" font-size="70" fill="#1D4ED8" text-anchor="middle">{curr_ops:,} ops/s</text>')
            svg_parts.append('<text x="540" y="525" font-family="sans-serif" font-weight="600" font-size="24" fill="#64748B" text-anchor="middle">Multi-Process Real-Time Signal Throughput</text>')
            
            # 5 Cascading Waves surging across Y: 680 to 1280
            for wi, (w_col, w_amp, w_speed, w_offset) in enumerate([
                ("#1D4ED8", 65, 4.2, 0.0),
                ("#2563EB", 55, 3.6, 1.2),
                ("#F59E0B", 48, 4.8, 2.4),
                ("#FBBF24", 40, 3.2, 3.6),
                ("#0284C7", 35, 5.0, 4.8)
            ]):
                w_cy = 760 + wi * 85
                _, d_casc = build_wave_path(w_cy, w_amp, 0.006, w_speed, 20, 0.012, 2.0, t, w_offset)
                svg_parts.append(f'<path d="{d_casc}" fill="none" stroke="{w_col}" stroke-width="{4 - wi*0.4:.1f}" stroke-opacity="{0.95 - wi*0.1:.2f}"/>')
            
            # 2 Bottom Status Pills - Y: 1360
            svg_parts.append('<rect x="180" y="1360" width="340" height="52" rx="26" fill="#EFF6FF" stroke="#2563EB" stroke-width="1.5"/>')
            svg_parts.append('<text x="350" y="1393" font-family="sans-serif" font-weight="700" font-size="20" fill="#1D4ED8" text-anchor="middle">⚡ 60 FPS Locked</text>')
            
            svg_parts.append('<rect x="560" y="1360" width="340" height="52" rx="26" fill="#FEF3C7" stroke="#F59E0B" stroke-width="1.5"/>')
            svg_parts.append('<text x="730" y="1393" font-family="sans-serif" font-weight="700" font-size="20" fill="#B45309" text-anchor="middle">✔ Zero Audio Delay</text>')

            svg_parts.append('</g>')

    # =========================================================================
    # SCENE 5 (22.0s – 25.0s): GRAND FINALE & CALL-TO-ACTION
    # =========================================================================
    if t >= 21.8:
        alpha5 = clamp((t - 22.0) / 0.4)
        scale5 = 0.85 + 0.15 * spring(clamp((t - 22.0) / 0.6))
        
        if alpha5 > 0.001:
            svg_parts.append(f'<g opacity="{alpha5:.3f}" transform="translate(540, 960) scale({scale5:.4f}) translate(-540, -960)">')
            
            # Ambient Center Burst
            svg_parts.append(f'<circle cx="540" cy="720" r="{int(340 + (t-22.0)*60)}" fill="url(#ambientBlue)" opacity="0.8"/>')
            
            # Minimalist Wave Crest Emblem - Y: 680
            svg_parts.append('<circle cx="540" cy="680" r="75" fill="#EFF6FF" stroke="#93C5FD" stroke-width="3"/>')
            _, d_emb = build_wave_path(680, 24, 0.025, 4.0, 10, 0.04, 2.0, t, 0.0, steps=20)
            # Clip emblem inside circle
            svg_parts.append(f'<path d="{d_emb}" fill="none" stroke="#1D4ED8" stroke-width="4.5" stroke-linecap="round"/>')
            
            # Title & Tagline - Y: 870 & 940 (Strict bounds)
            svg_parts.append('<text x="540" y="870" font-family="sans-serif" font-weight="900" font-size="76" fill="#0F172A" text-anchor="middle" letter-spacing="3">NEURAL WAVE</text>')
            svg_parts.append('<text x="540" y="938" font-family="sans-serif" font-weight="600" font-size="28" fill="#475569" text-anchor="middle">Experience Zero-Latency Motion &amp; Sound</text>')
            
            # Primary CTA Button - Y: 1040, W: 760, H: 108
            btn_w, btn_h = 760, 108
            btn_x = (WIDTH - btn_w) / 2
            btn_y = 1040
            svg_parts.append(f'<rect x="{btn_x}" y="{btn_y}" width="{btn_w}" height="{btn_h}" rx="54" fill="url(#ctaGrad)" stroke="#FFFFFF" stroke-width="2"/>')
            svg_parts.append(f'<text x="540" y="{btn_y+67}" font-family="sans-serif" font-weight="800" font-size="34" fill="#FFFFFF" text-anchor="middle" letter-spacing="1">Get Started Free  ➔</text>')
            
            # Domain Tag & Footer - Y: 1220 & 1275
            svg_parts.append(f'<text x="540" y="1220" font-family="sans-serif" font-weight="900" font-size="44" fill="#1D4ED8" text-anchor="middle" letter-spacing="2">neuralwave.ai</text>')
            svg_parts.append(f'<text x="540" y="1275" font-family="sans-serif" font-weight="500" font-size="20" fill="#64748B" text-anchor="middle">Zero External Dependencies • Instant Terminal Access</text>')
            
            svg_parts.append('</g>')

    svg_parts.append('</svg>')
    return "\n".join(svg_parts)

def process_single_frame(f):
    svg_content = render_frame_svg(f)
    svg_path = os.path.join(OUT_DIR, f"frame_{f:04d}.svg")
    png_path = os.path.join(OUT_DIR, f"frame_{f:04d}.png")
    
    with open(svg_path, "w", encoding="utf-8") as svg_file:
        svg_file.write(svg_content)
    
    cmd = ["rsvg-convert", "-w", str(WIDTH), "-h", str(HEIGHT), svg_path, "-o", png_path]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    
    if os.path.exists(svg_path):
        os.remove(svg_path)
    return f

if __name__ == "__main__":
    print(f"🎬 Rendering {TOTAL_FRAMES} Wave Animation Frames (1080x1920 @ 60 FPS, {DURATION}s)...")
    with ProcessPoolExecutor(max_workers=8) as executor:
        results = list(executor.map(process_single_frame, range(TOTAL_FRAMES)))
    print(f"✅ Finished rendering all {len(results)} frames!")
