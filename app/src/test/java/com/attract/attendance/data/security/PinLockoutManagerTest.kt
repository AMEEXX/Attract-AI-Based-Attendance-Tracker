package com.attract.attendance.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockoutManagerTest {

    private val storage = InMemoryPinLockoutStorage()
    private var currentTime = 1_000_000L
    private var bootCount = 0
    private val manager = PinLockoutManager(storage, timeProvider = { currentTime }, currentBootCount = { bootCount })

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
    fun sixthAttempt_isStillThirtySeconds() {
        repeat(5) { manager.recordFailedAttempt() } // 5 fails

        val duration = manager.recordFailedAttempt() // 6th fail
        assertEquals(30L, duration) // 30s (until 8 fails)
        assertTrue(manager.isLockedOut())
    }

    @Test
    fun eighthAttempt_locksOutForFiveMinutes() {
        repeat(7) { manager.recordFailedAttempt() }
        val duration = manager.recordFailedAttempt() // 8th fail
        assertEquals(300L, duration) // 300s = 5 min
    }

    @Test
    fun successfulAttempt_resetsLockoutAndCounter() {
        repeat(5) { manager.recordFailedAttempt() }
        assertTrue(manager.isLockedOut())

        manager.recordSuccessfulAttempt()
        assertFalse(manager.isLockedOut())
        assertEquals(0, manager.getFailedAttempts())
    }

    @Test
    fun rebootBypassAttempt_reappliesLockout() {
        repeat(5) { manager.recordFailedAttempt() }
        assertTrue(manager.isLockedOut())

        // Simulate reboot: bootCount increases, but elapsedRealtime resets (currentTime = 0)
        bootCount++
        currentTime = 1000L

        // Accessing the manager should detect the reboot and re-apply the lockout!
        assertTrue(manager.isLockedOut())
        assertEquals(30L, manager.getRemainingLockoutSeconds())
    }
}
