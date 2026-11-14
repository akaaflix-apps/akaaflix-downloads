package com.akaaflix.launcher.apps.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Akaa Flix's Home-button accessibility service as Android reports it (never assumed). */
enum class AccessState {
    /** Not turned on in Settings → Accessibility. */
    OFF,
    /** Turned on and connected (Android bound the service). */
    ON_RUNNING,
    /** Turned on in Settings but Android has not bound it (e.g. crashed or still starting). */
    ON_NOT_RUNNING,
}

/**
 * Reads and explains the state of [HomeAccessibilityService]. The user turns the service on
 * manually in Settings → Accessibility (Android shows its own warning); Akaa Flix never turns it on
 * by itself. The optional Shizuku path runs the same `settings` command a computer would, only
 * after the user started Shizuku and allowed Akaa Flix in it.
 */
object HomeAccess {
    const val SERVICE_CLASS = "com.akaaflix.launcher.apps.home.HomeAccessibilityService"
    const val MAIN_ACTIVITY = "com.akaaflix.launcher.MainActivity"
    /** Marks HOME intents sent by the service, so they are not counted as Android's own HOME start. */
    const val EXTRA_SOURCE = "com.akaaflix.launcher.extra.HOME_SOURCE"
    const val SOURCE_HOME_KEY = "home_key"
    const val SOURCE_RETURN = "return"
    const val SOURCE_BOOT = "boot"

    fun component(c: Context) = ComponentName(c.packageName, SERVICE_CLASS)

    fun state(c: Context): AccessState = when {
        !enabledInSettings(c) -> AccessState.OFF
        HomeAccessibilityService.connected || boundByAndroid(c) -> AccessState.ON_RUNNING
        else -> AccessState.ON_NOT_RUNNING
    }

    /** Listed in the secure setting Android writes when the user turns the service on. */
    fun enabledInSettings(c: Context): Boolean {
        val list = try { Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) } catch (_: Exception) { null }
        return containsService(list, c.packageName)
    }

    private fun boundByAndroid(c: Context): Boolean = try {
        val am = c.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo?.serviceInfo?.let { s -> s.packageName == c.packageName && s.name == SERVICE_CLASS } == true
        }
    } catch (_: Exception) { false }

    /**
     * Android 13+: an app installed from a file can be kept from turning on accessibility until the
     * user allows "restricted settings". Reads the app-op Android uses for that (own app only).
     */
    fun restrictedSettingsBlocked(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return try {
            val ops = c.getSystemService(AppOpsManager::class.java) ?: return false
            ops.unsafeCheckOpNoThrow("android:access_restricted_settings", Process.myUid(), c.packageName) != AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    /** Accessibility screens to try, most specific first (the details page is Android 11+ and not on every TV). */
    fun settingsIntents(c: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= 30) add(Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, component(c).flattenToString()))
        add(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    // ---- pure helpers (unit tested) -----------------------------------------------------------

    /** Entries of enabled_accessibility_services ("pkg/cls" or "pkg/.cls", separated by ':'). */
    fun parse(list: String?): List<Pair<String, String>> = list.orEmpty().split(':').mapNotNull { raw ->
        val s = raw.trim()
        val i = s.indexOf('/')
        if (i <= 0 || i == s.length - 1) null
        else { val p = s.substring(0, i); val cls = s.substring(i + 1); p to (if (cls.startsWith('.')) p + cls else cls) }
    }

    fun containsService(list: String?, pkg: String) = parse(list).any { it.first == pkg && it.second == SERVICE_CLASS }

    /** New value with Akaa Flix added; every service the user already enabled is kept unchanged. */
    fun withService(list: String?, pkg: String): String {
        val entries = list.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }
        return if (containsService(list, pkg)) entries.joinToString(":") else (entries + "$pkg/$SERVICE_CLASS").joinToString(":")
    }
}

/** Which windows count as "another launcher's Home screen" (pure, unit tested). */
object HomeRedirect {
    /**
     * [cls] is the class Android reports for the new window. Only the Home activity of another
     * launcher qualifies; its other screens (search, settings) and popups are left alone.
     */
    fun isHomeWindow(cls: String?, homeActivities: Set<String>): Boolean = !cls.isNullOrBlank() && cls in homeActivities

    /** Debounce so one Home press or one launcher window never opens Akaa Flix twice. */
    const val DEBOUNCE_MS = 800L
    /** The service's first connection after a restart within this time opens Akaa Flix. */
    const val BOOT_WINDOW_MS = 5 * 60 * 1000L
}
