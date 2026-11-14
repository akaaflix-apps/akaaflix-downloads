# Build & installation

## Requirements

| Tool | Version used |
|---|---|
| JDK | 17 or newer (built with OpenJDK 21) |
| Android Gradle Plugin | 9.5.1 (built-in Kotlin, KGP 2.2.10) |
| Gradle | 9.9.1 (wrapper included) |
| Android SDK | `platforms;android-37.0`, `build-tools;37.0.0` (AGP also fetches build-tools 36.0.0) |
| Android Studio | A version that supports AGP 9.5 |

Create `local.properties` (Android Studio does this automatically):

```properties
sdk.dir=/path/to/Android/sdk
```

## Build commands

Run from the `akaa-flix-launcher/` folder.

```bash
./gradlew assembleRelease
```

Only the signed release APK is built and delivered. The debug variant exists solely so the unit and
Robolectric tests can run (`testDebugUnitTest`); no debug APK is produced or shipped.

```bash
./gradlew test
```

Outputs:

| Variant | Path | Application id |
|---|---|---|
| Release (signed) | `app/build/outputs/apk/release/app-release.apk` | `com.akaaflix.launcher` |
| Release (no signing properties) | `app/build/outputs/apk/release/app-release-unsigned.apk` | `com.akaaflix.launcher` |

The release build is minified and resource-shrunk with R8 (`app/proguard-rules.pro`).

### Tests

```bash
./gradlew test
```

```bash
./gradlew :app:testDebugUnitTest -Pakaa.robolectricVersion=4.14.1 --tests "*Api23*"
```

- `./gradlew test` runs all unit tests plus the Robolectric integration suite on Android 7.0 → 17 (API 24–37)
  and renders the screenshots to `app/build/screenshots/`.
- The second command runs the same integration flow on Android 6.0 (API 23); Robolectric 4.18 no longer
  ships API 23, so an older Robolectric is selected for that run.
- If Maven Central is rate-limited on your network, set a mirror for Robolectric's framework jars in
  `~/.gradle/gradle.properties`: `akaa.robolectric.repoUrl=https://<your-maven-mirror>/maven2`.

### Regenerating resources

```bash
python3 tools/generate_strings.py
```

```bash
python3 tools/import_pdf_wallpapers.py
```

```bash
python3 tools/generate_icons.py
```

`tools/strings_source.py` (English + Arabic) and `tools/strings_fr.py` (French) are the single source of
every interface string; the generator checks that all three languages have the same keys and placeholders.
`tools/import_pdf_wallpapers.py` extracts the approved wallpapers from
`design-assets/wallpapers/akaa-flix-wallpapers.pdf` (embedded images, native resolution, WebP q90; page 7 –
a contact sheet – is skipped). Brand assets can be rebuilt with `tools/extract_logo.py` and
`tools/generate_brand_assets.py`; the v1.0 procedural wallpapers (`tools/generate_wallpapers.py`) now live in
`design-assets/legacy-wallpapers/` and are not packaged.

## Release signing

Keys are never stored in the repository (`*.jks`, `*.keystore` are git-ignored). Provide four properties in
`~/.gradle/gradle.properties`, as `-P` flags or as environment variables:

| Property | Meaning |
|---|---|
| `AKAAFLIX_LAUNCHER_KEYSTORE_FILE` | Absolute path to the keystore |
| `AKAAFLIX_LAUNCHER_KEYSTORE_PASSWORD` | Keystore password |
| `AKAAFLIX_LAUNCHER_KEY_ALIAS` | Key alias |
| `AKAAFLIX_LAUNCHER_KEY_PASSWORD` | Key password |

Create your permanent key once and keep it safe (every update must be signed with the same key):

```bash
keytool -genkeypair -v -keystore ~/keys/akaaflix-launcher.jks -alias akaalauncher -keyalg RSA -keysize 3072 -validity 10000
```

The release key used for the release APKs (v1.0.1 and later) (PKCS12, RSA 4096, valid until 2056,
alias `akaaflix-launcher`) was handed to the project owner together with its password; it is **not** in Git.
Certificate fingerprint (public – use it to check downloads):

