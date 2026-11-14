"""Removes the white background of the supplied logo (reference image 6) with colour-to-alpha.

Usage: python3 tools/extract_logo.py docs/reference/06_logo.jpg logo_full.png   (numpy, Pillow, scipy)
HOLES lists enclosed white areas (letter counters) verified visually for this specific image.
"""
import sys, numpy as np
from PIL import Image
from scipy import ndimage
src, out = sys.argv[1], sys.argv[2]
im = np.asarray(Image.open(src).convert('RGB')).astype(np.float32)
mn = im.min(axis=2); mx = im.max(axis=2)
near_white = (mn > 228) & ((mx - mn) < 30)
lab, n = ndimage.label(near_white)
border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
bg = np.isin(lab, list(border))
strict = (mn > 243) & ((mx - mn) < 14)
lab2, n2 = ndimage.label(strict)
HOLES = [162, 242, 243, 94, 160, 170, 128]  # verified visually (letter counters / enclosed gaps)
for h in HOLES:
    comp = lab2 == h
    bg |= ndimage.binary_dilation(comp, iterations=2) & near_white
region = ndimage.binary_dilation(bg, iterations=2)
a = np.ones(mn.shape, np.float32)
c2a = np.clip((255.0 - mn) / 255.0, 0, 1)
a[region] = c2a[region]
rgb = im.copy()
safe = np.maximum(a, 1e-3)[..., None]
un = 255.0 - (255.0 - im) / safe
rgb[region] = np.clip(un[region], 0, 255)
a[a < 0.03] = 0
img = Image.fromarray(np.dstack([rgb, a * 255]).astype(np.uint8), 'RGBA')
img = img.crop(img.getbbox())
img.save(out)
print('saved', out, img.size)
