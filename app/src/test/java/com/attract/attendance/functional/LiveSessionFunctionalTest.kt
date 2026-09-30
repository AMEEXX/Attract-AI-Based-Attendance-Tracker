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
 * Stage 2 Functional Test: Live Session Lifecycle
 * Verifies integration between SessionCoordinator state machine, RecordPresentCommand,
 * and FinalizeFaceSessionCommand.
 */
class LiveSessionFunctionalTest {

    @Test
    fun liveSession_fullLifecycle_startRecognizeRecordAndFinalize() = runTest {
        val sessionId = 50L
        val sessionDao = FakeSessionDao(
            activeSession = AttendanceSessionEntity(
                id = sessionId,
                classId = 10L,
                sessionDate = "2026-08-08",
                timeZoneId = "UTC",
                startedAt = 2000L,
                endedAt = null,
                status = SessionStatus.ACTIVE,
                mode = SessionMode.FACE,
                createdAt = 2000L,
                updatedAt = 2000L
            )
        )
        val studentEntity = StudentEntity(
            id = 1L,
            classId = 10L,
            name = "Alice Smith",
            rollNumber = "CS-101",
            serialNumber = "S1",
            enrollmentStatus = EnrollmentStatus.ENROLLED,
            enrolledAt = 1000L,
            eligibleFromSessionId = null,
            archived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val studentSummary = StudentSummary(
            id = 1L,
            classId = 10L,
            name = "Alice Smith",
            rollNumber = "CS-101",
            serialNumber = "S1",
            enrollmentStatus = EnrollmentStatus.ENROLLED
        )
        val studentDao = FakeStudentDao(listOf(studentEntity))
        val recordDao = FakeAttendanceRecordDao()

        val recordPresentCommand = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId, recordPresentCommand)

        val attemptId = coordinator.currentAttemptId
        assertTrue(coordinator.state.value is SessionState.Initializing)

        // Step 1: Start Ready
        coordinator.processEvent(SessionEvent.StartReady(attemptId = attemptId, initialPresentCount = 0))
        assertTrue(coordinator.state.value is SessionState.Ready)

        // Step 2: Check-in Pressed -> Acquiring
        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId = attemptId))
        assertTrue(coordinator.state.value is SessionState.Acquiring)

        // Step 3: Quality Accepted -> Recognizing
        val qualityResult = QualityResult.Accepted(score = 0.98f, poseBucket = PoseBucket.FRONTAL, configVersion = 1)
        coordinator.processEvent(SessionEvent.QualityEvaluated(attemptId = attemptId, result = qualityResult))
        assertTrue(coordinator.state.value is SessionState.Recognizing)

        // Step 4: Recognition Evaluated -> Persisting & Success Feedback
        coordinator.processEvent(
            SessionEvent.RecognitionEvaluated(
                attemptId = attemptId,
                matchedStudent = studentSummary,
                confidence = 0.95f
            )
        )

        assertTrue(coordinator.state.value is SessionState.SuccessFeedback)
        assertEquals(1, coordinator.currentPresentCount)

        val records = recordDao.forSession(sessionId)
        assertEquals(1, records.size)
        assertEquals(1L, records[0].studentId)
        assertEquals(AttendanceStatus.PRESENT, records[0].status)

        // Step 5: Finalize Face Session Command
        val finalizeCommand = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)
        val finalizeResult = finalizeCommand.execute(sessionId = sessionId, timestamp = 3000L)

        assertTrue(finalizeResult is com.attract.attendance.domain.FinalizeSessionResult.Success)

        // Step 6: End session in coordinator via AuthOutcome
        coordinator.processEvent(SessionEvent.EndRequested(attemptId = attemptId))
        assertTrue(coordinator.state.value is SessionState.Ending)

        coordinator.processEvent(SessionEvent.AuthOutcome(attemptId = attemptId, authResult = AuthResult.Success(AuthMethod.PIN)))
        assertTrue(coordinator.state.value is SessionState.Ended)
    }
}
