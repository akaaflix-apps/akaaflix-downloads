package com.akaaflix.launcher.prefs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesTest {
    @Test fun settingsRoundTrip() {
        val s = LauncherSettings(language = "en", themeId = "royal_blue", accentColor = 0xFF22D3EE.toInt(), homeRows = 3,
            weatherCity = "Cairo", weatherLat = 30.04, weatherLon = 31.23, hijriAdjust = -1, iconShape = "circle", pinHash = "ab", pinSalt = "cd")
        assertEquals(s, LauncherSettings.fromJson(JSONObject(s.toJson().toString())))
    }

    @Test fun invalidValuesFallBackToDefaults() {
        val o = JSONObject().put("v", 2).put("homeRows", 99).put("iconShape", "hexagon").put("language", "de").put("hijriAdjust", 7).put("fontScale", 9.0)
            .put("iconSize", "small")
        val s = LauncherSettings.fromJson(o)
        assertEquals(3, s.homeRows)
        assertEquals("original", s.iconShape)
        assertEquals("ar", s.language)
        assertEquals("medium", s.iconSize)
        assertEquals(2, s.hijriAdjust)
        assertEquals(1.4f, s.fontScale)
    }

    @Test fun defaultsMatchDesign() {
        val d = LauncherSettings()
        assertEquals("ar", d.language)
        assertEquals(LauncherSettings.DEFAULT_WALLPAPER, d.wallpaperId)
        assertEquals(3, d.homeRows)
        assertEquals("medium", d.iconSize)
        assertTrue(d.weatherEnabled)
        assertFalse(d.hasWeatherLocation)
        assertEquals("manual", d.weatherLocationMode)
        assertFalse(d.hasPin)
    }

    @Test fun frenchIsAValidLanguage() {
        assertEquals("fr", LauncherSettings.fromJson(JSONObject().put("language", "fr")).language)
        assertEquals(listOf("system", "ar", "en", "fr"), LauncherSettings.LANGUAGES)
        assertEquals(listOf("medium", "large"), LauncherSettings.ICON_SIZES)
    }

    @Test fun schema1SettingsMigrate() {
        // v1.0 stored v=1, two rows, a custom icon shape and the "small" size.
        val o = JSONObject().put("v", 1).put("homeRows", 2).put("iconShape", "circle").put("iconSize", "small").put("weatherCity", "Cairo")
            .put("weatherLat", 30.04).put("weatherLon", 31.24)
        val s = LauncherSettings.fromJson(o)
        assertEquals(3, s.homeRows)
        assertEquals("original", s.iconShape)
        assertEquals("medium", s.iconSize)
        assertEquals("manual", s.weatherLocationMode)
        assertTrue(s.hasWeatherLocation)
        assertEquals(LauncherSettings.SCHEMA, JSONObject(s.toJson().toString()).getInt("v"))
    }

    @Test fun schema1AppListBecomesHomeAndFavoritesStartEmpty() {
        val legacy = JSONObject().put("favorites", org.json.JSONArray(listOf("a/A#0", "b/B#0"))).put("favoritesInitialized", true)
        val c = AppCustomization.fromJson(legacy)
        assertEquals(listOf("a/A#0", "b/B#0"), c.homeApps)
        assertTrue(c.homeInitialized)
        assertTrue("nothing becomes a favourite automatically", c.favorites.isEmpty())
        val again = AppCustomization.fromJson(JSONObject(c.toJson().toString()))
        assertEquals(c, again)
    }

    @Test fun homeAndFavoritesAreIndependent() {
        val c = AppCustomization(homeApps = listOf("a/A#0"), favorites = listOf("b/B#0"))
        val fav = c.toggleFavorite("a/A#0")
        assertEquals(listOf("a/A#0"), fav.homeApps)
        assertEquals(listOf("b/B#0", "a/A#0"), fav.favorites)
        val home = fav.toggleHome("a/A#0")
        assertTrue(home.homeApps.isEmpty())
        assertEquals("removing from Home keeps the favourite", listOf("b/B#0", "a/A#0"), home.favorites)
        val unfav = c.toggleFavorite("b/B#0")
        assertEquals(listOf("a/A#0"), unfav.homeApps)
    }

    @Test fun customizationCopyOnlyTouchesCustomizationFields() {
        val cur = LauncherSettings(pinHash = "x", pinSalt = "y", language = "en", rotationEnabled = true)
        val draft = LauncherSettings(themeId = "pharaoh_gold", iconShape = "square", pinHash = "", language = "ar")
        val merged = cur.withCustomizationFrom(draft)
        assertEquals("pharaoh_gold", merged.themeId)
        assertEquals("square", merged.iconShape)
        assertEquals("x", merged.pinHash)
        assertEquals("en", merged.language)
        assertTrue(merged.rotationEnabled)
    }

    @Test fun appCustomizationRoundTripAndPrune() {
        val a = AppCustomization(homeApps = listOf("b/B#0", "a/A#0"), homeInitialized = true, favorites = listOf("a/A#0", "b/B#0"), hidden = setOf("c/C#0"), locked = setOf("a/A#0"),
            categories = mapOf("b/B#0" to "tools"), launchCounts = mapOf("a/A#0" to 3), lastLaunched = mapOf("a/A#0" to 5L), sortMode = "az")
        assertEquals(a, AppCustomization.fromJson(JSONObject(a.toJson().toString())))
        val pruned = a.pruned(setOf("a/A#0"))
        assertEquals(listOf("a/A#0"), pruned.favorites)
        assertEquals(listOf("a/A#0"), pruned.homeApps)
        assertTrue(pruned.hidden.isEmpty())
        val launched = a.recordLaunch("b/B#0", 9L)
        assertEquals(1, launched.launchCounts["b/B#0"])
        assertEquals(9L, launched.lastLaunched["b/B#0"])
    }

    @Test fun backupNeverContainsPin() {
        val text = BackupCodec.encode(LauncherSettings(pinHash = "secret", pinSalt = "salt", homeRows = 1), AppCustomization(homeApps = listOf("h"), favorites = listOf("x")), WallpaperUserData(setOf("w")))
        assertFalse(text.contains("secret"))
        val d = BackupCodec.decode(text)!!
        assertEquals(1, d.settings.homeRows)
        assertEquals(listOf("x"), d.apps.favorites)
        assertEquals(listOf("h"), d.apps.homeApps)
        assertEquals(setOf("w"), d.wallpaperFavorites)
        assertNull(BackupCodec.decode("{\"format\":\"other\"}"))
        assertNull(BackupCodec.decode("not json"))
    }

    @Test fun pinHashing() {
        val salt = PinHasher.newSalt()
        val h = PinHasher.hash("1234", salt)
        assertTrue(PinHasher.verify("1234", salt, h))
        assertFalse(PinHasher.verify("1235", salt, h))
        assertFalse(PinHasher.verify("1234", PinHasher.newSalt(), h))
        assertTrue(PinHasher.isValidPin("0007"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin("12345"))
    }

    @Test fun wallpaperUserDataRoundTrip() {
        val w = WallpaperUserData(setOf("builtin:x"), listOf(UserWallpaper("user:1", "Mine", "1.jpg", "static", 3840, 2160, 7680, 4320, 7L)))
        assertEquals(w, WallpaperUserData.fromJson(JSONObject(w.toJson().toString())))
    }
}
