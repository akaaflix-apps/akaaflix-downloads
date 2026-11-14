package com.akaaflix.launcher.device

import com.akaaflix.launcher.device.clock.HijriCalendar
import com.akaaflix.launcher.device.clock.HijriDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

class HijriCalendarTest {

    /** Every single day of the embedded Umm al-Qura range must match java.time.chrono.HijrahDate. */
    @Test
    fun ummAlQuraMatchesJdkForEveryDayInRange() {
        val start = HijrahDate.of(HijriCalendar.UQ_MIN_YEAR, 1, 1).toEpochDay()
        val end = HijrahDate.of(HijriCalendar.UQ_MAX_YEAR, 12, 1).toEpochDay()
        var checked = 0
        for (day in start..end) {
            val jdk = HijrahDate.from(LocalDate.ofEpochDay(day))
            val ours = HijriCalendar.ummAlQura(day)!!
            assertEquals("epochDay $day", HijriDate(jdk.get(ChronoField.YEAR), jdk.get(ChronoField.MONTH_OF_YEAR), jdk.get(ChronoField.DAY_OF_MONTH)), ours)
            checked++
        }
        assertTrue(checked > 100_000)
    }

    @Test
    fun knownDates() {
        // 14 Nov 2026 = 4 Jumada al-Thani 1448 (Umm al-Qura)
        assertEquals(HijriDate(1448, 6, 4), HijriCalendar.fromEpochDay(LocalDate.of(2026, 11, 14).toEpochDay()))
        // 1 Ramadan 1447 = 18 Feb 2026 (Umm al-Qura)
        assertEquals(HijriDate(1447, 9, 1), HijriCalendar.fromEpochDay(LocalDate.of(2026, 2, 18).toEpochDay()))
    }

    @Test
    fun adjustmentShiftsDays() {
        val d = LocalDate.of(2026, 11, 14).toEpochDay()
        assertEquals(HijriDate(1448, 6, 3), HijriCalendar.fromEpochDay(d, adjustDays = -1))
        assertEquals(HijriDate(1448, 6, 6), HijriCalendar.fromEpochDay(d, adjustDays = 2))
    }

    @Test
    fun tabularEpochIsFirstMuharramOne() {
        // 1 Muharram 1 AH (civil epoch) = 16 July 622 Julian = 19 July 622 proleptic Gregorian
        assertEquals(HijriDate(1, 1, 1), HijriCalendar.tabular(LocalDate.of(622, 7, 19).toEpochDay()))
    }

    @Test
    fun tabularIsConsistentAndCloseToUmmAlQura() {
        var prev = HijriCalendar.tabular(LocalDate.of(2000, 1, 1).toEpochDay())
        var day = LocalDate.of(2000, 1, 2).toEpochDay()
        val end = LocalDate.of(2060, 1, 1).toEpochDay()
        while (day < end) {
            val cur = HijriCalendar.tabular(day)
            val expectedNext = if (prev.day < HijriCalendar.monthLength(prev.year, prev.month, HijriCalendar.Method.TABULAR)) HijriDate(prev.year, prev.month, prev.day + 1)
                else if (prev.month < 12) HijriDate(prev.year, prev.month + 1, 1) else HijriDate(prev.year + 1, 1, 1)
            assertEquals(expectedNext, cur)
            // never more than 3 days away from Umm al-Qura
            val uq = HijriCalendar.ummAlQura(day)!!
            if (uq.year == cur.year && uq.month == cur.month) assertTrue(kotlin.math.abs(uq.day - cur.day) <= 3)
            prev = cur; day++
        }
    }

    @Test
    fun outsideTableFallsBackToTabular() {
        val far = LocalDate.of(2300, 1, 1).toEpochDay()
        assertNull(HijriCalendar.ummAlQura(far))
        assertEquals(HijriCalendar.tabular(far), HijriCalendar.fromEpochDay(far))
    }
}
