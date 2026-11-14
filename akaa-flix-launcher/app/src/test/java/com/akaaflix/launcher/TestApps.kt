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
    val SAMPLE = listOf(
        "com.akaaflix.player" to "Akaa Player", "com.google.android.youtube.tv" to "YouTube", "com.netflix.ninja" to "Netflix",
        "com.disney.disneyplus" to "Disney+", "com.amazon.amazonvideo.livingroom" to "Prime Video", "com.apple.atve.androidtv.appletv" to "Apple TV",
        "com.android.vending" to "Google Play", "com.android.chrome" to "Chrome", "com.example.files" to "Files", "com.android.tv.settings" to "Settings",
        "com.example.miracast" to "Miracast", "org.videolan.vlc" to "VLC", "com.mxtech.videoplayer.ad" to "MX Player", "org.xbmc.kodi" to "Kodi",
        "com.stremio.one" to "Stremio", "com.nst.iptvsmarterstvbox" to "IPTV Smarters", "com.esaba.downloader" to "Downloader",
        "com.ghisler.android.totalcommander" to "Solid Explorer", "org.telegram.messenger" to "Telegram", "com.whatsapp" to "WhatsApp",
        "com.opera.browser" to "Opera", "com.example.airscreen" to "AirScreen", "ru.zdevs.zarchiver" to "ZArchiver",
    )

    /**
     * androidx.core declares and requests a signature permission for not-exported dynamic receivers
     * on Android < 13 (granted automatically at install on devices); Robolectric needs it granted.
     */
    fun grantReceiverPermission(context: Context) {
        shadowOf(context.applicationContext as android.app.Application).grantPermissions(context.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    fun install(context: Context, leanback: Boolean, apps: List<Pair<String, String>> = SAMPLE) {
        grantReceiverPermission(context)
        val spm = shadowOf(context.packageManager)
        for ((pkg, label) in apps) {
            val ai = ApplicationInfo().apply { packageName = pkg; name = label; nonLocalizedLabel = label; enabled = true; flags = 0 }
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
