package com.akaaflix.launcher.device.clock

/** A Hijri (Islamic) calendar date. [month] is 1..12. */
data class HijriDate(val year: Int, val month: Int, val day: Int)

/**
 * Hijri calendar conversion that works on every Android version (API 23 has neither
 * android.icu nor java.time).
 *
 * - UMM_AL_QURA: official Saudi calendar. Month lengths for AH 1300–1600 (1882–2174 CE) are
 *   embedded below; they were generated from the JDK's HijrahChronology data and the unit tests
 *   verify every day of the range against java.time.chrono.HijrahDate.
 * - TABULAR: arithmetical civil calendar (30-year cycle, Friday epoch), used outside the table
 *   range and when the user selects it.
 * A user adjustment of ±N days (moon-sighting differences between countries) is applied first.
 */
object HijriCalendar {
    enum class Method { UMM_AL_QURA, TABULAR }

    const val UQ_MIN_YEAR = 1300
    const val UQ_MAX_YEAR = 1600
    /** Epoch day (days since 1970-01-01) of 1 Muharram 1300 in the Umm al-Qura calendar. */
    private const val UQ_START_EPOCH_DAY = -31826L

    /** Bit (m-1) set = month m has 30 days, else 29. One entry per year from [UQ_MIN_YEAR]. */
    private val UQ_MONTHS = intArrayOf(
        0x555, 0x2AB, 0x937, 0x2B6, 0x576, 0x36C, 0xB55, 0xAAA, 0x956, 0x49E, 0x95D, 0x2BA,
        0x5B5, 0x3AA, 0xB4B, 0xA96, 0x52E, 0x2AD, 0x56D, 0xB5A, 0x752, 0xF25, 0xE8A, 0xD16,
        0xA56, 0xAB5, 0x6B4, 0xDA9, 0xB92, 0xB25, 0x64B, 0xA9B, 0x35A, 0x6D9, 0x5D4, 0xDA5,
        0xD4A, 0xA95, 0x536, 0x975, 0x2F4, 0x6E9, 0x6D4, 0x6A9, 0x535, 0x25D, 0x4BD, 0x9BA,
        0x3B4, 0xB69, 0xB2A, 0xA55, 0x4AD, 0xA5D, 0x2DA, 0x6D9, 0xEAA, 0xE94, 0xD2A, 0xC56,
        0x4AE, 0xA6D, 0x56A, 0xD55, 0xD4A, 0xA93, 0x52B, 0xA5B, 0x53A, 0x6B5, 0xEA9, 0xD52,
        0xD29, 0xA55, 0x4AD, 0x56D, 0xAEA, 0x6E4, 0xED1, 0xDA2, 0xAAA, 0x95A, 0x2DA, 0x5B9,
        0xBB2, 0x764, 0x6C9, 0x555, 0x2AB, 0x4DB, 0xABA, 0x5B4, 0xDA9, 0xD52, 0xAA5, 0x92D,
        0x26D, 0x8ED, 0x2DA, 0xAD5, 0xAA5, 0xA4B, 0x497, 0x937, 0x2B6, 0x975, 0xD69, 0xD52,
        0xC95, 0x92B, 0x25B, 0x4DB, 0x9D5, 0x5D2, 0xDA5, 0xD4A, 0xA95, 0x54D, 0xAAD, 0x3AA,
        0xBD2, 0xBC4, 0xB89, 0xA95, 0x52D, 0x5AD, 0xB6A, 0x6D4, 0xDC9, 0xD92, 0xAA6, 0x956,
        0x2AE, 0x56D, 0x36A, 0xB55, 0xAAA, 0x94D, 0x49D, 0x95D, 0x2BA, 0x5B5, 0x5AA, 0xD55,
        0xA9A, 0x92E, 0x26E, 0x55D, 0xADA, 0x6D4, 0x6A5, 0xB27, 0xA4D, 0x4AD, 0x56D, 0xB5A,
        0x754, 0xF49, 0xE92, 0xD26, 0xA56, 0x356, 0x6B5, 0xBAA, 0xB92, 0xB25, 0x68B, 0xA9B,
        0x55A, 0xADA, 0x5B4, 0xDA9, 0xB52, 0xA9A, 0x536, 0x276, 0x575, 0xAF2, 0x6D4, 0x6A9,
        0x555, 0x2AD, 0x4BD, 0x9BA, 0x574, 0xB69, 0xB52, 0xA95, 0x52D, 0xA5D, 0x4DA, 0xAD9,
        0x6B2, 0xE95, 0xE2A, 0xC96, 0x92E, 0xAAD, 0x56A, 0xD65, 0xD4A, 0xD15, 0x62B, 0xC5B,
        0x53A, 0x6B5, 0xDB2, 0xD64, 0xD29, 0xA55, 0x4AD, 0x96D, 0xAEA, 0x6E8, 0xED1, 0xDA4,
        0xD4A, 0xA6A, 0x2DA, 0x5B9, 0xB72, 0xB68, 0x6D1, 0x655, 0x4AB, 0x95B, 0x2BA, 0x5B5,
        0xDA9, 0xD52, 0xCA6, 0x94E, 0x46E, 0x95D, 0x4DA, 0xAD5, 0xAAA, 0xA4D, 0x49B, 0x937,
        0x4B6, 0x975, 0xD6A, 0xD52, 0xAA5, 0x94B, 0x2AB, 0x55B, 0xAD9, 0x5D2, 0xDC5, 0xD92,
        0xB25, 0x555, 0xAB5, 0x5B4, 0xBA9, 0x7A2, 0x745, 0x593, 0xAAB, 0x4D6, 0x9D6, 0x5D2,
        0xBA5, 0xB4A, 0xA95, 0x4AD, 0x15D, 0x2DD, 0x9DA, 0x5B4, 0x5A9, 0x52D, 0x25B, 0x8B7,
        0x176, 0x56D, 0xB6A, 0xACA, 0xA96, 0x52B, 0x15B, 0x2BB, 0x5B6, 0xDAA, 0xB94, 0xD46,
        0xA8D, 0x52D, 0xA9D, 0x55A, 0x755, 0x749, 0xF13, 0xE4A, 0xA96, 0x556, 0x6B5, 0xBAA,
        0xB94,
    )

