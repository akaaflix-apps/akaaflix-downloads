# Akaa Flix Launcher

A standalone Android **home-screen launcher** for Android TV, Google TV, Android TV boxes, tablets and
phones, built from the approved *Akaa Flix Launcher* design (Egyptian-futuristic, Anubis, pyramids,
purple/blue/silver/gold neon glass). Arabic-first (RTL) with full English (LTR).

It is **not** an IPTV app, media player or content platform: it lists and launches the apps that are
installed on the device. Akaa Player (or any other app) appears only as a normal installed app.

| Home (Arabic, TV 1080p) | All apps |
|---|---|
| ![Home](docs/screenshots/tv_ar_01_home.jpg) | ![All apps](docs/screenshots/tv_ar_02_all_apps.jpg) |
| **Customization** (live preview) | **Wallpapers** |
| ![Customization](docs/screenshots/tv_ar_04_customization.jpg) | ![Wallpapers](docs/screenshots/tv_ar_05_wallpapers.jpg) |
| **Settings** | **Home (English)** |
| ![Settings](docs/screenshots/tv_ar_06_settings.jpg) | ![Home EN](docs/screenshots/tv_en_01_home.jpg) |

*Screenshots are rendered by the Robolectric screenshot test (`ScreenshotTest`) with test apps, so app
icons show Android's placeholder icon. On a device the launcher shows each app's real icon or its
Android TV banner. Phone screenshots: `docs/screenshots/phone_ar_*.jpg`.*

## Downloads

