package com.attract.attendance.feature.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.domain.RecordPresentCommand
import com.attract.attendance.domain.session.SessionCoordinator
import com.attract.attendance.domain.session.SessionEvent
import com.attract.attendance.domain.session.SessionState
import com.attract.attendance.test.FakeAttendanceRecordDao
import com.attract.attendance.test.FakeSessionDao
import com.attract.attendance.test.FakeStudentDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IT-03: FaceSessionE2EAndroidTest
 * Instrumented test verifying face attendance session lifecycle with injected fake analysis results on Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class FaceSessionE2EAndroidTest {

    private val sessionId = 200L
    private val classId = 20L

    private val activeSession = AttendanceSessionEntity(
        id = sessionId,
        classId = classId,
        sessionDate = "2026-08-08",
        timeZoneId = "UTC",
        startedAt = 1000L,
        endedAt = null,
        status = SessionStatus.ACTIVE,
        mode = SessionMode.FACE,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val studentEntity = StudentEntity(10L, classId, "Android Student", "CS-200", "S200", EnrollmentStatus.ENROLLED, 1000L, null, false, 1000L, 1000L)
    private val studentSummary = StudentSummary(10L, classId, "Android Student", "CS-200", "S200", EnrollmentStatus.ENROLLED)

    @Test
    fun sessionCheckIn_faceRecognized_attendanceMarked() = runBlocking {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(studentEntity))
        val recordDao = FakeAttendanceRecordDao()
        val recordCommand = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId, recordCommand)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId, 0))
        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId))
        coordinator.processEvent(
            SessionEvent.QualityEvaluated(
                attemptId,
                com.attract.attendance.domain.face.QualityResult.Accepted(0.98f, com.attract.attendance.domain.face.PoseBucket.FRONTAL, 1)
            )
        )
        coordinator.processEvent(SessionEvent.RecognitionEvaluated(attemptId, studentSummary, 0.98f))

        assertTrue(coordinator.state.value is SessionState.SuccessFeedback)
        val records = recordDao.forSession(sessionId)
        assertEquals(1, records.size)
        assertEquals(AttendanceStatus.PRESENT, records[0].status)
    }
}
