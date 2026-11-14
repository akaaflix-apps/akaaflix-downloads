# Akaa Flix Launcher – completed and unsupported features

## v1.3.0 additions (Home button service, Shizuku connection)

Full report: [V1.3_DELIVERY_REPORT.md](V1.3_DELIVERY_REPORT.md). Verified with JVM and Robolectric tests;
**not yet run on the Xiaomi box** (tests 16–22 in [XIAOMI_TEST_PLAN.md](XIAOMI_TEST_PLAN.md)).

| Requirement | Status | Evidence / notes |
|---|---|---|
| Genuine HOME launcher (HOME + DEFAULT intent filter, Default apps → Home app where the firmware has it) | ✅ unchanged | `registeredAsHomeApp` integration test. Google TV has no Home-app setting – hence the service below |
| Real accessibility service listed in Settings → Accessibility → Services | ✅ (manifest) / ⏳ device | `registeredAsAccessibilityServiceWithMinimalAccess`: declared with `BIND_ACCESSIBILITY_SERVICE`, key filtering, no window content |
| Manual activation like other TV launchers | ✅ | Android's own switch and warning; Akaa Flix only opens the screen. Optional "Turn on with Shizuku" |
| Real status detection | ✅ | Read from `enabled_accessibility_services` + whether Android bound the service (Off / On / On – not running); tests |
| Home button returns to Akaa Flix | ✅ (logic) / ⏳ device | Home key taken only while Android would open another launcher; other keys pass through; tests on API 24–36 |
| Start-up after reboot | ✅ (logic) / ⏳ device | First service connection after a restart opens Akaa Flix; start-up row says "Verified" only with that evidence; tests |
| Shizuku: authorization, connection, disconnection, reconnection | ✅ (code) / ⏳ device | Binder received/dead listeners, live state in App manager, "Shizuku stopped" notice, Open Shizuku / Check again |
| Shizuku: disable / uninstall incl. pre-installed apps for user 0 | ✅ (commands + tests) / ⏳ device | `pm disable-user --user 0`, `pm uninstall --user 0` (user apps), `pm uninstall -k --user 0` (pre-installed, restorable) |
| Release APK only | ✅ | Debug APK removed from `release/`; only `assembleRelease` is delivered |

## v1.2.0 additions (functional fixes)

Full report: [V1.2_DELIVERY_REPORT.md](V1.2_DELIVERY_REPORT.md). Same verification level as below: JVM and
Robolectric tests on API 23–37 plus inspection of the built APK. **The Xiaomi TV Box tests in
[XIAOMI_TEST_PLAN.md](XIAOMI_TEST_PLAN.md) have not been run.**

| Requirement | Status | Evidence / notes |
|---|---|---|
| No fake or duplicate Play Store entry | ✅ (logic) / ⏳ device | Entries come only from LauncherApps/PackageManager. Profile copies and same-name stubs are removed (`AppDedup`, 4 unit tests); Diagnostics lists each hidden entry |
| Real default-Home status | ✅ (logic) / ⏳ device | HOME role + resolved Home activity + Home app list; integration test checks the status rows on API 23–37 |
| Set as default (official path) | ✅ | RoleManager dialog (10+), Home settings (6–9); Google TV / Xiaomi: role command through Shizuku or ADB, re-read afterwards |
| Start-up status only from evidence | ✅ (logic) / ⏳ device | Boot broadcast + first HOME intent per boot (`BootLog`); "Verified" only after Android opened Akaa Flix as Home following the latest restart (unit tests) |
| Boot switch limited to Android 6–9 | ✅ | Row hidden on API 29+ (integration test); the quick toggle shows the real Default Home state there |
| App manager (user + pre-installed apps) | ✅ | Listing, filters, search, per-package actions; integration test opens it on API 23–37; screenshots `*_11_app_manager` |
| Shizuku integration (optional) | ✅ (code + tests without Shizuku) / ⏳ device | Official API 13.1.5; nothing privileged runs until the user allows access in Shizuku. Command validation tests include an injection attempt |
| Critical packages protected | ✅ | `PackageProtection` CRITICAL set; such packages are information-only (unit test) |
| Device information from Android APIs | ✅ | Exact refresh rate, available RAM, free storage, all ABIs, build number |


"Verified" means checked by automated tests (JVM unit tests and Robolectric, which runs the real Android
framework code of each API level on the JVM) and/or by inspecting rendered screenshots and the built APK.
**Nothing has been tested on a physical TV, box, phone or tablet** (no device in the build environment);
see `docs/COMPATIBILITY.md`.

