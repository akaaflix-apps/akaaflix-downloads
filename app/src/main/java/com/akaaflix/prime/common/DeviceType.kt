package com.akaaflix.prime.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** The two adaptive experiences the app provides. */
enum class DeviceType { MOBILE, TV }

object DeviceDetector {
    /**
     * Detects whether the app is running on a TV-class device (Android TV, Google TV,
     * TV boxes or other remote-controlled devices without a touchscreen).
     */
    fun detect(context: Context): DeviceType {
        val configUiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (configUiMode == Configuration.UI_MODE_TYPE_TELEVISION) return DeviceType.TV
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return DeviceType.TV
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return DeviceType.TV
        @Suppress("DEPRECATION")
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return DeviceType.TV
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) return DeviceType.TV
        return DeviceType.MOBILE
    }
}
