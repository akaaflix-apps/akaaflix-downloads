#!/usr/bin/env python3
"""Builds macos/AkaaFlix/Resources/Assets.xcassets from the approved brand artwork.

Sources (macos/branding/):
  akaaflix_logo.png       transparent wordmark cut from page 1 of the approved brand PDF
  akaaflix_app_icon.png   transparent app icon cut from page 2 of the approved brand PDF
  design/*.png            decorative artwork cropped from the approved screen designs

The artwork is only scaled — never redrawn or recoloured.
Usage: python3 macos/tools/generate_assets.py   (requires Pillow)
"""
import json
import shutil
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "branding"
OUT = ROOT / "AkaaFlix" / "Resources" / "Assets.xcassets"

INFO = {"author": "xcode", "version": 1}


def write_json(path: Path, obj) -> None:
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def image_set(name: str, image: Image.Image, fmt: str = "png", max_side: int = 2400) -> None:
    folder = OUT / f"{name}.imageset"
    folder.mkdir(parents=True, exist_ok=True)
    img = image.copy()
    img.thumbnail((max_side, max_side), Image.LANCZOS)
    filename = f"{name}.{fmt}"
    if fmt == "jpg":
        img.convert("RGB").save(folder / filename, "JPEG", quality=88, optimize=True, progressive=True)
    else:
        img.save(folder / filename, "PNG", optimize=True)
    write_json(folder / "Contents.json", {
        "images": [{"filename": filename, "idiom": "universal"}],
        "info": INFO,
        "properties": {"preserves-vector-representation": False},
    })


def app_icon(icon: Image.Image) -> None:
    """macOS icon grid: artwork scaled into the 824/1024 safe area with transparent margins."""
    folder = OUT / "AppIcon.appiconset"
    folder.mkdir(parents=True, exist_ok=True)
    side = max(icon.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(icon, ((side - icon.width) // 2, (side - icon.height) // 2))
    master = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    body = square.resize((860, 860), Image.LANCZOS)
    master.alpha_composite(body, (82, 78))
    images = []
    for points in (16, 32, 128, 256, 512):
        for scale in (1, 2):
            px = points * scale
            fn = f"icon_{points}x{points}{'@2x' if scale == 2 else ''}.png"
            master.resize((px, px), Image.LANCZOS).save(folder / fn, "PNG", optimize=True)
            images.append({"filename": fn, "idiom": "mac", "scale": f"{scale}x", "size": f"{points}x{points}"})
    write_json(folder / "Contents.json", {"images": images, "info": INFO})
    # Also used for the DMG volume icon.
    master.save(ROOT / "branding" / "build_icon_1024.png", "PNG", optimize=True)


def main() -> None:
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    write_json(OUT / "Contents.json", {"info": INFO})

    logo = Image.open(SRC / "akaaflix_logo.png").convert("RGBA")
    icon = Image.open(SRC / "akaaflix_app_icon.png").convert("RGBA")
    app_icon(icon)
    image_set("AkaaFlixLogo", logo, "png", 1600)
    image_set("AppIconArt", icon, "png", 640)

    design = {
        "SettingsHeroArt": "settings_hero.png",
        "PlayerHeroArt": "player_hero.png",
        "PlaylistsBanner": "playlists_banner.png",
        "AddPlaylistArt": "add_playlist.png",
        "AboutHeroArt": "about_hero.png",
        "CatalogHeaderArt": "catalog_header.png",
        "HomeTileLive": "home_tile_live.png",
        "HomeTileMovies": "home_tile_movies.png",
        "HomeTileSeries": "home_tile_series.png",
    }
    for name, file in design.items():
        image_set(name, Image.open(SRC / "design" / file).convert("RGB"), "jpg")

    accent = OUT / "AccentColor.colorset"
    accent.mkdir()
    write_json(accent / "Contents.json", {
        "colors": [{"idiom": "universal", "color": {"color-space": "srgb", "components": {"red": "0.898", "green": "0.063", "blue": "0.106", "alpha": "1.000"}}}],
        "info": INFO,
    })
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
