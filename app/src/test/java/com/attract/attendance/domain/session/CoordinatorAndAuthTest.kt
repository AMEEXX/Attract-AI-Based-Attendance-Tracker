package com.attract.attendance.domain.session

import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.domain.RecordPresentCommand
import com.attract.attendance.domain.face.AdaptiveVerificationEngine
import com.attract.attendance.domain.face.FrameObservation
import com.attract.attendance.domain.face.LivenessResult
import com.attract.attendance.domain.face.PoseBucket
import com.attract.attendance.domain.face.QualityResult
import com.attract.attendance.domain.face.StudentTemplatePair
import com.attract.attendance.test.FakeAttendanceRecordDao
import com.attract.attendance.test.FakeSessionDao
import com.attract.attendance.test.FakeStudentDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoordinatorAndAuthTest {

    private val sessionEntity = AttendanceSessionEntity(
        id = 10L,
        classId = 1L,
        sessionDate = "2026-10-02",
        timeZoneId = "UTC",
        mode = SessionMode.FACE,
        status = SessionStatus.ACTIVE,
        startedAt = 1000L,
        endedAt = null,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private val enrolledStudent = StudentEntity(
        id = 101L,
        classId = 1L,
        name = "Alice Enrolled",
        rollNumber = "CS-01",
        serialNumber = null,
        enrollmentStatus = EnrollmentStatus.ENROLLED,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private val unenrolledStudent = StudentEntity(
        id = 102L,
        classId = 1L,
        name = "Bob Unenrolled",
        rollNumber = "CS-02",
        serialNumber = null,
        enrollmentStatus = EnrollmentStatus.NOT_ENROLLED,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private val reenrollStudent = StudentEntity(
        id = 103L,
        classId = 1L,
        name = "Charlie ReEnroll",
        rollNumber = "CS-03",
        serialNumber = null,
        enrollmentStatus = EnrollmentStatus.REENROLL_REQUIRED,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    @Test
    fun candidateListForStudentEnrollment_containsOnlyNotEnrolled() {
        val allStudents = listOf(enrolledStudent, unenrolledStudent, reenrollStudent).map {
            StudentSummary(
                id = it.id,
                classId = it.classId,
                name = it.name,
                rollNumber = it.rollNumber,
                serialNumber = it.serialNumber,
                enrollmentStatus = it.enrollmentStatus,
            )
        }
        val presentIds = setOf<Long>()

        // R01 requirement: student enrollment candidate list must ONLY expose NOT_ENROLLED students
        val studentEnrollmentCandidates = allStudents.filter {
            it.enrollmentStatus == EnrollmentStatus.NOT_ENROLLED && it.id !in presentIds
        }

        assertEquals(1, studentEnrollmentCandidates.size)
        assertEquals("Bob Unenrolled", studentEnrollmentCandidates.first().name)
        assertFalse(studentEnrollmentCandidates.any { it.enrollmentStatus == EnrollmentStatus.ENROLLED })
        assertFalse(studentEnrollmentCandidates.any { it.enrollmentStatus == EnrollmentStatus.REENROLL_REQUIRED })

        // Authenticated teacher assistance roster exposes enrolled and re-enroll required students
        val teacherAssistanceRoster = allStudents.filter {
            (it.enrollmentStatus == EnrollmentStatus.ENROLLED || it.enrollmentStatus == EnrollmentStatus.REENROLL_REQUIRED) &&
                it.id !in presentIds
        }
        assertEquals(2, teacherAssistanceRoster.size)
        assertTrue(teacherAssistanceRoster.any { it.name == "Alice Enrolled" })
        assertTrue(teacherAssistanceRoster.any { it.name == "Charlie ReEnroll" })
    }

    @Test
    fun teacherAuthorizationGrant_validationRules() {
        val now = System.currentTimeMillis()
        val grant = TeacherAuthorizationGrant(
            sessionId = 10L,
            classId = 1L,
            studentId = 101L,
            action = TeacherAuthAction.TEACHER_ASSISTED_CHECKIN,
            interactionId = 42L,
            expiresAtMillis = now + 60_000L,
        )

        // Valid case
        assertTrue(
            grant.isValid(
                currentSessionId = 10L,
                currentClassId = 1L,
                targetStudentId = 101L,
                expectedAction = TeacherAuthAction.TEACHER_ASSISTED_CHECKIN,
                currentInteractionId = 42L,
                currentTimeMillis = now + 1000L,
            )
        )

        // Wrong student
        assertFalse(
            grant.isValid(
                currentSessionId = 10L,
                currentClassId = 1L,
                targetStudentId = 999L,
                expectedAction = TeacherAuthAction.TEACHER_ASSISTED_CHECKIN,
                currentInteractionId = 42L,
                currentTimeMillis = now + 1000L,
            )
        )

        // Wrong action
        assertFalse(
            grant.isValid(
                currentSessionId = 10L,
                currentClassId = 1L,
                targetStudentId = 101L,
                expectedAction = TeacherAuthAction.FIRST_ENROLLMENT,
                currentInteractionId = 42L,
                currentTimeMillis = now + 1000L,
            )
        )

        // Wrong interaction (e.g. next student stepped up)
        assertFalse(
            grant.isValid(
                currentSessionId = 10L,
                currentClassId = 1L,
                targetStudentId = 101L,
                expectedAction = TeacherAuthAction.TEACHER_ASSISTED_CHECKIN,
                currentInteractionId = 43L,
                currentTimeMillis = now + 1000L,
            )
        )

        // Expired grant
        assertFalse(
            grant.isValid(
                currentSessionId = 10L,
                currentClassId = 1L,
                targetStudentId = 101L,
                expectedAction = TeacherAuthAction.TEACHER_ASSISTED_CHECKIN,
                currentInteractionId = 42L,
                currentTimeMillis = now + 70_000L,
            )
        )
    }

    @Test
    fun terminalEngineCannotBeReused_andMustBeClearedBeforeNewAttempt() {
        val tpl = FloatArray(512) { 0.044194174f } // unit norm vector
        val templates = listOf(StudentTemplatePair(studentId = 101L, templateId = 1L, embedding = tpl))
        val engine = AdaptiveVerificationEngine(templates = templates)

        val obs = FrameObservation(
            signals = com.attract.attendance.domain.face.FaceQualitySignals(
                faceCount = 1,
                yawDegrees = 0f,
                pitchDegrees = 0f,
                rollDegrees = 0f,
                leftEyeOpenProbability = 0.99f,
                rightEyeOpenProbability = 0.99f,
                faceRatio = 0.25f,
                centerX = 0.5f,
                centerY = 0.5f,
                blurVariance = 150f,
                brightness = 120f,
            ),
            quality = QualityResult.Accepted(0.95f, PoseBucket.FRONTAL, 1),
            liveness = LivenessResult.Passed,
            embedding = tpl,
        )

        val step = engine.submit(obs)
        assertTrue(step is AdaptiveVerificationEngine.Step.Final)
        assertTrue(engine.isFinished)

        // Submitting to the same finished engine throws IllegalStateException
        var threw = false
        try {
            engine.submit(obs)
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("Submitting to finished engine must throw", threw)
    }

    @Test
    fun sessionCoordinator_deterministicRetryTransitions() = runTest {
        val sessionDao = FakeSessionDao(sessionEntity)
        val studentDao = FakeStudentDao(listOf(enrolledStudent, unenrolledStudent))
        val recordDao = FakeAttendanceRecordDao()
        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId = 10L, recordPresentCommand = command, classId = 1L)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId, initialPresentCount = 0))
        assertTrue(coordinator.state.value is SessionState.Ready)

        // Attempt 1: Unknown identity
        coordinator.processEvent(SessionEvent.CheckInPressed(attemptId))
        assertTrue(coordinator.state.value is SessionState.Acquiring)

        val quality = QualityResult.Accepted(0.95f, PoseBucket.FRONTAL, 1)
        coordinator.processEvent(SessionEvent.QualityEvaluated(attemptId, quality))
        assertTrue(coordinator.state.value is SessionState.Recognizing)

        // Recognition yields unknown (null matchedStudent)
        coordinator.processEvent(
            SessionEvent.RecognitionEvaluated(
                attemptId = attemptId,
                matchedStudent = null,
                confidence = 0f,
            )
        )

        // Should transition to RetryFeedback, not immediate Ready without attempt increment
        assertTrue(
            "State should be RetryFeedback",
            coordinator.state.value is SessionState.RetryFeedback
        )
        val feedback = coordinator.state.value as SessionState.RetryFeedback
        assertEquals(1, feedback.attemptIndex)
        assertEquals(2, feedback.maxAttempts)

        // When retry feedback timer expires: transitions to Ready with new attempt
        coordinator.processEvent(SessionEvent.RetryFeedbackExpired(attemptId))
        assertTrue(coordinator.state.value is SessionState.Ready)
        val newAttemptId = coordinator.currentAttemptId
        assertTrue(newAttemptId > attemptId)

        // Attempt 2: Another unknown identity -> triggers TeacherAssistance (exhausted retries)
        coordinator.processEvent(SessionEvent.CheckInPressed(newAttemptId))
        coordinator.processEvent(SessionEvent.QualityEvaluated(newAttemptId, quality))
        coordinator.processEvent(
            SessionEvent.RecognitionEvaluated(
                attemptId = newAttemptId,
                matchedStudent = null,
                confidence = 0f,
            )
        )

        assertTrue(
            "Exhausted retries must transition to TeacherAssistance",
            coordinator.state.value is SessionState.TeacherAssistance
        )
    }

    @Test
    fun sessionCoordinator_teacherAssistanceWithGrant() = runTest {
        val sessionDao = FakeSessionDao(sessionEntity)
        val studentDao = FakeStudentDao(listOf(enrolledStudent, unenrolledStudent))
        val recordDao = FakeAttendanceRecordDao()
        val command = RecordPresentCommand(sessionDao, studentDao, recordDao)
        val coordinator = SessionCoordinator(sessionId = 10L, recordPresentCommand = command, classId = 1L)

        val attemptId = coordinator.currentAttemptId
        coordinator.processEvent(SessionEvent.StartReady(attemptId, initialPresentCount = 0))

        // Request teacher assistance
        coordinator.processEvent(SessionEvent.TeacherAssistRequested(attemptId, "Student requested assistance"))
        assertTrue(coordinator.state.value is SessionState.TeacherAssistance)

        // Teacher authenticates and grants permission
        val grant = TeacherAuthorizationGrant(
            sessionId = 10L,
            classId = 1L,
            studentId = 101L,
            action = TeacherAuthAction.TEACHER_ASSISTED_CHECKIN,
            interactionId = attemptId,
            expiresAtMillis = System.currentTimeMillis() + 60_000L,
        )
        coordinator.processEvent(SessionEvent.TeacherApprovalGranted(attemptId, grant))
        assertTrue(coordinator.state.value is SessionState.AssistedActionSelection)
        val state = coordinator.state.value as SessionState.AssistedActionSelection
        assertEquals(grant, state.grant)
    }
}