| | |
|---|---|
| Subject | `CN=Akaa Flix Launcher, O=Akaa Flix, C=EG` |
| SHA-256 | `8ee47c472321a9f0045b3193f1d70bbb2df0893ed92f65833d8635a763873cdf` |
| SHA-1 | `2096d60af67fb34b1b342cd73b9afbe46a0737f1` |

Build a release with it (PKCS12 keystores work with the same properties):

```bash
export AKAAFLIX_LAUNCHER_KEYSTORE_FILE=/secure/path/akaaflix-launcher-release.p12
export AKAAFLIX_LAUNCHER_KEY_ALIAS=akaaflix-launcher
export AKAAFLIX_LAUNCHER_KEYSTORE_PASSWORD='…' AKAAFLIX_LAUNCHER_KEY_PASSWORD='…'
./gradlew clean :app:assembleRelease
```

Signature schemes v1 (needed by Android 6.0), v2 and v3 are enabled. Keep the keystore and password backed up:
losing them means future updates cannot be installed over existing installations.

> v1.0.0 was signed with a temporary key that no longer exists. If v1.0.0 is installed anywhere, uninstall it
> before installing v1.0.1 or later.

Verify a signature:

```bash
$ANDROID_HOME/build-tools/37.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## Installing

Phone / tablet: copy the APK to the device and open it (allow "Install unknown apps" for your file manager).

Android TV / TV box: use a sideloading app (for example *Downloader*, or a file manager from a USB stick), or ADB:

```bash
adb connect <tv-ip-address>:5555
```

```bash
adb install -r release/AkaaFlixLauncher-v1.4.0-release.apk
```

Updating from v1.0.1 keeps all settings (same signing key): the old single app list becomes the Home
screen list, Favorites start empty, Home switches to three rows, the icon size becomes Medium, and a
removed wallpaper falls back to the Egyptian Pyramids default.

## Making it the default launcher

Inside the launcher: **Settings → Interface settings → Set as default launcher** (system role dialog on
Android 10+, Home settings on older/TV devices) and **Default launcher setup guide** (step-by-step text in
Arabic, English and French). Once Akaa Flix is the default Home app, Android starts it after every boot and
the remote Home button returns to it. Akaa Flix uses only Android's official HOME role / Home setting for
this; it does not take over the Home key in any other way.

| Device | How |
|---|---|
| Phone / tablet (Android 6–9) | Press Home → choose **Akaa Flix Launcher** → *Always*. Or Settings → Apps → Default apps → Home app. |
| Phone / tablet (Android 10+) | Settings → Apps → Default apps → Home app, or in the launcher: Settings → Interface settings → *Set as default launcher* (opens the system role dialog). |
| Android TV (AOSP / most TV boxes) | Press Home and pick the launcher when asked, or Settings → Apps → Default apps / Home (names vary by vendor). |
| Google TV (Xiaomi TV Box, Chromecast with Google TV, recent Sony/TCL Google TV) | Google TV does **not** offer a home-app chooser. In the launcher: Settings → Interface settings → *Set as default launcher*; when Android shows nothing, choose *Other ways* → the HOME role is granted through Shizuku or with the ADB command below, then verified. |

Google TV via ADB (advanced – this disables Google's launcher for the current user; re-enable with the second command):

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
```

