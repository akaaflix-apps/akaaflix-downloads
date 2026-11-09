# Akaa Flix for macOS — Dependency review

Every dependency was checked for Intel + Apple Silicon support, minimum macOS, binary
availability, licence and maintenance **before** it was adopted. The guiding rule: no dependency
may raise the deployment target above macOS 11.0 or remove an architecture.

## Third-party

| Dependency | Version (pinned) | Used for | Architectures | Minimum macOS | Licence | Maintenance | Source / integrity |
|---|---|---|---|---|---|---|---|
| **VLCKit** (libVLC 3.0.x) | **3.7.3** | Primary playback engine: raw MPEG-TS over HTTP, HLS, DASH, RTMP/RTSP, MKV/MP4, embedded + external subtitles, audio tracks, VideoToolbox hardware decoding with libavcodec software fallback | `x86_64` + `arm64` in one `macos-arm64_x86_64` xcframework slice (verified with `lipo`) | x86_64 slice: 10.9 · arm64 slice: 11.0 (verified with `otool -l`) | LGPL-2.1+ (dynamically linked, replaceable) | Maintained by VideoLAN (3.x stable branch; release Feb 2026) | `download.videolan.org/pub/cocoapods/prod/VLCKit-3.7.3-319ed2c0-79128878.tar.xz`, SHA-256 `019afdae4e2e2d0f3ac325fac8f7ba0af25dca70b9d157df7d60db88e0be8e5d` (checked by `scripts/fetch-vlckit.sh`) |

No package manager is required: the binary is fetched from VideoLAN by script, verified by
checksum and placed in `macos/Vendor/` (git-ignored). CocoaPods and SwiftPM wrappers were not used
(CocoaPods trunk is being retired; the SwiftPM wrappers for VLCKit are unofficial).

### Alternatives considered

| Option | Why not primary |
|---|---|
| AVFoundation (`AVPlayer`) | Cannot play a raw MPEG-TS stream over HTTP, which the mandatory live priority requires first. Kept as a **fallback engine** (HLS/MP4) compiled into every build. |
| VLCKit 4.0 (alpha) | Pre-release API; not suitable for a production release. |
| libmpv / MPVKit | GPL/LGPL mix and larger integration surface; VLCKit already covers every required format on both architectures. |
| FFmpeg-based custom player | Large engineering effort with no functional benefit over libVLC. |

## Apple system frameworks only (no other third-party code)

| Framework | Purpose | Available since |
|---|---|---|
| SwiftUI + AppKit | User interface, windows, full screen, menus | SwiftUI app lifecycle: macOS 11 |
| AVFoundation / AVKit | Fallback engine, AirPlay route picker, camera for QR | ≤ 10.15 |
| SQLite3 (system `libsqlite3`) | Database (catalog, favorites, progress, groups, downloads) | system library; UPSERT needs SQLite 3.24 (macOS 11 ships 3.32) |
| Security (Keychain) | Playlist passwords | all versions |
| CoreImage | QR decode (`CIDetector`) and QR generation | 10.10 |
| UserNotifications | New-content / expiry notifications | 10.14 |
| ImageIO | Poster downsampling | all versions |

Swift concurrency (`async/await`) is back-deployed by Xcode to macOS 10.15+, so it does not
raise the deployment target. API availability is enforced at compile time by the Swift compiler
against `MACOSX_DEPLOYMENT_TARGET = 11.0` (any macOS 12+ API used without `#available` would fail
the build), and `scripts/verify-universal.sh` checks the `minos` of every Mach-O in the final bundle.

## Build tool versions used by CI

| Tool | Version |
|---|---|
| Xcode (primary build, tests, DMG) | 16.4 (Swift 6.1 compiler, Swift 5 language mode) on macOS 15 |
| Xcode (forward-compatibility build) | 26.x default on macOS 26 runner |
| Python (mock server, generators) | 3.x standard library; Pillow only for `tools/generate_assets.py` |
