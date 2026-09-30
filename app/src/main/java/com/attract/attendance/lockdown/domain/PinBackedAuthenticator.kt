package com.attract.attendance.lockdown.domain

import com.attract.attendance.data.security.PinHasher

class PinBackedAuthenticator(
    private val pinHasher: PinHasher,
    private val storedPinHash: String,
) : TeacherAuthenticator {

    var attemptCount = 0
        private set

    override suspend fun authenticate(reason: AuthReason): AuthResult {
        attemptCount++
        return AuthResult.Failed
    }

    fun verifyPin(pin: CharArray): Boolean {
        attemptCount++
        return try {
            pinHasher.verify(pin, storedPinHash)
        } finally {
            pin.fill('\u0000')
        }
    }
}
