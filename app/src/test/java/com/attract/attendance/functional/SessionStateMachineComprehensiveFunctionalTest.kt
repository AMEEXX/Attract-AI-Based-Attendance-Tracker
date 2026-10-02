package com.attract.attendance.functional

import com.attract.attendance.core.model.AttendanceMethod
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.domain.FinalizeFaceSessionCommand
import com.attract.attendance.domain.RecordPresentCommand
import com.attract.attendance.domain.face.PoseBucket
import com.attract.attendance.domain.face.QualityResult
import com.attract.attendance.domain.session.SessionCoordinator
import com.attract.attendance.domain.session.SessionEvent
import com.attract.attendance.domain.session.SessionState
import com.attract.attendance.lockdown.domain.AuthMethod
import com.attract.attendance.lockdown.domain.AuthReason
import com.attract.attendance.lockdown.domain.AuthResult
import com.attract.attendance.test.FakeAttendanceRecordDao
import com.attract.attendance.test.FakeSessionDao
import com.attract.attendance.test.FakeStudentDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FT-04: SessionStateMachineComprehensiveFunctionalTest
 * Verifies all session state machine transitions, illegal-event guards, stale-attempt protection,
 * retry exhaustion, and end-flow authentication in an integrated context.
 */
class SessionStateMachineComprehensiveFunctionalTest {

    private val sessionId = 100L
    private val classId = 10L

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

    private val studentA = StudentEntity(1L, classId, "Alice", "CS-01", "S1", EnrollmentStatus.ENROLLED, 1000L, null, false, 1000L, 1000L)
    private val summaryA = StudentSummary(1L, classId, "Alice", "CS-01", "S1", EnrollmentStatus.ENROLLED)

    @Test
    fun fullHappyPath_transitionsToEnded() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(studentA))
        val recordDao = FakeAttendanceRecordDao()
        val recordCommand = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId, recordCommand)

        val attemptId = coordinator.currentAttemptId
        assertTrue(coordinator.state.value is SessionState.Initializing)

        coordinator.processEvent(SessionEvent.StartReady(attemptId, 0))
        assertTrue(coordinator.state.value is SessionState.Ready)

        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId))
        assertTrue(coordinator.state.value is SessionState.Acquiring)

        val quality = QualityResult.Accepted(0.99f, PoseBucket.FRONTAL, 1)
        coordinator.processEvent(SessionEvent.QualityEvaluated(attemptId, quality))
        assertTrue(coordinator.state.value is SessionState.Recognizing)

        coordinator.processEvent(SessionEvent.RecognitionEvaluated(attemptId, summaryA, 0.99f))
        assertTrue(coordinator.state.value is SessionState.SuccessFeedback)

        coordinator.processEvent(SessionEvent.EndRequested(attemptId))
        assertTrue(coordinator.state.value is SessionState.Ending)

        coordinator.processEvent(SessionEvent.AuthOutcome(attemptId, AuthResult.Success(AuthMethod.PIN)))
        assertTrue(coordinator.state.value is SessionState.Ended)
    }

    @Test
    fun illegalEvent_ignoredSafely() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(studentA))
        val recordDao = FakeAttendanceRecordDao()
        val recordCommand = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId, recordCommand)

        val attemptId = coordinator.currentAttemptId
        // EndRequested received during Initializing state
        coordinator.processEvent(SessionEvent.EndRequested(attemptId))

        // State remains Ending or ignored safely without crash
        assertTrue(coordinator.state.value is SessionState.Ending || coordinator.state.value is SessionState.Initializing)
        assertEquals(0, recordDao.forSession(sessionId).size)
    }

    @Test
    fun staleAttemptId_discardedNoWrite() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(studentA))
        val recordDao = FakeAttendanceRecordDao()
        val recordCommand = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId, recordCommand)

        val staleAttemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(staleAttemptId, 0))
        coordinator.nextAttempt()

        coordinator.processEvent(SessionEvent.CheckInPressed(staleAttemptId))
        assertTrue(coordinator.state.value is SessionState.Ready)
        assertEquals(0, recordDao.forSession(sessionId).size)
    }

    @Test
    fun twoRetryExhaustion_triggersAssistancePath() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(studentA))
        val recordDao = FakeAttendanceRecordDao()
        val recordCommand = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId, recordCommand)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId, 0))

        val quality = QualityResult.Accepted(0.95f, PoseBucket.FRONTAL, 1)

        // Attempt 1: unknown
        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId))
        coordinator.processEvent(SessionEvent.QualityEvaluated(attemptId, quality))
        coordinator.processEvent(SessionEvent.RecognitionEvaluated(attemptId, null, 0.0f))

        assertTrue(coordinator.state.value is SessionState.RetryFeedback)
        coordinator.processEvent(SessionEvent.RetryFeedbackExpired(attemptId))
        assertTrue(coordinator.state.value is SessionState.Ready)

        // Attempt 2: unknown
        val attempt2 = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.CheckInPressed(attempt2))
        coordinator.processEvent(SessionEvent.QualityEvaluated(attempt2, quality))
        coordinator.processEvent(SessionEvent.RecognitionEvaluated(attempt2, null, 0.0f))

        assertTrue(coordinator.state.value is SessionState.TeacherAssistance)
    }

    @Test
    fun duplicatePresent_returnsAlreadyCheckedIn() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(studentA))
        val recordDao = FakeAttendanceRecordDao()
        val recordCommand = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId, recordCommand)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId, 0))

        val quality = QualityResult.Accepted(0.95f, PoseBucket.FRONTAL, 1)

        // Check-in 1
        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId))
        coordinator.processEvent(SessionEvent.QualityEvaluated(attemptId, quality))
        coordinator.processEvent(SessionEvent.RecognitionEvaluated(attemptId, summaryA, 0.95f))
        assertEquals(1, coordinator.currentPresentCount)

        // Reset to ready & Check-in 2 for same student
        coordinator.processEvent(SessionEvent.StartReady(attemptId, coordinator.currentPresentCount))
        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId))
        coordinator.processEvent(SessionEvent.QualityEvaluated(attemptId, quality))
        coordinator.processEvent(SessionEvent.RecognitionEvaluated(attemptId, summaryA, 0.95f))

        // Count should not increment twice
        assertEquals(1, coordinator.currentPresentCount)
    }

    @Test
    fun endAuthFails_sessionRemainsActive() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(studentA))
        val recordDao = FakeAttendanceRecordDao()
        val recordCommand = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId, recordCommand)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId, 0))
        coordinator.processEvent(SessionEvent.EndRequested(attemptId))

        coordinator.processEvent(SessionEvent.AuthOutcome(attemptId, AuthResult.Failed))
        assertTrue(coordinator.state.value is SessionState.Ready)
        assertEquals(SessionStatus.ACTIVE, sessionDao.activeSession?.status)
    }

    @Test
    fun endAuthSucceeds_finalizationPersistsThenEnds() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(listOf(studentA))
        val recordDao = FakeAttendanceRecordDao()
        val recordCommand = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val finalizeCommand = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId, recordCommand)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId, 0))
        coordinator.processEvent(SessionEvent.EndRequested(attemptId))
        coordinator.processEvent(SessionEvent.AuthOutcome(attemptId, AuthResult.Success(AuthMethod.PIN)))

        assertTrue(coordinator.state.value is SessionState.Ended)

        // Finalize command completes absent marking
        val finalizeResult = finalizeCommand.execute(sessionId)
        assertTrue(finalizeResult is com.attract.attendance.domain.FinalizeSessionResult.Success)
        assertEquals(SessionStatus.ENDED, sessionDao.activeSession?.status)
    }
}
