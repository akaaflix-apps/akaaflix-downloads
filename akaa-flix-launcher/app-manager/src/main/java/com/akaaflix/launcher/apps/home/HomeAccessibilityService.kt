package com.akaaflix.launcher.apps.home

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.akaaflix.launcher.prefs.BootLog

/**
 * Optional "Home button" service for devices whose firmware keeps its own launcher as Home and
 * offers no Home-app chooser (e.g. Google TV / Xiaomi TV boxes). Turned on by the user in
 * Settings → Accessibility; it does nothing while another app is not involved:
 *  - Home key: when Akaa Flix is not Android's default Home, the key is taken and Akaa Flix opens.
 *  - Another launcher's Home screen appears (boot, voice command, app): Akaa Flix is brought back.
 *  - First connection after a restart: Akaa Flix opens (start-up).
 * When Android itself already opens Akaa Flix for Home, the service leaves every key to Android.
 *
 * Data access is limited to the Home key and the package/class name of new windows
 * (see res/xml/akaa_home_accessibility.xml: no window content, no typed text).
 */
class HomeAccessibilityService : AccessibilityService() {
    private var homeKeyTaken = false
    // -1 = not yet (elapsedRealtime can be small right after boot).
    private var lastLaunchAt = -1L
    private var homes: Map<String, Set<String>> = emptyMap()
    private var homesAt = -1L
    private var ownDefault = false
    private var ownDefaultAt = -1L

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        val firstAfterRestart = BootLog.recordServiceConnected(this)
        if (firstAfterRestart && SystemClock.elapsedRealtime() < HomeRedirect.BOOT_WINDOW_MS && shouldRedirect()) launch(HomeAccess.SOURCE_BOOT)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        val activities = homeActivities()[pkg] ?: return
        if (!HomeRedirect.isHomeWindow(e.className?.toString(), activities)) return
        if (shouldRedirect()) launch(HomeAccess.SOURCE_RETURN)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        return when (event.action) {
            // Decide once per press; the matching UP is always consumed with it.
            KeyEvent.ACTION_DOWN -> { if (event.repeatCount == 0) homeKeyTaken = shouldRedirect(); homeKeyTaken }
            KeyEvent.ACTION_UP -> if (homeKeyTaken) { homeKeyTaken = false; launch(HomeAccess.SOURCE_HOME_KEY); true } else false
            else -> homeKeyTaken
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        // The user turned the service off: the next connection is a new activation, not a restart.
        connected = false
        BootLog.recordServiceDisabled(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() { connected = false; super.onDestroy() }

    /** Only act when Android would open another app for Home and setup is finished. */
    private fun shouldRedirect(): Boolean {
        val provisioned = try { Settings.Global.getInt(contentResolver, Settings.Global.DEVICE_PROVISIONED, 1) == 1 } catch (_: Exception) { true }
        return provisioned && !isOwnDefaultHome()
    }

    private fun isOwnDefaultHome(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (ownDefaultAt < 0 || now - ownDefaultAt > 3_000) {
            ownDefault = try {
                packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName == packageName
            } catch (_: Exception) { false }
            ownDefaultAt = now
        }
        return ownDefault
    }

    /** Home activities of the other installed launchers (fallback/setup homes excluded), cached 30 s. */
    private fun homeActivities(): Map<String, Set<String>> {
        val now = SystemClock.elapsedRealtime()
        if (homesAt >= 0 && now - homesAt < 30_000) return homes
        homes = try {
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                .mapNotNull { it.takeIf { ri -> ri.priority >= 0 }?.activityInfo }
                .filter { it.packageName != packageName && it.packageName != "android" && !it.name.contains("FallbackHome") }
                .groupBy { it.packageName }
                .mapValues { (_, list) -> list.flatMap { a -> listOfNotNull(a.name, a.targetActivity) }.toSet() }
        } catch (_: Exception) { emptyMap() }
        homesAt = now
        return homes
    }

    private fun launch(source: String) {
        val now = SystemClock.elapsedRealtime()
        if (lastLaunchAt >= 0 && now - lastLaunchAt < HomeRedirect.DEBOUNCE_MS) return
        lastLaunchAt = now
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setComponent(ComponentName(packageName, HomeAccess.MAIN_ACTIVITY))
            .putExtra(HomeAccess.EXTRA_SOURCE, source)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            // Allowed from the background: Android exempts services it has bound for accessibility.
            startActivity(intent)
            BootLog.recordServiceLaunch(this, homeKey = source == HomeAccess.SOURCE_HOME_KEY,
                startUp = source == HomeAccess.SOURCE_BOOT || (source == HomeAccess.SOURCE_RETURN && now < Startup.STARTUP_WINDOW_MS))
        } catch (_: Exception) { }
    }

    companion object {
        /** True while Android has this service bound (same process as the settings screen). */
        @Volatile var connected = false
            internal set
    }
}
