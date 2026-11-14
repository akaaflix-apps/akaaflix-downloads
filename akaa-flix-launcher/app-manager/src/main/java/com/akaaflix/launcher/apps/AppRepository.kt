package com.akaaflix.launcher.apps

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.akaaflix.launcher.apps.model.AppCategory
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.apps.model.AppInstallType
import com.akaaflix.launcher.core.perf.DevicePerformance
import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Discovers launchable apps and keeps the list current.
 *
 * - Phone/tablet apps: LauncherApps.getActivityList for every user profile (work profile aware).
 * - Android TV apps: activities with CATEGORY_LEANBACK_LAUNCHER (many TV apps declare only this).
 *   On TV the leanback activity (and its banner) is preferred.
 * - Package visibility (Android 11+) is satisfied by <queries> for both intents in the manifest;
 *   QUERY_ALL_PACKAGES is not needed.
 * - Changes: PACKAGE_* broadcasts + LauncherApps callbacks, debounced. A small text cache makes
 *   the first frame after boot show apps immediately.
 */
class AppRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val launcherApps = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cacheFile = File(appContext.filesDir, "apps_cache_v1.tsv")
    private val myUserSerial = userManager.getSerialNumberForUser(Process.myUserHandle())
    val isTv: Boolean = DevicePerformance.profile(appContext).isTv

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var refreshJob: Job? = null
    private var started = false

    init {
        scope.launch(Dispatchers.IO) {
            loadCache()?.let { if (_apps.value.isEmpty()) { _apps.value = it; _loaded.value = true } }
            refreshNow()
        }
    }

    /** Registers change listeners (called once from Application.onCreate; lives with the process). */
    fun start() {
        if (started) return
        started = true
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(appContext, packageReceiver, pkgFilter, ContextCompat.RECEIVER_EXPORTED)
        val other = IntentFilter().apply {
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
            addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            if (Build.VERSION.SDK_INT >= 24) {
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            }
        }
        ContextCompat.registerReceiver(appContext, genericReceiver, other, ContextCompat.RECEIVER_EXPORTED)
        try { launcherApps.registerCallback(launcherCallback) } catch (_: Exception) {}
    }

    fun refresh(delayMs: Long = 250) {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(delayMs)
            refreshNow()
        }
    }

    private suspend fun refreshNow() {
        val list = withContext(Dispatchers.IO) { try { query() } catch (_: Exception) { null } } ?: return
        if (list != _apps.value) {
            _apps.value = list
            withContext(Dispatchers.IO) { saveCache(list) }
        }
        _loaded.value = true
    }

    private fun isOwnPackage(pkg: String) = pkg.startsWith("com.akaaflix.launcher")

    private fun query(): List<AppEntry> {
        val byPackage = LinkedHashMap<String, AppEntry>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val list: List<LauncherActivityInfo> = try { launcherApps.getActivityList(null, user) } catch (_: Exception) { emptyList() }
            for (lai in list) {
                val pkg = lai.componentName.packageName
                if (isOwnPackage(pkg)) continue
                val k = "$pkg#$serial"
                if (byPackage.containsKey(k)) continue
                byPackage[k] = entryFrom(pkg, lai.componentName.className, serial, lai.label?.toString(), false,
                    lai.applicationInfo, serial != myUserSerial)
            }
        }
        // Fallback: some devices return an empty LauncherApps list; query PackageManager directly.
        if (byPackage.keys.none { it.endsWith("#$myUserSerial") }) {
            val launcher = try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            } catch (_: Exception) { emptyList() }
            for (ri in launcher) {
                val ai = ri.activityInfo ?: continue
                if (isOwnPackage(ai.packageName) || !ai.enabled || !ai.applicationInfo.enabled) continue
                val k = "${ai.packageName}#$myUserSerial"
                if (!byPackage.containsKey(k)) byPackage[k] = entryFrom(ai.packageName, ai.name, myUserSerial, ri.loadLabel(pm)?.toString(), false, ai.applicationInfo, false)
            }
        }
        val leanback = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        } catch (_: Exception) { emptyList() }
        for (ri in leanback) {
            val ai = ri.activityInfo ?: continue
            if (isOwnPackage(ai.packageName) || !ai.enabled || !ai.applicationInfo.enabled) continue
            val k = "${ai.packageName}#$myUserSerial"
            val existing = byPackage[k]
            if (existing == null || isTv) {
                byPackage[k] = entryFrom(ai.packageName, ai.name, myUserSerial, ri.loadLabel(pm)?.toString() ?: existing?.label,
                    true, ai.applicationInfo, false)
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    private fun entryFrom(pkg: String, cls: String, serial: Long, label: String?, leanback: Boolean,
                          ai: android.content.pm.ApplicationInfo, work: Boolean): AppEntry {
        var install = 0L; var update = 0L; var version = 0L
        if (!work) try {
            val pi = pm.getPackageInfo(pkg, 0)
            install = pi.firstInstallTime; update = pi.lastUpdateTime; version = PackageInfoCompat.getLongVersionCode(pi)
        } catch (_: Exception) {}
        val cleanLabel = (label ?: pkg).replace('\t', ' ').replace('\n', ' ').trim().ifEmpty { pkg }
        val sysCat = if (Build.VERSION.SDK_INT >= 26) ai.category else -1
        return AppEntry(AppEntry.key(pkg, cls, serial), pkg, cls, serial, cleanLabel, leanback, install, update, version,
            AppClassifier.classify(sysCat, pkg, cleanLabel), work, installTypeOf(pkg, ai.flags))
    }

    /** Core packages Android does not let users disable, regardless of flags. */
    private val protectedPackages = setOf("android", "com.android.systemui", "com.android.settings", "com.android.tv.settings",
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.phone", "com.android.shell")

    private fun installTypeOf(pkg: String, flags: Int): AppInstallType = AppRepository.installTypeOf(pkg, flags, protectedPackages)

    // ---- launching -------------------------------------------------------------------------

    fun userFor(entry: AppEntry): UserHandle? = if (entry.userSerial == myUserSerial) Process.myUserHandle()
        else userManager.getUserForSerialNumber(entry.userSerial)

    fun launch(activity: Activity, entry: AppEntry, source: View?): Boolean {
        val bounds = source?.let { v -> IntArray(2).also { v.getLocationOnScreen(it) }.let { Rect(it[0], it[1], it[0] + v.width, it[1] + v.height) } }
        val opts = source?.takeIf { it.width > 0 }?.let { ActivityOptionsCompat.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
        return try {
            if (entry.userSerial == myUserSerial) {
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(if (entry.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
                    .setComponent(entry.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                intent.sourceBounds = bounds
                activity.startActivity(intent, opts)
            } else {
                launcherApps.startMainActivity(entry.component, userFor(entry), bounds, opts)
            }
            true
        } catch (_: Exception) {
            // Fallback: the package's default launch intent (activity may have been renamed by an update)
            try {
                val alt = pm.getLeanbackLaunchIntentForPackage(entry.packageName) ?: pm.getLaunchIntentForPackage(entry.packageName)
                if (alt != null) { activity.startActivity(alt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); refresh(); true } else false
            } catch (_: Exception) { false }
        }
    }

    fun shortcuts(entry: AppEntry): List<ShortcutInfo> {
        if (Build.VERSION.SDK_INT < 25) return emptyList()
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery().setPackage(entry.packageName).setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            launcherApps.getShortcuts(q, userFor(entry) ?: return emptyList()).orEmpty().filter { it.isEnabled }.take(6)
        } catch (_: Exception) { emptyList() }
    }

    fun startShortcut(shortcut: ShortcutInfo, source: View?): Boolean {
        if (Build.VERSION.SDK_INT < 25) return false
        return try {
            val bounds = source?.let { v -> IntArray(2).also { v.getLocationOnScreen(it) }.let { Rect(it[0], it[1], it[0] + v.width, it[1] + v.height) } }
            launcherApps.startShortcut(shortcut, bounds, null); true
        } catch (_: Exception) { false }
    }

    fun uninstallIntent(entry: AppEntry): Intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isDefaultLauncher(): Boolean = try {
        val ri = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        ri?.activityInfo?.packageName == appContext.packageName
    } catch (_: Exception) { false }

    fun hasPlayStore(): Boolean = try { pm.getPackageInfo("com.android.vending", 0); true } catch (_: Exception) { false }

    // ---- change listeners ------------------------------------------------------------------

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) && pkg != null) {
                // Fully uninstalled: drop favourites / order / locks for it.
                SettingsRepository.get(appContext).editApps { c -> c.pruned(c.allKeys().filterNot { AppEntry.packageOfKey(it) == pkg }.toSet()) }
            }
            refresh()
        }
    }

    private val genericReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh(if (intent.action == Intent.ACTION_LOCALE_CHANGED) 0 else 400)
    }

    private val launcherCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
    }

    // ---- cache -----------------------------------------------------------------------------

    private fun saveCache(list: List<AppEntry>) {
        try {
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.bufferedWriter().use { w ->
                w.write("v2\n")
                for (e in list) {
                    w.write(listOf(e.key, e.packageName, e.className, e.userSerial, e.label, if (e.isLeanback) 1 else 0,
                        e.installTime, e.updateTime, e.versionCode, e.autoCategory.id, if (e.isWorkProfile) 1 else 0, e.installType.id).joinToString("\t"))
                    w.write("\n")
                }
            }
            tmp.renameTo(cacheFile)
        } catch (_: Exception) {}
    }

    private fun loadCache(): List<AppEntry>? = try {
        if (!cacheFile.exists()) null else cacheFile.bufferedReader().useLines { lines ->
            val it = lines.iterator()
            if (!it.hasNext() || it.next() != "v2") return@useLines null
            it.asSequence().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 12) null else AppEntry(p[0], p[1], p[2], p[3].toLong(), p[4], p[5] == "1", p[6].toLong(), p[7].toLong(),
                    p[8].toLong(), AppCategory.of(p[9]) ?: AppCategory.OTHER, p[10] == "1", AppInstallType.of(p[11]))
            }.toList()
        }
    } catch (_: Exception) { null }

    companion object {
        /** Pure classification from ApplicationInfo flags (unit tested). */
        fun installTypeOf(pkg: String, flags: Int, protectedPackages: Set<String>): AppInstallType {
            val system = flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
            val updated = flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            val persistent = flags and android.content.pm.ApplicationInfo.FLAG_PERSISTENT != 0
            return when {
                !system && !updated -> AppInstallType.USER
                persistent || pkg in protectedPackages -> AppInstallType.PROTECTED
                updated -> AppInstallType.UPDATED_SYSTEM
                else -> AppInstallType.SYSTEM
            }
        }

        @Volatile private var instance: AppRepository? = null
        fun get(context: Context): AppRepository =
            instance ?: synchronized(this) { instance ?: AppRepository(context).also { instance = it } }
    }
}

private fun com.akaaflix.launcher.prefs.AppCustomization.allKeys(): Set<String> =
    (homeApps + favorites + hidden + customOrder + locked + categories.keys + launchCounts.keys + lastLaunched.keys).toSet()
