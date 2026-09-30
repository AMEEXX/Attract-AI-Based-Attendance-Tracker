package com.attract.attendance.lockdown.domain

import java.time.Instant

enum class SecurityEventCategory {
    AUTH_ATTEMPT,
    AUTH_SUCCESS,
    AUTH_FAILED,
    AUTH_CANCELLED,
    TAMPER_DETECTED,
    KEY_INVALIDATED,
    CRYPTO_FAILURE,
    SESSION_START,
    SESSION_END,
}

data class SecurityEvent(
    val category: SecurityEventCategory,
    val result: String,
    val deviceInfo: String,
    val timestamp: Instant = Instant.now(),
    val safeErrorCode: String? = null,
)

interface SecurityEventLogger {
    fun log(event: SecurityEvent)
}
