package com.attract.attendance.functional

import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.domain.FinalizeFaceSessionCommand
import com.attract.attendance.domain.session.RecoveryEndResult
import com.attract.attendance.domain.session.RecoveryResumeResult
import com.attract.attendance.domain.session.SessionRecoveryManager
import com.attract.attendance.lockdown.domain.AuthMethod
import com.attract.attendance.lockdown.domain.AuthResult
import com.attract.attendance.lockdown.domain.FakeDeviceSecurityChecker
import com.attract.attendance.lockdown.domain.FakeLockTaskController
import com.attract.attendance.lockdown.domain.FakeTeacherAuthenticator
import com.attract.attendance.lockdown.domain.SetupCheck
import com.attract.attendance.test.FakeAttendanceRecordDao
import com.attract.attendance.test.FakeSessionDao
import com.attract.attendance.test.FakeStudentDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 2 Functional Test: Session Crash & Disaster Recovery
 * Verifies SessionRecoveryManager state resolution when an active face attendance session
 * exists across process restarts.
 */
class SessionRecoveryFunctionalTest {

    @Test
    fun crashRecovery_activeSessionFound_resumesSessionWhenAuthAndSecurityPass() = runTest {
        val activeSession = AttendanceSessionEntity(
            id = 99L,
            classId = 5L,
            sessionDate = "2026-08-08",
            timeZoneId = "UTC",
            startedAt = 1000L,
            endedAt = null,
            status = SessionStatus.ACTIVE,
            mode = SessionMode.FACE,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val sessionDao = FakeSessionDao(activeSession = activeSession)
        val studentDao = FakeStudentDao()
        val recordDao = FakeAttendanceRecordDao()
        val checker = FakeDeviceSecurityChecker().apply {
            SetupCheck.entries.forEach { add(it) }
        }
        val lockTaskController = FakeLockTaskController()
        val authenticator = FakeTeacherAuthenticator().apply {
            nextResult = AuthResult.Success(AuthMethod.PIN)
        }
        val finalizeCommand = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)

        val recoveryManager = SessionRecoveryManager(
            sessionDao = sessionDao,
            studentDao = studentDao,
            attendanceRecordDao = recordDao,
            securityChecker = checker,
            lockTaskController = lockTaskController,
            teacherAuthenticator = authenticator,
            finalizeFaceSessionCommand = finalizeCommand
        )

        // Step 1: Check active session summary
        val foundSession = recoveryManager.getActiveSessionSummary()
        assertEquals(99L, foundSession?.id)

        // Step 2: Resume session
        val resumeResult = recoveryManager.resumeSession()
        assertTrue(resumeResult is RecoveryResumeResult.Success)
        val success = resumeResult as RecoveryResumeResult.Success
        assertEquals(99L, success.activeSession.id)
        assertEquals(0, success.presentCount)
    }

    @Test
    fun crashRecovery_noActiveSession_returnsNoActiveSession() = runTest {
        val sessionDao = FakeSessionDao(activeSession = null)
        val studentDao = FakeStudentDao()
        val recordDao = FakeAttendanceRecordDao()
        val checker = FakeDeviceSecurityChecker()
        val lockTaskController = FakeLockTaskController()
        val authenticator = FakeTeacherAuthenticator().apply {
            nextResult = AuthResult.Success(AuthMethod.PIN)
        }
        val finalizeCommand = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)

        val recoveryManager = SessionRecoveryManager(
            sessionDao = sessionDao,
            studentDao = studentDao,
            attendanceRecordDao = recordDao,
            securityChecker = checker,
            lockTaskController = lockTaskController,
            teacherAuthenticator = authenticator,
            finalizeFaceSessionCommand = finalizeCommand
        )

        val resumeResult = recoveryManager.resumeSession()
        assertTrue(resumeResult is RecoveryResumeResult.NoActiveSession)

        val endResult = recoveryManager.endSession()
        assertTrue(endResult is RecoveryEndResult.NoActiveSession)
    }

    @Test
    fun crashRecovery_endSession_finalizesActiveSession() = runTest {
        val activeSession = AttendanceSessionEntity(
            id = 101L,
            classId = 5L,
            sessionDate = "2026-08-08",
            timeZoneId = "UTC",
            startedAt = 1000L,
            endedAt = null,
            status = SessionStatus.ACTIVE,
            mode = SessionMode.FACE,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val sessionDao = FakeSessionDao(activeSession = activeSession)
        val studentDao = FakeStudentDao()
        val recordDao = FakeAttendanceRecordDao()
        val checker = FakeDeviceSecurityChecker()
        val lockTaskController = FakeLockTaskController()
        val authenticator = FakeTeacherAuthenticator().apply {
            nextResult = AuthResult.Success(AuthMethod.PIN)
        }
        val finalizeCommand = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)

        val recoveryManager = SessionRecoveryManager(
            sessionDao = sessionDao,
            studentDao = studentDao,
            attendanceRecordDao = recordDao,
            securityChecker = checker,
            lockTaskController = lockTaskController,
            teacherAuthenticator = authenticator,
            finalizeFaceSessionCommand = finalizeCommand
        )

        val endResult = recoveryManager.endSession()
        assertTrue(endResult is RecoveryEndResult.Success)
    }
}
