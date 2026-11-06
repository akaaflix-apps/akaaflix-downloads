package com.akaaflix.prime.common

/**
 * Derives browse filters from provider category names. Xtream Codes does not expose
 * countries or channel kinds, so these are inferred from the category titles that the
 * provider configured (both Arabic and Latin keywords are recognised).
 */
enum class CategoryKind { ARABIC, INTERNATIONAL, MOVIES, SERIES, KIDS, DOCUMENTARY, NEWS, SPORTS, ADULT, OTHER }

object CategoryClassifier {

    private val adult = listOf("adult", "xxx", "18+", "+18", "for adults", "porn", "erotic", "للكبار", "كبار فقط", "+١٨")
    private val kids = listOf("kid", "child", "cartoon", "toon", "junior", "disney", "nick", "اطفال", "أطفال", "كرتون", "kinder", "enfant")
    private val docs = listOf("documentar", "docu", "doc ", "nat geo", "discovery", "history", "وثائقي", "وثائقية", "dokument")
    private val news = listOf("news", "akhbar", "اخبار", "أخبار", "info", "nachrichten", "noticias")
    private val sports = listOf("sport", "bein", "رياض", "football", "soccer", "ssc", "league", "f1", "nba", "ufc", "wwe")
    private val movies = listOf("movie", "cinema", "film", "aflam", "افلام", "أفلام", "سينما", "kino", "box office", "vod")
    private val series = listOf("series", "مسلسل", "مسلسلات", "drama", "دراما", "shows")
    private val arabic = listOf(
        "arab", "عرب", "عربي", "عربية", "مصر", "egypt", "saudi", "سعود", "ksa", "uae", "emirat", "الامارات", "qatar", "قطر",
        "kuwait", "الكويت", "iraq", "العراق", "syria", "سوري", "leban", "لبنان", "jordan", "الاردن", "morocco", "المغرب",
        "algeri", "الجزائر", "tunis", "تونس", "libya", "ليبيا", "oman", "عمان", "bahrain", "البحرين", "yemen", "اليمن",
        "sudan", "السودان", "palestin", "فلسطين", "osn", "mbc", "rotana", "روتانا", "shahid", "شاهد", "art ", "nile", "النيل",
    )

    private val arabicCountryCodes = setOf("AR", "ARB", "ARAB", "EG", "SA", "KSA", "AE", "UAE", "QA", "KW", "IQ", "SY", "LB", "JO", "MA", "DZ", "TN", "LY", "OM", "BH", "YE", "SD", "PS")

    private val countryPrefix = Regex("^\\s*[|\\[(]?\\s*([A-Za-z]{2,3})\\s*[|:\\-\\])]")

    // Keywords are normalised the same way as the category names they are matched against.
    private val adultN by lazy { adult.map(::norm) }
    private val kidsN by lazy { kids.map(::norm) }
    private val docsN by lazy { docs.map(::norm) }
    private val newsN by lazy { news.map(::norm) }
    private val sportsN by lazy { sports.map(::norm) }
    private val moviesN by lazy { movies.map(::norm) }
    private val seriesN by lazy { series.map(::norm) }
    private val arabicN by lazy { arabic.map(::norm) }

    private fun norm(k: String): String {
        val n = ContentText.normalize(k)
        return if (k.endsWith(" ")) "$n " else n
    }

    fun classify(name: String): CategoryKind {
        val n = " " + ContentText.normalize(name) + " "
        return when {
            adultN.any { n.contains(it) } -> CategoryKind.ADULT
            kidsN.any { n.contains(it) } -> CategoryKind.KIDS
            docsN.any { n.contains(it) } -> CategoryKind.DOCUMENTARY
            newsN.any { n.contains(it) } -> CategoryKind.NEWS
            sportsN.any { n.contains(it) } -> CategoryKind.SPORTS
            moviesN.any { n.contains(it) } -> CategoryKind.MOVIES
            seriesN.any { n.contains(it) } -> CategoryKind.SERIES
            isArabic(name) -> CategoryKind.ARABIC
            countryCode(name) != null -> CategoryKind.INTERNATIONAL
            else -> CategoryKind.OTHER
        }
    }

    fun isAdult(name: String): Boolean {
        val n = " " + ContentText.normalize(name) + " "
        return adultN.any { n.contains(it) }
    }

    fun isArabic(name: String): Boolean {
        val n = " " + ContentText.normalize(name) + " "
        if (arabicN.any { n.contains(it) }) return true
        val code = countryCode(name)
        return code != null && code in arabicCountryCodes
    }

    /** Country / region code from prefixes such as "AR | MBC", "[FR] Canal", "UK: Sports". */
    fun countryCode(name: String): String? {
        val m = countryPrefix.find(name) ?: return null
        val code = m.groupValues[1].uppercase()
        return code.takeIf { it.length in 2..3 && it !in setOf("HD", "SD", "FHD", "UHD", "VIP", "TV", "VOD", "PPV", "THE") }
    }
}
