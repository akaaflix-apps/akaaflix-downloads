package com.akaaflix.prime.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SubtitleParserTest {
    private val srt = """
        1
        00:00:01,000 --> 00:00:03,500
        <i>Hello</i> there

        2
        00:00:04,000 --> 00:00:06,000
        مرحبا بكم
        سطر ثاني

        3
        01:02:03,045 --> 01:02:04,000
        Late line
    """.trimIndent()

    @Test fun parsesSrt() {
        val cues = SubtitleParser.parse(srt)
        assertEquals(3, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals("Hello there", cues[0].text)
        assertEquals("مرحبا بكم\nسطر ثاني", cues[1].text)
        assertEquals(3_723_045L, cues[2].startMs)
    }

    @Test fun parsesVtt() {
        val vtt = "WEBVTT\n\n00:01.000 --> 00:02.000 align:start\nShort form\n\nnote\n00:00:05.5 --> 00:00:07.25\nFractions"
        val cues = SubtitleParser.parse(vtt)
        assertEquals(2, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(5_500L, cues[1].startMs)
        assertEquals(7_250L, cues[1].endMs)
    }

    @Test fun decodesConfiguredEncodingAndBom() {
        val arabic = "1\n00:00:01,000 --> 00:00:02,000\nأهلاً\n"
        val cp1256 = arabic.toByteArray(Charset.forName("windows-1256"))
        assertTrue(SubtitleParser.decode(cp1256, "windows-1256").contains("أهلاً"))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + arabic.toByteArray(Charsets.UTF_8)
        assertEquals(arabic, SubtitleParser.decode(bom, "windows-1256"))
    }

    @Test fun delayShiftsActiveCues() {
        val cues = SubtitleParser.parse(srt)
        assertEquals("Hello there", SubtitleParser.activeAt(cues, 2_000, 0).single().text)
        // Subtitles delayed by +2s: at 2s nothing yet is shown... the first cue starts at 3s.
        assertTrue(SubtitleParser.activeAt(cues, 2_000, 2_000).isEmpty())
        assertEquals("Hello there", SubtitleParser.activeAt(cues, 3_200, 2_000).single().text)
        // Negative delay shows them earlier.
        assertEquals("Hello there", SubtitleParser.activeAt(cues, 500, -1_000).single().text)
        assertTrue(SubtitleParser.activeAt(cues, 3_700, 0).isEmpty())
    }
}
