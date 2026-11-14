package com.akaaflix.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.robolectric.Shadows.shadowOf

/** Registers fake launchable apps in Robolectric's PackageManager (test data only). */
object TestApps {
    /**
     * Brand-neutral sample apps (test data only – the launcher itself never creates entries; on a
     * device every card comes from PackageManager / LauncherApps).
     */
    val SAMPLE = listOf(
        "com.example.videoplayer" to "Video Player", "com.example.livetv" to "Live TV", "com.example.movies" to "Movies",
        "com.example.series" to "Series", "com.example.music" to "Music", "com.example.radio" to "Radio",
        "com.example.photos" to "Photos", "com.example.browser" to "Browser", "com.example.files" to "Files",
        "com.example.news" to "News", "com.example.weather" to "Weather", "com.example.kids" to "Kids",
        "com.example.sports" to "Sports", "com.example.podcasts" to "Podcasts", "com.example.cooking" to "Cooking",
        "com.example.fitness" to "Fitness", "com.example.calendar" to "Calendar", "com.example.notes" to "Notes",
        "com.example.gallery" to "Gallery", "com.example.downloads" to "Downloads", "com.example.quran" to "Quran",
        "com.example.documentaries" to "Documentaries", "com.example.games" to "Arcade",
    )
    /** Pre-installed sample (FLAG_SYSTEM) used to check system-app handling. */
    const val SYSTEM_SAMPLE = "com.example.vendorsettings"

    /**
     * androidx.core declares and requests a signature permission for not-exported dynamic receivers
     * on Android < 13 (granted automatically at install on devices); Robolectric needs it granted.
     */
    fun grantReceiverPermission(context: Context) {
        shadowOf(context.applicationContext as android.app.Application).grantPermissions(context.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    fun install(context: Context, leanback: Boolean, apps: List<Pair<String, String>> = SAMPLE + (SYSTEM_SAMPLE to "Vendor Settings")) {
        grantReceiverPermission(context)
        val spm = shadowOf(context.packageManager)
        for ((pkg, label) in apps) {
            val sysFlags = if (pkg == SYSTEM_SAMPLE) ApplicationInfo.FLAG_SYSTEM else 0
            val ai = ApplicationInfo().apply { packageName = pkg; name = label; nonLocalizedLabel = label; enabled = true; flags = sysFlags }
            val pi = PackageInfo().apply { packageName = pkg; applicationInfo = ai; firstInstallTime = 1_700_000_000_000L; lastUpdateTime = firstInstallTime; versionName = "1" }
            spm.installPackage(pi)
            val cn = ComponentName(pkg, "$pkg.MainActivity")
            spm.addActivityIfNotPresent(cn)
            val f = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(if (leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER) }
            spm.addIntentFilterForActivity(cn, f)
            spm.getInternalMutablePackageInfo(pkg)?.activities?.forEach { it.nonLocalizedLabel = label; it.enabled = true; it.exported = true }
        }
    }
}
