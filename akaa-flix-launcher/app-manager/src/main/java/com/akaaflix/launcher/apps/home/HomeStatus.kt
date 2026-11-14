package com.akaaflix.launcher.apps.home

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.akaaflix.launcher.prefs.BootLog

/** Real default-Home state read from Android (never assumed). */
data class HomeStatus(
    val isDefault: Boolean,
    /** Android 10+: the HOME role exists and can be requested. */
    val roleAvailable: Boolean,
    val roleHeld: Boolean,
    /** Package Android currently opens for HOME, or null when it would ask (no default chosen). */
    val currentHomePackage: String?,
    val currentHomeLabel: String?,
    /** Every installed app that can act as Home. */
    val candidates: List<Pair<String, String>>,
    /** Google TV's launcher is installed and enabled (Google TV offers no home-app chooser). */
    val googleTvLauncher: Boolean,
) {
    companion object {
        const val GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"
        private val HOME_INTENT get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        fun read(context: Context): HomeStatus {
            val c = context.applicationContext
            val pm = c.packageManager
            val own = c.packageName
            var roleAvailable = false; var roleHeld = false
            if (Build.VERSION.SDK_INT >= 29) try {
                val rm = c.getSystemService(RoleManager::class.java)
                roleAvailable = rm?.isRoleAvailable(RoleManager.ROLE_HOME) == true
                roleHeld = rm?.isRoleHeld(RoleManager.ROLE_HOME) == true
            } catch (_: Exception) { }
            val resolved = try { pm.resolveActivity(HOME_INTENT, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo } catch (_: Exception) { null }
            // "android" = the system chooser (ResolverActivity): no default selected.
            val current = resolved?.packageName?.takeIf { it != "android" }
            val label = current?.let { p -> try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (_: Exception) { p } }
            val candidates = try {
                pm.queryIntentActivities(HOME_INTENT, 0).mapNotNull { ri ->
                    val p = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (p == "android" || ri.activityInfo.name.contains("FallbackHome")) null else p to (ri.loadLabel(pm)?.toString() ?: p)
                }.distinctBy { it.first }
            } catch (_: Exception) { emptyList() }
            val gtv = try { pm.getApplicationInfo(GOOGLE_TV_LAUNCHER, 0).enabled } catch (_: Exception) { false }
            return HomeStatus(roleHeld || current == own, roleAvailable, roleHeld, current, label, candidates, gtv)
        }
    }
}

/** Start-up state shown to the user; only the VERIFIED* states claim that it worked. */
enum class StartupState {
    /** Default Home + Android opened Akaa Flix as Home right after the latest boot (observed). */
    VERIFIED,
    /** Default Home, not observed yet (e.g. selected after the last boot) – verified at the next restart. */
    DEFAULT_PENDING,
    /** Not Android's default, but Akaa Flix's accessibility service opened Akaa Flix after this boot (observed). */
    VERIFIED_SERVICE,
    /** Accessibility service on and running; no restart observed with it yet. */
    SERVICE_PENDING,
    /** Accessibility service turned on in Settings but Android has not bound it. */
    SERVICE_NOT_RUNNING,
    /** Android 6–9 fallback ("Start with device") opened the launcher after this boot (observed). */
    VERIFIED_FALLBACK,
    /** Android 6–9 fallback enabled but not observed after this boot (broadcast blocked or not yet restarted). */
    FALLBACK_PENDING,
    /** Not the default Home; the device firmware keeps its own launcher (Google TV): extra steps required. */
    MANUFACTURER_STEPS,
    /** Not the default Home: Android opens the other Home app at start-up. */
    NOT_DEFAULT,
}

object Startup {
    /** Home start observed within this time after boot counts as the start-up launch. */
    const val STARTUP_WINDOW_MS = 10 * 60 * 1000L

    fun evaluate(home: HomeStatus, log: BootLog.Snapshot, sdkInt: Int, autoStartEnabled: Boolean, access: AccessState = AccessState.OFF): StartupState = when {
        home.isDefault && log.homeStartThisBoot && log.homeStartAtElapsedMs in 0..STARTUP_WINDOW_MS -> StartupState.VERIFIED
        home.isDefault -> StartupState.DEFAULT_PENDING
        access != AccessState.OFF && log.serviceStartThisBoot && log.serviceStartAtElapsedMs in 0..STARTUP_WINDOW_MS -> StartupState.VERIFIED_SERVICE
        access == AccessState.ON_RUNNING -> StartupState.SERVICE_PENDING
        access == AccessState.ON_NOT_RUNNING -> StartupState.SERVICE_NOT_RUNNING
        sdkInt < 29 && autoStartEnabled && log.fallbackLaunchThisBoot -> StartupState.VERIFIED_FALLBACK
        sdkInt < 29 && autoStartEnabled -> StartupState.FALLBACK_PENDING
        home.googleTvLauncher -> StartupState.MANUFACTURER_STEPS
        else -> StartupState.NOT_DEFAULT
    }
}
