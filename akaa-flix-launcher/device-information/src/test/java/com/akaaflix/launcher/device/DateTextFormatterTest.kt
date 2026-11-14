package com.akaaflix.launcher.device

import com.akaaflix.launcher.device.clock.DateConfig
import com.akaaflix.launcher.device.clock.DateTextFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class DateTextFormatterTest {
    private val cairo = TimeZone.getTimeZone("Africa/Cairo")
    // 2026-11-14 00:45 in Cairo (UTC+2) = 2026-11-13T22:45Z
    private val millis = java.time.ZonedDateTime.of(2026, 11, 14, 0, 45, 0, 0, java.time.ZoneId.of("Africa/Cairo")).toInstant().toEpochMilli()

    @Test
    fun arabicAndEnglishLikeTheReference() {
        val t = DateTextFormatter.format(millis, cairo, DateConfig(use24Hour = false))
        assertEquals("12:45", t.time)
        assertEquals("AM", t.amPm)
        assertEquals("السبت، 14 نوفمبر 2026", t.gregorianAr)
        assertEquals("Saturday, Nov 14, 2026", t.gregorianEn)
        assertEquals("4 جمادى الآخرة 1448 هـ", t.hijriAr)
        assertEquals("4 Jumada Al-Thani 1448 H", t.hijriEn)
    }

    @Test
    fun frenchDates() {
        val t = DateTextFormatter.format(millis, cairo, DateConfig(use24Hour = true))
        assertEquals("Samedi 14 novembre 2026", t.gregorianFr)
        assertEquals("4 Joumada ath-Thania 1448 H", t.hijriFr)
        assertEquals(t.gregorianFr, t.gregorian("fr"))
        assertEquals(t.hijriAr, t.hijriText("ar"))
        assertEquals("ar", com.akaaflix.launcher.device.clock.DateTexts.secondaryLanguage("fr"))
        assertEquals("en", com.akaaflix.launcher.device.clock.DateTexts.secondaryLanguage("ar"))
        val first = java.time.ZonedDateTime.of(2026, 11, 1, 10, 0, 0, 0, java.time.ZoneId.of("Africa/Cairo")).toInstant().toEpochMilli()
        assertEquals("Dimanche 1er novembre 2026", DateTextFormatter.format(first, cairo, DateConfig(use24Hour = true)).gregorianFr)
    }

    @Test
    fun twentyFourHourAndEasternDigits() {
        val t = DateTextFormatter.format(millis, cairo, DateConfig(use24Hour = true, easternDigits = true))
        assertEquals("٠٠:٤٥", t.time)
        assertEquals("", t.amPm)
        assertEquals("السبت، ١٤ نوفمبر ٢٠٢٦", t.gregorianAr)
        assertEquals("Saturday, Nov 14, 2026", t.gregorianEn) // English line keeps Latin digits
    }

    @Test
    fun resultDoesNotDependOnDefaultLocale() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val t = DateTextFormatter.format(millis, cairo, DateConfig(use24Hour = false))
            assertEquals("12:45", t.time)
        } finally { Locale.setDefault(old) }
    }

    @Test
    fun timeZoneIsRespected() {
        val utc = DateTextFormatter.format(millis, TimeZone.getTimeZone("UTC"), DateConfig(use24Hour = true))
        assertEquals("22:45", utc.time)
        assertEquals("Friday, Nov 13, 2026", utc.gregorianEn)
    }
}
