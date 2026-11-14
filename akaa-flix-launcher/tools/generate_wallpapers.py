"""Renders the original procedural wallpapers bundled with the launcher (no third-party imagery).

Usage: python3 tools/generate_wallpapers.py design-assets/legacy-wallpapers 3840 2160 [name ...]
(v1.1: only wp_neon_waves – the static frame of Animated Neon Pulse – is packaged, in wallpaper-engine/src/main/res/raw.)
Requires numpy + Pillow.
"""
import sys, os, numpy as np
from PIL import Image, ImageFilter
OUT = sys.argv[1]; W = int(sys.argv[2]); H = int(sys.argv[3])
os.makedirs(OUT, exist_ok=True)
rng = np.random.RandomState(7)
Y, X = np.mgrid[0:H, 0:W].astype(np.float32)
x = X / W; y = Y / H

def fbm(octaves=6, base=4, persistence=0.5, seed=0, aspect=True):
    r = np.random.RandomState(seed); acc = np.zeros((H, W), np.float32); amp = 1.0; tot = 0
    for o in range(octaves):
        gw = base * (2 ** o); gh = max(2, int(gw * H / W)) if aspect else gw
        g = (r.rand(gh + 1, gw + 1) * 255).astype(np.uint8)
        up = np.asarray(Image.fromarray(g).resize((W, H), Image.BICUBIC), np.float32) / 255.0
        acc += up * amp; tot += amp; amp *= persistence
    return acc / tot

def fbm1d(n, octaves=6, base=4, seed=0):
    r = np.random.RandomState(seed); acc = np.zeros(n, np.float32); amp = 1; tot = 0
    for o in range(octaves):
        k = base * 2 ** o; pts = r.rand(k + 1)
        acc += np.interp(np.linspace(0, k, n), np.arange(k + 1), pts) * amp; tot += amp; amp *= 0.5
    return acc / tot

def hexc(h): h = h.lstrip('#'); return np.array([int(h[i:i+2], 16) for i in (0, 2, 4)], np.float32) / 255.0
def lerp(a, b, t): return a + (b - a) * t
def grad(stops, t):
    t = np.clip(t, 0, 1); out = np.zeros(t.shape + (3,), np.float32)
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]; p1, c1 = stops[i + 1]
        m = (t >= p0) & (t <= p1); tt = ((t - p0) / max(p1 - p0, 1e-6))[..., None]
        out[m] = (lerp(hexc(c0), hexc(c1), tt))[m]
    return out

def stars(img, density=0.0009, seed=1, region=None, bright=1.0):
    r = np.random.RandomState(seed); n = int(W * H * density)
    sx = r.randint(0, W, n); sy = r.randint(0, H, n)
    if region is not None:
        keep = region[sy, sx] > r.rand(n); sx, sy = sx[keep], sy[keep]
    b = (r.rand(len(sx)) ** 6) * bright
    layer = np.zeros((H, W), np.float32); layer[sy, sx] = np.maximum(layer[sy, sx], b)
    gl = np.asarray(Image.fromarray((np.clip(layer, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(W / 1400)), np.float32) / 255.0
    tint = np.array([0.85, 0.85, 1.0], np.float32)
    return img + (layer * 0.9 + gl * 2.2)[..., None] * tint

def glow(mask, radius):
    return np.asarray(Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius)), np.float32) / 255.0

