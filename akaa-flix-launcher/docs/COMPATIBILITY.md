# Device compatibility report

**Version:** 1.0.0 (versionCode 1) · **minSdk** 23 (Android 6.0) · **targetSdk/compileSdk** 37 (Android 17)

This report separates what was **actually verified** from what is **expected but not yet tested**.
No physical device or hardware-accelerated emulator was available in the build environment (the build host
has no KVM), so **nothing has been tested on real hardware yet**.

## 1. Verified

### 1.1 Build

| Check | Result |
|---|---|
| `assembleDebug` (all 10 modules) | ✅ builds, 8.7 MB |
| `assembleRelease` with R8 minify + resource shrink, APK signature v2 | ✅ builds, 3.1 MB, `apksigner verify` OK |
| Manifest (`aapt2 dump badging`) | ✅ minSdk 23, targetSdk 37, `launchable-activity` and `leanback-launchable-activity` with TV banner, no required hardware features (touchscreen, leanback, Wi-Fi, Bluetooth, microphone all optional) |

### 1.2 Automated tests (all passing)

| Suite | What it covers |
|---|---|
| Unit tests (42) | Hijri calendar – **every day** of AH 1300–1600 (110,000+ days) compared with `java.time.chrono.HijrahDate`; tabular calendar continuity; date texts in Arabic/English, 12/24 h, Eastern digits, time zones; paging maths (incl. overflow regression); app filtering/sorting/search (Arabic normalisation), favourites, reorder, classifier (no Games/System categories); settings JSON round-trip and validation; backup never contains the PIN; PBKDF2 PIN hashing; 8K image decoded with sub-sampling to screen size; wallpaper badges; performance tiers for 1/2/4/8 GB; update-release parsing |
| Integration (Robolectric) | Real `MainActivity` launched as HOME: HOME intent resolves to the launcher; installed TV apps discovered and shown (launcher itself excluded); D-pad: LEFT from the first card enters the sidebar on the **selected** item, RIGHT returns to the **same card**; every page opens; Back returns Home; Back on Home does not exit; All apps lists all apps; HOME intent returns to Home; settings survive activity recreation; crash guard |
| Screenshot renders | Every page rendered at 1920×1080 (TV, Arabic + English) and 1233×2673 (phone portrait, Arabic), compared visually with the reference images |

Integration suite executed on these Android framework versions (Robolectric runs the real framework code of
each version on the JVM):

| Android | API | Result |
|---|---|---|
| 6.0 Marshmallow | 23 | ✅ (Robolectric 4.14.1; system font – see note) |
| 7.0 Nougat | 24 | ✅ |
| 8.0 Oreo | 26 | ✅ |
| 9 Pie | 28 | ✅ |
| 10 | 29 | ✅ |
| 11 | 30 | ✅ |
| 12 | 31 | ✅ |
| 13 | 33 | ✅ |
| 14 | 34 | ✅ |
| 15 | 35 | ✅ |
| 16 | 36 | ✅ |
| 17 | 37 | ✅ |

Bugs found and fixed by this matrix: a crash on first launch on Android 7–12 when the stored language differed
(activity recreation), D-pad leaving the sidebar could land in the top bar, and an integer overflow in the
phone wallpaper grid.

Note (API 23): Robolectric's legacy graphics cannot load font resources, so the API 23 run uses the system font.
AppCompat font resources are supported on Android 6 devices, but this specific path is not verified.

## 2. Not yet verified (requires real devices)

| Target | Status |
|---|---|
| Physical Android TV boxes (Amlogic/Allwinner/Rockchip, 1–4 GB) | ⚠️ not tested |
| Google TV (Chromecast with Google TV, Google TV televisions) | ⚠️ not tested |
| Android TV 9–14 televisions (Sony, TCL, Xiaomi…) | ⚠️ not tested |
| Phones and tablets (any vendor) | ⚠️ not tested |
| Real remote controls / air mice / game pads | ⚠️ not tested (D-pad logic verified in Robolectric only) |
| The **minified release APK** at runtime | ⚠️ built and signed, not executed (Robolectric runs non-minified code) |
| Default-launcher selection dialogs | ⚠️ not tested (vendor dependent) |
| Video wallpapers (MediaPlayer + TextureView) and procedural animated wallpapers on real GPUs | ⚠️ not tested |
| Startup time, frame rate, memory on 1 GB devices | ⚠️ not measured (see PERFORMANCE.md for the design budget) |
| Weather (Open-Meteo) and update check (GitHub) against the live services | ⚠️ the build environment blocked these hosts; parsing is unit-tested |
| Notification listener, voice search, document picker on TV | ⚠️ not tested |
| 4K and 8K output (UI is density-independent; 4K/8K wallpaper decoding is unit-tested) | ⚠️ not tested on screens |

Recommended device test plan before distribution: one 1 GB Android TV box (Android 7–9), one 2 GB box
(Android 9–11), one Google TV device (Android 12+), one Android 6 device, one Android 15/16 phone and one tablet.
Check: install, set as Home, launch 10 apps and return (focus restored), Home button behaviour, install /
uninstall refresh, every page with the remote only, Arabic/English switch, wallpaper import (image/video),
video wallpaper on/off, lightweight mode, reboot with "Start with device".

## 3. Operating-system restrictions (by design, not bugs)

| Feature | Restriction |
|---|---|
| Default launcher on **Google TV** | Google TV has no home-app chooser; the system Home key keeps opening Google's launcher unless it is disabled with ADB (docs/BUILD_AND_INSTALL.md). |
| "Start with device" | Works when another launcher is default. On Android 10+ only if the user grants *Display over other apps* (background activity start limits). |
| App shortcuts (long-press menu) | Android 7.1+ and only while Akaa Flix is the **default** launcher (`LauncherApps` shortcut host permission). |
| System settings (Wi-Fi, Bluetooth, VPN, date, language, display, sleep, HDMI-CEC) | A launcher cannot change them; the launcher opens the system screen. Some TVs lack some screens; the launcher then shows a message. |
| Bluetooth | Only on/off state. Connected-device details would need the `BLUETOOTH_CONNECT` runtime permission, which is intentionally not requested. |
| Recently used apps | The launcher's own launch history (no usage-access permission). Apps opened elsewhere are not listed. |
| Notifications panel | Requires user-granted notification access; Android TV builds often hide that setting (ADB command provided). |
| Parental locks | Apply inside the launcher only; apps can still be opened from Android settings or another launcher. |
| Per-app language on Android 6–12 | Handled by AppCompat (stored by the launcher); Android 13+ also shows it in system settings. |
| Custom mouse pointer | Android 7.0+ (`PointerIcon`), inside the launcher only. |
| Real-time background blur (glass) | Not used: Android 6–11 lack `RenderEffect`; glass is drawn with translucent gradients on all versions for identical look and lower GPU cost. |
| Video wallpaper | Formats depend on the device decoder (H.264 MP4 is the safest). Disabled in lightweight mode. |