| File | Notes |
|---|---|
| [`release/AkaaFlixLauncher-v1.0.0-release.apk`](release/) | R8-minified release build (3.1 MB). **Signed with a temporary key** – see [Release signing](docs/BUILD_AND_INSTALL.md#release-signing). |
| [`release/AkaaFlixLauncher-v1.0.0-debug.apk`](release/) | Debug build (`com.akaaflix.launcher.debug`), installs side by side. |

SHA-256 checksums: `release/SHA256SUMS.txt`. Install instructions: [docs/BUILD_AND_INSTALL.md](docs/BUILD_AND_INSTALL.md).

## Features

**Launcher core**
- Registered as a Home app (`MAIN/HOME/DEFAULT`), plus `LAUNCHER` and `LEANBACK_LAUNCHER` entries so it can be
  opened from another launcher or the Google TV apps row. Back never exits; pressing Home returns to the home
  page (a second press resets focus).
- Real app discovery: phone apps via `LauncherApps` (work-profile aware) and Android TV apps via
  `CATEGORY_LEANBACK_LAUNCHER`, with a PackageManager fallback. Android 11+ package visibility through `<queries>`
  (no `QUERY_ALL_PACKAGES`). Live refresh on install/update/uninstall.
- Icon cache (memory LRU sized per device + PNG disk cache), adaptive-icon shapes, Android TV banners on cards,
  per-app neon under-glow from the icon's dominant colour.

**Pages (as in the reference design)**
- **Home** – logo, favourite apps (1–3 rows) with *Add app*, clock, Gregorian + Hijri date (Arabic and English),
  Wi-Fi/Ethernet and Bluetooth status, optional weather widget, quick bar (search, voice search, all apps,
  notifications, device info).
- **All apps** – search (Arabic-normalised), filters *All / Entertainment / Tools* (no Games or System
  categories), sorting (custom, A–Z, Z–A, recently installed, most used, recently used), paging with D-pad edges,
  arrows and swipe, app options menu (favourite, move, hide, PIN lock, category, app info, uninstall, app shortcuts).
- **Favorites** – reorderable favourites and the launcher's recently-used apps.
- **Customization** – live miniature preview of unsaved changes; wallpapers, interface layout, colours & themes,
  app cards & icon styles, focus/cursor, fonts; motion, clock and favourites-bar toggles; restore defaults / save.
- **Wallpapers** – categories, favourites, resolution badges (8K/4K/2K/FHD/HD), preview behind the UI,
  full-screen preview, add images/videos from the device, auto-rotation with intervals.
- **Settings** – 12 sections from the reference (interface, appearance, apps, profiles & parental controls,
  network, connected devices, remote control, notifications, date & time, language, power & performance,
  about/updates/backup), quick toggles panel, storage/RAM/device cards.

**Platform features**
- Remote-first navigation: predictable D-pad routing between sidebar, top bar and content, focus memory,
  focus restoration after returning from apps, Menu-key and long-press actions, remote button test.
- Hijri calendar: embedded Umm al-Qura data (AH 1300–1600) validated day-by-day against the JDK, plus the
  arithmetical calendar and ±2-day adjustment. Western or Eastern Arabic numerals.
- Performance tiers (1 GB → 8 GB+ RAM) with an automatic and a manual **lightweight mode**.
- Parental PIN (PBKDF2-hashed) for apps, settings, customization and hidden apps.
- Backup & restore to a JSON file (never includes the PIN). Optional update check against GitHub releases.
- Crash guard: repeated crashes switch to a safe mode instead of a crash loop.

## Architecture

Kotlin, native Views (no Compose – lower startup cost and memory on 1 GB TV boxes), MVVM, coroutines/Flow,
Jetpack DataStore, AndroidX. No Room (no relational data), no DI framework, no analytics or ad SDKs.

```
app                 MainActivity (HOME), Application, boot receiver, crash guard, themes, manifest
launcher-core       design system (glass/neon drawables, switch, dialogs, PIN pad, setting rows), strings EN/AR,
                    icons, fonts, performance tiers, navigation contracts, safe intents
remote-navigation   D-pad focus routing (NavigationRootLayout), paged grids, key helpers
user-preferences    LauncherSettings / AppCustomization / wallpaper data (DataStore), backup codec, PIN hashing
device-information  clock ticker, Hijri calendar + date formatter, network & Bluetooth monitors, device info,
                    weather (Open-Meteo), system settings shortcuts, update checker, notification listener
app-manager         app discovery & launching, icon cache, list logic, app card/grid, All apps & Favorites pages
wallpaper-engine    catalogue, memory-efficient decoder, host view (static/video/animated), rotation, Wallpapers page
home-screen         shell views (sidebar, top bar), Home page, dialogs, live Home preview
customization       Customization page (draft → save)
settings            Settings page and sections
tools/              generators: icons, strings (EN/AR source of truth)
```

Requirements: minSdk **23** (Android 6.0), compileSdk/targetSdk **37** (Android 17), AGP 9.5.1, Gradle 9.9.1, JDK 17+.

## Documentation

- [Build & installation](docs/BUILD_AND_INSTALL.md) – build commands, signing, installing, setting as default launcher (incl. Google TV)
- [Device compatibility report](docs/COMPATIBILITY.md) – what was verified and what was not
- [Performance](docs/PERFORMANCE.md) – optimisations, tiers, lightweight mode
- [Design specification](docs/DESIGN_SPEC.md) – measurements taken from the reference, decisions to confirm

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Optional weather widget and *Check for updates* (both off until the user uses them) |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Wi-Fi / Ethernet status icon |
| `BLUETOOTH` (≤ Android 11) | Bluetooth on/off icon |
| `REQUEST_DELETE_PACKAGES` | "Uninstall" in the app menu opens the system uninstall dialog |
| `RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW` | Optional "Start with device" when another app is the default Home (Android 10+ requires the user to grant "Display over other apps") |
| `READ_MEDIA_IMAGES/VIDEO` (≤12: `READ_EXTERNAL_STORAGE`) | Only for the built-in wallpaper picker on devices without a system file picker; requested at that moment |

No location, contacts, microphone, phone or usage-access permission. Voice search uses the system recogniser.

## Privacy

No accounts, ads, analytics or tracking. Everything works offline; the launcher contacts the network only for
the weather widget (when enabled; city typed by the user, no GPS) and when the user checks for updates.
Settings stay on the device (Android cloud backup is disabled; use Settings → About → Back up settings).

## Licences

Fonts Tajawal and Cairo – SIL OFL 1.1 (`docs/licenses/`). Icons – original, in the style of the ISC-licensed
Lucide set. Bundled extra wallpapers are original renders made for this project (`tools/generate_wallpapers.py`). Akaa Flix branding and the default wallpaper come from the supplied references.
