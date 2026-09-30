package com.attract.attendance.domain.session

import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceRecordDao
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.SessionDao
import com.attract.attendance.data.local.StudentDao
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.domain.FinalizeFaceSessionCommand
import com.attract.attendance.lockdown.domain.AuthReason
import com.attract.attendance.lockdown.domain.AuthResult
import com.attract.attendance.lockdown.domain.FakeDeviceSecurityChecker
import com.attract.attendance.lockdown.domain.FakeLockTaskController
import com.attract.attendance.lockdown.domain.FakeTeacherAuthenticator
import com.attract.attendance.lockdown.domain.SetupCheck
import com.attract.attendance.test.FakeAttendanceRecordDao
import com.attract.attendance.test.FakeSessionDao
import com.attract.attendance.test.FakeStudentDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecoveryManagerTest {

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

    @Test
    fun resumeSession_authenticatedAndSecurityReady_returnsSuccess() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(emptyList())
        val recordDao = FakeAttendanceRecordDao()
        val securityChecker = FakeDeviceSecurityChecker().apply {
            SetupCheck.entries.forEach { add(it) }
        }
        val lockTaskController = FakeLockTaskController()
        val teacherAuth = FakeTeacherAuthenticator().apply {
            nextResult = AuthResult.Success(com.attract.attendance.lockdown.domain.AuthMethod.PIN)
        }
        val finalizeCommand = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)

        val manager = SessionRecoveryManager(
            sessionDao, studentDao, recordDao, securityChecker,
            lockTaskController, teacherAuth, finalizeCommand
        )

        val result = manager.resumeSession()

        assertTrue(result is RecoveryResumeResult.Success)
        val success = result as RecoveryResumeResult.Success
        assertEquals(10L, success.activeSession.id)
    }

    @Test
    fun resumeSession_authFailed_returnsAuthFailed() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(emptyList())
        val recordDao = FakeAttendanceRecordDao()
        val securityChecker = FakeDeviceSecurityChecker()
        val lockTaskController = FakeLockTaskController()
        val teacherAuth = FakeTeacherAuthenticator().apply {
            nextResult = AuthResult.Failed
        }
        val finalizeCommand = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)

        val manager = SessionRecoveryManager(
            sessionDao, studentDao, recordDao, securityChecker,
            lockTaskController, teacherAuth, finalizeCommand
        )

        val result = manager.resumeSession()

        assertTrue(result is RecoveryResumeResult.AuthFailed)
    }

    @Test
    fun endSession_authenticated_finalizesAndReleasesLockTask() = runTest {
        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao(emptyList())
        val recordDao = FakeAttendanceRecordDao()
        val securityChecker = FakeDeviceSecurityChecker()
        val lockTaskController = FakeLockTaskController()
        val teacherAuth = FakeTeacherAuthenticator().apply {
            nextResult = AuthResult.Success(com.attract.attendance.lockdown.domain.AuthMethod.PIN)
        }
        val finalizeCommand = FinalizeFaceSessionCommand(sessionDao, studentDao, recordDao)

        val manager = SessionRecoveryManager(
            sessionDao, studentDao, recordDao, securityChecker,
            lockTaskController, teacherAuth, finalizeCommand
        )

        val result = manager.endSession()

        assertTrue(result is RecoveryEndResult.Success)
    }
}