def save(img, name, q=84):
    img = np.clip(img, 0, 1) ** (1 / 1.0)
    Image.fromarray((img * 255).astype(np.uint8)).save(os.path.join(OUT, name + '.webp'), 'WEBP', quality=q, method=6)
    print(name, os.path.getsize(os.path.join(OUT, name + '.webp')) // 1024, 'KB')

def violet_galaxy():
    img = grad([(0, '#04020c'), (0.6, '#0a0420'), (1, '#05020e')], y)
    band = np.exp(-(((y - (0.95 - 0.9 * x)) / 0.20) ** 2))
    n1 = fbm(7, 3, 0.55, seed=11); n2 = fbm(6, 5, 0.5, seed=12)
    neb = np.clip(band * (n1 * 2.3 - 0.62), 0, 1) ** 1.2
    col = grad([(0, '#2a0f7a'), (0.4, '#6d28d9'), (0.7, '#c026d3'), (1, '#60a5fa')], n2)
    img = img + neb[..., None] * col * 1.3
    core = np.exp(-(((x - 0.55) / 0.05) ** 2 + ((y - 0.45) / 0.05) ** 2))
    img = img + (core * 0.45)[..., None] * hexc('#f0d9ff')
    dust = np.clip(band * (fbm(6, 8, 0.5, seed=13) - 0.55) * 3, 0, 1)
    img = img * (1 - dust[..., None] * 0.6)
    img = stars(img, 0.0012, 2, bright=1.2)
    save(img, 'wp_violet_galaxy')

def pyramids_sunset():
    sky = grad([(0, '#1b0638'), (0.30, '#4c1d95'), (0.52, '#c2410c'), (0.62, '#fb923c'), (1, '#fb923c')], y / 0.66)
    img = sky.copy()
    sun = np.sqrt(((x - 0.62) * W / H) ** 2 + (y - 0.60) ** 2)
    img += (np.exp(-(sun / 0.05) ** 2) * 1.2 + np.exp(-(sun / 0.22) ** 2) * 0.45)[..., None] * hexc('#ffd28a')
    img = stars(img, 0.0004, 5, region=np.clip(1 - y / 0.35, 0, 1))
    horizon = 0.66
    def pyramid(cx, base_w, height, lit):
        apex_y = horizon - height; half = base_w / 2
        inside = (y <= horizon + 0.004) & (y >= apex_y)
        t = np.clip((y - apex_y) / height, 0, 1); left = cx - half * t * (W / W); right = cx + half * t
        m = inside & (x >= left) & (x <= right)
        face = np.where(x < cx, 1.0, 0.0)
        lit_col = grad([(0, '#ffcf7a'), (1, '#b45309')], t)
        sh_col = grad([(0, '#5b2a6e'), (1, '#24103a')], t)
        tex = fbm(5, 40, 0.5, seed=int(cx * 100))[..., None] * 0.18 + 0.91
        col = np.where((face[..., None] > 0.5) == lit, lit_col, sh_col) * tex
        return m, col
    for cx, bw, h, lit in [(0.30, 0.34, 0.27, False), (0.50, 0.24, 0.17, False), (0.83, 0.20, 0.13, False)]:
        m, col = pyramid(cx, bw, h, lit)
        img[m] = col[m]
    dunes = np.zeros((H, W), bool); dimg = img.copy()
    for i, (base, amp, c0, c1) in enumerate([(0.70, 0.03, '#9a3412', '#431407'), (0.80, 0.05, '#7c2d12', '#2a0a05'), (0.91, 0.05, '#4a1a0a', '#120402')]):
        prof = base + amp * np.sin(x[0] * (5 + i * 2.3) + i) + fbm1d(W, 5, 3, seed=20 + i) * 0.04
        m = y > prof[None, :]
        t = np.clip((y - prof[None, :]) / 0.25, 0, 1)
        col = grad([(0, c0), (1, c1)], t) * (fbm(5, 30, 0.5, seed=30 + i)[..., None] * 0.15 + 0.92)
        img[m] = col[m]
    save(img, 'wp_pyramids_sunset')

def neon_waves():
    img = grad([(0, '#05010d'), (1, '#0d0322')], y)
    acc = np.zeros((H, W, 3), np.float32)
    for k in range(7):
        ph = k * 0.7; amp = 0.10 + 0.02 * k; base = 0.52 + (k - 3) * 0.035
        yy = base + amp * np.sin(x * 6.0 + ph) * np.sin(x * 2.1 + k) + 0.03 * np.sin(x * 13 + k * 2)
        d = np.abs(y - yy)
        core = np.exp(-(d / 0.0028) ** 2); halo = np.exp(-(d / 0.03) ** 2) * 0.35
        col = grad([(0, '#3b82f6'), (0.5, '#a855f7'), (1, '#ec4899')], np.abs(((x + k * 0.13) % 2.0) - 1.0))
        acc += (core + halo)[..., None] * col * (0.55 + 0.08 * k)
    img = img + acc
    img = stars(img, 0.0002, 9, bright=0.6)
    save(img, 'wp_neon_waves')

def tri_sdf(px, py, cx, cy, r):
    # equilateral triangle pointing up, centered (cx,cy), circumradius r ; returns distance to edges
    k = np.sqrt(3.0)
    qx = (px - cx) / r; qy = -(py - cy) / r
    qx = np.abs(qx) - 1.0 * 0.866; qy = qy + 0.5
    m = (qx + k * qy) > 0
    nx = np.where(m, (qx - k * qy) / 2, qx); ny = np.where(m, (-k * qx - qy) / 2, qy)
    nx = nx - np.clip(nx, -2 * 0.866, 0)
    return -np.sqrt(nx * nx + ny * ny) * np.sign(ny) * r

def neon_tunnel():
    img = grad([(0, '#030108'), (0.6, '#0b0320'), (1, '#120430')], y)
    px = (x - 0.5) * W / H; py = y - 0.52
    acc = np.zeros((H, W, 3), np.float32)
    for i in range(9):
        r = 0.08 * (1.42 ** i)
        d = np.abs(tri_sdf(px, py, 0, 0, r))
        inten = np.exp(-(d / (0.0016 + 0.0006 * i)) ** 2) + np.exp(-(d / 0.02) ** 2) * 0.3
        col = lerp(hexc('#e879f9'), hexc('#6d28d9'), i / 8)
        acc += inten[..., None] * col * (1.15 - i * 0.08)
    floor = np.clip((y - 0.62) / 0.38, 0, 1)
    img = img + acc
    refl = np.flipud(acc)[:, :, :] * (floor[..., None] ** 1.5) * 0.25
    img = img + refl
    img = stars(img, 0.00025, 4, region=np.clip(1 - y / 0.6, 0, 1), bright=0.7)
    save(img, 'wp_neon_tunnel')

def aurora():
    img = grad([(0, '#020617'), (0.5, '#0b1a3a'), (0.62, '#1e1b4b'), (1, '#0b1026')], y)
    img = stars(img, 0.0008, 6, region=np.clip(1 - y / 0.6, 0, 1))
    warp = fbm(5, 3, 0.5, seed=40)
    acc = np.zeros((H, W, 3), np.float32)
    for k, (c, off) in enumerate([('#22d3ee', 0.0), ('#4ade80', 0.08), ('#a855f7', 0.17)]):
        curve = 0.30 + off + 0.08 * np.sin(x * 5 + warp * 6 + k)
        d = y - curve
        curtain = np.where(d < 0, np.exp(-(d / 0.16) ** 2), np.exp(-(d / 0.015) ** 2))
        rays = 0.82 + 0.18 * np.sin(x * 90 + warp * 25) ** 2
        acc += (curtain * rays * 0.42)[..., None] * hexc(c)
    img = img + acc
    ridge = 0.58 + fbm1d(W, 7, 3, seed=41) * 0.12 - 0.05
    m = y > ridge[None, :]
    mt = grad([(0, '#1f2a44'), (1, '#05070f')], np.clip((y - ridge[None, :]) / 0.1, 0, 1))
    img[m] = mt[m]
    lake = y > 0.70
    src_rows = np.clip((2 * 0.70 - y) * H, 0, H - 1).astype(int)
    rip = (fbm(4, 60, 0.5, seed=42) - 0.5) * 0.012
    src_rows2 = np.clip(src_rows + (rip * H).astype(int), 0, H - 1)
    refl = img[src_rows2, X.astype(int)] * 0.45
    img[lake] = refl[lake]
    save(img, 'wp_aurora_night')

def dusk_lake():
    img = grad([(0, '#1e1145'), (0.35, '#6b21a8'), (0.55, '#f97316'), (0.62, '#fdba74'), (1, '#fdba74')], y / 0.62)
    img = stars(img, 0.0003, 8, region=np.clip(1 - y / 0.3, 0, 1))
    for i, (b, a, c) in enumerate([(0.42, 0.12, '#3b1d5e'), (0.48, 0.10, '#2a1446'), (0.54, 0.07, '#160a2a')]):
        ridge = b + (fbm1d(W, 7, 3 + i, seed=50 + i) - 0.5) * a * 2
        m = (y > ridge[None, :]) & (y < 0.62)
        img[m] = (hexc(c) * (0.9 + 0.2 * fbm(4, 20, 0.5, seed=60 + i)[..., None]))[m]
    lake = y > 0.62
    rip = (fbm(4, 80, 0.5, seed=61) - 0.5) * 0.01
    src = np.clip(((2 * 0.62 - y) + rip) * H, 0, H - 1).astype(int)
    refl = img[src, X.astype(int)] * 0.5
    refl = np.asarray(Image.fromarray((np.clip(refl,0,1)*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(W/900)),np.float32)/255.0
    img[lake] = refl[lake]
    save(img, 'wp_dusk_lake')

def moonrise():
    img = grad([(0, '#05020f'), (0.55, '#1a0b3b'), (1, '#2e1065')], y)
    img = stars(img, 0.0009, 12)
    mx_, my_, mr = 0.66, 0.36, 0.20
    d = np.sqrt(((x - mx_) * W / H) ** 2 + (y - my_) ** 2)
    disc = d < mr
    tex = fbm(7, 6, 0.55, seed=70)
    shade = np.clip(1 - (((x - mx_) * W / H + 0.08) ** 2 + (y - my_ + 0.05) ** 2) / (mr * mr * 2.2), 0.25, 1)
    moon = grad([(0, '#6b6f8a'), (0.5, '#c7c9e0'), (1, '#f1f1ff')], tex)[...] * shade[..., None]
    img[disc] = moon[disc]
    img += (np.exp(-((d - mr) / 0.05) ** 2) * (d > mr) * 0.35)[..., None] * hexc('#a78bfa')
    for i, (b, a, c0, c1) in enumerate([(0.74, 0.04, '#4c1d95', '#1e0b3d'), (0.84, 0.05, '#3b0f6e', '#12062a')]):
        prof = b + a * np.sin(x[0] * (4 + i * 3) + i * 2) + (fbm1d(W, 5, 3, seed=80 + i) - 0.5) * 0.05
        m = y > prof[None, :]
        col = grad([(0, c0), (1, c1)], np.clip((y - prof[None, :]) / 0.2, 0, 1))
        img[m] = col[m]
    save(img, 'wp_moonrise')

def purple_silk():
    u = x * 3.0; v = y * 3.0
    f = np.sin(u * 1.3 + np.sin(v * 1.7) * 1.5) + 0.6 * np.sin(v * 2.2 + np.sin(u * 0.9) * 2.0)
    f = (f - f.min()) / (f.max() - f.min())
    gy, gx = np.gradient(f)
    light = np.clip(0.5 + (gx * 0.7 - gy * 0.7) * 900, 0, 1)
    base = grad([(0, '#0b0218'), (0.5, '#3b0a6e'), (1, '#9333ea')], f)
    img = base * (0.45 + light[..., None] * 0.9)
    img += (np.clip(light - 0.8, 0, 1) * 1.5)[..., None] * hexc('#f5d0fe')
    save(img, 'wp_purple_silk')

which = sys.argv[4:] or ['violet_galaxy', 'pyramids_sunset', 'neon_waves', 'neon_tunnel', 'aurora', 'dusk_lake', 'moonrise', 'purple_silk']
for w in which: globals()[w]()
