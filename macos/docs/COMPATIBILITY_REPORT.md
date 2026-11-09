# Akaa Flix for macOS — Compatibility Report

Version **4.0.7** (build 407) · Universal `arm64` + `x86_64` · generated from CI results
(`macos/docs/ci/`) by `macos/tools/make_compat_report.py`.

```
run: https://github.com/akaaflix-apps/akaaflix-downloads/actions/runs/42711919595
commit: f59c2a6ab93752f64ef126e1b20941231f0002c2
```

## Summary

| Item | Result |
|---|---|
| Minimum supported macOS | **macOS 11.0 Big Sur** (deployment target; enforced at compile time and checked on every Mach-O) |
| Architectures in the single app bundle | **arm64 + x86_64** (see `lipo` output below) |
| Packaging | One Universal app + one Universal DMG (`AkaaFlix-4.0.7-Universal.dmg`); thin builds not required |
| Playback engine | VLCKit 3.7.3 (libVLC 3.0.x), universal; AVFoundation fallback |
| Live priority | MPEG-TS first, HLS second — fixed; verified end-to-end below |
| Signing / notarization | Hardened Runtime; ad-hoc signed and **not notarized** unless Developer ID secrets are configured (see INSTALL.md) |

## Final acceptance criteria

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Builds for Intel and Apple Silicon | ✅ | Release build `ARCHS = arm64 x86_64` with Xcode 16.4 (macOS 15) and Xcode 26 (macOS 26) in CI |
| 2 | Universal binary verified | ✅ | `lipo` + per-Mach-O check below |
| 3 | Minimum supported macOS documented | ✅ | macOS 11.0 — see below |
| 4 | Approved designs preserved | ✅ | Every screen implemented from the PDF; logo/icon/artwork used as supplied; screenshots in `macos/docs/screenshots/` |
| 5 | Xtream Codes functionality works | ✅ | Auth, wrong-password handling and catalog sync on 3 runners (mock Xtream server) |
| 6 | Live TV works | ✅ | Live playback + channel switching |
| 7 | MPEG-TS has first priority | ✅ | `LiveStreamPolicy.mandatoryOrder` + unit tests + end-to-end check |
| 8 | HLS has second priority | ✅ | Server returns 404 for `.ts` → app plays `.m3u8` (trail “MPEG-TS ✕ → HLS ✓”) |
| 9 | Movies and series play correctly | ✅ | MP4 with two audio tracks + external SRT; episodes |
| 10 | Continue Watching works | ✅ | Position saved and listed |
| 11 | Automatic next episode works | ✅ | End of episode → countdown → next episode playing |
| 12 | Application installs correctly | ✅ | DMG mounted, app copied to Applications, signature verified, launched from there on 3 runners |
| 13 | DMG generated | ✅ | `hdiutil verify` VALID; SHA-256 below |
| 14 | Compatibility report delivered | ✅ | This document |
| — | Developer ID signing + notarization | ❌ not done | No Apple Developer credentials available; pipeline ready (INSTALL.md). The DMG is ad-hoc signed and **not notarized**. |

## Why macOS 11.0 is the minimum

| Component | Minimum macOS | Notes |
|---|---|---|
| SwiftUI app lifecycle, `LazyVGrid`, `@StateObject`, `ScrollViewReader` | 11.0 | Required by the approved interface (grids, adaptive layout). |
| Apple Silicon hardware | 11.0 | Every Apple Silicon Mac shipped with macOS 11 or later. |
| VLCKit 3.7.3 | arm64: 11.0 · x86_64: 10.9 | Read from the framework's load commands. |
| Swift concurrency | 10.15 (back-deployed) | Embedded by Xcode; does not raise the target. |
| SQLite UPSERT | SQLite 3.24 (macOS 11 ships 3.32) | |

macOS 10.15 Catalina and earlier are **not supported**: SwiftUI on 10.15 lacks lazy grids, the
app lifecycle and `@StateObject`, which the approved screens depend on, and no Apple Silicon Mac
can run 10.15. Supporting it would require a separate AppKit UI, which would change the
approved implementation.

Intel Macs able to run macOS 11 include MacBook Air (2013+), MacBook Pro (late 2013+), iMac
(mid 2014+), Mac mini (2014+), Mac Pro (2013+) and iMac Pro. All Apple Silicon Macs (M1–M5 and
later) are supported natively.

## Binary verification (from CI)

## Universal binary verification

```
Architectures in the fat file: build/Release/Akaa Flix.app/Contents/MacOS/Akaa Flix are: x86_64 arm64 
```
✅ Main executable contains: x86_64 arm64

