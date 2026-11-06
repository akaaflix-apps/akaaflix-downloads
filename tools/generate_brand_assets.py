#!/usr/bin/env python3
"""Generates Android launcher icons, adaptive icon layers, TV banner and logo
resources from the supplied Akaa Flix Prime brand artwork in branding/source.

Usage:  python3 tools/generate_brand_assets.py
Requires Pillow.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "branding" / "source"
RES = ROOT / "app" / "src" / "main" / "res"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def trimmed(img: Image.Image) -> Image.Image:
    bbox = img.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    return img.crop(bbox) if bbox else img


def fit(img: Image.Image, max_w: int, max_h: int) -> Image.Image:
    """Scale preserving aspect ratio (never stretches)."""
    scale = min(max_w / img.width, max_h / img.height)
    return img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)


def rounded_mask(size, radius) -> Image.Image:
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return m


def main() -> None:
    logo = trimmed(Image.open(SRC / "akaaflix_prime_logo.png").convert("RGBA"))
    icon = Image.open(SRC / "akaaflix_prime_app_icon.png").convert("RGBA")

    # --- Logo used in headers and the splash screen (aspect ratio preserved) ---
    nodpi = RES / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    fit(logo, 1200, 1200).save(nodpi / "akaa_logo.webp", "WEBP", quality=92, method=6)

    # --- Square app tile cropped from the supplied icon artwork ---
    tile = icon.crop((250, 0, 1286, 1024)).resize((1024, 1024), Image.LANCZOS)
    tile_rgba = tile.copy()
    tile_rgba.putalpha(rounded_mask(tile.size, 190))

    # Interior artwork (play symbol + wordmark) without the outer frame and device row.
    content = tile.crop((58, 46, 966, 790))

    # Legacy launcher icons (API < 26).
    for name, factor in DENSITIES.items():
        px = round(48 * factor)
        out = RES / f"mipmap-{name}"
        out.mkdir(parents=True, exist_ok=True)
        fit(tile_rgba, px, px).save(out / "ic_launcher.png")

    # Adaptive icon layers (108dp, visible area ~72dp).
    for name, factor in DENSITIES.items():
        px = round(108 * factor)
        out = RES / f"mipmap-{name}"
        # Background: heavily blurred interior so the foreground blends seamlessly.
        bg = content.resize((px, px), Image.LANCZOS).filter(ImageFilter.GaussianBlur(px / 10))
        bg = Image.blend(bg.convert("RGB"), Image.new("RGB", (px, px), (8, 3, 8)), 0.35)
        bg.save(out / "ic_launcher_background.png")
        # Foreground: wordmark artwork inside the safe zone with feathered edges.
        fg_content = fit(content, round(px * 0.66), round(px * 0.66))
        feather = Image.new("L", fg_content.size, 0)
        inset = max(2, round(fg_content.width * 0.04))
        ImageDraw.Draw(feather).rounded_rectangle(
            (inset, inset, fg_content.width - inset, fg_content.height - inset), radius=inset * 3, fill=255)
        feather = feather.filter(ImageFilter.GaussianBlur(inset))
        fg_content.putalpha(feather)
        fg = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        fg.alpha_composite(fg_content, ((px - fg_content.width) // 2, (px - fg_content.height) // 2))
        fg.save(out / "ic_launcher_foreground.png")
        # Round legacy icon = composed adaptive layers in a circle.
        legacy_px = round(48 * factor)
        composed = bg.convert("RGBA")
        composed.alpha_composite(fg)
        composed = composed.crop((px // 6, px // 6, px - px // 6, px - px // 6)).resize((legacy_px, legacy_px), Image.LANCZOS)
        circle = Image.new("L", composed.size, 0)
        ImageDraw.Draw(circle).ellipse((0, 0, legacy_px - 1, legacy_px - 1), fill=255)
        composed.putalpha(circle)
        composed.save(out / "ic_launcher_round.png")

    # --- Android TV launcher banner: 320x180dp (xhdpi = 320x180px, xxhdpi = 480x270px) ---
    for name, (w, h) in {"xhdpi": (320, 180), "xxhdpi": (480, 270)}.items():
        banner = Image.new("RGBA", (w, h), (3, 8, 18, 255))
        glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(glow)
        d.ellipse((w * 0.15, h * 0.1, w * 0.85, h * 0.95), fill=(240, 24, 53, 90))
        d.ellipse((-w * 0.2, h * 0.5, w * 0.5, h * 1.4), fill=(8, 120, 255, 60))
        glow = glow.filter(ImageFilter.GaussianBlur(h / 5))
        banner.alpha_composite(glow)
        mark = fit(logo, round(w * 0.86), round(h * 0.7))
        banner.alpha_composite(mark, ((w - mark.width) // 2, (h - mark.height) // 2))
        out = RES / f"drawable-{name}"
        out.mkdir(parents=True, exist_ok=True)
        banner.convert("RGB").save(out / "tv_banner.png")

    # --- Splash icon for the Android 12+ system splash (icon area 240dp, mask 160dp) ---
    for name, factor in DENSITIES.items():
        px = round(240 * factor)
        splash = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        mark = fit(logo, round(px * 0.6), round(px * 0.6))
        splash.alpha_composite(mark, ((px - mark.width) // 2, (px - mark.height) // 2))
        out = RES / f"drawable-{name}"
        out.mkdir(parents=True, exist_ok=True)
        splash.save(out / "splash_logo.png")

    print("Brand assets generated in", RES)


if __name__ == "__main__":
    main()
