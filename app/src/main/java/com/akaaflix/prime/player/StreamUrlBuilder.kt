package com.akaaflix.prime.player

import com.akaaflix.prime.data.settings.LiveFormat
import com.akaaflix.prime.domain.model.XtreamCredentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A resolved, playable stream. [mimeType] hints ExoPlayer when the extension is ambiguous. */
data class StreamSource(val url: String, val mimeType: String?, val format: LiveFormat) {
    override fun toString(): String = "StreamSource(format=$format, mime=$mimeType)" // never print URL (contains credentials)
}

/**
 * Builds Xtream Codes stream URLs:
 *  live:    {base}/live/{user}/{pass}/{id}.{ext}
 *  movie:   {base}/movie/{user}/{pass}/{id}.{ext}
 *  episode: {base}/series/{user}/{pass}/{id}.{ext}
 *  rtmp:    rtmp://{host}:{rtmpPort}/live/{user}/{pass}/{id}  (only when the server exposes RTMP)
 */
object StreamUrlBuilder {

    const val MIME_HLS = "application/x-mpegURL"
    const val MIME_DASH = "application/dash+xml"

    /**
     * Picks the live format. AUTO prefers HLS when the server allows it, else MPEG-TS.
     * A manually selected format the server does not allow falls back to AUTO instead of
     * forcing an unsupported stream.
     */
    fun resolveLiveFormat(preferred: LiveFormat, allowedFormats: List<String>, rtmpPort: String?): LiveFormat {
        val allowed = allowedFormats.map { it.lowercase() }
        fun supported(f: LiveFormat): Boolean = when (f) {
            LiveFormat.AUTO -> false
            LiveFormat.HLS -> allowed.isEmpty() || "m3u8" in allowed
            LiveFormat.MPEG_TS -> allowed.isEmpty() || "ts" in allowed
            LiveFormat.RTMP -> !rtmpPort.isNullOrBlank() && rtmpPort != "0" && (allowed.isEmpty() || "rtmp" in allowed)
            // Panels rarely offer these; only honour them when explicitly listed.
            LiveFormat.DASH -> "mpd" in allowed
            LiveFormat.PROGRESSIVE_MP4 -> "mp4" in allowed
        }
        if (preferred != LiveFormat.AUTO && supported(preferred)) return preferred
        return when {
            supported(LiveFormat.HLS) -> LiveFormat.HLS
            supported(LiveFormat.MPEG_TS) -> LiveFormat.MPEG_TS
            supported(LiveFormat.RTMP) -> LiveFormat.RTMP
            else -> LiveFormat.HLS
        }
    }

    /** Alternative format to try when playback of [failed] errors out. */
    fun fallbackLiveFormat(failed: LiveFormat, allowedFormats: List<String>): LiveFormat? {
        val allowed = allowedFormats.map { it.lowercase() }
        return when (failed) {
            LiveFormat.HLS -> if (allowed.isEmpty() || "ts" in allowed) LiveFormat.MPEG_TS else null
            LiveFormat.MPEG_TS -> if (allowed.isEmpty() || "m3u8" in allowed) LiveFormat.HLS else null
            LiveFormat.DASH, LiveFormat.PROGRESSIVE_MP4, LiveFormat.RTMP -> LiveFormat.HLS
            LiveFormat.AUTO -> null
        }
    }

    fun live(creds: XtreamCredentials, streamId: Long, format: LiveFormat, rtmpPort: String?): StreamSource {
        if (format == LiveFormat.RTMP && !rtmpPort.isNullOrBlank()) {
            val host = creds.baseUrl.toHttpUrlOrNull()?.host ?: ""
            return StreamSource("rtmp://$host:$rtmpPort/live/${enc(creds.username)}/${enc(creds.password)}/$streamId", null, LiveFormat.RTMP)
        }
        val effective = if (format == LiveFormat.AUTO || format == LiveFormat.RTMP) LiveFormat.HLS else format
        val ext = effective.extension ?: "m3u8"
        val mime = when (effective) {
            LiveFormat.HLS -> MIME_HLS
            LiveFormat.DASH -> MIME_DASH
            else -> null
        }
        return StreamSource("${base(creds)}live/${enc(creds.username)}/${enc(creds.password)}/$streamId.$ext", mime, effective)
    }

    fun movie(creds: XtreamCredentials, streamId: Long, containerExtension: String?): StreamSource {
        val ext = sanitizeExt(containerExtension) ?: "mp4"
        return StreamSource("${base(creds)}movie/${enc(creds.username)}/${enc(creds.password)}/$streamId.$ext", mimeFor(ext), LiveFormat.AUTO)
    }

    fun episode(creds: XtreamCredentials, episodeId: String, containerExtension: String?): StreamSource {
        val ext = sanitizeExt(containerExtension) ?: "mp4"
        return StreamSource("${base(creds)}series/${enc(creds.username)}/${enc(creds.password)}/${enc(episodeId)}.$ext", mimeFor(ext), LiveFormat.AUTO)
    }

    private fun mimeFor(ext: String): String? = when (ext) {
        "m3u8" -> MIME_HLS
        "mpd" -> MIME_DASH
        else -> null
    }

    private fun sanitizeExt(ext: String?): String? = ext?.trim()?.trimStart('.')?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }

    private fun base(creds: XtreamCredentials) = if (creds.baseUrl.endsWith("/")) creds.baseUrl else creds.baseUrl + "/"

    /** Path-segment encoding for credentials that contain special characters. */
    private fun enc(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { b ->
            val c = b.toInt().toChar()
            if (c.isLetterOrDigit() && c.code < 128 || c in "-._~@!$&'()*+,;=:") append(c)
            else append('%').append("%02X".format(b.toInt() and 0xff))
        }
    }
}
