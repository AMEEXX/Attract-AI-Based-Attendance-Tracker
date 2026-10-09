package com.attract.attendance.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockoutManagerTest {

    private val storage = InMemoryPinLockoutStorage()
    private var currentTime = 1_000_000L
    private val manager = PinLockoutManager(storage, timeProvider = { currentTime })

    @Test
    fun underFiveAttempts_noLockout() {
        repeat(4) {
            val duration = manager.recordFailedAttempt()
            assertEquals(0L, duration)
            assertFalse(manager.isLockedOut())
        }
        assertEquals(4, manager.getFailedAttempts())
    }

    @Test
    fun fifthAttempt_locksOutForThirtySeconds() {
        repeat(4) { manager.recordFailedAttempt() }

        val duration = manager.recordFailedAttempt()
        assertEquals(30L, duration)
        assertTrue(manager.isLockedOut())
        assertEquals(30L, manager.getRemainingLockoutSeconds())

        // Advance time 29 seconds -> still locked out
        currentTime += 29_000L
        assertTrue(manager.isLockedOut())

        // Advance 2 more seconds -> unlocked
        currentTime += 2_000L
        assertFalse(manager.isLockedOut())
        assertEquals(0L, manager.getRemainingLockoutSeconds())
    }

    @Test
    fun sixthAttempt_exponentialBackoffDoubles() {
        repeat(5) { manager.recordFailedAttempt() }

        val duration = manager.recordFailedAttempt()
        assertEquals(60L, duration) // 30s * 2 = 60s
        assertTrue(manager.isLockedOut())
    }

    @Test
    fun successfulAttempt_resetsLockoutAndCounter() {
        repeat(5) { manager.recordFailedAttempt() }
        assertTrue(manager.isLockedOut())

        manager.recordSuccessfulAttempt()
        assertFalse(manager.isLockedOut())
        assertEquals(0, manager.getFailedAttempts())
    }
}
