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
./gradlew assembleDebug
```

```bash
./gradlew assembleRelease
```

```bash
./gradlew test
```

Outputs:

| Variant | Path | Application id |
|---|---|---|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | `com.akaaflix.launcher.debug` |
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
python3 tools/generate_icons.py
```

`tools/strings_source.py` is the single source of every English/Arabic string. Brand assets and the
procedural wallpapers can be rebuilt with `tools/extract_logo.py`, `tools/generate_brand_assets.py` and
`tools/generate_wallpapers.py` (Python 3, numpy, Pillow, scipy) from the images in `docs/reference/`.

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

The release key used for `release/AkaaFlixLauncher-v1.0.1-release.apk` (PKCS12, RSA 4096, valid until 2056,
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
> before installing v1.0.1.

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
adb install -r release/AkaaFlixLauncher-v1.0.1-release.apk
```

## Making it the default launcher

| Device | How |
|---|---|
| Phone / tablet (Android 6–9) | Press Home → choose **Akaa Flix Launcher** → *Always*. Or Settings → Apps → Default apps → Home app. |
| Phone / tablet (Android 10+) | Settings → Apps → Default apps → Home app, or in the launcher: Settings → Interface settings → *Set as default launcher* (opens the system role dialog). |
| Android TV (AOSP / most TV boxes) | Press Home and pick the launcher when asked, or Settings → Apps → Default apps / Home (names vary by vendor). |
| Google TV (Chromecast with Google TV, recent Sony/TCL Google TV) | Google TV does **not** offer a home-app chooser. Open Akaa Flix Launcher from the apps row, or use ADB as below. |

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

## Optional configuration (`gradle.properties`)

| Property | Default | Purpose |
|---|---|---|
| `akaa.update.repo` | `akaaflix-apps/akaaflix-downloads` | GitHub repository whose releases are checked |
| `akaa.update.tagPrefix` | `launcher-v` | Only releases tagged `launcher-v<version>` are considered |
| `akaa.weather.apiKey` | empty | Open-Meteo customer API key. The free endpoint is for non-commercial use; set a key for commercial distribution |

To publish an update so that *Check for updates* finds it, create a GitHub release in the configured repository
with a tag like `launcher-v1.0.1` and attach the APK.

## Google Play Protect

v1.0.0 was blocked by Play Protect's enhanced fraud protection because it contained a notification listener.
v1.0.1 removes it and requests no sensitive permission (docs/SECURITY_AUDIT.md). Play Protect may still show its
standard prompt for apps from developers it does not know yet; that is expected for sideloaded apps and must
not be worked around by disabling Play Protect. See the recommendations in the audit (developer verification,
store distribution).
