# Security & Google Play Protect audit — Akaa Flix Launcher

| | |
|---|---|
| Audited builds | v1.0.0 (versionCode 1, blocked by Play Protect) → v1.0.1 (versionCode 2) → **v1.1.0** (versionCode 3, §11) |
| Date | 14 Nov 2026 |
| Package | `com.akaaflix.launcher` |
| Release APK | v1.0.1: SHA-256 `1c32ca926bd5d1fb7ca3dd42888de2f75093c100e76c10e1b2a11a7918fa618d` (Git history) · **v1.1.0: `release/AkaaFlixLauncher-v1.1.0-release.apk` — SHA-256 `e9117920b29421424ddb7b96a8f8129f1a585201db2a76884e82382a3357925c`** |
| Signing certificate | `CN=Akaa Flix Launcher, O=Akaa Flix, C=EG`, RSA 4096, SHA-256 `8ee47c472321a9f0045b3193f1d70bbb2df0893ed92f65833d8635a763873cdf` |
| Method | `aapt2` manifest/permission dump, `apksigner`, `zipalign`, `dexdump` scan of the release DEX de-obfuscated with the R8 mapping, Gradle dependency tree, Robolectric test matrix |

## 1. The warning and its cause

The installer showed **"App blocked to protect your device — This app can request access to sensitive data.
This can increase the risk of identity theft or financial fraud."**

That dialog matches Google Play Protect's **enhanced fraud protection**. It blocks apps installed from
"internet sideloading" sources (browsers, messaging apps, file managers) when they use any of four
capabilities that are frequently abused for financial fraud: **RECEIVE_SMS, READ_SMS, notification-listener
access, or Accessibility services**.

**v1.0.0 declared exactly one of these:** a `NotificationListenerService`
(`com.akaaflix.launcher.device.notifications.LauncherNotificationListener`), exported and protected by
`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`. It powered the optional notifications list behind the
bell button on Home. It had no SMS or Accessibility component. This listener is therefore the most likely cause,
and it has been removed.

Confidence: high, but not confirmed on a device. Play Protect decides on Google's servers; the build
environment cannot run Play Protect, and Google's developer page about Play Protect warnings could not be opened
from the build environment (network policy). The conclusion rests on the exact wording of the dialog,
independent reporting on enhanced fraud protection, and the fact that v1.0.0 contained one of the listed
capabilities while v1.0.1 contains none.

## 2. Permissions — before and after

| Permission | v1.0.0 | v1.0.1 | Type | Reason / decision |
|---|---|---|---|---|
| Notification listener (`BIND_NOTIFICATION_LISTENER_SERVICE` service) | declared | **removed** | special access, fraud-protection trigger | Not essential. The bell now opens Android's own notification screen; the launcher no longer reads notifications. |
| `SYSTEM_ALERT_WINDOW` ("Display over other apps") | declared | **removed** | special access, often abused by malware | Only served "Start with device" on Android 10+. Removed; that option now works on Android 6–9, and on 10+ the user is guided to set the launcher as default Home. |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` | declared | **removed** | runtime (photos/videos) | Wallpaper import now uses the system photo picker / document picker, which grant access to one chosen file. |
| `READ_EXTERNAL_STORAGE` (≤ Android 12) | declared | **removed** | runtime (storage) | Same as above. |
| `RECEIVE_BOOT_COMPLETED` | ✓ | ✓ | normal | "Start with device" on Android 6–9. |
| `REQUEST_DELETE_PACKAGES` | ✓ | ✓ | normal | "Uninstall" in the app menu opens the system uninstall confirmation (same as AOSP Launcher3). The user always confirms. |
| `INTERNET` | ✓ | ✓ | normal | Opt-in weather widget and user-initiated update check. |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | ✓ | ✓ | normal | Wi-Fi / Ethernet status icon and signal level. |
| `BLUETOOTH` (maxSdk 30) | ✓ | ✓ | normal | Bluetooth on/off icon on Android 6–11. |
| `com.akaaflix.launcher.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | ✓ | ✓ | signature (own app) | Added by AndroidX Core so dynamically registered receivers stay private on Android < 13. |

**v1.0.1 requests no runtime ("dangerous") permission and no special-access permission.** It requests no
location, camera, microphone, contacts, SMS, phone, call log, storage, notification access, accessibility,
overlay, usage access, device admin or install-packages permission.

## 3. Components (merged release manifest)

