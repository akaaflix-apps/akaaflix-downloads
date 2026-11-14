package com.akaaflix.launcher

import android.content.Context

/**
 * A home app that crash-loops makes the device unusable. Repeated crashes within a short window
 * switch the launcher into safe mode (lightweight rendering, default wallpaper, system font) on
 * the next start; the user can leave safe mode in Settings → About.
 */
object CrashGuard {
    private const val FILE = "akaa_crash_guard"
    private const val WINDOW_MS = 3 * 60 * 1000L
    private const val THRESHOLD = 3

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { record(app, System.currentTimeMillis()) } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
    }

    fun record(context: Context, now: Long) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val list = (p.getString("times", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < WINDOW_MS } + now
        p.edit().putString("times", list.takeLast(10).joinToString(",")).commit()
    }

    fun shouldEnterSafeMode(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val recent = (p.getString("times", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.count { now - it < WINDOW_MS }
        return recent >= THRESHOLD
    }

    fun reset(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
