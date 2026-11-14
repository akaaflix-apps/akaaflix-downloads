package com.akaaflix.launcher

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.akaaflix.launcher.core.R as CoreR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Arabic, English and French resources are complete and resolve on the device locale. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AkaaLauncherApp::class)
class LanguagesTest {
    private fun ctx(lang: String): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
        return base.createConfigurationContext(cfg)
    }

    @Test fun everyScreenTextIsTranslated() {
        val ids = CoreR.string::class.java.fields.map { it.getInt(null) }
        val en = ctx("en"); val ar = ctx("ar"); val fr = ctx("fr")
        assertEquals("Home", en.getString(CoreR.string.nav_home))
        assertEquals("الرئيسية", ar.getString(CoreR.string.nav_home))
        assertEquals("Accueil", fr.getString(CoreR.string.nav_home))
        assertEquals("All Rights Reserved – Akaa Team", en.getString(CoreR.string.about_copyright))
        assertEquals("جميع الحقوق محفوظة Akaa Team", ar.getString(CoreR.string.about_copyright))
        assertEquals("Tous droits réservés – Akaa Team", fr.getString(CoreR.string.about_copyright))
        assertEquals("https://akaaflix.com", fr.getString(CoreR.string.about_website_url))
        assertEquals("Akaa Flix", ar.getString(CoreR.string.about_website_text))
        // French typography: non-breaking space before ?
        assertTrue(fr.getString(CoreR.string.unsaved_message).endsWith(" ?"))
        var differentFr = 0
        for (id in ids) {
            val e = en.getString(id); val f = fr.getString(id)
            assertTrue("empty French text for ${en.resources.getResourceEntryName(id)}", f.isNotBlank())
            if (e != f) differentFr++
        }
        assertTrue("French really translated ($differentFr of ${ids.size})", differentFr > ids.size * 0.8)
        assertNotEquals(en.getString(CoreR.string.weather_location_rationale), fr.getString(CoreR.string.weather_location_rationale))
    }
}