| Component | Exported | Protection | Notes |
|---|---|---|---|
| `MainActivity` | yes | — | Required: `MAIN/HOME/DEFAULT` (home app) and `MAIN/LAUNCHER/LEANBACK_LAUNCHER` intent filters. `singleTask`, excluded from recents. |
| `BootReceiver` | yes | protected system broadcast | Accepts only `BOOT_COMPLETED`, which only the system can send. The non-protected `QUICKBOOT_POWERON` action, which any app could send, was **removed**. Does nothing on Android 10+. |
| `androidx.appcompat.app.AppLocalesMetadataHolderService` | no | disabled | AndroidX per-app language storage. |
| `androidx.startup.InitializationProvider` | no | — | AndroidX start-up (emoji2, lifecycle, profile installer). |
| `androidx.profileinstaller.ProfileInstallReceiver` | yes | `android.permission.DUMP` | Only the shell/system can call it (ART profile installation, standard AndroidX). |
| ~~`LauncherNotificationListener`~~ | — | — | **Removed in v1.0.1.** |

No content providers of our own. No accessibility service, no device-admin receiver, no foreground or
background service, no JobScheduler/WorkManager jobs, no alarms.

Application flags: `allowBackup=false`, `usesCleartextTraffic=false` (HTTPS only), no `debuggable` and no
`testOnly` in the release build. Cloud backup and device transfer are excluded (`data_extraction_rules.xml`).

## 4. Dependencies and third-party code

Release runtime classpath: 87 artifacts, all from **AndroidX**, **Kotlin / kotlinx.coroutines** (JetBrains),
**Okio** (Square, used by DataStore), `org.jspecify` annotations and Guava's `listenablefuture` stub.
**There are no analytics, advertising, crash-reporting, attribution or networking SDKs, and no closed-source
binaries.**

Native code: AndroidX DataStore bundles `libdatastore_shared_counter.so`, which only multi-process DataStore uses.
The launcher uses single-process DataStore, the release DEX contains no reference to the library and no
`System.loadLibrary` call, and v1.0.1 excludes it from packaging. **v1.0.1 contains no native code.**

## 5. Code scan of the release DEX (`dexdump`, de-obfuscated with the R8 mapping)

| Capability | v1.0.0 | v1.0.1 | Where |
|---|---|---|---|
| SMS APIs | 0 | 0 | — |
| Accessibility service | 0 | 0 | — |
| Notification listener | 7 | **0** | was our `LauncherNotificationListener` |
| Reading secure settings (`Settings.Secure`) | 1 | **0** | was the notification-access check |
| Dynamic code loading (`DexClassLoader`, in-memory DEX) | 0 | 0 | — |
| Shell commands (`Runtime.exec`, `ProcessBuilder`) | 0 | 0 | — |
| `System.loadLibrary` / native code | 0 | 0 | — |
| WebView | 0 | 0 | — |
| Device identifiers (IMEI, serial, Android ID) | 0 | 0 | — |
| Accounts, contacts, call log, camera, microphone | 0 | 0 | — |
| Overlay windows, package installation, device admin | 0 | 0 | — |
| Enumerating all installed packages (`getInstalledPackages`) | 0 | 0 | Launchable apps only, via `LauncherApps` / `<queries>` intents |
| Location | 2 | 2 | AndroidX AppCompat auto night mode (`TwilightManager`). It checks for location permission first; the app declares none, so this path can never read location. |
| Clipboard read | 1 | 1 | AndroidX `AppCompatEditText` — paste in the search field, only when the user chooses Paste. |
| Reflection | 67 | 67 | Entirely in AndroidX / Kotlin / DataStore-protobuf internals; none in launcher code. |
| HTTP (`URL.openConnection`) | 2 | 2 | Weather and update check — our code, HTTPS only. |

Network endpoints in the APK: `https://api.open-meteo.com`, `https://geocoding-api.open-meteo.com`,
`https://customer-api.open-meteo.com` (weather, only after the user enables it and types a city) and
`https://api.github.com/repos/…` (only when the user presses *Check for updates*, or weekly if the user enables it).
No data about the user or the device is sent: weather requests carry only the coordinates of the city the user
typed; the update request carries nothing beyond a `User-Agent` with the app version.

## 6. Signing and integrity

- A new **persistent** release key was generated outside the repository: PKCS12, RSA 4096, SHA256withRSA, valid
  until 13 Nov 2056 (alias `akaaflix-launcher`). The keystore and its password are delivered to the owner and are
  not in Git (`*.jks`, `*.keystore`, `*.p12` are ignored).
