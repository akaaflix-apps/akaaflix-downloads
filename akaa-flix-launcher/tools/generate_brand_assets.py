"""Builds launcher brand assets from the approved reference images (docs/reference/).

Usage: python3 tools/generate_brand_assets.py <ref_dir> <work_dir> <project_dir>
  ref_dir  : folder with img-000.jpg … img-007.jpg (the PDF images; see docs/reference for named copies)
  work_dir : must contain logo_full.png produced by tools/extract_logo.py
Requires numpy + Pillow.
"""
import sys, os, numpy as np
from PIL import Image, ImageDraw, ImageFilter
REF, OUT, P = sys.argv[1], sys.argv[2], sys.argv[3]
def mk(p): os.makedirs(p, exist_ok=True); return p
core_nodpi = mk(f'{P}/launcher-core/src/main/res/drawable-nodpi')
wp_raw = mk(f'{P}/wallpaper-engine/src/main/res/raw')
app_res = f'{P}/app/src/main/res'

# 1. Logo
logo = Image.open(f'{OUT}/logo_full.png')
w = 1400; logo.resize((w, int(logo.height * w / logo.width)), Image.LANCZOS).save(f'{core_nodpi}/akaa_logo.webp', 'WEBP', quality=90, method=6)

# 2. Header pyramid art (All Apps reference, clean region left of title) with feathered alpha
src0 = Image.open(f'{REF}/img-000.jpg').convert('RGB')
hdr = src0.crop((262, 0, 698, 152)).resize((872, 304), Image.LANCZOS)
hw, hh = hdr.size
yy, xx = np.mgrid[0:hh, 0:hw].astype(np.float32)
fx = np.clip(xx / (hw * 0.12), 0, 1) * np.clip((hw - xx) / (hw * 0.30), 0, 1)
fy = np.clip((hh - yy) / (hh * 0.30), 0, 1)
alpha = (np.sqrt(fx * fy) ** 1.2 * 255).astype(np.uint8)
hdr.putalpha(Image.fromarray(alpha)); hdr.save(f'{core_nodpi}/header_pyramid.webp', 'WEBP', quality=88, method=6)

# 3. Remote decor (home bottom-right) with elliptical feather
src2 = Image.open(f'{REF}/img-002.jpg').convert('RGB')
rem = src2.crop((1318, 768, 1672, 941)).resize((708, 346), Image.LANCZOS)
rw, rh = rem.size
yy, xx = np.mgrid[0:rh, 0:rw].astype(np.float32)
d = np.sqrt(((xx - rw * 0.55) / (rw * 0.50)) ** 2 + ((yy - rh * 0.55) / (rh * 0.52)) ** 2)
a = np.clip((1.0 - d) / 0.28, 0, 1) ** 1.3
lum = np.asarray(rem, np.float32).max(axis=2) / 255.0
a = a * np.clip(lum * 2.2, 0.0, 1.0)  # dark areas fade so it blends on any wallpaper
rem.putalpha(Image.fromarray((a * 255).astype(np.uint8))); rem.save(f'{core_nodpi}/home_remote_decor.webp', 'WEBP', quality=85, method=6)

# 4. App icon art from reference (rounded square)
src1 = Image.open(f'{REF}/img-001.jpg').convert('RGB')
icon = src1.crop((372, 13, 1303, 934))  # 931x921
side = 1024
icon_sq = icon.resize((side, side), Image.LANCZOS)
def rounded_mask(s, r, inset=0):
    m = Image.new('L', (s * 4, s * 4), 0); ImageDraw.Draw(m).rounded_rectangle((inset * 4, inset * 4, (s - inset) * 4 - 1, (s - inset) * 4 - 1), r * 4, fill=255)
    return m.resize((s, s), Image.LANCZOS)
