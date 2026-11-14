package com.akaaflix.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.akaaflix.launcher.prefs.BootLog
import com.akaaflix.launcher.prefs.BootPrefs

/**
 * "Start with device": opens the launcher after boot when the user enabled it and another app is
 * the default Home. Only Android 6–9 allow this; Android 10+ blocks activity starts from the
 * background, and the launcher deliberately does not request the "Display over other apps"
 * permission that would bypass that. Only the protected BOOT_COMPLETED broadcast is accepted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Evidence for the start-up status screen (all versions): the broadcast reached the launcher.
        BootLog.recordBootBroadcast(context)
        // Android 10+ only starts the default Home app; a broadcast receiver cannot (and must not try to) open an activity.
        if (Build.VERSION.SDK_INT >= 29) return
        if (!BootPrefs.autoStart(context) || isDefaultHome(context)) return
        try {
            // singleTask activity + CLEAR_TOP: a second boot broadcast never creates a duplicate launcher.
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            BootLog.recordFallbackLaunch(context)
        } catch (_: Exception) {}
    }

    private fun isDefaultHome(context: Context): Boolean = try {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    } catch (_: Exception) { false }
}
