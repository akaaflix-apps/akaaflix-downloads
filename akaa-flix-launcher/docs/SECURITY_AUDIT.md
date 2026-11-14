# Security & Google Play Protect audit — Akaa Flix Launcher

| | |
|---|---|
| Audited builds | v1.0.0 (versionCode 1, blocked by Play Protect) → **v1.0.1** (versionCode 2) |
| Date | 14 Nov 2026 |
| Package | `com.akaaflix.launcher` |
| Release APK | `release/AkaaFlixLauncher-v1.0.1-release.apk` — SHA-256 `1c32ca926bd5d1fb7ca3dd42888de2f75093c100e76c10e1b2a11a7918fa618d` |
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
