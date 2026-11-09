#!/usr/bin/env bash
# Verifies that every Mach-O in the bundle contains arm64 AND x86_64, that no binary requires a
# macOS newer than the deployment target, and that the bundle metadata/signature are consistent.
# Usage: scripts/verify-universal.sh "path/to/Akaa Flix.app" [report.md]
set -euo pipefail
APP="${1:?usage: verify-universal.sh <app> [report]}"
REPORT="${2:-}"
TARGET_MIN="11.0"
EXE="$APP/Contents/MacOS/Akaa Flix"
fail=0
out() { echo "$@"; [[ -n "$REPORT" ]] && echo "$@" >> "$REPORT"; return 0; }
[[ -n "$REPORT" ]] && : > "$REPORT"

vercmp_le() { # $1 <= $2 ?
  [[ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | head -1)" == "$1" ]]
}

out "## Universal binary verification"
out ""
out '```'
out "$(lipo -info "$EXE")"
out '```'
archs="$(lipo -archs "$EXE")"
if [[ "$archs" == *arm64* && "$archs" == *x86_64* ]]; then out "✅ Main executable contains: $archs"; else out "❌ Main executable archs: $archs"; fail=1; fi
out ""
out "| Mach-O file | Architectures | Minimum macOS (per slice) |"
out "|---|---|---|"
while IFS= read -r -d '' f; do
  if file -b "$f" | grep -q "Mach-O"; then
    rel="${f#"$APP"/}"
    a="$(lipo -archs "$f" 2>/dev/null || echo '?')"
    mins=""
    for arch in $a; do
      m="$(otool -arch "$arch" -l "$f" 2>/dev/null | awk '/LC_BUILD_VERSION/{b=1} b&&/minos/{print $2; exit} /LC_VERSION_MIN_MACOSX/{v=1} v&&/version/{print $2; exit}')"
      mins+="$arch:${m:-?} "
      if [[ -n "$m" ]] && ! vercmp_le "$m" "$TARGET_MIN"; then out "❌ $rel ($arch) requires macOS $m > $TARGET_MIN"; fail=1; fi
    done
    [[ "$a" == *arm64* && "$a" == *x86_64* ]] || { out "❌ $rel is not universal ($a)"; fail=1; }
    out "| \`$rel\` | $a | $mins |"
  fi
done < <(find "$APP" -type f \( -perm -u+x -o -name '*.dylib' \) -print0)
out ""
plist="$APP/Contents/Info.plist"
name="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleName' "$plist")"
ver="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$plist")"
minos="$(/usr/libexec/PlistBuddy -c 'Print :LSMinimumSystemVersion' "$plist")"
bid="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$plist")"
out "- Bundle name: **$name** · version **$ver** · identifier \`$bid\` · LSMinimumSystemVersion **$minos**"
[[ "$name" == "Akaa Flix" ]] || { out "❌ bundle name must be exactly 'Akaa Flix'"; fail=1; }
[[ "$ver" == "4.0.7" ]] || { out "❌ unexpected version $ver"; fail=1; }
[[ "$minos" == "$TARGET_MIN" ]] || { out "❌ LSMinimumSystemVersion $minos != $TARGET_MIN"; fail=1; }
sig="$(codesign -dvv "$APP" 2>&1 || true)"
out "- Signature: $(echo "$sig" | grep -E '^(Authority|Signature)=' | head -1 | tr -d '\n') · $(echo "$sig" | grep -o 'flags=0x[0-9a-f]*([^)]*)' | head -1)"
if codesign --verify --deep --strict "$APP" 2>/dev/null; then out "- ✅ codesign --verify --deep --strict passed"; else out "- ❌ code signature invalid"; fail=1; fi
echo "$sig" | grep -q "runtime" && out "- ✅ Hardened Runtime enabled" || out "- ⚠️ Hardened Runtime flag not present"
exit $fail
