package com.akaaflix.prime.player

import androidx.media3.common.C
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akaaflix.prime.data.settings.AppSettings
import com.akaaflix.prime.data.settings.BufferProfile
import com.akaaflix.prime.data.settings.DecoderMode
import com.akaaflix.prime.data.settings.SubtitleSource
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The ExoPlayer instance reflects the user's player / subtitle / data-saver settings. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlaybackConfigTest {
    private val factory = PlayerFactory(ApplicationProvider.getApplicationContext(), OkHttpClient())

    @Test fun defaultSettings() {
        val player = factory.create(AppSettings())
        val p = player.trackSelectionParameters
        assertEquals(listOf("ar"), p.preferredTextLanguages)
        assertFalse(p.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertEquals(Int.MAX_VALUE, p.maxVideoHeight)
        assertEquals(PlayerFactory.SEEK_MS, player.seekBackIncrement)
        player.release()
    }

    @Test fun subtitlesOffDataSaverAndLanguages() {
        val s = AppSettings(subtitlesEnabled = false, dataSaver = true, preferredAudioLanguage = "en", subtitleLanguage = "fr", decoder = DecoderMode.SOFTWARE, bufferProfile = BufferProfile.HIGH)
        val player = factory.create(s)
        val p = player.trackSelectionParameters
        assertTrue(p.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertEquals(720, p.maxVideoHeight)
        assertEquals(listOf("en"), p.preferredAudioLanguages)
        assertEquals(listOf("fr"), p.preferredTextLanguages)
        player.release()
    }

    @Test fun externalOnlySubtitles_disableEmbeddedTrack() {
        val player = factory.create(AppSettings(subtitleSource = SubtitleSource.EXTERNAL_ONLY))
        assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        player.release()
    }

    @Test fun bufferProfilesAreOrdered() {
        assertTrue(BufferProfile.LOW.maxMs < BufferProfile.NORMAL.maxMs)
        assertTrue(BufferProfile.NORMAL.maxMs < BufferProfile.HIGH.maxMs)
        BufferProfile.entries.forEach { assertTrue(it.minMs <= it.maxMs && it.rebufferMs >= it.startMs) }
    }
}
