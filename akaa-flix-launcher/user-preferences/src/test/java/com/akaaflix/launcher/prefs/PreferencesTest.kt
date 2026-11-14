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
        val o = JSONObject().put("homeRows", 99).put("iconShape", "hexagon").put("language", "fr").put("hijriAdjust", 7).put("fontScale", 9.0)
        val s = LauncherSettings.fromJson(o)
        assertEquals(3, s.homeRows)
        assertEquals("original", s.iconShape)
        assertEquals("ar", s.language)
        assertEquals(2, s.hijriAdjust)
        assertEquals(1.4f, s.fontScale)
    }

    @Test fun defaultsMatchDesign() {
        val d = LauncherSettings()
        assertEquals("ar", d.language)
        assertEquals(LauncherSettings.DEFAULT_WALLPAPER, d.wallpaperId)
        assertEquals(2, d.homeRows)
        assertFalse(d.hasPin)
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
        val a = AppCustomization(favorites = listOf("a/A#0", "b/B#0"), hidden = setOf("c/C#0"), locked = setOf("a/A#0"),
            categories = mapOf("b/B#0" to "tools"), launchCounts = mapOf("a/A#0" to 3), lastLaunched = mapOf("a/A#0" to 5L), sortMode = "az")
        assertEquals(a, AppCustomization.fromJson(JSONObject(a.toJson().toString())))
        val pruned = a.pruned(setOf("a/A#0"))
        assertEquals(listOf("a/A#0"), pruned.favorites)
        assertTrue(pruned.hidden.isEmpty())
        val launched = a.recordLaunch("b/B#0", 9L)
        assertEquals(1, launched.launchCounts["b/B#0"])
        assertEquals(9L, launched.lastLaunched["b/B#0"])
    }

    @Test fun backupNeverContainsPin() {
        val text = BackupCodec.encode(LauncherSettings(pinHash = "secret", pinSalt = "salt", homeRows = 1), AppCustomization(favorites = listOf("x")), WallpaperUserData(setOf("w")))
        assertFalse(text.contains("secret"))
        val d = BackupCodec.decode(text)!!
        assertEquals(1, d.settings.homeRows)
        assertEquals(listOf("x"), d.apps.favorites)
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
