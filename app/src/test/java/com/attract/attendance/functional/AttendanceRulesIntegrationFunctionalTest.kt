package com.attract.attendance.functional

import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.ClassReportStudentRow
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.domain.AttendanceRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FT-05: AttendanceRulesIntegrationFunctionalTest
 * Verifies the attendance eligibility and percentage rule engine (LLD-05) works correctly
 * when integrated across multiple session/enrollment scenarios.
 */
class AttendanceRulesIntegrationFunctionalTest {

    @Test
    fun eligibility_enrolledAfterSessionStart_notCountedAsAbsent() {
        val studentJoinedAtSession3 = StudentEntity(
            id = 1L,
            classId = 10L,
            name = "Late Joiner",
            rollNumber = "CS-99",
            serialNumber = null,
            enrollmentStatus = EnrollmentStatus.ENROLLED,
            enrolledAt = 3000L,
            eligibleFromSessionId = 3L,
            archived = false,
            createdAt = 3000L,
            updatedAt = 3000L
        )

        assertFalse(AttendanceRules.isEligible(studentJoinedAtSession3.eligibleFromSessionId, sessionId = 1L))
        assertFalse(AttendanceRules.isEligible(studentJoinedAtSession3.eligibleFromSessionId, sessionId = 2L))
        assertTrue(AttendanceRules.isEligible(studentJoinedAtSession3.eligibleFromSessionId, sessionId = 3L))
        assertTrue(AttendanceRules.isEligible(studentJoinedAtSession3.eligibleFromSessionId, sessionId = 4L))
    }

    @Test
    fun eligibility_enrolledBeforeFirstSession_countedFromFirst() {
        val studentFromStart = StudentEntity(
            id = 2L,
            classId = 10L,
            name = "Early Joiner",
            rollNumber = "CS-01",
            serialNumber = null,
            enrollmentStatus = EnrollmentStatus.ENROLLED,
            enrolledAt = 1000L,
            eligibleFromSessionId = null,
            archived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        assertTrue(AttendanceRules.isEligible(studentFromStart.eligibleFromSessionId, sessionId = 1L))
        assertTrue(AttendanceRules.isEligible(studentFromStart.eligibleFromSessionId, sessionId = 10L))
    }

    @Test
    fun absentMarking_somePresentSomeAbsent_correctPercent() {
        val presentCount = 7
        val eligibleCount = 10
        val percentage = AttendanceRules.percentage(presentCount, eligibleCount)

        assertEquals("70.0%", percentage.display)
        assertEquals(7, percentage.presentSessions)
        assertEquals(10, percentage.eligibleSessions)
    }

    @Test
    fun lateJoiner_percentBasedOnEligibleOnly() {
        val presentCount = 4
        val eligibleCount = 5 // Total class sessions = 10, but student was eligible for only 5
        val percentage = AttendanceRules.percentage(presentCount, eligibleCount)

        assertEquals("80.0%", percentage.display)
        assertEquals(4, percentage.presentSessions)
        assertEquals(5, percentage.eligibleSessions)
    }

    @Test
    fun multipleSessionSameDay_countedSeparately() {
        val morningSession = AttendanceSessionEntity(
            id = 101L, classId = 10L, sessionDate = "2026-08-08", timeZoneId = "UTC", startedAt = 1000L, endedAt = 2000L, status = SessionStatus.ENDED, mode = SessionMode.FACE, createdAt = 1000L, updatedAt = 2000L
        )
        val afternoonSession = AttendanceSessionEntity(
            id = 102L, classId = 10L, sessionDate = "2026-08-08", timeZoneId = "UTC", startedAt = 5000L, endedAt = 6000L, status = SessionStatus.ENDED, mode = SessionMode.FACE, createdAt = 5000L, updatedAt = 6000L
        )

        val studentId = 5L
        val morningRecord = AttendanceRecordEntity(1L, morningSession.id, studentId, AttendanceStatus.PRESENT, 1500L, com.attract.attendance.core.model.AttendanceSource.AI_RECOGNITION, 0.95f, null, 1500L, 1500L)
        val afternoonRecord = AttendanceRecordEntity(2L, afternoonSession.id, studentId, AttendanceStatus.ABSENT, null, com.attract.attendance.core.model.AttendanceSource.AI_RECOGNITION, null, null, 6000L, 6000L)

        val records = listOf(morningRecord, afternoonRecord)
        val presentCount = records.count { it.status == AttendanceStatus.PRESENT }
        val eligibleCount = records.size

        val percentage = AttendanceRules.percentage(presentCount, eligibleCount)
        assertEquals("50.0%", percentage.display)
    }
}
