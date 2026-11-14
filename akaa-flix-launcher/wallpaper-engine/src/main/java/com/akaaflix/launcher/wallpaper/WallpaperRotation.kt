package com.akaaflix.launcher.wallpaper

import com.akaaflix.launcher.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Automatic wallpaper rotation without background services: runs only while the launcher is
 * visible; when it becomes visible again an overdue rotation happens immediately.
 */
class WallpaperRotation(
    private val scope: CoroutineScope,
    private val repo: WallpaperRepository,
    private val prefs: SettingsRepository,
    private val allowMotion: () -> Boolean,
) {
    private var job: Job? = null

    fun onVisible() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val s = prefs.settings.value
                if (!s.rotationEnabled) return@launch
                val interval = s.rotationIntervalMin.coerceAtLeast(5) * 60_000L
                val wait = s.lastRotationAt + interval - System.currentTimeMillis()
                if (wait <= 0) {
                    val next = repo.nextForRotation(s, prefs.wallpapers.value, allowMotion())
                    prefs.update { it.copy(wallpaperId = next ?: it.wallpaperId, lastRotationAt = System.currentTimeMillis()) }
                } else delay(wait.coerceAtMost(60_000L))
            }
        }
    }

    fun onHidden() { job?.cancel(); job = null }
}
