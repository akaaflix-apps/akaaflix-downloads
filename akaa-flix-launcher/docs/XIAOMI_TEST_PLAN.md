# Real-device test plan – Xiaomi TV Box (MiTV-AFMU0, Android TV 14 / API 34)

These tests need the physical box. The build environment has no device, so **none of the results below
are filled in by the developer**; the launcher itself records evidence (Settings → About → Diagnostics)
and `tools/device_check.sh` prints what Android reports over ADB.

Install: uninstall v1.0.0 if present, then install `release/AkaaFlixLauncher-v1.3.0-release.apk`
(updates v1.0.1 / v1.1.0 / v1.2.0 in place).

| # | Test | How | Expected | Evidence |
|---|---|---|---|---|
| 1 | Play Store shown once | Open All apps, look for Play Store | One entry. Diagnostics → "Duplicate entries hidden" lists the hidden copy (package, user profile, reason) | Diagnostics screenshot + `device_check.sh` section "Packages named like the Play Store" |
| 2 | Real app data | Long-press / Menu on Play Store | Package `com.android.vending`, entry type, installation type | App options screenshot |
| 3 | Default Home status | Settings → Interface → Home app & start-up | Shows the real current Home app (e.g. Google TV) – never "Akaa Flix" unless Android says so | Screenshot + `resolve-activity` / role holder in the script |
| 4 | Become default (official) | "Set as default launcher" | Android's dialog/settings opens if the firmware offers it. On Google TV builds nothing may open (no chooser) – the status stays "not default" | Status row after returning |
| 5 | Become default (Shizuku) | Start Shizuku, App manager → Advanced access → Allow, then "Make default with Shizuku" | Dialog "Verified: Android now reports Akaa Flix…" **or** "Android did not accept…" with the command output | Dialog screenshot + script `--set-default` output |
| 6 | Remote Home button | Open any app, press Home | Akaa Flix appears. Diagnostics → "Last Home button / HOME intent" shows the time | Diagnostics + script "Home key test" (`topResumedActivity … com.akaaflix.launcher`) |
| 7 | Start-up after reboot | Reboot the box with Akaa Flix default | Akaa Flix is the first screen. Status row: "Verified: Android opened Akaa Flix as the Home app m:ss after the last restart" | Status row screenshot |
| 8 | Stock launcher still appearing | If Google TV's launcher still opens: App manager → Google TV Home → Disable (Shizuku), or `adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx` | Home goes to Akaa Flix. Undo: Enable in App manager or `adb shell pm enable com.google.android.apps.tv.launcherx` | Script output |
| 9 | App discovery / launch | Install and uninstall an app; open several apps | List refreshes within ~1 s; apps open | – |
| 10 | Uninstall user app | App options → Uninstall | Android's uninstall confirmation | – |
| 11 | Pre-installed app (no Shizuku) | App manager → a pre-installed app | Disable → App info screen; Remove for this user → exact ADB command | Screenshot |
| 12 | Pre-installed app (Shizuku) | Disable / Enable / Remove for this user / Restore on a non-essential vendor app | "done" toast, filter Disabled / Removed shows it, Restore brings it back | Screenshots |
| 13 | Protected packages | App manager → System UI / Settings / Google Play services | Information only, "Protected", no disable/remove | Screenshot |
| 14 | Remote keys | D-pad Up/Down/Left/Right, OK, Back, Home, long-press OK on cards | Neon focus always visible on the selected card; Back never leaves the launcher; long-press opens app options | Settings → Remote control → Remote button test |
| 15 | Memory / long run | Leave on Home 1 h, navigate, open apps | No crash, smooth focus; `dumpsys meminfo com.akaaflix.launcher` PSS stays stable | Script "Memory" before/after |
| 16 | Home button service listed | Android Settings → Accessibility → Services | "Akaa Flix Launcher" appears (Off), like Monet | Photo |
| 17 | Manual activation | Akaa Flix Launcher → Enable → OK on Android's warning | Switch stays on. If greyed out: run the `appops … ACCESS_RESTRICTED_SETTINGS allow` command from BUILD_AND_INSTALL.md, then retry | Photo |
| 18 | Status detection | Akaa Flix → Settings → Interface → Home app & start-up → Home button service | Shows "On" (and "Off" again after turning it off in Android settings) | Photos of both |
| 19 | Home key with the service | Open any app (YouTube…), press Home | Akaa Flix opens immediately, Google TV Home does not stay on screen. Diagnostics → Home button service → "last launch" updates | Video / photo |
| 20 | Start-up with the service | Restart the box | Akaa Flix comes to the front after Google TV starts (a short flash of Google TV Home is possible). Start-up row: "Verified: the Home button service opened Akaa Flix m:ss after the last restart" | Photo of the row |
| 21 | Service turned off | Turn the service off, press Home | Google TV Home opens normally (nothing intercepted) | – |
| 22 | Shizuku reconnection | Start Shizuku, allow Akaa Flix, then stop Shizuku (or restart) | App manager shows "Shizuku stopped…" and the row becomes "Not running"; after starting Shizuku again, "Check again" → "Shizuku connected" | Photos |

Report back: screenshots of Diagnostics, the Home & start-up rows and the output of
`./tools/device_check.sh` before and after the tests (it also prints the accessibility service state).
