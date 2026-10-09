package com.attract.attendance.lockdown.domain

import com.attract.attendance.data.security.PinHasher
import com.attract.attendance.data.security.PinLockoutManager

class PinBackedAuthenticator(
    private val pinHasher: PinHasher,
    private val storedPinHash: String,
    private val lockoutManager: PinLockoutManager? = null,
) : TeacherAuthenticator {

    private var _inMemoryAttempts = 0

    val attemptCount: Int
        get() = lockoutManager?.getFailedAttempts() ?: _inMemoryAttempts

    override suspend fun authenticate(reason: AuthReason): AuthResult {
        recordAttempt(false)
        return AuthResult.Failed
    }

    fun verifyPin(pin: CharArray): Boolean {
        if (lockoutManager?.isLockedOut() == true) {
            pin.fill('\u0000')
            return false
        }
        val success = try {
            pinHasher.verify(pin, storedPinHash)
        } finally {
            pin.fill('\u0000')
        }
        recordAttempt(success)
        return success
    }

    private fun recordAttempt(success: Boolean) {
        if (lockoutManager != null) {
            if (success) {
                lockoutManager.recordSuccessfulAttempt()
            } else {
                lockoutManager.recordFailedAttempt()
            }
        } else {
            _inMemoryAttempts++
        }
    }
}
