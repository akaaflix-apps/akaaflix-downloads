package com.akaaflix.launcher.device.info

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import com.akaaflix.launcher.core.perf.DevicePerformance

data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class MemoryInfo(val totalBytes: Long, val availableBytes: Long, val lowMemory: Boolean) {
    val usedBytes get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class DeviceSummary(
    val deviceName: String,
    val model: String,
    val manufacturer: String,
    val androidRelease: String,
    val sdkInt: Int,
    val isTv: Boolean,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val cores: Int,
    val abi: String,
    /** Every ABI the device supports, e.g. "arm64-v8a, armeabi-v7a, armeabi". */
    val abis: String,
    val uptimeMs: Long,
)

/** Read-only device facts from Android APIs (no hard-coded or sample values). */
class DeviceInfoProvider(context: Context) {
    private val appContext = context.applicationContext

    fun storage(): StorageInfo = try {
        val s = StatFs(Environment.getDataDirectory().path)
        StorageInfo(s.totalBytes, s.availableBytes)
    } catch (_: Exception) { StorageInfo(0, 0) }

    fun memory(): MemoryInfo {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return MemoryInfo(mi.totalMem, mi.availMem, mi.lowMemory)
    }

    @Suppress("DEPRECATION")
    fun summary(): DeviceSummary {
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = android.util.DisplayMetrics()
        val display = wm.defaultDisplay
        display.getRealMetrics(metrics)
        val name = (if (Build.VERSION.SDK_INT >= 25) Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME) else null)
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL
        return DeviceSummary(
            deviceName = name,
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            isTv = DevicePerformance.profile(appContext).isTv,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            refreshRateHz = display.refreshRate,
            cores = Runtime.getRuntime().availableProcessors(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "",
            abis = Build.SUPPORTED_ABIS.joinToString(", "),
            uptimeMs = SystemClock.elapsedRealtime(),
        )
    }

    /** Screen-off timeout in ms (readable without permission), or null. */
    fun screenTimeoutMs(): Long? = try {
        Settings.System.getLong(appContext.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
    } catch (_: Exception) { null }
}
