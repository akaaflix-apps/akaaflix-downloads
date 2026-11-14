# Device compatibility report

**Version:** 1.4.0 (versionCode 6) · **minSdk** 23 (Android 6.0) · **targetSdk/compileSdk** 37 (Android 17)

This report separates what was **actually verified** from what is **expected but not yet tested**.
No physical device or hardware-accelerated emulator was available in the build environment (the build host
has no KVM), so **nothing has been tested on real hardware yet**.

## 1. Verified

### 1.1 Build

| Check | Result |
|---|---|
| Unit-test compilation (debug variant, never shipped) | ✅ builds |
| `assembleRelease` with R8 minify + resource shrink, persistent RSA-4096 key, signature v1+v2+v3 | ✅ builds, `apksigner verify` OK, `zipalign` OK, no native code (size: see SECURITY_AUDIT.md §11) |
| Permissions (`aapt2`) | ✅ one optional runtime permission (approximate location for the weather city), no special-access permissions (see SECURITY_AUDIT.md) |
| Locales in the APK | ✅ `en`, `ar`, `fr` (resource filter + `locales_config`) |
| Manifest (`aapt2 dump badging`) | ✅ minSdk 23, targetSdk 37, `launchable-activity` and `leanback-launchable-activity` with TV banner, no required hardware features (touchscreen, leanback, Wi-Fi, Bluetooth, microphone all optional) |

### 1.2 Automated tests (all passing)

| Suite | What it covers |
|---|---|
| Unit tests (63) | Duplicate removal (profile copies, same-name stubs, different apps never merged), App manager protection tiers / action planning / command validation, start-up evaluation;  Home/Favorites independence and schema migration (old list → Home, Favorites empty), settings migration (3 rows, Medium icons, no icon style), French language value, weather parsing (Open-Meteo forecast with high/low, city search, BigDataCloud reverse geocoding), coordinate rounding, French Gregorian/Hijri texts, accent-insensitive search, install-type classification (user / system / updated / protected); Hijri calendar – **every day** of AH 1300–1600 (110,000+ days) compared with `java.time.chrono.HijrahDate`; tabular calendar continuity; date texts in Arabic/English, 12/24 h, Eastern digits, time zones; paging maths (incl. overflow regression); app filtering/sorting/search (Arabic normalisation), favourites, reorder, classifier (no Games/System categories); settings JSON round-trip and validation; backup never contains the PIN; PBKDF2 PIN hashing; 8K image decoded with sub-sampling to screen size; wallpaper badges; performance tiers for 1/2/4/8 GB; update-release parsing |
| Integration (Robolectric) | Real `MainActivity` launched as HOME: HOME intent resolves to the launcher; installed apps discovered (nothing invented, launcher excluded, system app classified); **Home shows exactly 3 rows, below the logo and above the quick bar / remote art**; voice, notifications and Bluetooth controls absent; sidebar = Home, All apps, Favorites, Customize, Settings; Home ↔ Favorites independence through the real menu actions; D-pad: LEFT from the first card enters the sidebar on the **selected** item, RIGHT returns to the **same card**; every page opens; Wallpapers highlights Customize and Back returns to Customization, then Home; Back on Home does not exit; All apps lists every installed app in its own scrolling grid (> 3 rows); HOME intent returns to Home; settings (rows, Large icons, theme) survive recreation; crash guard |
| Languages (Robolectric) | Every string resolves in Arabic, English and French; copyright and website texts; French typography |
| Screenshot renders | Every page rendered at 1920×1080 (TV: Arabic, English, **French**) and 1233×2673 (phone portrait, Arabic), plus the About page and the neon focus state; compared visually with the reference images |

Integration suite executed on these Android framework versions (Robolectric runs the real framework code of
each version on the JVM). Requested compatibility matrix – **Robolectric result / real device**:

| Android | API | Result |
|---|---|---|
| 6.0 Marshmallow | 23 | ✅ (Robolectric 4.14.1; system font – see note) · Shizuku not available (ADB commands shown) · device: not tested |
| 7.0 Nougat | 24 | ✅ · device: not tested |
| 8.0 Oreo | 26 | ✅ · device: not tested |
| 9 Pie | 28 | ✅ · "Start with device" fallback available · device: not tested |
| 10 | 29 | ✅ · HOME role · device: not tested |
| 11 | 30 | ✅ · device: not tested |
| 12 | 31 | ✅ · device: not tested |
| 13 | 33 | ✅ · device: not tested |
| 14 | 34 | ✅ · **Xiaomi TV Box MiTV-AFMU0: to be tested with docs/XIAOMI_TEST_PLAN.md** |
| 15 | 35 | ✅ · device: not tested |
| 16 | 36 | ✅ · device: not tested |
| 17 | 37 | ✅ · device: not tested |

Backups do not depend on a document picker: Download/AkaaFlix through MediaStore (Android 10+, no permission),
the launcher's own folder on Android 6–9, plus an internal copy on every version. TV "framework package stubs"
placeholders are recognised and never treated as a picker or a Home settings screen.

API-level fallbacks: HOME role (29+) → preferred Home activity / Home settings (23–28); boot-broadcast start
only on 23–28 (Android 10+ blocks background activity starts); `pm uninstall-system-updates` (29+) → App info;
Shizuku API (24+) → ADB commands on 23; photo picker (Android 11+/Play services) → document picker.

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
| Google Play Protect result for v1.0.1 / v1.1.0 on a real device | ⚠️ not tested (v1.0.0 block cause removed; v1.1 adds only optional approximate location) |
| Weather against the live Open-Meteo / BigDataCloud services and location fix on a device | ⚠️ not tested (hosts blocked in the build environment; parsing unit-tested) |
| Animated Neon Pulse frame rate on real TV GPUs | ⚠️ not measured |
| Photo/document picker on TV, system notification shortcut | ⚠️ not tested |
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
| "Start with device" | Android 6–9 only, when another launcher is default. Android 10+ blocks background activity starts; the launcher deliberately does not request "Display over other apps" to bypass that, and instead offers to become the default Home app. |
| App shortcuts (long-press menu) | Android 7.1+ and only while Akaa Flix is the **default** launcher (`LauncherApps` shortcut host permission). |
| System settings (Wi-Fi, Bluetooth, VPN, date, language, display, sleep, HDMI-CEC) | A launcher cannot change them; the launcher opens the system screen. Some TVs lack some screens; the launcher then shows a message. |
| Bluetooth | Only on/off state. Connected-device details would need the `BLUETOOTH_CONNECT` runtime permission, which is intentionally not requested. |
| Recently used apps | The launcher's own launch history (no usage-access permission). Apps opened elsewhere are not listed. |
| Notifications | The launcher does not read notifications (no notification-listener access, to avoid Play Protect fraud blocks and for privacy). Settings → Notifications opens the system notification panel/history/settings, whichever the device provides. |
| Weather city detection | Needs a location provider (network/fused). Many TV boxes have none; the launcher then offers manual city search. Only approximate location, one fix, no background location. |
| Pre-installed apps | Uninstall is offered only for user apps; system apps open Android App info (disable / uninstall updates where allowed); protected apps need OEM/ADB/Device Owner (BUILD_AND_INSTALL.md). |
| Wallpaper import | Uses the system photo picker / document picker or any file manager that offers `GET_CONTENT`. TV boxes without any picker need a file-manager app installed. |
| Parental locks | Apply inside the launcher only; apps can still be opened from Android settings or another launcher. |
| Per-app language on Android 6–12 | Handled by AppCompat (stored by the launcher); Android 13+ also shows it in system settings. |
| Custom mouse pointer | Android 7.0+ (`PointerIcon`), inside the launcher only. |
| Real-time background blur (glass) | Not used: Android 6–11 lack `RenderEffect`; glass is drawn with translucent gradients on all versions for identical look and lower GPU cost. |
| Video wallpaper | Formats depend on the device decoder (H.264 MP4 is the safest). Disabled in lightweight mode. |