## v1.1.0 acceptance checklist

| Requirement | Status | Evidence / notes |
|---|---|---|
| Original Akaa Flix design preserved | ✅ | Same artwork, palette, glass cards, sidebar, typography and RTL/LTR rules; screenshots `docs/screenshots/tv_*` |
| Neon remote focus on app cards | ✅ | Purple-magenta neon tube (coloured stroke + white core), outer glow inside the card margin, inner illumination, zoom, ~150 ms cross-fade; same drawable on every focusable item. Screenshot `tv_*_09_home_focus`; D-pad routing asserted in `LauncherIntegrationTest` |
| Voice button removed | ✅ | Bottom bar = search, all apps, device info; integration test asserts no "Voice search" view. Voice search code and its manifest query were removed with it (no other entry point) |
| Notifications button removed | ✅ | Integration test asserts no "Notifications" view. Android's notification screen stays reachable from Settings → Notifications |
| Bluetooth top icon removed | ✅ | Top bar = clock, Wi-Fi, settings, date pill; Bluetooth status/shortcut remain in Settings → Connected devices |
| Wallpapers removed from sidebar, reachable via Customization | ✅ | Sidebar items asserted (Home, All apps, Favorites, Customize, Settings); Customization → Wallpapers tile; Wallpapers highlights Customize and Back returns to Customization (asserted) |
| Featured Wallpapers removed | ✅ | Title, category chips and carousel removed from Customization (both orientations) |
| Icon Style settings removed | ✅ | Card on Customization page, row in the Apps sheet and in Settings → Appearance removed; stored shapes migrate to the original icons |
| Small icon size removed | ✅ | Only Medium (default) and Large – Customization card, Layout/Apps sheets and Settings; stored "small" migrates to Medium (unit test) |
| Medium and Large icon sizes | ✅ | Large persists across recreation (integration test) |
| Home screen uses three rows | ✅ | Default 3 (maximum 3); card height fitted to the free band; asserted: 3 rows, grid below the logo, above the quick bar and the remote art (960×540 dp = 720p/1080p/4K TVs) |
| All Apps independent layout + scrolling | ✅ | Own vertically scrolling grid; asserted it lists every installed app and is not limited to the Home rows |
| Weather with real data | ⚠️ implemented, not verified live | Open-Meteo current temperature, condition text + icon, daily high/low, offline cache, JobScheduler hourly refresh. Parsing unit-tested with documented responses; the build environment blocks the weather hosts, so no live request was made |
| Weather city auto-detected with permission | ⚠️ implemented, not verified on a device | "Detect my location": explanation → runtime ACCESS_COARSE_LOCATION → one fix (no tracking) → Geocoder / BigDataCloud city. Denied/unavailable → manual city search. Needs a device with a location provider (many TV boxes have none – manual city is offered) |
| Favorites independent of Home | ✅ | Separate `homeApps` / `favorites` lists, separate menu actions and pickers, migration (old list → Home, Favorites empty). Unit + integration tests |
| No fake Google Play Store entry | ✅ | App code never creates entries; the README screenshots' "Google Play" card came from the old Robolectric fixtures, now brand-neutral. Asserted that an app that is not installed never appears |
| Real installed apps discovered dynamically | ✅ | LauncherApps + PackageManager (LAUNCHER and LEANBACK_LAUNCHER), `<queries>` for Android 11+, live refresh |
| User app uninstall | ✅ (dialog launch) | Opens Android's uninstall confirmation (`ACTION_DELETE` + `REQUEST_DELETE_PACKAGES`); only offered for user-installed apps. The system dialog itself was not exercised on a device |
| Protected system apps handled per Android permissions | ✅ | Type detection (user / pre-installed updated / pre-installed / protected) unit + integration tested; system apps → App info (disable / uninstall updates where allowed); protected → explanation. Removal needs OEM/ADB/Device Owner (documented, not implemented) |
| Registered as a real HOME launcher | ✅ | HOME/DEFAULT filter resolves to MainActivity (test); LAUNCHER + LEANBACK_LAUNCHER entries |
| Default launcher selection | ⚠️ implemented, not verified on a device | RoleManager request (Android 10+), Home settings fallback, step-by-step guide in 3 languages |
| Home button returns to Akaa Flix when default | ✅ (simulated) | HOME intent while on another page returns to Home (integration test). Real remote Home key routing is done by Android and was not tested on hardware |
| Startup behaviour on supported devices | ⚠️ not verified on hardware | Default Home app is started by Android at boot; optional "Start with device" on Android 6–9 only |
| Arabic, English and French | ✅ | 509 strings per language (generator enforces identical keys/placeholders); French typography; French Gregorian/Hijri dates; `LanguagesTest`; French TV screenshots |
| Official website displayed and clickable | ✅ | About: "Official website – Akaa Flix" → `https://akaaflix.com` via a BROWSABLE view intent; message with the address when no browser exists |
| Copyright text displayed | ✅ | جميع الحقوق محفوظة Akaa Team / All Rights Reserved – Akaa Team / Tous droits réservés – Akaa Team (asserted in `LanguagesTest`, screenshot `*_08_settings_about`) |
| Every visible icon/button works | ✅ | Control-by-control audit below |
| Android 6 compatibility tested | ✅ (Robolectric) | API 23 run with Robolectric 4.14.1 – not on a real Android 6 device |
| Newer Android compatibility tested | ✅ (Robolectric) | API 24–37 |
| No critical crashes | ✅ within the tests | No crash in any automated run; untested on hardware |
| Release build succeeds | ✅ | Signed (v1+v2+v3) with the persistent key, R8 minified |