| Mach-O file | Architectures | Minimum macOS (per slice) |
|---|---|---|
| `Contents/MacOS/Akaa Flix` | x86_64 arm64 | x86_64:11.0 arm64:11.0  |
| `Contents/Frameworks/VLCKit.framework/Versions/A/VLCKit` | x86_64 arm64 | x86_64:10.9 arm64:11.0  |
| `Contents/Frameworks/libswift_Concurrency.dylib` | x86_64 arm64 | x86_64:10.9 arm64:11.0  |

- Bundle name: **Akaa Flix** · version **4.0.7** · identifier `com.akaaflix.mac` · LSMinimumSystemVersion **11.0**
- Signature: Signature=adhoc · flags=0x10002(adhoc,runtime)
- ✅ codesign --verify --deep --strict passed
- ✅ Hardened Runtime enabled

DMG checksum:

```
eec296c4132d114f0141bb2f55d3d578c2be5104cf0271032b5deaf113ee04cb  AkaaFlix-4.0.7-Universal.dmg
```

## Test matrix

| Feature | Apple Silicon · macOS 15.8.2 | Intel · macOS 15.7.9 | Apple Silicon · macOS 26.7.2 |
|---|---|---|---|
| Application installation | ✅ | ✅ | ✅ |
| Application startup | ✅ | ✅ | ✅ |
| Native execution (no Rosetta) | ✅ | ✅ | ✅ |
| Xtream Codes authentication | ✅ | ✅ | ✅ |
| Wrong-credential handling | ✅ | ✅ | ✅ |
| Playlist sync (live/movies/series) | ✅ | ✅ | ✅ |
| Database persistence | ✅ | ✅ | ✅ |
| Live TV · MPEG-TS first | ✅ | ✅ | ✅ |
| HLS fallback after MPEG-TS failure | ✅ | ✅ | ✅ |
| Live channel switching | ✅ | ✅ | ✅ |
| Movie playback · audio selection · subtitles | ✅ | ✅ | ✅ |
| Seek + Continue Watching | ✅ | ✅ | ✅ |
| Full-screen mode | ⚠️ | ✅ | ⚠️ |
| Series playback + automatic next episode | ✅ | ✅ | ✅ |
| QR code playlist import | ✅ | ✅ | ✅ |
| Playlist management | ✅ | ✅ | ✅ |
| Navigation (all screens, Arabic + English) | ✅ | ✅ | ✅ |
| Window resizing (1100×680) | ✅ | ✅ | ✅ |
| Shutdown and resource cleanup | ✅ | ✅ | ✅ |

✅ passed · ❌ failed · ⚠️ optional check failed (see notes) · — not run

## Environments

| Environment | Mac architecture | macOS | Application build | Playback engine | Result | Known limitations |
|---|---|---|---|---|---|---|
| Apple Silicon (virtualised M1, GitHub `macos-15`) | arm64 (binary slice arm64, Rosetta: no) | 15.8.2 Sequoia | 4.0.7 (407) | VLCKit 3.7.3 (libVLC 3.0.23 Vetinari) | **PASS** | Full-screen mode: window did not enter full screen (CI display limitation possible) |
| Intel (Core i7-8700B, GitHub `macos-15-intel`) | x86_64 (binary slice x86_64, Rosetta: no) | 15.7.9 Sequoia | 4.0.7 (407) | VLCKit 3.7.3 (libVLC 3.0.23 Vetinari) | **PASS** | none observed |
| Apple Silicon (virtualised M1, GitHub `macos-26`) | arm64 (binary slice arm64, Rosetta: no) | 26.7.2 Tahoe | 4.0.7 (407) | VLCKit 3.7.3 (libVLC 3.0.23 Vetinari) | **PASS** | Full-screen mode: window did not enter full screen (CI display limitation possible) |
| Older Intel Mac (2013–2016) | x86_64 | 11 Big Sur | 4.0.7 (407) | VLCKit 3.7.3 (libVLC 3.0.23) | **not tested** | No such hardware in CI. Build targets it (x86_64 slice, minos ≤ 11.0) but it is **not verified**. |
| Recent Intel Mac on macOS 12–14 | x86_64 | 12 Monterey · 13 Ventura · 14 Sonoma | 4.0.7 (407) | VLCKit 3.7.3 (libVLC 3.0.23) | **not tested** | GitHub has retired these runner images. **Not verified**; same binary as the tested macOS 15 Intel run. |
| Apple Silicon M2/M3 | arm64 | any | 4.0.7 (407) | VLCKit 3.7.3 (libVLC 3.0.23) | **not tested** | CI hardware is a virtualised M1. **Not verified** on M2/M3; no generation-specific code paths exist. |
| Apple Silicon M4/M5 (e.g. MacBook Air M5) | arm64 | any | 4.0.7 (407) | VLCKit 3.7.3 (libVLC 3.0.23) | **not tested** | **Not verified** in CI; no generation-specific code paths exist. |
| macOS 11 Big Sur (oldest supported) | arm64 / x86_64 | 11.x | 4.0.7 (407) | VLCKit 3.7.3 (libVLC 3.0.23) | **not tested** | No macOS 11 runner. Compile-time availability checking and `minos` verification only — **not run**. |

