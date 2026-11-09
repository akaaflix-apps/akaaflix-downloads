#!/usr/bin/env bash
# Packages the app into a compressed DMG with an Applications shortcut.
# Usage: scripts/make-dmg.sh "build/Release/Akaa Flix.app" [output-dir] [suffix]
set -euo pipefail
APP="${1:?usage: make-dmg.sh <app> [outdir] [suffix]}"
OUT="${2:-build/dmg}"
SUFFIX="${3:-Universal}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Contents/Info.plist")"
NAME="AkaaFlix-$VER-$SUFFIX.dmg"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$OUT"
ditto "$APP" "$STAGE/Akaa Flix.app"
ln -s /Applications "$STAGE/Applications"
# Volume icon from the approved app icon (best effort).
ICON_SRC="$ROOT/branding/build_icon_1024.png"
if [[ -f "$ICON_SRC" ]] && command -v iconutil >/dev/null; then
  ICONSET="$STAGE/.icon.iconset"; mkdir -p "$ICONSET"
  for s in 16 32 128 256 512; do
    sips -z $s $s "$ICON_SRC" --out "$ICONSET/icon_${s}x${s}.png" >/dev/null
    sips -z $((s*2)) $((s*2)) "$ICON_SRC" --out "$ICONSET/icon_${s}x${s}@2x.png" >/dev/null
  done
  iconutil -c icns "$ICONSET" -o "$STAGE/.VolumeIcon.icns" && rm -rf "$ICONSET"
  command -v SetFile >/dev/null && SetFile -a C "$STAGE" || true
fi
rm -f "$OUT/$NAME"
hdiutil create -volname "Akaa Flix" -srcfolder "$STAGE" -ov -fs HFS+ -format UDZO -imagekey zlib-level=9 "$OUT/$NAME"
hdiutil verify "$OUT/$NAME"
if [[ -n "${AKAAFLIX_SIGN_IDENTITY:-}" ]]; then
  codesign --force --timestamp --sign "$AKAAFLIX_SIGN_IDENTITY" "$OUT/$NAME"
fi
shasum -a 256 "$OUT/$NAME" | tee "$OUT/$NAME.sha256"
echo "DMG: $OUT/$NAME"