## Control audit (every visible control)

| Screen | Control | Action | Remote focus |
|---|---|---|---|
| Top bar | Brand pill | Home: Settings → Profiles; other pages: back to Home | ✅ |
| | Wi-Fi icon | Android network settings | ✅ |
| | Settings gear | Launcher Settings | ✅ |
| | Clock / Gregorian + Hijri date | Display only (not a button) | – |
| Sidebar | Home, All apps, Favorites, Customize, Settings | Navigate; selected item highlighted | ✅ |
| Home | App card | Open (PIN if locked); long-press / Menu → app options | ✅ |
| | Add app | Home apps picker | ✅ |
| | Weather widget | Weather panel (detect / manual city / refresh / unit / show) | ✅ |
| | Search | All apps with the search field focused | ✅ |
| | All apps | All apps page | ✅ |
| | Device info | Device information dialog | ✅ |
| | Remote art (bottom right) | Touch: Settings → Remote control (decorative on TV, not focusable) | – |
| App options | Open, Add/Remove Home, Add/Remove Favorites, Move, Hide, Lock, Category, App info, Uninstall (user apps) / Manage in Android (system apps), shortcuts | All functional | ✅ |
| All apps | Search, Sort, All / Entertainment / Tools chips, cards, "Search in store" (only when no result and a store is installed) | Functional | ✅ |
| Favorites | Add or remove, Reorder, cards, Add card, recently used | Functional | ✅ |
| Customization | Wallpapers, Interface layout, Colors & themes, App customization, Cursor, Fonts tiles | Wallpapers page / option sheets | ✅ |
| | Motion, Clock & date, Home apps toggles; Medium / Large | Draft, saved with Save | ✅ |
| | Restore defaults, Save changes | Functional (unsaved-changes guard) | ✅ |
| Wallpapers | Category chips (only non-empty), cards, Add from device, Set, Preview, Full screen, Favorite, rotation toggle, interval | Functional | ✅ |
| Settings | 12 section tiles, quick toggles, Check for updates, Restore defaults, Apply, storage / RAM / device cards, Back | Functional | ✅ |
| About | Official website, version, build, updates, backup, restore, privacy, licences | Functional | ✅ |

## Unsupported / requires extra privileges

| Feature | Why | Option |
|---|---|---|
| Removing or permanently hiding pre-installed system apps | Android reserves it for the system, Device Owner apps and ADB | ADB `pm disable-user` / `pm uninstall -k --user 0`, Device Owner provisioning, or OEM system integration (docs/BUILD_AND_INSTALL.md) |
| Replacing the launcher on Google TV and some OEM firmwares | No home-app chooser; the stock launcher is hard-wired | ADB disable of the stock launcher, or OEM integration |
| "Start with device" when not the default Home on Android 10+ | Background activity starts are blocked | Make Akaa Flix the default Home app |
| Weather city detection on devices without a location provider | No network/fused location source | Manual city search |
| Thumbnails of PDF page 7 as wallpapers | Only ~480×260 px per tile; upscaling would degrade quality | Supply the full-resolution images and run `tools/import_pdf_wallpapers.py` |
