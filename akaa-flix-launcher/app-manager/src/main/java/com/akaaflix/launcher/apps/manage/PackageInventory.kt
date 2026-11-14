package com.akaaflix.launcher.apps.manage

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.core.content.pm.PackageInfoCompat
import com.akaaflix.launcher.apps.AppClassifier
import com.akaaflix.launcher.apps.model.AppCategory
import java.io.File

/** How carefully a package must be treated by the app manager. */
enum class Protection {
    /** Normal user app. */
    NORMAL,
    /** Pre-installed or Home app: allowed after an explicit warning (restorable). */
    CAUTION,
    /** Core Android / Google services, input method, the launcher itself: never disabled or removed here. */
    CRITICAL,
}

/** One installed (or removed-for-this-user) package, read from PackageManager. */
data class ManagedPackage(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    /** Installed for the current user (false = removed for this user, still on the system partition). */
    val installed: Boolean,
    val enabled: Boolean,
    val launchable: Boolean,
    val isHomeApp: Boolean,
    val apkBytes: Long,
    /** Launcher category, or null for system components without a launcher entry. */
    val category: AppCategory?,
    val protection: Protection,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
)

object PackageProtection {
    /** Packages whose loss makes the device unusable or unrecoverable without a reset. */
    val CRITICAL = setOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.tv.settings", "com.android.providers.settings",
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.permissioncontroller",
        "com.google.android.permissioncontroller", "com.android.shell", "com.android.phone", "com.android.server.telecom",
        "com.android.inputdevices", "com.android.location.fused", "com.android.externalstorage", "com.android.keychain",
        "com.android.se", "com.android.bluetooth", "com.google.android.bluetooth", "com.android.wifi.resources",
        "com.android.extservices", "com.google.android.ext.services", "com.android.webview", "com.google.android.webview",
        "com.google.android.trichromelibrary", "com.google.android.gms", "com.google.android.gsf", "com.android.certinstaller",
        "com.android.statementservice", "com.android.tv.frameworkpackagestubs", "com.google.android.tv.frameworkpackagestubs",
        "com.android.media.tv.remoteprovider", "com.android.devicelockcontroller", "com.android.ondevicepersonalization.services",
    )
    private val CRITICAL_PREFIXES = listOf(
        "com.android.providers.", "com.android.networkstack", "com.google.android.networkstack", "com.android.server.",
        "com.android.wifi.", "com.google.android.wifi.", "com.android.adservices", "com.google.android.adservices",
        "com.android.connectivity.", "com.google.android.connectivity.", "com.android.permission", "com.google.android.permission",
        "com.android.cellbroadcast", "com.google.android.cellbroadcast", "com.android.modulemetadata", "com.google.android.modulemetadata",
        "com.android.resolv", "com.google.android.resolv", "com.android.tethering", "com.google.android.tethering",
        "com.android.runtime", "com.android.art", "com.google.android.art", "com.android.media.swcodec", "com.android.mediaprovider",
        "com.google.android.mediaprovider", "com.android.os.statsd", "com.google.android.os.statsd", "com.android.uwb",
    )

    fun classify(pkg: String, ownPackage: String, isSystem: Boolean, isPersistent: Boolean, isHomeApp: Boolean, inputMethods: Set<String>): Protection = when {
        pkg == ownPackage || pkg.startsWith("com.akaaflix.launcher") -> Protection.CRITICAL
        pkg in CRITICAL || CRITICAL_PREFIXES.any { pkg.startsWith(it) } -> Protection.CRITICAL
        isPersistent || pkg in inputMethods -> Protection.CRITICAL
        isSystem || isHomeApp -> Protection.CAUTION
        else -> Protection.NORMAL
    }
}

object PackageInventory {
    private val labelCache = HashMap<String, String>()

    @Suppress("DEPRECATION")
    fun load(context: Context): List<ManagedPackage> {
        val c = context.applicationContext
        val pm = c.packageManager
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        val packages: List<PackageInfo> = try { pm.getInstalledPackages(flags) } catch (_: Exception) { emptyList() }
        val homes = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DISABLED_COMPONENTS)
                .mapNotNullTo(HashSet()) { it.activityInfo?.packageName }
        } catch (_: Exception) { emptySet() }
        val imes = try {
            c.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.mapTo(HashSet()) { it.packageName } ?: emptySet()
        } catch (_: Exception) { emptySet<String>() }
        return packages.mapNotNull { pi ->
            val ai = pi.applicationInfo ?: return@mapNotNull null
            val pkg = pi.packageName
            val installed = ai.flags and ApplicationInfo.FLAG_INSTALLED != 0
            val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            val updated = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            val persistent = ai.flags and ApplicationInfo.FLAG_PERSISTENT != 0
            val state = try { pm.getApplicationEnabledSetting(pkg) } catch (_: Exception) { PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
            val enabled = ai.enabled && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            val launchable = installed && (pm.getLaunchIntentForPackage(pkg) != null || pm.getLeanbackLaunchIntentForPackage(pkg) != null)
            val key = pkg + "@" + pi.lastUpdateTime
            val label = labelCache.getOrPut(key) { try { ai.loadLabel(pm).toString().trim().ifEmpty { pkg } } catch (_: Exception) { pkg } }
            val sysCat = if (Build.VERSION.SDK_INT >= 26) ai.category else -1
            ManagedPackage(
                packageName = pkg, label = label, versionName = pi.versionName ?: "", versionCode = PackageInfoCompat.getLongVersionCode(pi),
                isSystem = system, isUpdatedSystem = updated, installed = installed, enabled = enabled, launchable = launchable,
                isHomeApp = pkg in homes, apkBytes = apkBytes(ai),
                category = if (launchable) AppClassifier.classify(sysCat, pkg, label) else null,
                protection = PackageProtection.classify(pkg, c.packageName, system, persistent, pkg in homes, imes),
                firstInstallTime = pi.firstInstallTime, lastUpdateTime = pi.lastUpdateTime,
            )
        }.sortedWith(compareBy<ManagedPackage> { it.label.lowercase() }.thenBy { it.packageName })
    }

    fun apkBytes(ai: ApplicationInfo): Long =
        (listOf(ai.sourceDir) + (ai.splitSourceDirs?.toList() ?: emptyList())).sumOf { p -> p?.let { try { File(it).length() } catch (_: Exception) { 0L } } ?: 0L }

    enum class Filter { ALL, USER, SYSTEM, DISABLED, REMOVED }

    fun filter(list: List<ManagedPackage>, f: Filter, query: String): List<ManagedPackage> {
        val q = query.trim().lowercase()
        return list.filter { p ->
            val byFilter = when (f) {
                Filter.ALL -> p.installed
                Filter.USER -> p.installed && !p.isSystem
                Filter.SYSTEM -> p.installed && p.isSystem
                Filter.DISABLED -> p.installed && !p.enabled
                Filter.REMOVED -> !p.installed
            }
            byFilter && (q.isEmpty() || p.label.lowercase().contains(q) || p.packageName.lowercase().contains(q))
        }
    }
}
