package com.akaaflix.launcher.apps

import com.akaaflix.launcher.apps.model.AppCategory
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.apps.model.AppFilter
import com.akaaflix.launcher.prefs.AppCustomization
import java.text.Collator
import java.util.Locale

/** Pure list logic (filtering, search, sorting, favourites) – unit tested on the JVM. */
object AppLists {
    /** Ordering preference used only to pick the initial Home apps among apps that ARE installed. */
    val PREFERRED_DEFAULT_PACKAGES = listOf(
        "com.akaaflix.player", "com.akaaflix.prime",
        "com.google.android.youtube.tv", "com.google.android.youtube", "com.netflix.ninja", "com.netflix.mediaclient",
        "com.disney.disneyplus", "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient",
        "com.apple.atve.androidtv.appletv", "com.android.vending", "com.android.chrome",
        "org.videolan.vlc", "com.mxtech.videoplayer.ad", "org.xbmc.kodi", "com.stremio.one",
        "com.android.tv.settings", "com.android.settings", "com.google.android.apps.photos",
    )

    /** Search normalisation: case, Arabic letter variants / diacritics and Latin accents (é → e). */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        val decomposed = java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        for (c in decomposed) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue   // accents, harakat, hamza marks
            when (c) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                'ـ' -> {}
                else -> if (c.code in 0x064B..0x0652) {} else sb.append(c)
            }
        }
        return sb.toString()
    }

    fun matches(entry: AppEntry, query: String): Boolean {
        val q = normalize(query.trim())
        if (q.isEmpty()) return true
        return normalize(entry.label).contains(q) || entry.packageName.lowercase().contains(q)
    }

    fun categoryOf(entry: AppEntry, custom: AppCustomization): AppCategory =
        AppCategory.of(custom.categories[entry.key]) ?: entry.autoCategory

    fun visible(apps: List<AppEntry>, custom: AppCustomization, filter: AppFilter, query: String, sort: String,
                locale: Locale = Locale.getDefault()): List<AppEntry> {
        val base = apps.filter { it.key !in custom.hidden && matches(it, query) }
        val filtered = when (filter) {
            AppFilter.ALL -> base
            AppFilter.ENTERTAINMENT -> base.filter { categoryOf(it, custom) == AppCategory.ENTERTAINMENT }
            AppFilter.TOOLS -> base.filter { categoryOf(it, custom) == AppCategory.TOOLS }
        }
        return sort(filtered, custom, sort, locale)
    }

    fun sort(list: List<AppEntry>, custom: AppCustomization, mode: String, locale: Locale = Locale.getDefault()): List<AppEntry> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val byName = Comparator<AppEntry> { a, b -> collator.compare(a.label, b.label) }
        return when (mode) {
            "az" -> list.sortedWith(byName)
            "za" -> list.sortedWith(byName.reversed())
            "installed" -> list.sortedWith(compareByDescending<AppEntry> { it.installTime }.then(byName))
            "most_used" -> list.sortedWith(compareByDescending<AppEntry> { custom.launchCounts[it.key] ?: 0 }.then(byName))
            "recent" -> list.sortedWith(compareByDescending<AppEntry> { custom.lastLaunched[it.key] ?: 0L }.then(byName))
            else -> { // custom order: explicitly ordered apps first, then the rest alphabetically
                val index = custom.customOrder.withIndex().associate { it.value to it.index }
                list.sortedWith(compareBy<AppEntry> { index[it.key] ?: Int.MAX_VALUE }.then(byName))
            }
        }
    }

    /** The user's Favorites (independent of the Home screen). */
    fun favorites(apps: List<AppEntry>, custom: AppCustomization): List<AppEntry> = resolve(apps, custom, custom.favorites)

    /** Apps placed on the Home screen (independent of Favorites). */
    fun homeApps(apps: List<AppEntry>, custom: AppCustomization): List<AppEntry> = resolve(apps, custom, custom.homeApps)

    private fun resolve(apps: List<AppEntry>, custom: AppCustomization, keys: List<String>): List<AppEntry> {
        val byKey = apps.associateBy { it.key }
        return keys.mapNotNull { byKey[it] }.filter { it.key !in custom.hidden }
    }

    fun recent(apps: List<AppEntry>, custom: AppCustomization, limit: Int): List<AppEntry> =
        apps.filter { it.key !in custom.hidden && (custom.lastLaunched[it.key] ?: 0L) > 0L }
            .sortedByDescending { custom.lastLaunched[it.key] ?: 0L }
            .take(limit)

    /** First-run Home apps: real installed apps only (never favourites, never invented entries). */
    fun defaultHomeApps(apps: List<AppEntry>, limit: Int, locale: Locale = Locale.getDefault()): List<String> {
        val result = ArrayList<String>()
        for (pkg in PREFERRED_DEFAULT_PACKAGES) {
            apps.firstOrNull { it.packageName == pkg && !it.isWorkProfile }?.let { if (it.key !in result) result.add(it.key) }
            if (result.size >= limit) return result
        }
        val collator = Collator.getInstance(locale)
        val rest = apps.filter { it.key !in result && !it.isWorkProfile }
            .sortedWith(compareBy<AppEntry> { if (it.autoCategory == AppCategory.ENTERTAINMENT) 0 else 1 }.thenComparator { a, b -> collator.compare(a.label, b.label) })
        for (e in rest) { if (result.size >= limit) break; result.add(e.key) }
        return result
    }

    /** Moves [key] by [delta] positions inside [order] (clamped). */
    fun move(order: List<String>, key: String, delta: Int): List<String> {
        val i = order.indexOf(key)
        if (i < 0) return order
        val j = (i + delta).coerceIn(0, order.size - 1)
        if (i == j) return order
        val m = order.toMutableList()
        m.removeAt(i)
        m.add(j, key)
        return m
    }
}
