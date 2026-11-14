package com.akaaflix.launcher.apps

import com.akaaflix.launcher.apps.model.AppCategory
import com.akaaflix.launcher.apps.model.AppEntry
import com.akaaflix.launcher.apps.model.AppFilter
import com.akaaflix.launcher.prefs.AppCustomization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppListsTest {
    private fun app(pkg: String, label: String, cat: AppCategory = AppCategory.OTHER, installed: Long = 0) =
        AppEntry(AppEntry.key(pkg, "$pkg.Main", 0), pkg, "$pkg.Main", 0, label, false, installed, installed, 1, cat, false)

    private val yt = app("com.google.android.youtube.tv", "YouTube", AppCategory.ENTERTAINMENT, 3)
    private val vlc = app("org.videolan.vlc", "VLC", AppCategory.ENTERTAINMENT, 1)
    private val files = app("com.example.files", "Files", AppCategory.TOOLS, 2)
    private val arabic = app("com.example.quran", "القرآن الكريم", AppCategory.OTHER, 4)
    private val all = listOf(yt, vlc, files, arabic)

    @Test fun filtersWithoutGamesOrSystemCategories() {
        val c = AppCustomization()
        assertEquals(4, AppLists.visible(all, c, AppFilter.ALL, "", "az", Locale.ENGLISH).size)
        assertEquals(setOf(yt, vlc), AppLists.visible(all, c, AppFilter.ENTERTAINMENT, "", "az").toSet())
        assertEquals(listOf(files), AppLists.visible(all, c, AppFilter.TOOLS, "", "az"))
        assertEquals(AppCategory.values().map { it.id }.toSet(), setOf("entertainment", "tools", "other"))
    }

    @Test fun hiddenAppsDisappearEverywhere() {
        val c = AppCustomization(hidden = setOf(vlc.key), favorites = listOf(vlc.key, yt.key))
        assertFalse(AppLists.visible(all, c, AppFilter.ALL, "", "az").contains(vlc))
        assertEquals(listOf(yt), AppLists.favorites(all, c))
    }

    @Test fun userCategoryOverride() {
        val c = AppCustomization(categories = mapOf(files.key to "entertainment"))
        assertTrue(AppLists.visible(all, c, AppFilter.ENTERTAINMENT, "", "az").contains(files))
    }

    @Test fun searchIsArabicNormalized() {
        val c = AppCustomization()
        assertEquals(listOf(arabic), AppLists.visible(all, c, AppFilter.ALL, "القران", "az"))
        assertEquals(listOf(yt), AppLists.visible(all, c, AppFilter.ALL, "tube", "az"))
        assertEquals(listOf(vlc), AppLists.visible(all, c, AppFilter.ALL, "videolan", "az"))
    }

    @Test fun sortModes() {
        val c = AppCustomization(launchCounts = mapOf(files.key to 5, vlc.key to 2), lastLaunched = mapOf(vlc.key to 20L, files.key to 10L),
            customOrder = listOf(vlc.key, files.key))
        val en = Locale.ENGLISH
        val three = listOf(yt, vlc, files)
        assertEquals(listOf(files, vlc, yt), AppLists.sort(three, c, "az", en))
        assertEquals(listOf(yt, vlc, files), AppLists.sort(three, c, "za", en))
        assertEquals(listOf(yt, files, vlc), AppLists.sort(three, c, "installed", en))
        assertEquals(listOf(files, vlc, yt), AppLists.sort(three, c, "most_used", en))
        assertEquals(listOf(vlc, files, yt), AppLists.sort(three, c, "recent", en))
        assertEquals(listOf(vlc, files, yt), AppLists.sort(three, c, "custom", en))
    }

    @Test fun recentAndMove() {
        val c = AppCustomization(lastLaunched = mapOf(files.key to 10L, yt.key to 30L))
        assertEquals(listOf(yt, files), AppLists.recent(all, c, 5))
        assertEquals(listOf("b", "a", "c"), AppLists.move(listOf("a", "b", "c"), "a", 1))
        assertEquals(listOf("a", "b", "c"), AppLists.move(listOf("a", "b", "c"), "a", -3))
        assertEquals(listOf("b", "c", "a"), AppLists.move(listOf("a", "b", "c"), "a", 99))
    }

    @Test fun defaultFavoritesOnlyUseInstalledApps() {
        val favs = AppLists.defaultFavorites(all, 11, Locale.ENGLISH)
        assertEquals(yt.key, favs.first())   // preferred package first
        assertEquals(all.size, favs.size)    // never invents apps
        assertTrue(favs.containsAll(all.map { it.key }))
    }

    @Test fun classifier() {
        assertEquals(AppCategory.ENTERTAINMENT, AppClassifier.classify(-1, "com.netflix.ninja", "Netflix"))
        assertEquals(AppCategory.TOOLS, AppClassifier.classify(-1, "com.estrongs.android.pop", "ES File Explorer"))
        assertEquals(AppCategory.OTHER, AppClassifier.classify(-1, "com.whatsapp", "WhatsApp"))
        assertEquals(AppCategory.ENTERTAINMENT, AppClassifier.classify(0, "x.y", "z"))   // CATEGORY_GAME → entertainment
        assertEquals(AppCategory.TOOLS, AppClassifier.classify(7, "x.y", "z"))           // CATEGORY_PRODUCTIVITY
    }
}
