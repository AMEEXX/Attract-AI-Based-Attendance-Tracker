package com.attract.attendance.domain

import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.test.FakeAttendanceRecordDao
import com.attract.attendance.test.FakeSessionDao
import com.attract.attendance.test.FakeStudentDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class RecordPresentCommandTest {

    private val activeSession = AttendanceSessionEntity(
        id = 10L,
        classId = 1L,
        sessionDate = "2026-08-08",
        timeZoneId = "UTC",
        mode = SessionMode.FACE,
        status = SessionStatus.ACTIVE,
        startedAt = 100L,
        createdAt = 100L,
        updatedAt = 100L,
    )

    private val student = StudentEntity(
        id = 100L,
        classId = 1L,
        name = "Alice",
        rollNumber = "CS-01",
        serialNumber = null,
        enrollmentStatus = EnrollmentStatus.ENROLLED,
        eligibleFromSessionId = null,
        createdAt = 100L,
        updatedAt = 100L,
    )

    @Test
    fun execute_validStudent_returnsSuccess() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(student))
        val recordDao = FakeAttendanceRecordDao()

        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val result = command.execute(sessionId = 10L, studentId = 100L, method = AttendanceSource.AI_RECOGNITION)

        assertTrue(result is RecordPresentResult.Success)
        assertEquals(1, recordDao.all().size)
        assertEquals(AttendanceStatus.PRESENT, recordDao.all()[0].status)
    }

    @Test
    fun execute_sessionNotActive_returnsSessionNotActive() = runTest {
        val endedSession = activeSession.copy(status = SessionStatus.ENDED)
        val sessionDao = FakeSessionDao(endedSession)
        val studentDao = FakeStudentDao(listOf(student))
        val recordDao = FakeAttendanceRecordDao()

        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val result = command.execute(sessionId = 10L, studentId = 100L, method = AttendanceSource.AI_RECOGNITION)

        assertEquals(RecordPresentResult.SessionNotActive, result)
        assertTrue(recordDao.all().isEmpty())
    }

    @Test
    fun execute_studentNotEligibleDueToSessionBoundary_returnsNotEligible() = runTest {
        val ineligibleStudent = student.copy(eligibleFromSessionId = 15L) // Session is 10 < 15
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(ineligibleStudent))
        val recordDao = FakeAttendanceRecordDao()

        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val result = command.execute(sessionId = 10L, studentId = 100L, method = AttendanceSource.AI_RECOGNITION)

        assertEquals(RecordPresentResult.StudentNotEligible, result)
    }

    @Test
    fun execute_alreadyPresent_returnsAlreadyPresent() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(student))
        val recordDao = FakeAttendanceRecordDao()

        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        command.execute(sessionId = 10L, studentId = 100L, method = AttendanceSource.AI_RECOGNITION)

        val secondResult = command.execute(sessionId = 10L, studentId = 100L, method = AttendanceSource.AI_RECOGNITION)

        assertEquals(RecordPresentResult.AlreadyPresent, secondResult)
    }
}