legacy = icon_sq.copy(); legacy.putalpha(rounded_mask(side, int(side * 0.20), inset=6))
legacy.save(f'{P}/branding/source/akaaflix_launcher_icon_1024.png')
inner = src1.crop((420, 55, 1255, 890)).resize((side, side), Image.LANCZOS)  # inside the neon frame, for adaptive fg
inner.save(f'{P}/branding/source/akaaflix_launcher_icon_art.png')
dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for k, f in dens.items():
    dd = mk(f'{app_res}/mipmap-{k}')
    s = int(48 * f)
    lg = legacy.resize((s, s), Image.LANCZOS); lg.save(f'{dd}/ic_launcher.webp', 'WEBP', quality=92, method=6)
    rd = icon_sq.resize((s, s), Image.LANCZOS); cm = Image.new('L', (s * 4, s * 4), 0); ImageDraw.Draw(cm).ellipse((4, 4, s * 4 - 5, s * 4 - 5), fill=255)
    rd.putalpha(cm.resize((s, s), Image.LANCZOS)); rd.save(f'{dd}/ic_launcher_round.webp', 'WEBP', quality=92, method=6)
    fs = int(108 * f); fg = Image.new('RGBA', (fs, fs), (0, 0, 0, 0)); art = int(76 * f)
    fg.paste(inner.resize((art, art), Image.LANCZOS).convert('RGBA'), ((fs - art) // 2, (fs - art) // 2))
    fg.save(f'{dd}/ic_launcher_foreground.webp', 'WEBP', quality=92, method=6)

# 5. TV banner 320x180 (xhdpi) : wallpaper + logo
wp = Image.open(f'{REF}/img-004.jpg').convert('RGB')
ban = wp.crop((300, 40, 1640, 794)).resize((640, 360), Image.LANCZOS).convert('RGBA')
dark = Image.new('RGBA', ban.size, (8, 2, 20, 120)); ban.alpha_composite(dark)
lw = 560; lg = logo.resize((lw, int(logo.height * lw / logo.width)), Image.LANCZOS)
ban.alpha_composite(lg, ((640 - lw) // 2, (360 - lg.height) // 2))
bm = rounded_mask(640, 0); ban = ban.resize((320, 180), Image.LANCZOS)
mk(f'{app_res}/drawable-xhdpi'); ban.convert('RGB').save(f'{app_res}/drawable-xhdpi/tv_banner.webp', 'WEBP', quality=92, method=6)
ban.save(f'{P}/branding/source/tv_banner_320x180.png')

# 6. Avatars (circle)
def circle(img, s=256):
    img = img.resize((s, s), Image.LANCZOS).convert('RGBA'); m = Image.new('L', (s * 4, s * 4), 0)
    ImageDraw.Draw(m).ellipse((0, 0, s * 4 - 1, s * 4 - 1), fill=255); img.putalpha(m.resize((s, s), Image.LANCZOS)); return img
circle(src1.crop((560, 60, 1000, 500))).save(f'{core_nodpi}/avatar_anubis.webp', 'WEBP', quality=90)
circle(wp.crop((560, 180, 1100, 720))).save(f'{core_nodpi}/avatar_pyramids.webp', 'WEBP', quality=90)
circle(wp.crop((1150, 20, 1600, 470))).save(f'{core_nodpi}/avatar_galaxy.webp', 'WEBP', quality=90)

# 7. Default wallpaper (supplied) – upscale 1671x941 -> 1920x1080 (Lanczos)
wp.resize((1920, 1080), Image.LANCZOS).save(f'{wp_raw}/wp_default_pyramids.webp', 'WEBP', quality=93, method=6)
wp.save(f'{P}/branding/source/default_wallpaper_original_1671x941.jpg', quality=95)
Image.open(f'{REF}/img-005.jpg').save(f'{P}/branding/source/akaaflix_launcher_logo_original.jpg', quality=95)
logo.save(f'{P}/branding/source/akaaflix_launcher_logo_transparent.png')
src1.save(f'{P}/branding/source/akaaflix_launcher_icon_reference.jpg', quality=95)
print('assets ok')
