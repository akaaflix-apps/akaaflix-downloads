package com.akaaflix.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.akaaflix.launcher.prefs.BootPrefs

/**
 * "Start with device": opens the launcher after boot when the user enabled it and another app is
 * the default Home (e.g. Google TV, where the default launcher cannot be changed in Settings).
 * Android 10+ blocks background activity starts unless "Display over other apps" is granted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        if (!BootPrefs.autoStart(context) || isDefaultHome(context)) return
        if (Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(context)) return
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (_: Exception) {}
    }

    private fun isDefaultHome(context: Context): Boolean = try {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    } catch (_: Exception) { false }
}
