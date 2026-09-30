package com.attract.attendance.domain

import com.attract.attendance.core.model.AttendancePercentage

object AttendanceRules {
    fun isEligible(eligibleFromSessionId: Long?, sessionId: Long): Boolean =
        eligibleFromSessionId == null || eligibleFromSessionId <= sessionId

    fun percentage(presentSessions: Int, eligibleSessions: Int): AttendancePercentage {
        require(presentSessions >= 0) { "Present sessions cannot be negative." }
        require(eligibleSessions >= 0) { "Eligible sessions cannot be negative." }
        require(presentSessions <= eligibleSessions) { "Present sessions cannot exceed eligible sessions." }
        return AttendancePercentage(presentSessions, eligibleSessions)
    }
}
