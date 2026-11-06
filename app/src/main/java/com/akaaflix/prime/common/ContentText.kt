package com.akaaflix.prime.common

import java.text.Normalizer
import java.util.Locale

/** Text helpers for search normalisation, quality badges and year extraction. */
object ContentText {

    private val diacritics = Regex("[\\u064B-\\u065F\\u0670\\u0640]") // Arabic harakat + tatweel
    private val combining = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    /** Lower-cased, accent/diacritic-free form used for case-insensitive search in SQLite. */
    fun normalize(input: String): String {
        var s = Normalizer.normalize(input, Normalizer.Form.NFD)
        s = combining.replace(s, "")
        s = diacritics.replace(s, "")
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
        return spaces.replace(s.lowercase(Locale.ROOT), " ").trim()
    }

    /** Escapes a user query for use inside a SQL LIKE pattern with ESCAPE '\'. */
    fun likePattern(query: String): String {
        val n = normalize(query)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$n%"
    }

    private val qualityPatterns = listOf(
        "4K" to Regex("(?i)\\b(4k|uhd|2160p?)\\b"),
        "FHD" to Regex("(?i)\\b(fhd|1080p?|full ?hd)\\b"),
        "HD" to Regex("(?i)\\b(hd|720p?)\\b"),
        "SD" to Regex("(?i)\\b(sd|480p?|576p?)\\b"),
    )
    private val hdrPattern = Regex("(?i)\\b(hdr10\\+?|hdr|dolby ?vision|dv)\\b")

    /** Quality badge parsed from a provider title, or null when unknown. */
    fun qualityOf(title: String): String? = qualityPatterns.firstOrNull { it.second.containsMatchIn(title) }?.first

    fun isHdr(title: String): Boolean = hdrPattern.containsMatchIn(title)

    private val yearPattern = Regex("(?<!\\d)(19[2-9]\\d|20[0-4]\\d)(?!\\d)")

    /** Extracts a plausible release year from a title or date string. */
    fun yearOf(vararg sources: String?): Int? {
        for (source in sources) {
            if (source.isNullOrBlank()) continue
            yearPattern.find(source)?.let { return it.value.toInt() }
        }
        return null
    }
}
