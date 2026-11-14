package com.akaaflix.launcher.core.perf

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

enum class PerformanceTier { LOW, MEDIUM, HIGH, ULTRA }

data class DeviceProfile(
    val totalRamBytes: Long,
    val memoryClassMb: Int,
    val cores: Int,
    val isLowRamDevice: Boolean,
    val isTv: Boolean,
    val hasTouchscreen: Boolean,
    val tier: PerformanceTier,
)

/** What the UI is allowed to do on this device, resolved from the tier and the user's mode. */
data class QualityProfile(
    val mode: String,               // high | balanced | light
    val motion: Boolean,
    val glow: Boolean,
    val focusScaleFactor: Float,    // multiplies the chosen zoom
    val animDurationMs: Long,
    val videoWallpapers: Boolean,
    val animatedWallpapers: Boolean,
    val wallpaperRgb565: Boolean,
    val wallpaperCrossfade: Boolean,
    val iconMemoryCacheBytes: Int,
    val thumbnailCacheBytes: Int,
    val maxWallpaperEdgePx: Int,
)

object DevicePerformance {
    private const val GB = 1024L * 1024L * 1024L
    @Volatile private var cached: DeviceProfile? = null

    fun tierFor(totalRamBytes: Long, isLowRam: Boolean, cores: Int): PerformanceTier = when {
        isLowRam || totalRamBytes <= (GB * 3 / 2) -> PerformanceTier.LOW
        totalRamBytes <= 3 * GB + GB / 4 || cores <= 2 -> PerformanceTier.MEDIUM
        totalRamBytes <= 6 * GB + GB / 2 -> PerformanceTier.HIGH
        else -> PerformanceTier.ULTRA
    }

    fun profile(context: Context): DeviceProfile = cached ?: detect(context.applicationContext).also { cached = it }

    private fun detect(context: Context): DeviceProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val cores = Runtime.getRuntime().availableProcessors()
        val lowRam = am.isLowRamDevice
        val pm = context.packageManager
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val touch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val tv = ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            (!touch && pm.hasSystemFeature("android.hardware.type.television"))
        return DeviceProfile(mi.totalMem, am.memoryClass, cores, lowRam, tv, touch, tierFor(mi.totalMem, lowRam, cores))
    }

    fun quality(mode: String, profile: DeviceProfile): QualityProfile {
        val resolved = when (mode) {
            "high", "balanced", "light" -> mode
            else -> when (profile.tier) {
                PerformanceTier.LOW -> "light"
                PerformanceTier.MEDIUM -> "balanced"
                else -> "high"
            }
        }
        val mb = 1024 * 1024
        val heapMb = profile.memoryClassMb.coerceAtLeast(48)
        return when (resolved) {
            "light" -> QualityProfile(resolved, motion = false, glow = false, focusScaleFactor = 0.6f, animDurationMs = 0,
                videoWallpapers = false, animatedWallpapers = false, wallpaperRgb565 = true, wallpaperCrossfade = false,
                iconMemoryCacheBytes = (heapMb / 12).coerceIn(3, 8) * mb, thumbnailCacheBytes = 3 * mb, maxWallpaperEdgePx = 1920)
            "balanced" -> QualityProfile(resolved, motion = true, glow = true, focusScaleFactor = 0.85f, animDurationMs = 140,
                videoWallpapers = true, animatedWallpapers = true, wallpaperRgb565 = false, wallpaperCrossfade = true,
                iconMemoryCacheBytes = (heapMb / 10).coerceIn(6, 16) * mb, thumbnailCacheBytes = 6 * mb, maxWallpaperEdgePx = 2560)
            else -> QualityProfile("high", motion = true, glow = true, focusScaleFactor = 1f, animDurationMs = 170,
                videoWallpapers = true, animatedWallpapers = true, wallpaperRgb565 = false, wallpaperCrossfade = true,
                iconMemoryCacheBytes = (heapMb / 8).coerceIn(8, 32) * mb, thumbnailCacheBytes = 12 * mb, maxWallpaperEdgePx = 3840)
        }
    }
}