## Self-test details

### Apple Silicon (virtualised M1, GitHub `macos-15`)

macOS 15.8.2 (Sequoia) · model `VirtualMac2,1` · CPU Apple M1 (Virtual) · 7 GB · slice **arm64** · Rosetta no · VLCKit 3.0.23 Vetinari

| Check | Result | Detail | Seconds |
|---|---|---|---|
| Application startup | ✅ | window visible, data dir /var/folders/2s/vty41l455yv698g4xkgfv7z00000gn/T/tmp.OPhQXbxhyG | 0.0 |
| Native architecture (no Rosetta) | ✅ | arm64 slice on arm64 hardware | 0.0 |
| Xtream Codes authentication | ✅ | account active, max connections 2 | 0.1 |
| Wrong credentials are rejected | ✅ | invalid credentials reported | 0.0 |
| Catalog sync (live, movies, series) | ✅ | 12 channels, 12 movies, 8 series | 0.2 |
| Database persistence (reopen from disk) | ✅ | favorites, progress and catalog survive reopening akaaflix.sqlite | 0.0 |
| Live TV plays MPEG-TS first | ✅ | playing as MPEG-TS · MPEG-TS ✓ | 1.1 |
| HLS fallback when MPEG-TS fails | ✅ | MPEG-TS ✕ → HLS ✓ | 1.3 |
| Live channel switching | ✅ | Sports One HD → Sports Three HD (channel up ×2), playing | 1.3 |
| Movie playback, audio tracks and subtitles | ✅ | 2 audio tracks (switched), 1 subtitle track(s) | 4.9 |
| Seek and Continue Watching | ✅ | position 40s saved; 1 item(s) in Continue Watching | 0.0 |
| Full-screen mode | ⚠️ | window did not enter full screen (CI display limitation possible) | 6.1 |
| Series playback and automatic next episode | ✅ | advanced 50111 → 50112 after countdown | 10.2 |
| QR code playlist import (image) | ✅ | decoded 1 QR payload → Xtream login OK (active) | 0.3 |
| Playlist management (duplicate, default, reorder, delete) | ✅ | duplicate → set default → reorder → delete, credentials kept in the secret store | 0.1 |
| Navigation through every screen | ✅ | 28 screens rendered (Arabic and English) | 9.5 |
| Window resizing and small screens | ✅ | rendered at 1100×708 | 2.9 |
| Shutdown and resource cleanup | ✅ | player released, database checkpointed | 0.0 |

### Intel (Core i7-8700B, GitHub `macos-15-intel`)

macOS 15.7.9 (Sequoia) · model `Macmini6,2` · CPU Intel(R) Core(TM) i7-8700B CPU @ 3.20GHz · 14 GB · slice **x86_64** · Rosetta no · VLCKit 3.0.23 Vetinari

