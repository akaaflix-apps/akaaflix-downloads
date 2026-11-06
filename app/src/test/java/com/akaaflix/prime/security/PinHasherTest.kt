package com.akaaflix.prime.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test fun hashAndVerify() {
        val h = PinHasher.hash("1234")
        assertNotEquals("1234", h.hash)
        assertTrue(PinHasher.verify("1234", h.hash, h.salt))
        assertFalse(PinHasher.verify("4321", h.hash, h.salt))
        assertFalse(PinHasher.verify("1234", "", ""))
        assertFalse(PinHasher.verify("1234", "zz", h.salt))
    }

    @Test fun saltsDiffer() {
        assertNotEquals(PinHasher.hash("0000").hash, PinHasher.hash("0000").hash)
    }

    @Test fun pinRules() {
        assertTrue(PinHasher.isValidPin("0000"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}
