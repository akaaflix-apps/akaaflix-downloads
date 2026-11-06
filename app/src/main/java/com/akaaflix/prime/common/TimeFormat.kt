package com.akaaflix.prime.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Date/time helpers honouring the user's time-zone setting. */
object TimeFormat {

    fun zone(zoneSetting: String): ZoneId =
        if (zoneSetting.isBlank() || zoneSetting == "system") ZoneId.systemDefault()
        else runCatching { ZoneId.of(zoneSetting) }.getOrDefault(ZoneId.systemDefault())

    fun date(epochSeconds: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    fun time(epochSeconds: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm", locale).format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    fun dateTime(epochMillis: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** "1:05:09" / "05:09" style duration. */
    fun duration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** Whole days between now and [expEpochSeconds], or null if already past. */
    fun remainingDays(expEpochSeconds: Long, nowMillis: Long = System.currentTimeMillis()): Long? {
        val diff = expEpochSeconds * 1000 - nowMillis
        if (diff < 0) return null
        return TimeUnit.MILLISECONDS.toDays(diff)
    }

    /** Parses "HH:MM:SS" durations and plain seconds/minutes as used by Xtream panels. */
    fun parseDurationToSeconds(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(":")
        return when (parts.size) {
            3 -> parts.map { it.toLongOrNull() ?: return null }.let { it[0] * 3600 + it[1] * 60 + it[2] }
            2 -> parts.map { it.toLongOrNull() ?: return null }.let { it[0] * 60 + it[1] }
            else -> raw.filter { it.isDigit() }.toLongOrNull()?.let { if (it < 400) it * 60 else it }
        }
    }
}
