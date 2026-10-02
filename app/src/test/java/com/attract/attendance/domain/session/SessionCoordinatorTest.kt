package com.attract.attendance.domain.session

import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.data.local.AttendanceRecordDao
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.SessionDao
import com.attract.attendance.data.local.StudentDao
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.domain.RecordPresentCommand
import com.attract.attendance.lockdown.domain.AuthMethod
import com.attract.attendance.lockdown.domain.AuthResult

import com.attract.attendance.test.FakeAttendanceRecordDao
import com.attract.attendance.test.FakeSessionDao
import com.attract.attendance.test.FakeStudentDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCoordinatorTest {

    private val activeSession = AttendanceSessionEntity(
        id = 10,
        classId = 1,
        sessionDate = "2026-08-08",
        timeZoneId = "UTC",
        mode = SessionMode.FACE,
        status = SessionStatus.ACTIVE,
        startedAt = 100,
        endedAt = null,
        createdAt = 100,
        updatedAt = 100,
    )

    private val student = StudentEntity(
        id = 100,
        classId = 1,
        name = "John Doe",
        rollNumber = "CS-101",
        serialNumber = null,
        enrollmentStatus = EnrollmentStatus.ENROLLED,
        createdAt = 100,
        updatedAt = 100,
    )

    private val studentSummary = StudentSummary(
        id = 100,
        classId = 1,
        name = "John Doe",
        rollNumber = "CS-101",
        serialNumber = null,
        enrollmentStatus = EnrollmentStatus.ENROLLED,
    )

    @Test
    fun startReady_transitionsToReadyState() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(student))
        val recordDao = FakeAttendanceRecordDao()
        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(10L, command)

        coordinator.processEvent(SessionEvent.StartReady(attemptId = coordinator.currentAttemptId, initialPresentCount = 5))

        assertTrue(coordinator.state.value is SessionState.Ready)
        assertEquals(5, (coordinator.state.value as SessionState.Ready).presentCount)
    }

    @Test
    fun fullCheckInFlow_recordsPresentAndTransitionsToSuccessFeedback() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(student))
        val recordDao = FakeAttendanceRecordDao()
        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(10L, command)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId = attemptId, initialPresentCount = 0))
        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId = attemptId))
        assertTrue(coordinator.state.value is SessionState.Acquiring)

        val qualityResult = com.attract.attendance.domain.face.QualityResult.Accepted(
            score = 0.95f,
            poseBucket = com.attract.attendance.domain.face.PoseBucket.FRONTAL,
            configVersion = 1,
        )
        coordinator.processEvent(SessionEvent.QualityEvaluated(attemptId = attemptId, result = qualityResult))
        assertTrue(coordinator.state.value is SessionState.Recognizing)

        coordinator.processEvent(
            SessionEvent.RecognitionEvaluated(
                attemptId = attemptId,
                matchedStudent = studentSummary,
                confidence = 0.95f,
            )
        )

        assertTrue(coordinator.state.value is SessionState.SuccessFeedback)
        val feedback = coordinator.state.value as SessionState.SuccessFeedback
        assertEquals("John Doe", feedback.studentName)
        assertEquals(1, coordinator.currentPresentCount)
    }

    @Test
    fun staleAttemptIdEvent_isDiscarded() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(student))
        val recordDao = FakeAttendanceRecordDao()
        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(10L, command)

        coordinator.processEvent(SessionEvent.StartReady(attemptId = coordinator.currentAttemptId, initialPresentCount = 0))
        val staleAttemptId = coordinator.currentAttemptId
        coordinator.nextAttempt()

        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId = staleAttemptId))

        assertTrue(coordinator.state.value is SessionState.Ready)
    }

    @Test
    fun endRequestedAndAuthSuccess_transitionsToEnded() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(student))
        val recordDao = FakeAttendanceRecordDao()
        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(10L, command)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId = attemptId, initialPresentCount = 3))
        coordinator.processEvent(SessionEvent.EndRequested(attemptId = attemptId))

        assertTrue(coordinator.state.value is SessionState.Ending)

        coordinator.processEvent(
            SessionEvent.AuthOutcome(
                attemptId = attemptId,
                authResult = AuthResult.Success(AuthMethod.PIN)
            )
        )

        assertTrue(coordinator.state.value is SessionState.Ended)
        val ended = coordinator.state.value as SessionState.Ended
        assertEquals(3, ended.finalPresentCount)
    }

    @Test
    fun twoRetryExhaustion_triggersTeacherAssistancePath() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(student))
        val recordDao = FakeAttendanceRecordDao()
        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(10L, command)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId = attemptId, initialPresentCount = 0))

        // Attempt 1: Unknown identity
        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId = attemptId))
        val qualityResult = com.attract.attendance.domain.face.QualityResult.Accepted(
            score = 0.95f,
            poseBucket = com.attract.attendance.domain.face.PoseBucket.FRONTAL,
            configVersion = 1
        )
        coordinator.processEvent(SessionEvent.QualityEvaluated(attemptId = attemptId, result = qualityResult))
        coordinator.processEvent(SessionEvent.RecognitionEvaluated(attemptId = attemptId, matchedStudent = null, confidence = 0.0f))
        assertTrue(coordinator.state.value is SessionState.RetryFeedback)
        coordinator.processEvent(SessionEvent.RetryFeedbackExpired(attemptId = attemptId))
        assertTrue(coordinator.state.value is SessionState.Ready)

        // Attempt 2: Unknown identity -> Triggers TeacherAssistance
        val secondAttemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId = secondAttemptId))
        coordinator.processEvent(SessionEvent.QualityEvaluated(attemptId = secondAttemptId, result = qualityResult))
        coordinator.processEvent(SessionEvent.RecognitionEvaluated(attemptId = secondAttemptId, matchedStudent = null, confidence = 0.0f))

        assertTrue(coordinator.state.value is SessionState.TeacherAssistance)
        val assistance = coordinator.state.value as SessionState.TeacherAssistance
        assertEquals("Unrecognized face after multiple attempts", assistance.reason)
    }
}

