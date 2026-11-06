package com.akaaflix.prime.player

import java.nio.charset.Charset

/** A single timed subtitle line from an external file. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Parser for external SubRip (.srt) and WebVTT (.vtt) files. Decoding uses the user's
 * "Subtitle encoding" setting (e.g. windows-1256 for legacy Arabic files); a UTF-8 / UTF-16
 * BOM always wins. Parsed cues are rendered by the app so delay/sync can be adjusted live.
 */
object SubtitleParser {

    private val timeRegex = Regex("(\\d{1,2}:)?(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})")
    private val arrow = Regex("\\s*-->\\s*")
    private val tags = Regex("<[^>]+>|\\{\\\\[^}]*\\}")

    fun decode(bytes: ByteArray, encoding: String): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).drop(1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).drop(1)
        val charset = runCatching { Charset.forName(encoding) }.getOrDefault(Charsets.UTF_8)
        return String(bytes, charset)
    }

    fun parse(content: String): List<SubtitleCue> {
        val text = content.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = text.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<SubtitleCue>()
        for (block in blocks) {
            val lines = block.trim('\n').lines().filter { it.isNotBlank() || false }
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) continue
            val parts = lines[timingIndex].split(arrow)
            if (parts.size < 2) continue
            val start = parseTime(parts[0]) ?: continue
            val end = parseTime(parts[1].trim().split(' ').first()) ?: continue
            val body = lines.drop(timingIndex + 1).joinToString("\n") { tags.replace(it, "").trim() }.trim()
            if (body.isNotEmpty() && end > start) cues += SubtitleCue(start, end, body)
        }
        return cues.sortedBy { it.startMs }
    }

    fun parseTime(raw: String): Long? {
        val m = timeRegex.find(raw.trim()) ?: return null
        val h = m.groupValues[1].trimEnd(':').toLongOrNull() ?: 0
        val min = m.groupValues[2].toLong()
        val s = m.groupValues[3].toLong()
        val msRaw = m.groupValues[4]
        val ms = when (msRaw.length) {
            1 -> msRaw.toLong() * 100
            2 -> msRaw.toLong() * 10
            else -> msRaw.toLong()
        }
        return ((h * 60 + min) * 60 + s) * 1000 + ms
    }

    /** Cues visible at [positionMs] with a user [delayMs] (positive = subtitles appear later). */
    fun activeAt(cues: List<SubtitleCue>, positionMs: Long, delayMs: Long): List<SubtitleCue> {
        val t = positionMs - delayMs
        if (cues.isEmpty()) return emptyList()
        // Binary search for the last cue starting at or before t.
        var lo = 0
        var hi = cues.lastIndex
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs <= t) { idx = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (idx < 0) return emptyList()
        val out = mutableListOf<SubtitleCue>()
        var i = idx
        // Overlapping cues: scan back a few entries.
        while (i >= 0 && idx - i < 8) {
            val c = cues[i]
            if (t < c.endMs) out += c
            i--
        }
        return out.reversed()
    }
}
