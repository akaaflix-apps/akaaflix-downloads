# Akaa Flix Launcher v1.1 – implementation checklist

Prepared after inspecting the v1.0.1 project (checkpoint: commit `28a0003` on branch
`claude/akaa-flix-launcher-0e1olk`) and the supplied wallpaper PDF. Status is updated at the end of the
work in `docs/FEATURE_STATUS.md`.

## Findings from the inspection

| Topic | What exists in v1.0.1 | Consequence |
|---|---|---|
| Focus | `GlassDrawable` draws accent glow rings + 2.2 dp border inside a 6 dp margin; `FocusEffects` zooms with `ViewPropertyAnimator` | Upgrade to a magenta neon tube (core + halo), inner illumination and an animated glow level (cross-fade between cards, no flicker) |
| Bottom bar | search, voice, all apps, notifications (bell), device info | Remove voice + bell; remaining 3 stay centred |
| Top bar | clock, Wi-Fi, Bluetooth, settings, date pill | Remove Bluetooth icon (status remains in Settings → Connected devices) |
| Sidebar | Home, All apps, Favorites, Customize, **Wallpapers**, Settings | Remove Wallpapers item; Wallpapers page opened from Customization (sidebar highlights Customize, Back returns to Customization) |
| Customization | preview, 6 tiles, *Featured wallpapers* title + category chips + carousel, toggles, *Icon style* card | Remove featured section, chips, carousel and icon-style card; add a Medium/Large icon-size card; enlarge preview/tiles so no gap remains |
| Icon size | small / medium / large in Settings and the Layout sheet | Only Medium (default) and Large; stored "small" migrates to "medium" |
| Home rows | `homeRows` default 2, cards sized from width | Default 3, maximum 3; card height computed from the available height so 3 rows never overlap header, clock, quick bar or weather (960×540 dp canvas = 720p/1080p/4K TVs) |
| All apps | paged grid with arrows (rows = available height) | Own vertically scrolling grid, independent of the Home row setting |
| Favorites | **Home grid = favourites list** (`AppCustomization.favorites`); "Add app" edits favourites; first run auto-fills favourites | Separate `homeApps` and `favorites` lists, migration (old list → Home, Favorites empty), separate menu actions and pickers, first run fills Home only |
| "Google Play" card | Not created by app code. It appears in the README screenshots because the Robolectric fixtures (`TestApps.kt`) register a fake `com.android.vending` app | Replace fixtures with brand-neutral sample apps; keep dynamic discovery (Play Store shown only when really installed) |
| App management | open, favourite, move, hide, lock, category, app info, uninstall (for every app) | Detect user / updated-system / system / protected apps; show the type; uninstall only for user apps; system apps → App info (disable / uninstall updates where Android allows); protected apps → explanation; document ADB/Device Owner paths |
| Weather | Open-Meteo, manual city only, current temp + icon, cache, refresh while visible | Add approximate location (ACCESS_COARSE_LOCATION on request, single fix, no tracking), reverse geocoding (Android `Geocoder`, fallback BigDataCloud client API), daily high/low, condition text, JobScheduler periodic refresh, offline cache |
| Wallpapers | default Egyptian artwork + 8 generated stills + Neon Pulse rings overlay + Starfield | Library = Egyptian Pyramids Cinematic (default, unchanged) + Animated Neon Pulse (purple/blue waves) + the 10 full-page PDF images. Old generated stills moved out of the APK to `design-assets/legacy-wallpapers/` (kept, not deleted) |
| PDF | 11 pages, each one embedded sRGB JPEG 1670–1672 × 941 px. Pages 1–6 and 8–11 are full wallpapers; page 7 is a 4×4 contact sheet (≈ 480×260 px tiles) | Use the 10 full images at native resolution (no upscaling, badge "HD"); do not use page 7 (thumbnails too small for TV) |
| Languages | English + Arabic (472 strings), Hijri/Gregorian texts in AR/EN | Add French for every string, French date/Hijri texts, language picker (System/العربية/English/Français), `locales_config` |
| About | version/build rows | Logo header, app name, version, clickable https://akaaflix.com ("Akaa Flix"), copyright in AR/EN/FR |
| HOME | HOME/DEFAULT + LAUNCHER/LEANBACK_LAUNCHER filters, RoleManager request, boot receiver (Android 6–9 only) | Keep; add a guided "Set as default launcher" setup and document OEM limits |
| Voice search | only reachable from the bottom-bar mic | Removed with the button (plus its manifest query and microphone feature) |

## Work items

1. Focus system – `launcher-core/ui/GlassDrawable.kt`, `FocusEffects.kt`, `LauncherUi.kt`, `app-manager/ui/AppCardView.kt`.
2. Shell – `home-screen/shell/SidebarView.kt`, `TopBarView.kt`, `view_top_bar.xml` (+port), `fragment_home.xml` (+port), `core/nav/Navigation.kt`, `app/MainActivity.kt`.
3. Customization – `customization/*` layouts and fragment, `CustomizationSheets.kt`, `settings/SettingsSections.kt`, `prefs/LauncherSettings.kt` (schema 2).
4. Home / All apps / Favorites – `HomeFragment.kt`, `HomeViewModel.kt`, `AllAppsFragment.kt`, `FavoritesFragment.kt`, `AppGridController.kt`, `AppLists.kt`, `AppCustomization.kt` (apps schema 2), `AppActions.kt`.
5. App management – `AppRepository.kt` (install type), `AppEntry.kt`, `AppActions.kt`, docs.
6. Weather – `device-information/weather/*` (location, geocoding, job), `WeatherSetup.kt`, `HomeFragment.kt`, manifests.
7. Wallpapers – `tools/import_pdf_wallpapers.py`, `wallpaper-engine/res/raw/*`, `WallpaperCatalog.kt`, `AnimatedOverlays.kt`, `WallpapersFragment.kt` categories.
8. French + About – `tools/strings_source.py` / `strings_fr.py`, `generate_strings.py`, `DateTextFormatter.kt`, `SettingsSections.kt`, `SettingRow` hero row, `locales_config.xml`.
9. HOME / boot review – manifest, `BootReceiver.kt`, `SettingsFragment.kt`, `docs/BUILD_AND_INSTALL.md`.
10. Audit, tests (unit + Robolectric API 23–37 + screenshots), debug + signed release APK, docs.

## Requires elevated privileges (documented, not implemented)

| Need | Why a normal launcher cannot | Path |
|---|---|---|
| Remove / permanently hide pre-installed system apps | Android only lets the system, a Device Owner or ADB do it | `adb shell pm disable-user --user 0 <pkg>` / `pm uninstall -k --user 0 <pkg>`, or a Device Owner app (`DevicePolicyManager.setApplicationHidden`) provisioned on a reset device |
| Guarantee the manufacturer launcher never appears | OEM firmware may hard-code its launcher (Google TV) | Choose Akaa Flix as default Home where offered; on Google TV disable the stock launcher with ADB; OEM builds can pre-install as a privileged system app |
| Start on boot on Android 10+ when not the default Home | Background activity starts are blocked | Be the default Home app (Android then starts it automatically) |