    /** Epoch day of 1 Muharram for each table year (+1 entry for the end of the table). */
    private val yearStarts: LongArray by lazy {
        val starts = LongArray(UQ_MONTHS.size + 1)
        var d = UQ_START_EPOCH_DAY
        for (i in UQ_MONTHS.indices) {
            starts[i] = d
            d += yearLength(UQ_MONTHS[i])
        }
        starts[UQ_MONTHS.size] = d
        starts
    }

    private fun yearLength(bits: Int): Int = 354 + Integer.bitCount(bits) - 6

    fun fromEpochDay(epochDay: Long, method: Method = Method.UMM_AL_QURA, adjustDays: Int = 0): HijriDate {
        val day = epochDay + adjustDays
        if (method == Method.UMM_AL_QURA) ummAlQura(day)?.let { return it }
        return tabular(day)
    }

    /** Returns null outside the embedded table range. */
    fun ummAlQura(epochDay: Long): HijriDate? {
        val starts = yearStarts
        if (epochDay < starts[0] || epochDay >= starts[starts.size - 1]) return null
        // binary search for the year
        var lo = 0
        var hi = UQ_MONTHS.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= epochDay) lo = mid else hi = mid - 1
        }
        var rem = (epochDay - starts[lo]).toInt()
        val bits = UQ_MONTHS[lo]
        var month = 1
        while (month < 12) {
            val len = if ((bits shr (month - 1)) and 1 == 1) 30 else 29
            if (rem < len) break
            rem -= len
            month++
        }
        return HijriDate(UQ_MIN_YEAR + lo, month, rem + 1)
    }

    /** Arithmetical (tabular, civil) Islamic calendar. Epoch: 1 Muharram 1 AH = 622-07-19 (Gregorian). */
    fun tabular(epochDay: Long): HijriDate {
        val jdn = epochDay + 2440588L               // Julian day number
        val daysSinceEpoch = jdn - 1948440L         // day 0 = 1 Muharram 1 AH
        val cycle = Math.floorDiv(daysSinceEpoch, 10631L)
        var rem = daysSinceEpoch - cycle * 10631L
        var yearInCycle = 1
        while (yearInCycle <= 30) {
            val len = if (isTabularLeap(yearInCycle)) 355 else 354
            if (rem < len) break
            rem -= len
            yearInCycle++
        }
        val year = (cycle * 30 + yearInCycle).toInt()
        var month = 1
        while (month < 12) {
            val len = if (month % 2 == 1) 30 else 29
            if (rem < len) break
            rem -= len
            month++
        }
        return HijriDate(year, month, rem.toInt() + 1)
    }

    fun isTabularLeap(year: Int): Boolean = Math.floorMod(11 * year + 14, 30) < 11

    fun monthLength(year: Int, month: Int, method: Method = Method.UMM_AL_QURA): Int {
        if (method == Method.UMM_AL_QURA && year in UQ_MIN_YEAR..UQ_MAX_YEAR) {
            return if ((UQ_MONTHS[year - UQ_MIN_YEAR] shr (month - 1)) and 1 == 1) 30 else 29
        }
        return if (month % 2 == 1 || (month == 12 && isTabularLeap(year))) 30 else 29
    }
}
