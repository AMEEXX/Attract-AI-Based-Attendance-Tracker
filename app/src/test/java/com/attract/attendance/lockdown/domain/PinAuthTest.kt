package com.attract.attendance.lockdown.domain

import com.attract.attendance.data.security.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinAuthTest {

    @Test
    fun correctPin_returnsSuccess() {
        val pinHasher = PinHasher()
        val encoded = pinHasher.hash("2580".toCharArray())
        val pinAuth = PinBackedAuthenticator(pinHasher, encoded)

        val result = pinAuth.verifyPin("2580".toCharArray())

        assertTrue(result)
        assertEquals(1, pinAuth.attemptCount)
    }

    @Test
    fun wrongPin_returnsFailure() {
        val pinHasher = PinHasher()
        val encoded = pinHasher.hash("2580".toCharArray())
        val pinAuth = PinBackedAuthenticator(pinHasher, encoded)

        val result = pinAuth.verifyPin("9999".toCharArray())

        assertFalse(result)
        assertEquals(1, pinAuth.attemptCount)
    }

    @Test
    fun constantTimeComparison_pathIsUsed() {
        val pinHasher = PinHasher()
        val encoded = pinHasher.hash("2580".toCharArray())
        val pinAuth = PinBackedAuthenticator(pinHasher, encoded)

        pinAuth.verifyPin("1111".toCharArray())
        pinAuth.verifyPin("2580".toCharArray())
        pinAuth.verifyPin("9999".toCharArray())
        pinAuth.verifyPin("2580".toCharArray())

        assertEquals(4, pinAuth.attemptCount)
    }

    @Test
    fun malformedStoredPinHash_failsClosed() {
        val pinHasher = PinHasher()
        val pinAuth = PinBackedAuthenticator(pinHasher, "invalid")

        val result = pinAuth.verifyPin("2580".toCharArray())

        assertFalse(result)
    }

    @Test
    fun pinBufferIsClearedBeforeReturn() {
        val pinHasher = PinHasher()
        val encoded = pinHasher.hash("2580".toCharArray())
        val pinAuth = PinBackedAuthenticator(pinHasher, encoded)

        val pin = "2580".toCharArray()
        pinAuth.verifyPin(pin)

        assertTrue(pin.all { it == '\u0000' })
    }
}
