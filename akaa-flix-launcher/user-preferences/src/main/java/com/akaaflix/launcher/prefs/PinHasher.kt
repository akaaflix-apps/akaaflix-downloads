package com.akaaflix.launcher.prefs

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2 PIN hashing. The PIN itself is never stored. Available on API 23+. */
object PinHasher {
    private const val ITERATIONS = 12_000
    private const val KEY_BITS = 256

    fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return hex(b)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), unhex(salt), ITERATIONS, KEY_BITS)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return hex(f.generateSecret(spec).encoded)
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        if (salt.isEmpty() || expectedHash.isEmpty()) return false
        val actual = hash(pin, salt)
        // constant-time comparison
        if (actual.length != expectedHash.length) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].code xor expectedHash[i].code)
        return diff == 0
    }

    fun isValidPin(pin: String) = pin.length == 4 && pin.all { it in '0'..'9' }

    private fun hex(b: ByteArray): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(b.size * 2)
        for (x in b) { val v = x.toInt() and 0xFF; sb.append(chars[v ushr 4]).append(chars[v and 0x0F]) }
        return sb.toString()
    }

    private fun unhex(s: String): ByteArray = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
