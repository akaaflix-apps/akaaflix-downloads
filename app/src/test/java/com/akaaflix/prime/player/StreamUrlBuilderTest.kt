package com.akaaflix.prime.player

import com.akaaflix.prime.data.settings.LiveFormat
import com.akaaflix.prime.domain.model.XtreamCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StreamUrlBuilderTest {
    private val creds = XtreamCredentials("http://host:8080/", "user", "p@ss/w")

    @Test fun liveUrls() {
        val hls = StreamUrlBuilder.live(creds, 101, LiveFormat.HLS, null)
        assertEquals("http://host:8080/live/user/p@ss%2Fw/101.m3u8", hls.url)
        assertEquals(StreamUrlBuilder.MIME_HLS, hls.mimeType)
        assertEquals("http://host:8080/live/user/p@ss%2Fw/101.ts", StreamUrlBuilder.live(creds, 101, LiveFormat.MPEG_TS, null).url)
        assertEquals("rtmp://host:8001/live/user/p@ss%2Fw/101", StreamUrlBuilder.live(creds, 101, LiveFormat.RTMP, "8001").url)
        assertEquals(StreamUrlBuilder.MIME_DASH, StreamUrlBuilder.live(creds, 101, LiveFormat.DASH, null).mimeType)
    }

    @Test fun vodUrls() {
        assertEquals("http://host:8080/movie/user/p@ss%2Fw/201.mkv", StreamUrlBuilder.movie(creds, 201, "mkv").url)
        assertEquals("http://host:8080/movie/user/p@ss%2Fw/201.mp4", StreamUrlBuilder.movie(creds, 201, null).url)
        assertEquals("http://host:8080/movie/user/p@ss%2Fw/201.mp4", StreamUrlBuilder.movie(creds, 201, "../../x").url)
        assertEquals("http://host:8080/series/user/p@ss%2Fw/9001.avi", StreamUrlBuilder.episode(creds, "9001", ".AVI").url)
    }

    @Test fun toStringNeverLeaksUrl() {
        assertFalse(StreamUrlBuilder.movie(creds, 1, "mp4").toString().contains("p@ss"))
        assertFalse(creds.toString().contains("p@ss"))
    }

    @Test fun autoFormatFollowsServerCapabilities() {
        assertEquals(LiveFormat.HLS, StreamUrlBuilder.resolveLiveFormat(LiveFormat.AUTO, listOf("m3u8", "ts"), null))
        assertEquals(LiveFormat.MPEG_TS, StreamUrlBuilder.resolveLiveFormat(LiveFormat.AUTO, listOf("ts"), null))
        assertEquals(LiveFormat.HLS, StreamUrlBuilder.resolveLiveFormat(LiveFormat.AUTO, emptyList(), null))
        assertEquals(LiveFormat.RTMP, StreamUrlBuilder.resolveLiveFormat(LiveFormat.AUTO, listOf("rtmp"), "1935"))
    }

    @Test fun unsupportedManualFormat_isNotForced() {
        assertEquals(LiveFormat.MPEG_TS, StreamUrlBuilder.resolveLiveFormat(LiveFormat.MPEG_TS, listOf("m3u8", "ts"), null))
        assertEquals(LiveFormat.HLS, StreamUrlBuilder.resolveLiveFormat(LiveFormat.DASH, listOf("m3u8", "ts"), null))
        assertEquals(LiveFormat.HLS, StreamUrlBuilder.resolveLiveFormat(LiveFormat.RTMP, listOf("m3u8"), "0"))
        assertEquals(LiveFormat.HLS, StreamUrlBuilder.resolveLiveFormat(LiveFormat.PROGRESSIVE_MP4, listOf("m3u8"), null))
        assertEquals(LiveFormat.PROGRESSIVE_MP4, StreamUrlBuilder.resolveLiveFormat(LiveFormat.PROGRESSIVE_MP4, listOf("m3u8", "mp4"), null))
    }

    @Test fun fallbackFormats() {
        assertEquals(LiveFormat.MPEG_TS, StreamUrlBuilder.fallbackLiveFormat(LiveFormat.HLS, listOf("m3u8", "ts")))
        assertEquals(LiveFormat.HLS, StreamUrlBuilder.fallbackLiveFormat(LiveFormat.MPEG_TS, listOf("m3u8", "ts")))
        assertNull(StreamUrlBuilder.fallbackLiveFormat(LiveFormat.HLS, listOf("m3u8")))
    }
}
