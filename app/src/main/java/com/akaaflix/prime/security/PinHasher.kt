package com.akaaflix.prime.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the parental-control PIN. The PIN itself is never stored.
 */
object PinHasher {
    private const val ITERATIONS = 12_000
    private const val KEY_LENGTH = 256

    data class Hashed(val hash: String, val salt: String)

    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }

    fun hash(pin: String, salt: ByteArray = newSalt()): Hashed {
        val derived = derive(pin, salt)
        return Hashed(derived.toHex(), salt.toHex())
    }

    fun verify(pin: String, hash: String, salt: String): Boolean {
        if (hash.isBlank() || salt.isBlank()) return false
        val expected = hash.hexToBytesOrNull() ?: return false
        val saltBytes = salt.hexToBytesOrNull() ?: return false
        return MessageDigest.isEqual(expected, derive(pin, saltBytes))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching { ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }

    private fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = runCatching { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256") }
            .getOrElse { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1") }
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
