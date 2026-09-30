package com.attract.attendance.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun hash_andVerify_acceptsOnlyTheOriginalPin() {
        val hasher = PinHasher()
        val encoded = hasher.hash("2580".toCharArray())

        assertTrue(hasher.verify("2580".toCharArray(), encoded))
        assertFalse(hasher.verify("2581".toCharArray(), encoded))
    }

    @Test
    fun verify_malformedStoredValue_failsClosed() {
        assertFalse(PinHasher().verify("2580".toCharArray(), "invalid"))
    }

    @Test
    fun hashAndVerify_emptyPin_handlesCorrectly() {
        val hasher = PinHasher()
        val encoded = hasher.hash("".toCharArray())
        assertTrue(hasher.verify("".toCharArray(), encoded))
        assertFalse(hasher.verify("1".toCharArray(), encoded))
    }

    @Test
    fun hashAndVerify_maxCharacterLimitPin_handlesCorrectly() {
        val hasher = PinHasher()
        val longPin = "9".repeat(64).toCharArray()
        val encoded = hasher.hash(longPin)
        assertTrue(hasher.verify(longPin, encoded))
        assertFalse(hasher.verify("9".repeat(63).toCharArray(), encoded))
    }
}