- Release signing reads the key only from external properties or environment variables (`app/build.gradle.kts`).
- Schemes explicitly enabled: **v1** (required by Android 6.0), **v2** (Android 7+), **v3** (Android 9+, enables
  future key rotation).
- `apksigner verify -v`: **Verifies**, v1 true, v2 true, v3 true; 4096-bit RSA signer.
- `zipalign -c -P 16 4`: OK.
- `apksigner` lists four META-INF metadata entries as "not protected by signature". These are only outside the
  **v1** JAR signature (build metadata and service-loader index files that AGP always adds); v2/v3 protect the
  entire APK on Android 7+.
- v1.0.0 was signed with a throwaway key that no longer exists. v1.0.1 uses the new key, so a device holding
  v1.0.0 must uninstall it first. No device installed v1.0.0, because Play Protect blocked it.

## 7. Android 6 → 17 compatibility (v1.0.1)

| Check | Result |
|---|---|
| minSdk / targetSdk | 23 / 37 |
| v1 signature (Android 6.0 installs) | present, verified |
| Unit tests (42) | pass |
| Integration tests (HOME registration, app discovery, D-pad routing, all pages, Back/Home, settings persistence, crash guard) | pass on API **23, 24, 26, 28, 29, 30, 31, 33, 34, 35, 36, 37** (Robolectric; API 23 with Robolectric 4.14.1) |
| Screenshot renders (TV Arabic/English, phone) | pass, design unchanged |

Robolectric runs the real framework code of each Android version on the JVM; it is not a substitute for testing
on physical devices (see COMPATIBILITY.md).

## 8. Functional changes made for security (design unchanged)

1. **Bell button (Home):** opens the system notification screen instead of an in-launcher list: the Android TV
   notification panel where the system allows it, else notification history (Android 11+), else notification
   settings. The notification-count badge was removed (it required reading notifications). Settings →
   Notifications now offers the system shortcut and explains the privacy choice.
2. **"Start with device":** Android 6–9 only. On Android 10+ the launcher offers to become the default Home app,
   which is the supported way to open after every restart.
3. **Wallpaper import:** system photo picker (Android 11+, or older devices with the Google Play services backport),
   then the system document picker, then any file manager that handles `GET_CONTENT`. On a TV box with none of
   these, the launcher explains that a file-manager app is needed. The built-in gallery, which needed storage
   permissions, was removed.

## 9. What this audit does not prove

- It does **not** prove the app is "safe" in absolute terms; it documents the evidence above.
- Play Protect's final decision is made by Google on the device. v1.0.1 should no longer meet the
  enhanced-fraud-protection criteria described in section 1, but this could not be tested from the build
  environment. Please install it on the TV and report the result.
- Play Protect may still show its **standard** prompt for apps from unknown developers (e.g. "Unsafe app" or
  "Send app for scanning", with an option to install). That is expected for any sideloaded app with a new signing
  certificate. It is not the fraud-protection block, and it was not bypassed or suppressed.

## 10. Recommendations

1. Back up the keystore and password in two safe places (password manager plus offline copy). Every future update
   must be signed with this key.
2. **Android developer verification:** since 30 Sep 2026, certified Android devices in Brazil, Indonesia,
   Singapore and Thailand only install apps from verified developers through the normal flow, and Google plans to
   extend this to all certified devices in 2027. Register the developer identity, package name
   `com.akaaflix.launcher` and this signing certificate in the Android Developer Console before distributing
   widely.
3. For the widest trust, distribute through Google Play (or another store). If Play Protect keeps warning about
   v1.0.1, use Google's Play Protect appeal process for developers. Do not ask users to disable Play Protect.
4. Keep `docs/SECURITY_AUDIT.md` in step with the manifest when features are added. Any future feature needing
   notification access, Accessibility or SMS will trigger enhanced fraud protection again.

## 11. v1.1.0 security review (feature update)

Same method as above, applied to `release/AkaaFlixLauncher-v1.1.0-release.apk` (7,054,902 bytes,
SHA-256 `e9117920b29421424ddb7b96a8f8129f1a585201db2a76884e82382a3357925c`).

**Signing / integrity** – `apksigner verify`: Verifies, v1 + v2 + v3 true, signer
`CN=Akaa Flix Launcher, O=Akaa Flix, C=EG`, RSA 4096, certificate SHA-256
`8ee47c472321a9f0045b3193f1d70bbb2df0893ed92f65833d8635a763873cdf` (same persistent key as v1.0.1, so it updates
in place). `zipalign -c -P 16 4`: OK. Native libraries: none. minSdk 23, targetSdk 37, locales en/ar/fr.

