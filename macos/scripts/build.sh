#!/usr/bin/env bash
# Builds Akaa Flix. Usage: scripts/build.sh [Release|Development|Testing] [extra xcodebuild args…]
# Release produces a Universal (arm64 + x86_64) app at build/Release/Akaa Flix.app
set -euo pipefail
CONFIG="${1:-Release}"; shift || true
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

"$ROOT/scripts/fetch-vlckit.sh"

DESTINATION="generic/platform=macOS"
[[ "$CONFIG" != "Release" ]] && DESTINATION="platform=macOS,arch=$(uname -m)"

xcodebuild -project AkaaFlix.xcodeproj -scheme "Akaa Flix" -configuration "$CONFIG" \
  -destination "$DESTINATION" -derivedDataPath build/DerivedData "$@" build

mkdir -p "build/$CONFIG"
rm -rf "build/$CONFIG/Akaa Flix.app"
ditto "build/DerivedData/Build/Products/$CONFIG/Akaa Flix.app" "build/$CONFIG/Akaa Flix.app"
echo "Built: $ROOT/build/$CONFIG/Akaa Flix.app"
if [[ "$CONFIG" == "Release" ]]; then
  "$ROOT/scripts/verify-universal.sh" "build/$CONFIG/Akaa Flix.app"
fi
