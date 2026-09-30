package com.attract.attendance.lockdown.platform

import android.util.Log
import com.attract.attendance.lockdown.domain.SecurityEvent
import com.attract.attendance.lockdown.domain.SecurityEventLogger

class SecurityEventLoggerImpl(
    private val tag: String = "LockdownSecurity",
) : SecurityEventLogger {

    override fun log(event: SecurityEvent) {
        val safeMessage = buildString {
            append("category=${event.category}")
            append(", result=${event.result}")
            append(", device=${event.deviceInfo}")
            event.safeErrorCode?.let { append(", code=$it") }
        }
        Log.i(tag, safeMessage)
    }
}