Google TV runs Android 10 or newer, where only the default Home app is started automatically after a restart
(the launcher's *Start with device* option applies to Android 6–9). The system Home button keeps opening
Google's launcher unless it is disabled as above; this is an operating-system restriction a normal app cannot
bypass.

## Xiaomi / Google TV boxes (no Home chooser)

1. Install the APK and open Akaa Flix once from the apps row.
2. Settings → Interface settings → **Home app & start-up** shows what Android reports.
3. **Set as default launcher** asks Android for the HOME role. Google TV usually shows no screen; the launcher
   then says so and offers **Other ways**: Android's Home settings where they exist, otherwise **Make default
   with Shizuku** (after starting Shizuku), or from a computer / an ADB app:

```bash
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.akaaflix.launcher
```

4. If the Google TV launcher still opens on Home, disable it (undo with `pm enable`):

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

5. Reboot once: the start-up row then shows "Verified" when Android opened Akaa Flix after the restart.

## Backup and restore

About → **Backup settings** writes the backup itself – it does not need a file picker (Google TV has none; its
placeholder only says "You don't have an app that can do this"):

- a copy inside the launcher (restore at any time on this device, kept across updates);
- `Download/AkaaFlix/AkaaFlixLauncher-backup-<date>.json` (Android 10+; Android 6–9: the launcher's folder
  under `Android/data`) – copy it to move the settings to another device.

About → **Restore settings from backup** lists those backups by date. Where a document picker or a file
manager is installed, *Other file…* opens any backup file. The PIN is never stored in a backup.

Evidence script: `./tools/device_check.sh` (read-only by default). Full test list: `docs/XIAOMI_TEST_PLAN.md`.

## Shizuku (optional advanced app management)

Shizuku lets the App manager run `pm` / `cmd` commands with ADB rights **after** the user allows it in
Shizuku's own dialog: install Shizuku, start it (Wireless debugging on Android 11+, or
`adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`), then
Settings → App management → App manager → Advanced access → Allow. Without root it must be started again after
every reboot. Android 6.0 is not supported by the Shizuku library – the App manager shows ADB commands there.

## Pre-installed (system) apps

A normal launcher can uninstall only apps the user installed (Android shows its own confirmation). The App
manager (Settings → App management) lists every package with its real state. Without Shizuku, pre-installed
apps open Android's App info screen (Disable / Uninstall updates appear only if the device allows them) and the
exact ADB command is shown; with Shizuku they can be disabled, enabled, removed for the current user and
restored directly. Critical components (System UI, Settings, package installer, permission controller, Google
Play services, WebView, input methods, mainline modules, the launcher) are information-only.

The same commands from a computer (use only on devices you administer):

```bash
adb shell pm disable-user --user 0 <package>
```

```bash
adb shell pm uninstall -k --user 0 <package>
```

```bash
adb shell cmd package install-existing <package>
```

The first two disable / remove a system app for the main user (reversible with `pm enable` / the third
command or a factory reset). Fleet deployments can use a **Device Owner** app provisioned on a freshly reset
device (`DevicePolicyManager.setApplicationHidden`) or ship Akaa Flix as a privileged system app in OEM
firmware. Akaa Flix Launcher does not declare device-admin or root features.

## Weather

Open-Meteo is used without an API key (free tier: non-commercial use). For commercial distribution set
`akaa.weather.apiKey` (customer endpoint). Any key compiled into an APK can be extracted from it; if the key
must stay secret, route requests through your own server instead. City detection uses Android's Geocoder or,
when the device has no geocoder, BigDataCloud's free client-side reverse-geocoding endpoint (coordinates are
rounded to about 1 km first).

## Optional configuration (`gradle.properties`)

| Property | Default | Purpose |
|---|---|---|
| `akaa.update.repo` | `akaaflix-apps/akaaflix-downloads` | GitHub repository whose releases are checked |
| `akaa.update.tagPrefix` | `launcher-v` | Only releases tagged `launcher-v<version>` are considered |
| `akaa.weather.apiKey` | empty | Open-Meteo customer API key. The free endpoint is for non-commercial use; set a key for commercial distribution |

To publish an update so that *Check for updates* finds it, create a GitHub release in the configured repository
with a tag like `launcher-v1.1.0` and attach the APK.

## Google Play Protect

v1.0.0 was blocked by Play Protect's enhanced fraud protection because it contained a notification listener.
v1.0.1 removed it. v1.1.0 adds only the optional approximate-location permission for the weather city (asked
at runtime, never at startup) – not one of the capabilities enhanced fraud protection blocks (SMS,
notification access, accessibility) (docs/SECURITY_AUDIT.md). Play Protect may still show its
standard prompt for apps from developers it does not know yet; that is expected for sideloaded apps and must
not be worked around by disabling Play Protect. See the recommendations in the audit (developer verification,
store distribution).