**Permissions (aapt2)** – v1.0.1 list plus one optional runtime permission:

| Permission | Type | Use |
|---|---|---|
| `ACCESS_COARSE_LOCATION` | runtime (dangerous), **optional** | "Detect my location" for the weather city. Requested only after the user taps it and reads an explanation (AR/EN/FR); one approximate fix (last known ≤ 6 h or a single `getCurrentLocation`), no listener kept, no background location (`ACCESS_BACKGROUND_LOCATION` not declared), coordinates rounded to 0.01° (≈ 1 km) before use or storage; only city name + rounded coordinates are stored. Denying it leaves manual city search. |

Unchanged: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `BLUETOOTH` (≤ API 30, status in Settings
only), `REQUEST_DELETE_PACKAGES`, `RECEIVE_BOOT_COMPLETED` (Android 6–9 auto-start; also keeps the persisted
weather job after reboot), androidx `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature). Still **no** SMS,
notification-listener, accessibility, overlay, storage/media, contacts, camera, microphone, usage-access or
device-admin permission. Removed with the voice button: the `RECOGNIZE_SPEECH` package query and the optional
microphone feature. Added queries: `VIEW` + `BROWSABLE` + `https` (About → official website).

**Components** – v1.0.1 components plus `WeatherRefreshJob` (`JobService`, `exported=false`, protected by
`android.permission.BIND_JOB_SERVICE`, so only the system's JobScheduler can bind it). No other new component.

**DEX scan** (`dexdump` of the release DEX, same patterns as §5):

| Pattern | v1.0.1 | v1.1.0 | Notes |
|---|---|---|---|
| SMS, accessibility, notification listener, dynamic code loading, shell exec, native code, WebView, device identifiers, accounts, contacts, camera/microphone, overlay, PackageInstaller, installed-package enumeration, device admin | 0 | 0 | |
| Location (`getLastKnownLocation` / `requestLocationUpdates`) | 2 | 5 | +3: the weather city detection (`ApproxLocation`) and `LocationManagerCompat.getCurrentLocation` (single update, removed after the first fix/timeout). The 2 existing hits are AppCompat's auto-night helper, which the launcher never enables. R8 merges small lambda classes, so one hit carries a merged class name in the mapping |
| Geocoder `getFromLocation` | 0 | 1 | city name for the detected coordinates (device geocoder) |
| HTTP (`URL.openConnection`) | 2 | 2 | `WeatherRepository` and `UpdateChecker` |
| Clipboard read / reflection | 1 / 67 | 1 / 67 | unchanged (AppCompat text paste / AndroidX internals) |

**Network endpoints in the code** – `api.open-meteo.com`, `customer-api.open-meteo.com` (only with an API key),
`geocoding-api.open-meteo.com` (city search), `api.bigdatacloud.net/data/reverse-geocode-client` (city name when
the device has no geocoder; rounded coordinates + UI language only), `api.github.com` (update check). All HTTPS;
cleartext traffic remains disabled. No API key is committed: `akaa.weather.apiKey` is read from Gradle properties
at build time (and, like any key in an APK, is extractable from a distributed APK).

**Data** – Home apps, favourites, settings and the weather cache stay in app-private storage
(`allowBackup=false`, data-extraction rules exclude everything). The PDF wallpapers are bundled resources; no
image is downloaded at runtime.

**Play Protect assessment** – v1.1.0 does not add any capability that enhanced fraud protection targets (SMS,
notification access, accessibility). Approximate location is an ordinary runtime permission that the user must
grant. The installation result on a real device has not been tested (§9 still applies).

## 12. v1.2.0 security review

`release/AkaaFlixLauncher-v1.2.0-release.apk` – 7,108,760 bytes – SHA-256
`38df5a608c7b7cce6d3d492683cbd9dd56eb689e1c6d02906d7f9fba2a291b56`. `apksigner`: Verifies, v1 + v2 + v3, same
persistent RSA-4096 key (certificate SHA-256 `8ee47c47…73cdf`). `zipalign`: OK. Native libraries: none.

**New permissions** – `QUERY_ALL_PACKAGES` (install-time, App manager package list, data stays on the device) and
`moe.shizuku.manager.permission.API_V23` (declared by the Shizuku provider library; only meaningful when the user
installed Shizuku). No new runtime or special-access permission; still no SMS, notification-listener,
accessibility, overlay, device-admin or usage-access permission.

**New components** – `rikka.shizuku.ShizukuProvider` (exported, protected by
`android.permission.INTERACT_ACROSS_USERS_FULL`, which only the system and Shizuku hold; disabled below
Android 7.0) and the Shizuku *user service* `ShellService` (not a manifest component: Shizuku starts it in its
own process with the shell/root identity, only after the user granted permission in Shizuku).

**Code scan delta** – `ProcessBuilder` 0 → 1 (inside `ShellService` only: runs `pm` / `cmd`, binary
allow-list, argv array – no shell interpretation – and package names validated against a strict pattern; an
injection attempt is rejected in a unit test); installed-package enumeration 0 → 1 (`PackageInventory`);
reflection 67 → 68 (Shizuku library). Unchanged: SMS, accessibility, notification listener, dynamic code
loading, native code, WebView, device identifiers, accounts, contacts, camera/microphone, overlay,
PackageInstaller, device admin = 0. Network endpoints unchanged.

**Safety of package actions** – critical packages (core Android, Google Play services, WebView, input methods,
mainline modules, persistent apps, the launcher) are never disabled or removed; pre-installed and Home apps
require an explicit warning that explains how to undo; removal is per-user (`pm uninstall -k --user N`,
restorable with `pm install-existing`). Nothing runs without Shizuku being started by the user.

**Play Protect** – none of the capabilities targeted by enhanced fraud protection (SMS, notification access,
accessibility) were added. Shizuku-based app managers are an established category; the result on the Xiaomi
box has not been observed.

## 13. v1.3.0 security review

`release/AkaaFlixLauncher-v1.3.0-release.apk` – 7,131,169 bytes – SHA-256
`5ccc4726c141e1dadce2803fa46ca27f30a8e166c8e960758a204491ddf60ad4`. `apksigner`: Verifies, v1 + v2 + v3, same persistent RSA-4096 key (certificate SHA-256
`8ee47c47…73cdf`). `zipalign`: OK. Native libraries: none. Only the release APK is delivered.

**New component – accessibility service** `com.akaaflix.launcher.apps.home.HomeAccessibilityService`, added at
the owner's explicit request (Google TV offers no Home-app chooser). Exported only to holders of
`android.permission.BIND_ACCESSIBILITY_SERVICE` (Android itself). Configuration checked inside the APK:
`accessibilityEventTypes=0x20` (window state changed), `accessibilityFlags=0x20` (key filtering),
`canRetrieveWindowContent=false`, `isAccessibilityTool=false`. It acts on `KEYCODE_HOME` only (every other key
returns `false` immediately and is not stored) and on the package/class name of new windows (to recognise
another launcher's Home activity). It stores only launch timestamps (start-up evidence) in private
preferences and has no network access. It is **off until the user turns it on** in Android's Accessibility
settings, where Android shows its own warning.

**Optional Shizuku path** – only after the user started Shizuku, granted Akaa Flix in Shizuku and pressed
"Turn on with Shizuku": `cmd appops set --user N <pkg> ACCESS_RESTRICTED_SETTINGS allow` (Android 13+) and
`cmd settings put --user N secure enabled_accessibility_services <list>` where the list is the current list plus
Akaa Flix, validated against a strict component-list pattern (injection attempts rejected in unit tests), then
`accessibility_enabled 1`. Also new: `pm uninstall --user N <pkg>` for user-installed apps, after a confirmation
that states it cannot be undone.

**Code scan delta (v1.2.0 → v1.3.0)** – accessibility service references 0 → 5 (the service above);
`Settings.Secure.getString` 0 → 2 (both read `enabled_accessibility_services` to show the service state – no
Android ID or other identifier). Unchanged: SMS, notification listener, dynamic code loading, native code,
WebView, accounts, contacts, camera/microphone, overlay, PackageInstaller, device admin = 0; shell exec 1
(Shizuku `ShellService`, `pm`/`cmd` allow-list); network endpoints unchanged.

**Play Protect – changed risk.** v1.0.1 removed every capability that Google Play Protect's enhanced fraud
protection targets in sideloaded apps (SMS, notification listener, accessibility). v1.3.0 deliberately adds an
accessibility service again. In regions where that protection is active, installing the APK from a browser or
file manager can be blocked or warned about; installing over ADB is not affected by that check. Nothing in
the APK hides or disguises the service. On Google Play, an accessibility service that is not an accessibility
tool requires a policy declaration.

