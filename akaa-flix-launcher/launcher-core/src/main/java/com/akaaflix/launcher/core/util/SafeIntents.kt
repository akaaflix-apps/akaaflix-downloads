package com.akaaflix.launcher.core.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.akaaflix.launcher.core.R

/** Starts the first intent that works. Never throws: launchers must not crash on odd devices. */
object SafeIntents {
    fun start(context: Context, vararg intents: Intent?): Boolean {
        for (intent in intents) {
            if (intent == null) continue
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
            }
        }
        return false
    }

    /** Like [start] and shows a toast when the device offers none of the screens. */
    fun startOrToast(context: Context, vararg intents: Intent?): Boolean {
        val ok = start(context, *intents)
        if (!ok) Toast.makeText(context, R.string.system_setting_unavailable, Toast.LENGTH_LONG).show()
        return ok
    }

    fun action(action: String): Intent = Intent(action)

    fun systemSettings(): Intent = Intent(Settings.ACTION_SETTINGS)

    fun appDetails(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    fun browse(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
}
