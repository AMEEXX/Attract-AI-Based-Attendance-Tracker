package com.attract.attendance.domain

import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.test.FakeAttendanceRecordDao
import com.attract.attendance.test.FakeSessionDao
import com.attract.attendance.test.FakeStudentDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizeFaceSessionCommandTest {

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

    private val student1 = StudentEntity(
        id = 101L,
        classId = 1L,
        name = "Alice",
        rollNumber = "CS-01",
        serialNumber = null,
        enrollmentStatus = EnrollmentStatus.ENROLLED,
        createdAt = 100L,
        updatedAt = 100L,
    )

    private val student2 = StudentEntity(
        id = 102L,
        classId = 1L,
        name = "Bob",
        rollNumber = "CS-02",
        serialNumber = null,
        enrollmentStatus = EnrollmentStatus.ENROLLED,
        createdAt = 100L,
        updatedAt = 100L,
    )

    @Test
    fun execute_unrecordedStudentsAreMarkedAbsentAndSessionEnded() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(student1, student2))
        val recordDao = FakeAttendanceRecordDao()

        // Mark student 1 PRESENT first
        recordDao.insert(
            AttendanceRecordEntity(
                sessionId = 10L,
                studentId = 101L,
                status = AttendanceStatus.PRESENT,
                checkInTime = 150L,
                attendanceMethod = com.attract.attendance.core.model.AttendanceSource.AI_RECOGNITION,
                createdAt = 150L,
                updatedAt = 150L,
            )
        )

        val command = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)
        val result = command.execute(sessionId = 10L)

        assertTrue(result is FinalizeSessionResult.Success)
        val success = result as FinalizeSessionResult.Success
        assertEquals(1, success.presentCount)
        assertEquals(1, success.absentCount) // Student 2 auto-marked ABSENT

        val allRecords = recordDao.all()
        assertEquals(2, allRecords.size)
        val bobRecord = allRecords.find { it.studentId == 102L }
        assertEquals(AttendanceStatus.ABSENT, bobRecord?.status)
    }

    @Test
    fun execute_sessionNotActive_returnsError() = runTest {
        val endedSession = activeSession.copy(status = SessionStatus.ENDED)
        val sessionDao = FakeSessionDao(endedSession)
        val studentDao = FakeStudentDao(listOf(student1))
        val recordDao = FakeAttendanceRecordDao()

        val command = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)
        val result = command.execute(sessionId = 10L)

        assertEquals(FinalizeSessionResult.SessionNotFoundOrEnded, result)
    }

    @Test
    fun execute_partialAbsentScenario_somePresentRestNeedAbsentFinalization() = runTest {
        val student3 = student1.copy(id = 103L, rollNumber = "CS-03", name = "Charlie")
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(student1, student2, student3))
        val recordDao = FakeAttendanceRecordDao()

        // Student 1 and 3 check in
        recordDao.insert(
            AttendanceRecordEntity(
                sessionId = 10L,
                studentId = 101L,
                status = AttendanceStatus.PRESENT,
                checkInTime = 150L,
                attendanceMethod = com.attract.attendance.core.model.AttendanceSource.AI_RECOGNITION,
                createdAt = 150L,
                updatedAt = 150L
            )
        )
        recordDao.insert(
            AttendanceRecordEntity(
                sessionId = 10L,
                studentId = 103L,
                status = AttendanceStatus.PRESENT,
                checkInTime = 160L,
                attendanceMethod = com.attract.attendance.core.model.AttendanceMethod.MANUAL,
                createdAt = 160L,
                updatedAt = 160L
            )
        )

        val command = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)
        val result = command.execute(sessionId = 10L)

        assertTrue(result is FinalizeSessionResult.Success)
        val success = result as FinalizeSessionResult.Success
        assertEquals(2, success.presentCount)
        assertEquals(1, success.absentCount)

        val records = recordDao.all()
        assertEquals(3, records.size)
        assertEquals(AttendanceStatus.PRESENT, records.find { it.studentId == 101L }?.status)
        assertEquals(AttendanceStatus.ABSENT, records.find { it.studentId == 102L }?.status)
        assertEquals(AttendanceStatus.PRESENT, records.find { it.studentId == 103L }?.status)
    }
}

