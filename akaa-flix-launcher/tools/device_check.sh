#!/usr/bin/env bash
# Akaa Flix Launcher – device evidence collector (run on a computer with `adb` connected to the TV box).
#
#   ./tools/device_check.sh                 read-only report (default)
#   ./tools/device_check.sh --set-default   also asks Android to make Akaa Flix the HOME role holder
#   ./tools/device_check.sh --restore-stock re-enables Google TV's launcher if it was disabled
#
# The report shows what Android itself says (packages, user profiles, Home resolution, role holder,
# Home-key result, memory) so Play Store duplicates, default-launcher state and start-up behaviour can
# be checked on the real device, e.g. Xiaomi TV Box (MiTV-AFMU0, Android 14).
set -u
PKG=com.akaaflix.launcher
GTV=com.google.android.apps.tv.launcherx

adb get-state >/dev/null 2>&1 || { echo "No device: connect with 'adb connect <tv-ip>:5555' first."; exit 1; }
sh() { adb shell "$@" 2>&1 | tr -d '\r'; }
SDK=$(sh getprop ro.build.version.sdk)

echo "== Device"
for p in ro.product.manufacturer ro.product.model ro.build.version.release ro.build.version.sdk ro.product.cpu.abilist ro.build.characteristics; do
  printf '%-28s %s\n' "$p" "$(sh getprop $p)"
done

echo; echo "== User profiles (a second profile can expose a copy of the Play Store)"
sh pm list users

echo; echo "== Akaa Flix"
sh dumpsys package $PKG | grep -E "versionName|versionCode|firstInstallTime|lastUpdateTime" | head -4

echo; echo "== Packages named like the Play Store"
sh pm list packages -f --user all 2>/dev/null | grep -i -E "vending|playstore|play\.store|store" || sh pm list packages -f | grep -i -E "vending|store"

echo; echo "== Launcher entries for those packages (TV row / phone launcher)"
sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER | grep -i -E "vending|store"
sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | grep -i -E "vending|store"

echo; echo "== Home apps and the current default"
sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
echo "-- resolves to:"; sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
if [ "${SDK:-0}" -ge 29 ]; then echo "-- HOME role holder:"; sh cmd role get-role-holders android.app.role.HOME; fi
echo "-- Google TV launcher enabled?"; sh pm list packages -e | grep -q "$GTV" && echo yes || echo "no / not installed"

case "${1:-}" in
  --set-default)
    echo; echo "== Asking Android to make Akaa Flix the default Home"
    if [ "${SDK:-0}" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME $PKG
    else sh cmd package set-home-activity --user 0 $PKG/$PKG.MainActivity; fi
    sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 ;;
  --restore-stock)
    echo; echo "== Re-enabling the Google TV launcher"; sh pm enable $GTV ;;
esac

echo; echo "== Akaa Flix backups (Download/AkaaFlix)"
sh ls -l /sdcard/Download/AkaaFlix 2>/dev/null || echo "none yet"

echo; echo "== Home key test"
sh input keyevent KEYCODE_HOME; sleep 3
sh dumpsys activity activities | grep -m2 -E "topResumedActivity|mResumedActivity"

echo; echo "== Memory used by Akaa Flix (PSS)"
sh dumpsys meminfo $PKG | grep -m1 -E "TOTAL PSS|TOTAL:"

echo; echo "== Shizuku"
sh pm list packages | grep -i shizuku || echo "Shizuku not installed"

echo; echo "Done. Compare with Akaa Flix → Settings → About → Diagnostics."
