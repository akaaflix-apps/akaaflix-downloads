package com.akaaflix.launcher.home.ui

import com.akaaflix.launcher.core.util.str

import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import androidx.fragment.app.Fragment
import com.akaaflix.launcher.core.AppConfig
import com.akaaflix.launcher.core.R
import com.akaaflix.launcher.core.perf.DevicePerformance
import com.akaaflix.launcher.core.perf.PerformanceTier
import com.akaaflix.launcher.core.ui.dialog.Dialogs
import com.akaaflix.launcher.core.ui.dialog.LauncherDialog
import com.akaaflix.launcher.core.ui.rows.SettingRow
import com.akaaflix.launcher.core.util.Bytes
import com.akaaflix.launcher.device.info.DeviceInfoProvider
import com.akaaflix.launcher.device.notifications.NotificationCenter
import com.akaaflix.launcher.device.status.NetworkMonitor
import com.akaaflix.launcher.device.status.NetworkState
import com.akaaflix.launcher.device.system.SystemShortcuts

object HomeDialogs {
    fun tierLabel(t: PerformanceTier) = when (t) {
        PerformanceTier.LOW -> R.string.tier_low
        PerformanceTier.MEDIUM -> R.string.tier_medium
        PerformanceTier.HIGH -> R.string.tier_high
        PerformanceTier.ULTRA -> R.string.tier_ultra
    }

    fun networkLabel(ctx: Context, n: NetworkState): String = ctx.getString(when {
        !n.connected -> R.string.net_none
        n.type == NetworkState.Type.WIFI -> R.string.net_wifi
        n.type == NetworkState.Type.ETHERNET -> R.string.net_ethernet
        n.type == NetworkState.Type.CELLULAR -> R.string.net_cellular
        n.type == NetworkState.Type.VPN -> R.string.net_vpn
        else -> R.string.net_other
    })

    /** Device information (real values; no special permissions). */
    fun showDeviceInfo(context: Context) {
        val info = DeviceInfoProvider(context)
        val s = info.summary()
        val mem = info.memory()
        val st = info.storage()
        val net = NetworkMonitor(context).also { it.refresh() }.state.value
        val profile = DevicePerformance.profile(context)
        val upMin = s.uptimeMs / 60000
        val rows = listOf(
            SettingRow.Info("name", context.getString(R.string.info_device_name), s.deviceName, R.drawable.ic_tv),
            SettingRow.Info("model", context.getString(R.string.info_model), s.model, R.drawable.ic_cpu),
            SettingRow.Info("maker", context.getString(R.string.info_manufacturer), s.manufacturer, R.drawable.ic_tag),
            SettingRow.Info("android", context.getString(R.string.info_android), context.str(R.string.info_android_fmt, s.androidRelease, s.sdkInt), R.drawable.ic_shield),
            SettingRow.Info("screen", context.getString(R.string.info_screen), context.str(R.string.info_screen_fmt, s.screenWidthPx, s.screenHeightPx, s.densityDpi, s.refreshRateHz), R.drawable.ic_monitor),
            SettingRow.Info("ram", context.getString(R.string.info_ram), context.str(R.string.set_size_fmt, Bytes.memory(mem.usedBytes), Bytes.memory(mem.totalBytes)), R.drawable.ic_memory),
            SettingRow.Info("storage", context.getString(R.string.info_storage), context.str(R.string.set_size_fmt, Bytes.storage(st.usedBytes), Bytes.storage(st.totalBytes)), R.drawable.ic_storage),
            SettingRow.Info("net", context.getString(R.string.info_network), networkLabel(context, net) + (net.ipAddress?.let { " • $it" } ?: ""), R.drawable.ic_wifi),
            SettingRow.Info("cpu", context.getString(R.string.info_cpu), context.str(R.string.info_cpu_fmt, s.cores, s.abi), R.drawable.ic_cpu),
            SettingRow.Info("perf", context.getString(R.string.info_performance), context.getString(tierLabel(profile.tier)), R.drawable.ic_gauge),
            SettingRow.Info("uptime", context.getString(R.string.info_uptime), context.str(R.string.uptime_fmt, (upMin / 1440).toInt(), ((upMin / 60) % 24).toInt(), (upMin % 60).toInt()), R.drawable.ic_clock),
            SettingRow.Info("launcher", context.getString(R.string.info_launcher), "v" + AppConfig.versionName, R.drawable.ic_logo_mark),
        )
        Dialogs.rows(context, context.getString(R.string.home_device_info), rows, icon = R.drawable.ic_info, widthDp = 560) { d, _ ->
            d.button(R.string.info_open_system) { SystemShortcuts.deviceInfo(context) }
            d.button(R.string.close, primary = true)
        }
    }

    /** Notification panel (requires the user to grant notification access). */
    fun showNotifications(fragment: Fragment) {
        val context = fragment.requireContext()
        if (!NotificationCenter.isAccessGranted(context)) {
            LauncherDialog(context).title(R.string.notif_title).icon(R.drawable.ic_bell)
                .message(R.string.notif_access_needed)
                .button(R.string.cancel)
                .button(R.string.notif_grant, primary = true) {
                    if (!SystemShortcuts.notificationAccess(context))
                        Dialogs.info(context, context.getString(R.string.row_notif_access), context.str(R.string.notif_access_unavailable,
                            context.packageName + "/com.akaaflix.launcher.device.notifications.LauncherNotificationListener"))
                }.show()
            return
        }
        val items = NotificationCenter.items.value
        if (items.isEmpty()) { Dialogs.info(context, context.getString(R.string.notif_title), context.getString(R.string.notif_empty)); return }
        var dialog: LauncherDialog? = null
        val pm = context.packageManager
        val rows = items.map { n ->
            val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(n.packageName, 0)).toString() } catch (_: Exception) { n.packageName }
            val time = DateUtils.getRelativeTimeSpanString(n.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            SettingRow.Action("n_" + n.key, n.title.ifBlank { appName }, subtitle = listOf(n.text, "$appName • $time").filter { it.isNotBlank() }.joinToString("\n"),
                iconLoader = { iv -> try { iv.setImageDrawable(pm.getApplicationIcon(n.packageName)) } catch (_: Exception) {} },
                showChevron = false,
                onLongClick = { NotificationCenter.dismiss(n.key); dialog?.dialog?.dismiss(); showNotifications(fragment) }) {
                dialog?.dialog?.dismiss(); NotificationCenter.open(n)
            }
        }
        dialog = Dialogs.rows(context, context.getString(R.string.notif_title), rows, icon = R.drawable.ic_bell, widthDp = 560) { d, _ ->
            d.message(context.getString(R.string.notif_hint))
            d.button(R.string.notif_clear_all) { NotificationCenter.dismissAll() }
            d.button(R.string.close, primary = true)
        }
        @Suppress("UNUSED_VARIABLE") val unused = Build.VERSION.SDK_INT
    }
}
