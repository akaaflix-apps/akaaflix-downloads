package com.akaaflix.prime.common

/**
 * Parses provider age ratings (MPAA, TV parental guidelines, European "16+", "FSK 12", ...)
 * into a minimum viewer age. Returns null when the rating is unknown.
 */
object AgeRating {
    private val numeric = Regex("(\\d{1,2})\\s*\\+|\\+\\s*(\\d{1,2})|(?i)(?:fsk|pegi|age|ab)\\s*(\\d{1,2})")

    fun minimumAge(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val r = raw.trim().uppercase()
        when (r) {
            "G", "TV-Y", "TV-G", "U", "ALL", "0", "0+" -> return 0
            "TV-Y7", "TV-Y7-FV" -> return 7
            "PG", "TV-PG" -> return 10
            "PG-13", "TV-14", "12A" -> return 13
            "R", "TV-MA", "M", "MA15+" -> return 17
            "NC-17", "X", "XXX", "R18", "R18+" -> return 18
        }
        numeric.find(r)?.let { m ->
            val v = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull()
            if (v != null && v in 0..21) return v
        }
        return r.toIntOrNull()?.takeIf { it in 0..21 }
    }

    /** True when content with [raw] rating exceeds the configured [maxAllowedAge] (0 = no limit). */
    fun isRestricted(raw: String?, maxAllowedAge: Int): Boolean {
        if (maxAllowedAge <= 0) return false
        val age = minimumAge(raw) ?: return false
        return age > maxAllowedAge
    }
}
