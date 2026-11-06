package com.akaaflix.prime.common

/**
 * Minimal Base64 decoder (standard + URL-safe alphabets, padding optional) used for Xtream
 * EPG titles/descriptions. Pure Kotlin so it works on API 24 and in JVM unit tests.
 */
object Base64Text {
    private val table = IntArray(128) { -1 }.apply {
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, c -> this[c.code] = i }
        this['-'.code] = 62
        this['_'.code] = 63
    }

    fun decodeOrNull(input: String?): ByteArray? {
        if (input.isNullOrEmpty()) return null
        val clean = input.filter { !it.isWhitespace() }.trimEnd('=')
        if (clean.isEmpty()) return null
        if (clean.any { it.code >= 128 || table[it.code] < 0 }) return null
        if (clean.length % 4 == 1) return null
        val out = ByteArray(clean.length * 3 / 4)
        var buffer = 0
        var bits = 0
        var index = 0
        for (c in clean) {
            buffer = (buffer shl 6) or table[c.code]
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[index++] = ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return out.copyOf(index)
    }

    /** Decodes Base64 text as UTF-8; returns the input unchanged when it is not Base64. */
    fun decodeTextOrSelf(input: String?): String? {
        if (input == null) return null
        val bytes = decodeOrNull(input) ?: return input
        val text = String(bytes, Charsets.UTF_8)
        // Heuristic: real text should not contain control characters (other than newlines/tabs).
        return if (text.any { it.code < 32 && it != '\n' && it != '\r' && it != '\t' } || text.contains('�')) input else text
    }
}