| Check | Result | Detail | Seconds |
|---|---|---|---|
| Application startup | ✅ | window visible, data dir /var/folders/20/jp1_0n3n7kndh6rnbqb5344m0000gn/T/tmp.PcontYrKps | 0.0 |
| Native architecture (no Rosetta) | ✅ | x86_64 slice on x86_64 hardware | 0.0 |
| Xtream Codes authentication | ✅ | account active, max connections 2 | 0.1 |
| Wrong credentials are rejected | ✅ | invalid credentials reported | 0.0 |
| Catalog sync (live, movies, series) | ✅ | 12 channels, 12 movies, 8 series | 0.2 |
| Database persistence (reopen from disk) | ✅ | favorites, progress and catalog survive reopening akaaflix.sqlite | 0.0 |
| Live TV plays MPEG-TS first | ✅ | playing as MPEG-TS · MPEG-TS ✓ | 2.5 |
| HLS fallback when MPEG-TS fails | ✅ | MPEG-TS ✕ → HLS ✓ | 1.4 |
| Live channel switching | ✅ | Sports One HD → Sports Three HD (channel up ×2), playing | 7.1 |
| Movie playback, audio tracks and subtitles | ✅ | 2 audio tracks (switched), 1 subtitle track(s) | 4.8 |
| Seek and Continue Watching | ✅ | position 40s saved; 1 item(s) in Continue Watching | 0.0 |
| Full-screen mode | ✅ | entered and left full screen | 6.6 |
| Series playback and automatic next episode | ✅ | advanced 50111 → 50112 after countdown | 9.4 |
| QR code playlist import (image) | ✅ | decoded 1 QR payload → Xtream login OK (active) | 0.6 |
| Playlist management (duplicate, default, reorder, delete) | ✅ | duplicate → set default → reorder → delete, credentials kept in the secret store | 0.2 |
| Navigation through every screen | ✅ | 28 screens rendered (Arabic and English) | 9.6 |
| Window resizing and small screens | ✅ | rendered at 1100×680 | 2.9 |
| Shutdown and resource cleanup | ✅ | player released, database checkpointed | 0.0 |

### Apple Silicon (virtualised M1, GitHub `macos-26`)

macOS 26.7.2 (Tahoe) · model `VirtualMac2,1` · CPU Apple M1 (Virtual) · 7 GB · slice **arm64** · Rosetta no · VLCKit 3.0.23 Vetinari

| Check | Result | Detail | Seconds |
|---|---|---|---|
| Application startup | ✅ | window visible, data dir /var/folders/h5/t85nd63d71x27_8gl5gw3l_w0000gn/T/tmp.8hz8Px1kUn | 0.0 |
| Native architecture (no Rosetta) | ✅ | arm64 slice on arm64 hardware | 0.0 |
| Xtream Codes authentication | ✅ | account active, max connections 2 | 0.1 |
| Wrong credentials are rejected | ✅ | invalid credentials reported | 0.0 |
| Catalog sync (live, movies, series) | ✅ | 12 channels, 12 movies, 8 series | 0.2 |
| Database persistence (reopen from disk) | ✅ | favorites, progress and catalog survive reopening akaaflix.sqlite | 0.0 |
| Live TV plays MPEG-TS first | ✅ | playing as MPEG-TS · MPEG-TS ✓ | 1.0 |
| HLS fallback when MPEG-TS fails | ✅ | MPEG-TS ✕ → HLS ✓ | 0.9 |
| Live channel switching | ✅ | Sports One HD → Sports Three HD (channel up ×2), playing | 2.1 |
| Movie playback, audio tracks and subtitles | ✅ | 2 audio tracks (switched), 1 subtitle track(s) | 4.7 |
| Seek and Continue Watching | ✅ | position 40s saved; 1 item(s) in Continue Watching | 0.1 |
| Full-screen mode | ⚠️ | window did not enter full screen (CI display limitation possible) | 6.1 |
| Series playback and automatic next episode | ✅ | advanced 50111 → 50112 after countdown | 9.9 |
| QR code playlist import (image) | ✅ | decoded 1 QR payload → Xtream login OK (active) | 0.3 |
| Playlist management (duplicate, default, reorder, delete) | ✅ | duplicate → set default → reorder → delete, credentials kept in the secret store | 0.1 |
| Navigation through every screen | ✅ | 28 screens rendered (Arabic and English) | 9.7 |
| Window resizing and small screens | ✅ | rendered at 1100×708 | 3.1 |
| Shutdown and resource cleanup | ✅ | player released, database checkpointed | 0.0 |

## Known limitations

- **Notarization:** not performed unless Developer ID credentials are added as repository secrets; until then
  Gatekeeper asks for confirmation on first launch (INSTALL.md).
- **Playback was verified against a local mock Xtream server** with generated media (H.264/AAC MPEG-TS, HLS
  and MP4). Real provider streams (HEVC, 4K, DRM-free variants, unusual containers) depend on the provider.
- **Hardware decoding:** VLCKit uses VideoToolbox on Intel and Apple Silicon with automatic libavcodec
  fallback; CI runners are virtual machines, so GPU decoding performance was not measured.
- **Full screen** on CI runners can be limited by the virtual display; it is an optional check.
- **Quality menu:** only adaptive (HLS/DASH) sources offer multiple qualities; single-file VOD has one quality.
- **Cast photos:** Xtream Codes provides cast names only; cast cards show a silhouette instead of a photo.
- **Downloads** work only where the provider allows direct file access.

