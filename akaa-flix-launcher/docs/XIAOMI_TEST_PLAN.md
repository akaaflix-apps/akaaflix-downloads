# Real-device test plan – Xiaomi TV Box (MiTV-AFMU0, Android TV 14 / API 34)

These tests need the physical box. The build environment has no device, so **none of the results below
are filled in by the developer**; the launcher itself records evidence (Settings → About → Diagnostics)
and `tools/device_check.sh` prints what Android reports over ADB.

Install: uninstall v1.0.0 if present, then install `release/AkaaFlixLauncher-v1.4.0-release.apk`
(updates v1.0.1 – v1.3.0 in place; if v1.3.0 was blocked by Play Protect, just install v1.4.0).

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
| 16 | Installs without Play Protect block | Install v1.4.0 from USB / file manager | Normal install (v1.3.0's block came from the accessibility service, removed in v1.4.0) | Photo if a warning appears |
| 17 | Top-right gear | Press the gear at the top right of Home | Android's own Settings open; Back returns to Akaa Flix | Photo |
| 18 | Icon size | Customization → Icon size | Only "Large" is shown and selected; icons are large on Home / All apps | Photo |
| 19 | Privacy sections | Settings → About Launcher; Settings → Notifications | No "Privacy" header or row, no privacy note | Photos |
| 20 | Backup / restore | About → Backup settings → OK; change e.g. Home rows; About → Restore settings from backup → pick the date → Restore | Backup message shows Download/AkaaFlix/…json; after restore the earlier settings are back | Photos |
| 21 | Default HOME (official) | Interface → Set as default launcher | Either Android's dialog, or the message "Android did not make Akaa Flix the default…" with Other ways → Shizuku / ADB command; after granting: "Verified", Home button opens Akaa Flix | Photos |
| 22 | Shizuku reconnection | Start Shizuku, allow Akaa Flix, then stop Shizuku (or restart) | App manager shows "Shizuku stopped…" and the row becomes "Not running"; after starting Shizuku again, "Check again" → "Shizuku connected" | Photos |

Report back: screenshots of Diagnostics, the Home & start-up rows and the output of
`./tools/device_check.sh` before and after the tests.
