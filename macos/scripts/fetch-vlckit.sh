#!/usr/bin/env bash
# Downloads the official, universal (arm64 + x86_64) VLCKit binary from VideoLAN and verifies it.
# VLCKit 3.7.3 — LGPL-2.1 — minimum macOS: 10.9 (x86_64 slice) / 11.0 (arm64 slice).
set -euo pipefail

VERSION="3.7.3"
URL="https://download.videolan.org/pub/cocoapods/prod/VLCKit-3.7.3-319ed2c0-79128878.tar.xz"
SHA256="019afdae4e2e2d0f3ac325fac8f7ba0af25dca70b9d157df7d60db88e0be8e5d"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/Vendor"

if [[ -d "$DEST/VLCKit.xcframework" && "$(cat "$DEST/.vlckit-version" 2>/dev/null || true)" == "$VERSION" ]]; then
  echo "VLCKit $VERSION already present in $DEST"
  exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
echo "Downloading VLCKit ${VERSION}…"
curl -fL --retry 4 --retry-delay 3 -o "$TMP/vlckit.tar.xz" "$URL"
echo "$SHA256  $TMP/vlckit.tar.xz" | shasum -a 256 -c -
tar -xJf "$TMP/vlckit.tar.xz" -C "$TMP"
PKG="$(find "$TMP" -maxdepth 2 -type d -name 'VLCKit.xcframework' | head -1)"
[[ -n "$PKG" ]] || { echo "VLCKit.xcframework not found in archive" >&2; exit 1; }

rm -rf "$DEST/VLCKit.xcframework"
mkdir -p "$DEST"
cp -R "$PKG" "$DEST/"
cp "$(dirname "$PKG")/COPYING.txt" "$DEST/VLCKit-COPYING.txt" 2>/dev/null || true
echo "$VERSION" > "$DEST/.vlckit-version"
echo "VLCKit $VERSION installed:"
lipo -info "$DEST/VLCKit.xcframework/macos-arm64_x86_64/VLCKit.framework/VLCKit" 2>/dev/null || true
