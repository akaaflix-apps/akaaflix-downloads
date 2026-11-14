package com.akaaflix.launcher.device.clock

import com.akaaflix.launcher.core.util.Numerals
import java.util.Calendar
import java.util.TimeZone

data class DateConfig(
    val use24Hour: Boolean,
    val hijriMethod: HijriCalendar.Method = HijriCalendar.Method.UMM_AL_QURA,
    val hijriAdjust: Int = 0,
    val easternDigits: Boolean = false,
)

data class DateTexts(
    val time: String,          // "12:45"
    val amPm: String,          // "AM" (empty for 24h)
    val gregorianAr: String,   // "الأحد، 8 نوفمبر 2026"
    val gregorianEn: String,   // "Sunday, Nov 8, 2026"
    val hijriAr: String,       // "28 جمادى الأولى 1448 هـ"
    val hijriEn: String,       // "28 Jumada Al-Awwal 1448 H"
    val hijri: HijriDate,
    val gregorianFr: String = "",   // "Dimanche 8 novembre 2026"
    val hijriFr: String = "",       // "28 Joumada al-Oula 1448 H"
) {
    /** Gregorian date text for a UI language ("ar", "fr", anything else = English). */
    fun gregorian(lang: String) = when (lang) { "ar" -> gregorianAr; "fr" -> gregorianFr; else -> gregorianEn }
    fun hijriText(lang: String) = when (lang) { "ar" -> hijriAr; "fr" -> hijriFr; else -> hijriEn }

    companion object {
        /** Second language of the bilingual date pill: English under Arabic, Arabic under English/French. */
        fun secondaryLanguage(primary: String) = if (primary == "ar") "en" else "ar"
    }
}

/**
 * Locale-independent date text builder (identical output on every Android version, matching the
 * reference design: Arabic month names with Western digits unless Eastern digits are chosen).
 */
object DateTextFormatter {
    private val DAYS_AR = arrayOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    private val DAYS_EN = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val MONTHS_AR = arrayOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    private val MONTHS_EN = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val HIJRI_AR = arrayOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    val HIJRI_EN = arrayOf("Muharram", "Safar", "Rabi Al-Awwal", "Rabi Al-Thani", "Jumada Al-Awwal", "Jumada Al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu Al-Qi'dah", "Dhu Al-Hijjah")
    private val DAYS_FR = arrayOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")
    private val MONTHS_FR = arrayOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    val HIJRI_FR = arrayOf("Mouharram", "Safar", "Rabia al-Awal", "Rabia ath-Thani", "Joumada al-Oula", "Joumada ath-Thania", "Rajab", "Chaabane", "Ramadan", "Chawwal", "Dhou al-Qi'da", "Dhou al-Hijja")

    fun localEpochDay(millis: Long, tz: TimeZone): Long = Math.floorDiv(millis + tz.getOffset(millis), 86_400_000L)

    fun format(millis: Long, tz: TimeZone, cfg: DateConfig): DateTexts {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = millis }
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        val h24 = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val e = cfg.easternDigits

        val time: String
        val amPm: String
        if (cfg.use24Hour) {
            time = String.format(java.util.Locale.US, "%02d:%02d", h24, min); amPm = ""
        } else {
            val h12 = if (h24 % 12 == 0) 12 else h24 % 12
            time = "$h12:" + String.format(java.util.Locale.US, "%02d", min)
            amPm = if (e) (if (h24 < 12) "ص" else "م") else (if (h24 < 12) "AM" else "PM")
        }
        val hd = HijriCalendar.fromEpochDay(localEpochDay(millis, tz), cfg.hijriMethod, cfg.hijriAdjust)
        return DateTexts(
            time = Numerals.localize(time, e),
            amPm = amPm,
            gregorianAr = Numerals.localize("${DAYS_AR[dow]}، $day ${MONTHS_AR[month]} $year", e),
            gregorianEn = "${DAYS_EN[dow]}, ${MONTHS_EN[month]} $day, $year",
            hijriAr = Numerals.localize("${hd.day} ${HIJRI_AR[hd.month - 1]} ${hd.year} هـ", e),
            hijriEn = "${hd.day} ${HIJRI_EN[hd.month - 1]} ${hd.year} H",
            hijri = hd,
            gregorianFr = "${DAYS_FR[dow]} ${if (day == 1) "1er" else day.toString()} ${MONTHS_FR[month]} $year",
            hijriFr = "${hd.day} ${HIJRI_FR[hd.month - 1]} ${hd.year} H",
        )
    }
}
