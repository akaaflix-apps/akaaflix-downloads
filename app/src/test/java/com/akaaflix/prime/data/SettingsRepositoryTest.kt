package com.akaaflix.prime.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.data.settings.LiveFormat
import com.akaaflix.prime.data.settings.SettingsRepository
import com.akaaflix.prime.data.settings.SubtitleEdge
import com.akaaflix.prime.data.settings.SubtitlePosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var repo: SettingsRepository

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("settings.preferences_pb") })
    }

    @After fun tearDown() = scope.cancel()

    @Test fun defaults() = runBlocking {
        assertEquals(AppSettings(), repo.current())
        assertEquals(LiveFormat.AUTO, repo.current().liveFormat)
    }

    @Test fun subtitleSettings_persist_andAreClamped() = runBlocking {
        repo.update {
            it.copy(
                subtitlesEnabled = false, subtitleLanguage = "fr", subtitleEncoding = "windows-1256", subtitleScale = 400,
                subtitleBackgroundOpacity = 75, subtitlePosition = SubtitlePosition.TOP, subtitleEdge = SubtitleEdge.DROP_SHADOW,
                subtitleDelayMs = 90_000, subtitleTextColor = 0xFFFFE600,
            )
        }
        val s = repo.current()
        assertFalse(s.subtitlesEnabled)
        assertEquals("fr", s.subtitleLanguage)
        assertEquals("windows-1256", s.subtitleEncoding)
        assertEquals(250, s.subtitleScale) // clamped
        assertEquals(75, s.subtitleBackgroundOpacity)
        assertEquals(SubtitlePosition.TOP, s.subtitlePosition)
        assertEquals(SubtitleEdge.DROP_SHADOW, s.subtitleEdge)
        assertEquals(30_000L, s.subtitleDelayMs) // clamped
        assertEquals(0xFFFFE600, s.subtitleTextColor)
    }

    @Test fun pin_setVerify_andRestoreDefaultsKeepsIt() = runBlocking {
        assertFalse(repo.setPin("12"))
        assertTrue(repo.setPin("2468"))
        assertTrue(repo.verifyPin("2468"))
        assertFalse(repo.verifyPin("0000"))
        repo.update { it.copy(parentalEnabled = true, liveFormat = LiveFormat.MPEG_TS) }
        repo.restoreDefaults(includeParental = false)
        val s = repo.current()
        assertEquals(LiveFormat.AUTO, s.liveFormat)
        assertTrue(s.parentalEnabled)
        assertTrue(repo.verifyPin("2468"))
        repo.restoreDefaults(includeParental = true)
        assertFalse(repo.current().hasPin)
    }

    @Test fun export_neverContainsPin_andImportRoundTrips() = runBlocking {
        repo.setPin("1357")
        repo.update { it.copy(liveFormat = LiveFormat.HLS, dataSaver = true, subtitleDelayMs = 500) }
        val exported = repo.exportMap()
        assertFalse(exported.keys.any { it.contains("pin") })
        repo.restoreDefaults(includeParental = true)
        repo.importMap(exported + ("pin_hash" to "evil"))
        val s = repo.current()
        assertEquals(LiveFormat.HLS, s.liveFormat)
        assertTrue(s.dataSaver)
        assertEquals(500L, s.subtitleDelayMs)
        assertFalse(s.hasPin)
    }
}
