package com.akaaflix.launcher.prefs

import android.content.Context
import android.os.SystemClock

/**
 * Evidence used to verify start-up behaviour instead of assuming it:
 *  - when Android delivered BOOT_COMPLETED to the launcher (per boot),
 *  - when the launcher was started through the HOME intent, and how long after boot.
 * A "boot id" is the wall-clock time of the boot rounded to 10 s (currentTimeMillis − elapsedRealtime).
 */
object BootLog {
    private const val FILE = "akaa_boot_log"

    data class Snapshot(
        val currentBootId: Long,
        val bootBroadcastBootId: Long,
        val bootBroadcastAtElapsedMs: Long,
        val homeStartBootId: Long,
        val homeStartAtElapsedMs: Long,
        val fallbackLaunchBootId: Long,
        /** Wall-clock time of the most recent HOME intent (Home button / boot) that reached Akaa Flix. */
        val lastHomeIntentAt: Long = 0L,
    ) {
        val bootBroadcastThisBoot get() = sameBoot(bootBroadcastBootId, currentBootId)
        val homeStartThisBoot get() = sameBoot(homeStartBootId, currentBootId)
        val fallbackLaunchThisBoot get() = sameBoot(fallbackLaunchBootId, currentBootId)
    }

    fun currentBootId(nowMs: Long = System.currentTimeMillis(), elapsedMs: Long = SystemClock.elapsedRealtime()): Long =
        (nowMs - elapsedMs) / 10_000L

    /** Boot ids within 3 units (30 s) are the same boot (clock adjustments, rounding). */
    fun sameBoot(a: Long, b: Long) = a > 0 && b > 0 && kotlin.math.abs(a - b) <= 3

    fun recordBootBroadcast(c: Context) = prefs(c).edit().putLong("boot_id", currentBootId()).putLong("boot_at", SystemClock.elapsedRealtime()).apply()

    /** Called for every HOME intent; only the first one of a boot is kept (that is the start-up one). */
    fun recordHomeStart(c: Context) {
        val p = prefs(c)
        val id = currentBootId()
        val e = p.edit().putLong("home_last", System.currentTimeMillis())
        if (!sameBoot(p.getLong("home_id", 0), id)) e.putLong("home_id", id).putLong("home_at", SystemClock.elapsedRealtime())
        e.apply()
    }

    fun recordFallbackLaunch(c: Context) = prefs(c).edit().putLong("fallback_id", currentBootId()).apply()

    fun read(c: Context): Snapshot {
        val p = prefs(c)
        return Snapshot(currentBootId(), p.getLong("boot_id", 0), p.getLong("boot_at", 0), p.getLong("home_id", 0), p.getLong("home_at", 0),
            p.getLong("fallback_id", 0), p.getLong("home_last", 0))
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
