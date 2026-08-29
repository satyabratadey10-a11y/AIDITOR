import os
import math
import subprocess
from concurrent.futures import ProcessPoolExecutor

WIDTH = 1080
HEIGHT = 1920
FPS = 60
DURATION = 10.0
TOTAL_FRAMES = int(FPS * DURATION)
OUT_DIR = "/data/data/com.termux/files/home/saas_frames"

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

def render_frame_svg(f):
    t = f / float(FPS)
    
    # Background Glow Orbs
    orb1_cx = 540 + int(70 * math.sin(t * 1.8))
    orb1_cy = 500 + int(50 * math.cos(t * 1.4))
    orb2_cx = 600 + int(90 * math.cos(t * 1.5))
    orb2_cy = 1350 + int(60 * math.sin(t * 1.7))
    orb_glow = 0.45 + 0.15 * math.sin(t * 3.0)

    svg_parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">',
        '<defs>',
        '  <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">',
        '    <stop offset="0%" stop-color="#07080C"/>',
        '    <stop offset="50%" stop-color="#0B0D14"/>',
        '    <stop offset="100%" stop-color="#06070A"/>',
        '  </linearGradient>',
        '  <linearGradient id="brandGrad" x1="0%" y1="0%" x2="100%" y2="0%">',
        '    <stop offset="0%" stop-color="#FFFFFF"/>',
        '    <stop offset="40%" stop-color="#E0E7FF"/>',
        '    <stop offset="100%" stop-color="#A855F7"/>',
        '  </linearGradient>',
        '  <linearGradient id="btnGrad" x1="0%" y1="0%" x2="100%" y2="0%">',
        '    <stop offset="0%" stop-color="#6366F1"/>',
        '    <stop offset="50%" stop-color="#8B5CF6"/>',
        '    <stop offset="100%" stop-color="#EC4899"/>',
        '  </linearGradient>',
        '  <linearGradient id="chartGrad" x1="0%" y1="0%" x2="0%" y2="100%">',
        '    <stop offset="0%" stop-color="#06B6D4" stop-opacity="0.45"/>',
        '    <stop offset="100%" stop-color="#06B6D4" stop-opacity="0.0"/>',
        '  </linearGradient>',
        '  <radialGradient id="orb1" cx="50%" cy="50%" r="50%">',
        f'    <stop offset="0%" stop-color="#6366F1" stop-opacity="{orb_glow:.3f}"/>',
        '    <stop offset="100%" stop-color="#6366F1" stop-opacity="0.0"/>',
        '  </radialGradient>',
        '  <radialGradient id="orb2" cx="50%" cy="50%" r="50%">',
        f'    <stop offset="0%" stop-color="#06B6D4" stop-opacity="{orb_glow * 0.8:.3f}"/>',
        '    <stop offset="100%" stop-color="#06B6D4" stop-opacity="0.0"/>',
        '  </radialGradient>',
        '</defs>',
        # Base Background
        f'<rect width="{WIDTH}" height="{HEIGHT}" fill="url(#bgGrad)"/>',
        f'<circle cx="{orb1_cx}" cy="{orb1_cy}" r="550" fill="url(#orb1)"/>',
        f'<circle cx="{orb2_cx}" cy="{orb2_cy}" r="650" fill="url(#orb2)"/>',
    ]

    # Subtle Grid Lines
    for gy in range(120, HEIGHT, 160):
        svg_parts.append(f'<line x1="0" y1="{gy}" x2="{WIDTH}" y2="{gy}" stroke="#FFFFFF" stroke-opacity="0.03" stroke-width="1"/>')
    for gx in range(90, WIDTH, 180):
        svg_parts.append(f'<line x1="{gx}" y1="0" x2="{gx}" y2="{HEIGHT}" stroke="#FFFFFF" stroke-opacity="0.03" stroke-width="1"/>')

    # ==========================================
    # SCENE 1: HOOK & BRAND (0.0s -> 2.8s)
    # ==========================================
    if t < 3.0:
        p = t / 2.8
        alpha = clamp(t / 0.3) * (1.0 - clamp((t - 2.4) / 0.4))
        scale = 0.85 + 0.15 * ease_out_expo(clamp(p * 1.2))
        rot = (1.0 - ease_out_expo(clamp(p * 1.5))) * 25.0
        
        if alpha > 0.001:
            svg_parts.append(f'<g opacity="{alpha:.3f}" transform="translate(540, 960) scale({scale:.4f}) translate(-540, -960)">')
            
            # Top Badge Pill
            svg_parts.append('<rect x="330" y="580" width="420" height="52" rx="26" fill="#1E1B4B" fill-opacity="0.8" stroke="#818CF8" stroke-opacity="0.5" stroke-width="1.5"/>')
            svg_parts.append('<text x="540" y="614" font-family="sans-serif" font-weight="700" font-size="20" fill="#A5B4FC" text-anchor="middle" letter-spacing="3">✦ INTRODUCING 3.0 ✦</text>')
            
            # Hero Logo Hexagon / Cube
            svg_parts.append(f'<g transform="translate(540, 780) rotate({rot:.2f})">')
            svg_parts.append('<polygon points="0,-90 78,-45 78,45 0,90 -78,45 -78,-45" fill="#18182E" stroke="#8B5CF6" stroke-width="4"/>')
            svg_parts.append('<line x1="0" y1="0" x2="0" y2="90" stroke="#8B5CF6" stroke-width="3"/>')
            svg_parts.append('<line x1="0" y1="0" x2="78" y2="-45" stroke="#8B5CF6" stroke-width="3"/>')
            svg_parts.append('<line x1="0" y1="0" x2="-78" y2="-45" stroke="#8B5CF6" stroke-width="3"/>')
            svg_parts.append('<circle cx="0" cy="0" r="18" fill="#38BDF8"/>')
            svg_parts.append('</g>')
            
            # Title & Tagline
            svg_parts.append('<text x="540" y="990" font-family="sans-serif" font-weight="900" font-size="82" fill="url(#brandGrad)" text-anchor="middle" letter-spacing="4">ANTIGRAVITY</text>')
            svg_parts.append('<text x="540" y="1060" font-family="sans-serif" font-weight="500" font-size="34" fill="#94A3B8" text-anchor="middle">The Autonomous Motion Engine</text>')
            
            # Floating Feature Tags
            tag_y = 1170
            svg_parts.append('<rect x="180" y="1150" width="220" height="46" rx="23" fill="#111827" stroke="#374151" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="290" y="{tag_y+8}" font-family="sans-serif" font-weight="600" font-size="18" fill="#38BDF8" text-anchor="middle">⚡ 60 FPS Flow</text>')
            
            svg_parts.append('<rect x="430" y="1150" width="220" height="46" rx="23" fill="#111827" stroke="#374151" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="540" y="{tag_y+8}" font-family="sans-serif" font-weight="600" font-size="18" fill="#A855F7" text-anchor="middle">🎯 Neural VFX</text>')
            
            svg_parts.append('<rect x="680" y="1150" width="220" height="46" rx="23" fill="#111827" stroke="#374151" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="790" y="{tag_y+8}" font-family="sans-serif" font-weight="600" font-size="18" fill="#34D399" text-anchor="middle">🔒 Zero-Latency</text>')
            
            svg_parts.append('</g>')

    # ==========================================
    # SCENE 2: INTERACTIVE DASHBOARD (2.8s -> 6.0s)
    # ==========================================
    if 2.7 <= t <= 6.2:
        p2 = (t - 2.8) / 3.2
        alpha2 = clamp((t - 2.8) / 0.3) * (1.0 - clamp((t - 5.7) / 0.3))
        card_scale = 0.90 + 0.10 * spring(clamp((t - 2.8) / 0.8))
        
        if alpha2 > 0.001:
            svg_parts.append(f'<g opacity="{alpha2:.3f}" transform="translate(540, 960) scale({card_scale:.4f}) translate(-540, -960)">')
            
            # Glassmorphism Window Card
            cx, cy, cw, ch = 90, 360, 900, 1200
            svg_parts.append(f'<rect x="{cx}" y="{cy}" width="{cw}" height="{ch}" rx="36" fill="#0E121B" fill-opacity="0.90" stroke="#334155" stroke-opacity="0.7" stroke-width="2"/>')
            
            # Window Titlebar
            svg_parts.append(f'<line x1="{cx}" y1="{cy+70}" x2="{cx+cw}" y2="{cy+70}" stroke="#1E293B" stroke-width="1.5"/>')
            # 3 macOS traffic dots
            svg_parts.append(f'<circle cx="{cx+45}" cy="{cy+35}" r="8" fill="#EF4444"/>')
            svg_parts.append(f'<circle cx="{cx+75}" cy="{cy+35}" r="8" fill="#F59E0B"/>')
            svg_parts.append(f'<circle cx="{cx+105}" cy="{cy+35}" r="8" fill="#10B981"/>')
            svg_parts.append(f'<text x="{cx+cw/2}" y="{cy+42}" font-family="sans-serif" font-weight="700" font-size="20" fill="#94A3B8" text-anchor="middle" letter-spacing="2">ANTIGRAVITY // NEURAL RUNTIME</text>')
            
            # Live Metrics Header Bar
            svg_parts.append(f'<text x="{cx+50}" y="{cy+130}" font-family="sans-serif" font-weight="800" font-size="34" fill="#FFFFFF">Real-Time Optical Flow</text>')
            svg_parts.append(f'<rect x="{cx+cw-200}" y="{cy+100}" width="150" height="38" rx="19" fill="#065F46" fill-opacity="0.8" stroke="#10B981" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="{cx+cw-125}" y="{cy+125}" font-family="sans-serif" font-weight="700" font-size="16" fill="#6EE7B7" text-anchor="middle">● 60 FPS ACTIVE</text>')
            
            # Animated Vector Wave Chart
            chart_x = cx + 50
            chart_y = cy + 170
            chart_w = cw - 100
            chart_h = 320
            
            # Draw chart container
            svg_parts.append(f'<rect x="{chart_x}" y="{chart_y}" width="{chart_w}" height="{chart_h}" rx="20" fill="#07090F" stroke="#1E293B" stroke-width="1"/>')
            
            # Build wave path
            wave_pts = []
            steps = 40
            wave_progress = clamp((t - 3.0) / 2.0)
            max_i = int(steps * wave_progress)
            
            for i in range(steps + 1):
                px = chart_x + (i / float(steps)) * chart_w
                ny = math.sin((i / 4.0) + (t * 3.5)) * 45.0 + math.cos((i / 6.0) - (t * 2.0)) * 25.0
                py = chart_y + (chart_h / 2.0) + ny
                wave_pts.append((px, py))
            
            if len(wave_pts) > 2 and max_i > 1:
                active_pts = wave_pts[:max_i+1]
                path_d = f"M {active_pts[0][0]:.1f} {active_pts[0][1]:.1f} " + " ".join(f"L {p[0]:.1f} {p[1]:.1f}" for p in active_pts[1:])
                area_d = f"{path_d} L {active_pts[-1][0]:.1f} {chart_y+chart_h} L {active_pts[0][0]:.1f} {chart_y+chart_h} Z"
                
                svg_parts.append(f'<path d="{area_d}" fill="url(#chartGrad)"/>')
                svg_parts.append(f'<path d="{path_d}" fill="none" stroke="#22D3EE" stroke-width="4.5" stroke-linecap="round"/>')
                
                head_x, head_y = active_pts[-1]
                svg_parts.append(f'<circle cx="{head_x:.1f}" cy="{head_y:.1f}" r="16" fill="#06B6D4" fill-opacity="0.3"/>')
                svg_parts.append(f'<circle cx="{head_x:.1f}" cy="{head_y:.1f}" r="8" fill="#FFFFFF"/>')

            # 3 High-Tech Metric Cards Below Chart
            stat_y = chart_y + chart_h + 35
            stat_w = 245
            stat_h = 130
            
            # Stat 1: Throughput
            val_ops = int(1420000 + (t - 2.8) * 85000)
            svg_parts.append(f'<rect x="{chart_x}" y="{stat_y}" width="{stat_w}" height="{stat_h}" rx="18" fill="#131824" stroke="#1E293B" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="{chart_x+24}" y="{stat_y+36}" font-family="sans-serif" font-weight="600" font-size="16" fill="#94A3B8">THROUGHPUT</text>')
            svg_parts.append(f'<text x="{chart_x+24}" y="{stat_y+80}" font-family="sans-serif" font-weight="800" font-size="28" fill="#38BDF8">{val_ops:,}</text>')
            svg_parts.append(f'<text x="{chart_x+24}" y="{stat_y+108}" font-family="sans-serif" font-weight="600" font-size="14" fill="#10B981">▲ +42.8% vs base</text>')

            # Stat 2: Latency
            svg_parts.append(f'<rect x="{chart_x+stat_w+32}" y="{stat_y}" width="{stat_w}" height="{stat_h}" rx="18" fill="#131824" stroke="#1E293B" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="{chart_x+stat_w+56}" y="{stat_y+36}" font-family="sans-serif" font-weight="600" font-size="16" fill="#94A3B8">NEURAL LATENCY</text>')
            svg_parts.append(f'<text x="{chart_x+stat_w+56}" y="{stat_y+80}" font-family="sans-serif" font-weight="800" font-size="28" fill="#A855F7">3.8 ms</text>')
            svg_parts.append(f'<text x="{chart_x+stat_w+56}" y="{stat_y+108}" font-family="sans-serif" font-weight="600" font-size="14" fill="#A855F7">⚡ Ultra Real-Time</text>')

            # Stat 3: Accuracy
            svg_parts.append(f'<rect x="{chart_x+(stat_w+32)*2}" y="{stat_y}" width="{stat_w}" height="{stat_h}" rx="18" fill="#131824" stroke="#1E293B" stroke-width="1.5"/>')
            svg_parts.append(f'<text x="{chart_x+(stat_w+32)*2+24}" y="{stat_y+36}" font-family="sans-serif" font-weight="600" font-size="16" fill="#94A3B8">ACCURACY</text>')
            svg_parts.append(f'<text x="{chart_x+(stat_w+32)*2+24}" y="{stat_y+80}" font-family="sans-serif" font-weight="800" font-size="28" fill="#34D399">99.98%</text>')
            svg_parts.append(f'<text x="{chart_x+(stat_w+32)*2+24}" y="{stat_y+108}" font-family="sans-serif" font-weight="600" font-size="14" fill="#34D399">Sub-pixel precision</text>')

            # Terminal Shell at Bottom of Card
            term_y = stat_y + stat_h + 35
            term_h = 370
            svg_parts.append(f'<rect x="{chart_x}" y="{term_y}" width="{chart_w}" height="{term_h}" rx="20" fill="#080A10" stroke="#1E293B" stroke-width="1.5"/>')
            
            full_cmd = "$ agy deploy --optical-flow --neural-vfx"
            type_len = int(len(full_cmd) * clamp((t - 3.2) / 1.4))
            curr_cmd = full_cmd[:type_len]
            cursor = "█" if int(t * 4) % 2 == 0 else ""
            
            svg_parts.append(f'<text x="{chart_x+30}" y="{term_y+55}" font-family="monospace" font-weight="700" font-size="22" fill="#E2E8F0">{curr_cmd}{cursor}</text>')
            
            if t >= 4.6:
                svg_parts.append(f'<text x="{chart_x+30}" y="{term_y+115}" font-family="monospace" font-size="18" fill="#64748B">[INFO] Connecting cluster: arm64-node-us-east</text>')
                svg_parts.append(f'<text x="{chart_x+30}" y="{term_y+160}" font-family="monospace" font-size="18" fill="#38BDF8">[FLOW] Synthesizing 60 FPS motion vectors...</text>')
            if t >= 5.1:
                svg_parts.append(f'<text x="{chart_x+30}" y="{term_y+210}" font-family="monospace" font-weight="700" font-size="20" fill="#10B981">✔ [DEPLOY SUCCESS] 100% Pipeline Active in 0.04s</text>')
            if t >= 5.5:
                svg_parts.append(f'<rect x="{chart_x+30}" y="{term_y+250}" width="420" height="42" rx="10" fill="#1E293B" fill-opacity="0.8"/>')
                svg_parts.append(f'<text x="{chart_x+45}" y="{term_y+278}" font-family="monospace" font-size="16" fill="#F8FAFC">⚡ Endpoint: https://api.antigravity.ai/v3</text>')

            svg_parts.append('</g>')

    # ==========================================
    # SCENE 3: FEATURE MATRIX (6.0s -> 8.4s)
    # ==========================================
    if 5.9 <= t <= 8.5:
        p3 = (t - 6.0) / 2.4
        alpha3 = clamp((t - 6.0) / 0.3) * (1.0 - clamp((t - 8.1) / 0.3))
        
        if alpha3 > 0.001:
            svg_parts.append(f'<g opacity="{alpha3:.3f}">')
            
            # Title
            svg_parts.append('<text x="540" y="380" font-family="sans-serif" font-weight="900" font-size="52" fill="#FFFFFF" text-anchor="middle">BUILT FOR SPEED</text>')
            svg_parts.append('<text x="540" y="440" font-family="sans-serif" font-weight="500" font-size="26" fill="#94A3B8" text-anchor="middle">Engineered from ground up for high-throughput VFX</text>')
            
            # 3 Staggered Big Feature Cards
            cards_data = [
                ("⚡ 60 FPS OPTICAL MOTION", "Synthesizes real sub-pixel frames with zero judder &amp; bidirectional vectors.", "#6366F1", "#A5B4FC", 6.0),
                ("🎯 NEURAL AUTO-TRACKING", "Autonomous camera lock, face stabilization, and dynamic 3D roto.", "#8B5CF6", "#C4B5FD", 6.4),
                ("🔒 ZERO-EGRESS MULTI-CLOUD", "End-to-end encrypted video processing deployed directly on device.", "#06B6D4", "#67E8F9", 6.8)
            ]
            
            for ci, (head, desc, col, text_col, start_t) in enumerate(cards_data):
                card_t = clamp((t - start_t) / 0.5)
                card_slide = (1.0 - spring(card_t)) * 120.0
                card_y = 520 + ci * 300
                
                svg_parts.append(f'<g transform="translate(0, {card_slide:.2f})">')
                svg_parts.append(f'<rect x="90" y="{card_y}" width="900" height="240" rx="28" fill="#101420" stroke="{col}" stroke-opacity="0.6" stroke-width="2"/>')
                svg_parts.append(f'<circle cx="160" cy="{card_y+75}" r="32" fill="{col}" fill-opacity="0.2"/>')
                svg_parts.append(f'<text x="160" y="{card_y+85}" font-family="sans-serif" font-weight="800" font-size="28" fill="{col}" text-anchor="middle">0{ci+1}</text>')
                svg_parts.append(f'<text x="220" y="{card_y+85}" font-family="sans-serif" font-weight="800" font-size="34" fill="#FFFFFF">{head}</text>')
                svg_parts.append(f'<text x="220" y="{card_y+145}" font-family="sans-serif" font-weight="400" font-size="22" fill="#94A3B8">{desc}</text>')
                svg_parts.append('</g>')
            
            svg_parts.append('</g>')

    # ==========================================
    # SCENE 4: CLIMAX & CTA (8.4s -> 10.0s)
    # ==========================================
    if t >= 8.3:
        p4 = (t - 8.4) / 1.6
        alpha4 = clamp((t - 8.4) / 0.3)
        cta_scale = 0.80 + 0.20 * spring(clamp((t - 8.4) / 0.6))
        
        if alpha4 > 0.001:
            svg_parts.append(f'<g opacity="{alpha4:.3f}" transform="translate(540, 960) scale({cta_scale:.4f}) translate(-540, -960)">')
            
            # Supernova Glow Burst
            svg_parts.append(f'<circle cx="540" cy="750" r="{int(300 + p4 * 200)}" fill="url(#orb1)" opacity="0.8"/>')
            
            # Glowing Brand Emblem
            svg_parts.append('<polygon points="540,630 630,680 630,780 540,830 450,780 450,680" fill="#18182E" stroke="#A855F7" stroke-width="5"/>')
            svg_parts.append('<circle cx="540" cy="730" r="30" fill="#38BDF8"/>')
            
            # Main Outro Header
            svg_parts.append('<text x="540" y="940" font-family="sans-serif" font-weight="900" font-size="76" fill="url(#brandGrad)" text-anchor="middle" letter-spacing="4">ANTIGRAVITY</text>')
            svg_parts.append('<text x="540" y="1010" font-family="sans-serif" font-weight="600" font-size="32" fill="#94A3B8" text-anchor="middle">Ship Next-Gen AI Video Beyond Limits</text>')
            
            # Massive Primary CTA Button
            btn_w, btn_h = 760, 110
            btn_x = (WIDTH - btn_w) / 2
            btn_y = 1100
            
            svg_parts.append(f'<rect x="{btn_x}" y="{btn_y}" width="{btn_w}" height="{btn_h}" rx="55" fill="url(#btnGrad)" stroke="#FFFFFF" stroke-opacity="0.4" stroke-width="2"/>')
            svg_parts.append(f'<text x="540" y="{btn_y+68}" font-family="sans-serif" font-weight="800" font-size="36" fill="#FFFFFF" text-anchor="middle" letter-spacing="1">Get Started Free  ➔</text>')
            
            # Domain Tag
            svg_parts.append(f'<text x="540" y="{btn_y+190}" font-family="sans-serif" font-weight="800" font-size="44" fill="#38BDF8" text-anchor="middle" letter-spacing="2">antigravity.ai</text>')
            svg_parts.append(f'<text x="540" y="{btn_y+250}" font-family="sans-serif" font-weight="500" font-size="22" fill="#64748B" text-anchor="middle">Zero Credit Card Required • Instant CLI Access</text>')
            
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
    print(f"🎬 Re-rendering missing frames...")
    missing_frames = [f for f in range(TOTAL_FRAMES) if not os.path.exists(os.path.join(OUT_DIR, f"frame_{f:04d}.png"))]
    print(f"Total missing: {len(missing_frames)}")
    if missing_frames:
        with ProcessPoolExecutor(max_workers=8) as executor:
            list(executor.map(process_single_frame, missing_frames))
    print(f"✅ All {TOTAL_FRAMES} frames are ready!")
