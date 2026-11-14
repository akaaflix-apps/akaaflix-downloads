#!/usr/bin/env python3
"""Extracts the approved Akaa Flix wallpapers from the supplied PDF into the wallpaper engine.

Source of truth: design-assets/wallpapers/akaa-flix-wallpapers.pdf (11 pages).
Each page embeds ONE sRGB JPEG (1670-1672 x 941 px). The embedded image streams are read
directly (no page rendering / screenshots), then stored as WebP at their native resolution:
no upscaling, no stretching, aspect ratio untouched.

Page 7 is a 4x4 contact sheet of small previews (~480x260 px per tile); it is not a wallpaper and
is skipped. Run:  python3 tools/import_pdf_wallpapers.py
Requires: pypdf, Pillow.
"""
import io, os, sys
from pypdf import PdfReader
from PIL import Image, ImageStat

ROOT = os.path.join(os.path.dirname(__file__), '..')
PDF = os.path.join(ROOT, 'design-assets', 'wallpapers', 'akaa-flix-wallpapers.pdf')
OUT = os.path.join(ROOT, 'wallpaper-engine', 'src', 'main', 'res', 'raw')
QUALITY = 90

# page number (1-based) -> resource name
PAGES = {
    1: 'wp_dubai_skyline',
    2: 'wp_aqsa_dome',
    3: 'wp_neon_supercar',
    4: 'wp_sphinx_pyramids',
    5: 'wp_cherry_lake',
    6: 'wp_moon_mountains',
    8: 'wp_dubai_waterfront',
    9: 'wp_al_azhar',
    10: 'wp_prophets_mosque',
    11: 'wp_lakeside_mosque',
}
SKIP = {7: 'contact sheet of 16 small previews (not a wallpaper)'}


def page_image(page):
    xobjects = page['/Resources']['/XObject']
    images = [v.get_object() for v in xobjects.values() if v.get_object().get('/Subtype') == '/Image']
    if len(images) != 1:
        raise SystemExit(f'expected exactly one image per page, found {len(images)}')
    obj = images[0]
    filt = obj.get('/Filter')
    if filt != '/DCTDecode':
        raise SystemExit(f'unexpected image filter {filt}')
    cs = obj.get('/ColorSpace')
    cs = cs.get_object() if hasattr(cs, 'get_object') else cs
    icc = cs[1].get_object().get_data() if isinstance(cs, list) and cs[0] == '/ICCBased' else None
    # The raw (still DCT-encoded) stream is the original JPEG file: read it without re-encoding.
    return obj._data, icc


def main():
    reader = PdfReader(PDF)
    os.makedirs(OUT, exist_ok=True)
    print(f'{PDF}: {len(reader.pages)} pages')
    for n, page in enumerate(reader.pages, start=1):
        if n in SKIP:
            print(f'  page {n:2d}: skipped – {SKIP[n]}')
            continue
        name = PAGES[n]
        jpeg, icc = page_image(page)
        im = Image.open(io.BytesIO(jpeg))
        im.load()
        if icc and b'sRGB' not in icc[:600] and b'sRGB' not in icc:
            raise SystemExit(f'page {n}: colour profile is not sRGB')
        im = im.convert('RGB')
        w, h = im.size
        path = os.path.join(OUT, name + '.webp')
        im.save(path, 'WEBP', quality=QUALITY, method=6)
        # Validation: identical size after the round trip and no colour shift (mean per channel).
        back = Image.open(path).convert('RGB')
        assert back.size == im.size
        err = sum(abs(a - b) for a, b in zip(ImageStat.Stat(back).mean, ImageStat.Stat(im).mean)) / 3
        assert err < 1.0, f'page {n}: colour shift {err:.2f}'
        print(f'  page {n:2d}: {name}.webp  {w}x{h}  aspect {w / h:.3f}  {len(jpeg) // 1024} KB jpeg -> {os.path.getsize(path) // 1024} KB webp  (mean shift {err:.2f})')


if __name__ == '__main__':
    sys.exit(main())
