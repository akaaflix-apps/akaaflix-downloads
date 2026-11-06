# Akaa Flix Prime — Completion Report

## Build results (verified in this environment)

| Command | Result |
|---|---|
| `./gradlew clean` | OK |
| `./gradlew test` | **73 tests, 0 failures, 0 errors, 0 skipped** |
| `./gradlew assembleDebug` | OK → `app/build/outputs/apk/debug/app-debug.apk` (26.8 MB, `com.akaaflix.prime.debug`) |
| `./gradlew assembleRelease` (with external signing properties) | OK → `app/build/outputs/apk/release/app-release.apk` (5.4 MB, R8-minified, `com.akaaflix.prime`) |

Both APKs pass `apksigner verify`. Both declare `LAUNCHER` and `LEANBACK_LAUNCHER`, a TV banner, and
`touchscreen`/`leanback` as *not required*, so one APK installs on phones, tablets, Android TV and TV boxes.

> **Release signing note.** The `app-release.apk` produced in this environment is signed with a throwaway
> local test key created outside the repository (it was never committed). Use it for sideload testing only.
> For distribution, create your own keystore and build with the four `AKAAFLIX_*` properties described in
> the README; an APK signed with a different key cannot update one signed with the test key.

## Implemented features (by spec section)

| # | Area | Status |
|---|---|---|
| 1–2 | Kotlin, Compose, Material 3, Media3, Navigation, Coroutines, Retrofit, OkHttp, kotlinx.serialization, Room, DataStore, Keystore, Coil, WorkManager; Clean Architecture + MVVM; minSdk 24 | Done |
| 3 | Brand palette (#030812, #071426, #0878FF, #9B19FF, #F01835, #FFC928…), reusable design system, RTL Arabic / LTR English, logo in headers & splash (aspect ratio preserved) | Done |
| 4 | Separate phone and TV experiences, auto-detected (config UI mode, UiModeManager, leanback, touchscreen) with a manual override; D-pad focus rings, MENU = context actions, back handling, TV back-to-exit | Done |
| 5 | Cinematic splash (logo, animated red play symbol, blue/red light, loader); routes to Add Playlist / Home / playlist selection | Done |
| 6 | Xtream-only playlists: exactly Name/Username/Password/URL; add, edit, delete, activate, default, refresh, test connection, status, expiry, counts; validation, error mapping, encrypted credentials, multiple URL formats and ports | Done |
| 7 | Home: header (logo, search, notifications, account), navigation, hero (Watch now / Favorite / Add to group), Continue watching, Latest, Most watched, Recommended, Popular series, Live channels | Done |
| 8 | Live TV: all channels, categories, countries, Arabic/International/Movie/Kids/Documentary/News quick filters, groups, favorites; channel logo/name/category/EPG now-next; player controls incl. audio/subtitles/quality/aspect/mute; last channel remembered | Done |
| 9 | Movies: all, latest, most watched, genres, countries, search, sorting, favorites, groups; cards (poster, title, year, rating, quality, favorite, add-to-group); details (backdrop, poster, plot, year, genre, duration, cast, watch/favorite/group) | Done |
| 10 | Series: same browse features; details with seasons and episodes (number, title, thumbnail, duration, watch); last episode + position saved | Done |
| 11 | One unified Favorites & Groups system (create, rename, delete, add/remove, view; mixed content) in Room | Done |
| 12 | ExoPlayer: HLS/DASH/progressive/RTMP, play/pause/seek/±10 s, brightness & volume gestures, aspect, audio/subtitle tracks, external subtitles, speed, next/previous episode, resume; buffering, retries, expired-session and unsupported-format handling | Done |
| 13 | Live Stream Format: Auto, HLS, MPEG-DASH, RTMP, HTTP Progressive (.mp4) (+ MPEG-TS); unsupported choices never forced; persisted | Done |
| 14 | Subtitle settings: enable, language, embedded/external, encoding, size, colour, background, opacity, position, outline, delay/sync — also adjustable during playback; no translation claimed | Done |
| 15 | Player settings: built-in (default), VLC, MX Player, system player via intents with real availability detection; hardware/software decoder preference, audio output (passthrough/PCM), aspect, auto play, auto next, resume, buffering | Done |
| 16 | Hide content (sections, categories, items, groups, keywords) with management/restore; parental PIN, change PIN, hide adult, lock items/categories, age restriction; restricted content never revealed without the PIN | Done |
| 17 | Account dashboard from `user_info`: playlist, username, connection, status, activation, expiry, remaining days, max connections, active playlist; Renew (configured contacts), Add playlist, Logout; no upgrade/activation-code/change-account | Done |
| 18 | Settings with all 19 sections; language, theme (dark/light/auto), time zone, notifications, auto refresh, clear cache, restore defaults; persisted | Done |
| 19 | Global instant search (channels, movies, series, favorites, groups), history, clear history, keyboard + TV IME | Done |
| 20 | Room entities Playlist, Favorite, CustomGroup, GroupContent, HiddenContent, PlaybackHistory, UserSettings, SearchHistory, EpgCache (+ catalog tables and notifications); exported schema + migration hook; user data kept on refresh; unique indexes prevent duplicates | Done |
| 21 | Paging for large catalogs, chunked inserts, streamed JSON decoding, image cache, no bulk poster loading, IO off the main thread, timeouts, offline handling, invalid-response handling, playback recovery | Done |
| 22 | Keystore AES-GCM credentials, redacted logging, URL validation, HTTPS preferred, no hard-coded credentials, backups disabled | Done |
| 23 | Launcher icon (legacy + adaptive + themed monochrome), TV banner, splash, notification icon, all densities, generated from the supplied artwork | Done |
| 24 | Project structure, Gradle/version catalog, manifest, network config, resources, build scripts | Done |
| 25 | Tests (see below) | Done |
| 26 | Debug + release APKs, source, build/signing instructions, README | Done |

## Tested features

`./gradlew test` (JVM + Robolectric, debug variant): **73 passed**.

- **Xtream authentication & API** (11): success parsing of string-typed numbers and formats, unlimited
  expiry, wrong password, Expired/Banned/Disabled, HTTP 403/404/502, HTML and non-Xtream JSON, timeouts,
  offline vs unreachable, inconsistent list types, empty-list shapes, server errors, credential redaction.
- **Playlist creation/editing/deletion** (7, Room): encrypted storage, activation/default, duplicates,
  invalid URL, keep password on edit, cascade delete of user data, exclusive active/default, expired status.
- **Favorites / groups / hidden content / parental** (8): unified favorites, mixed groups, survival across
  catalog refresh, hidden items/categories/keywords excluded from browse and search, adult filtering, PIN
  locks, sorting and category counts, local most-watched.
- **Catalog sync & details** (7): counts, new-content detection, partial failure keeps old data, VOD info,
  series seasons/episodes in both JSON shapes, EPG base64 decoding and caching.
- **Settings & subtitles** (4 + 4): persistence, clamping, PIN, restore defaults, export without PIN;
  SRT/VTT parsing, windows-1256/BOM decoding, delay.
- **Playback configuration** (4 + 6): ExoPlayer track parameters from settings (subtitle language/disable,
  data saver 720p, audio language), buffer profiles; stream URLs, live-format resolution and fallback.
- **Validation, URL normalisation, PIN hashing, text/category/age helpers, backup round trip** (21).
- **Android TV navigation** (3): D-pad focus across a grid, DPAD_CENTER click, MENU long-press, PIN entry
  with remote number keys.
- **UI smoke tests** (2): the real `MainActivity` on a phone and a TV configuration, first run → add
  playlist against the mock server → sync → Home, Live TV, Movies, Series, details, My List. Screenshots are
  written to `app/build/screenshots` (copies in `docs/screenshots`).

### Bugs found by these tests and fixed
- WorkManager scheduling could throw where androidx.startup had not run → app now implements
  `Configuration.Provider` and scheduling is guarded.
- Navigation after a suspending parental check could run off the main thread → all PIN-guarded
  navigation now resumes on `Dispatchers.Main.immediate`.
- TV detection ignored the configuration UI mode → checked first now.
- Opening Home right after adding a playlist could start a duplicate catalog download → syncs are marked
  queued synchronously; a genuinely empty playlist shows an empty state instead of an endless loader.
- Phone section tiles truncated their titles; the TV top bar wrapped labels at 960 dp → both layouts fixed.

## Known limitations

- **No device/emulator run.** This environment has no hardware virtualisation, so the app was not launched
  on a physical device or emulator. Behaviour was verified with the tests above, including Robolectric
  rendering of real screens. **Real stream playback was not verified** because no real Xtream Codes server
  is available here; the mock server never serves video. Please test playback on your devices with your
  own provider account.
- EPG uses `get_short_epg` (now/next per channel); there is no full multi-day XMLTV grid.
- Countries and channel kinds are inferred from provider category names; providers that do not encode
  them in names will show fewer filters.
- "Most watched" is based on local watch history (Xtream has no popularity data); "Popular series" and
  "Recommended" are ordered by provider ratings and your viewing categories.
- Negative subtitle delay applies to external subtitle files only; embedded subtitles can only be delayed.
- "Software decoding" prefers the platform's software decoders; no FFmpeg extension is bundled, so codecs
  the device cannot decode (e.g. some AC-3/DTS on certain boxes) still need an external player.
- MPEG-DASH, HTTP-progressive MP4 and RTMP live formats are only used when the server advertises them.
- Some Android TV devices have no system file picker, so loading external subtitle files or backups may be
  unavailable there (the app reports this).
- Catch-up/timeshift (`tv_archive`), picture-in-picture and downloads are not implemented (not in scope).
- "Connected devices" can only show the connection counts reported by the Xtream server.
- Default UI language is Arabic; English is available in Settings → Language.

## Environment notes (for reproducing the build)

- The build needs `dl.google.com` / `maven.google.com` (Android SDK and Google Maven) and Maven Central.
- Maven Central returned HTTP 429 here, so a **machine-local** Gradle init script
  (`~/.gradle/init.d/central-mirror.gradle`, not part of the repository) puts Google's Maven Central mirror
  first. The project itself uses the standard `google()` / `mavenCentral()` repositories.
