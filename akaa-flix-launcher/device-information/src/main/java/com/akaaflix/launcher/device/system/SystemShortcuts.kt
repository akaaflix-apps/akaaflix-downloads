package com.akaaflix.launcher.device.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.akaaflix.launcher.core.util.SafeIntents

/**
 * Safe shortcuts to Android system settings screens. A launcher has no permission to change these
 * settings itself, so each entry opens the closest screen the device provides, with fallbacks
 * (Android TV builds often lack phone-only screens).
 */
object SystemShortcuts {
    private fun i(action: String) = Intent(action)

    fun wifi(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_WIFI_SETTINGS), i(Settings.ACTION_WIRELESS_SETTINGS), SafeIntents.systemSettings())
    fun network(c: Context) = SafeIntents.startOrToast(c,
        if (Build.VERSION.SDK_INT >= 29) i(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else null,
        i(Settings.ACTION_WIRELESS_SETTINGS), i(Settings.ACTION_WIFI_SETTINGS), SafeIntents.systemSettings())
    fun vpn(c: Context) = SafeIntents.startOrToast(c, i("android.settings.VPN_SETTINGS"), i(Settings.ACTION_WIRELESS_SETTINGS))
    fun bluetooth(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_BLUETOOTH_SETTINGS), SafeIntents.systemSettings())
    fun inputMethods(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_INPUT_METHOD_SETTINGS), SafeIntents.systemSettings())
    fun date(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_DATE_SETTINGS), SafeIntents.systemSettings())
    fun language(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_LOCALE_SETTINGS), SafeIntents.systemSettings())
    fun display(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_DISPLAY_SETTINGS), SafeIntents.systemSettings())
    fun screensaver(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_DREAM_SETTINGS), i(Settings.ACTION_DISPLAY_SETTINGS))
    fun storage(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), i(Settings.ACTION_MEMORY_CARD_SETTINGS), SafeIntents.systemSettings())
    fun deviceInfo(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_DEVICE_INFO_SETTINGS), SafeIntents.systemSettings())
    fun apps(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), i(Settings.ACTION_APPLICATION_SETTINGS))
    fun battery(c: Context) = SafeIntents.startOrToast(c, i(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), i(Settings.ACTION_DISPLAY_SETTINGS))
    fun system(c: Context) = SafeIntents.startOrToast(c, SafeIntents.systemSettings())
    fun hdmiCec(c: Context) = SafeIntents.startOrToast(c,
        i("android.settings.HDMI_CEC_SETTINGS"),
        Intent().setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings.device.display.DisplayActivity")),
        SafeIntents.systemSettings())

    fun homeSettings(c: Context) = SafeIntents.start(c, i(Settings.ACTION_HOME_SETTINGS))

    fun notificationAccess(c: Context): Boolean {
        val detail = if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(c.packageName, "com.akaaflix.launcher.device.notifications.LauncherNotificationListener").flattenToString()) else null
        return SafeIntents.start(c, detail, i("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    fun overlayPermission(c: Context): Boolean =
        SafeIntents.start(c, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName)))

    fun appLanguage(c: Context): Boolean = if (Build.VERSION.SDK_INT >= 33)
        SafeIntents.start(c, Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + c.packageName)), i(Settings.ACTION_LOCALE_SETTINGS))
    else language(c)
}
