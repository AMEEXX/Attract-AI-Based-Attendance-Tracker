package com.attract.attendance.lockdown.domain

class FakeTeacherAuthenticator : TeacherAuthenticator {

    var nextResult: AuthResult = AuthResult.Failed
    var lastReason: AuthReason? = null
    var callCount = 0
    var promptShouldCancel = false

    override suspend fun authenticate(reason: AuthReason): AuthResult {
        lastReason = reason
        callCount++
        return nextResult
    }

    fun reset() {
        nextResult = AuthResult.Failed
        lastReason = null
        callCount = 0
        promptShouldCancel = false
    }
}
