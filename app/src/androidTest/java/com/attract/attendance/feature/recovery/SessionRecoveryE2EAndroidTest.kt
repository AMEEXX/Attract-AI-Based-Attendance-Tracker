package com.attract.attendance.feature.recovery

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.domain.FinalizeFaceSessionCommand
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IT-04: SessionRecoveryE2EAndroidTest
 * Instrumented test verifying process relaunch with an active session in DB on Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class SessionRecoveryE2EAndroidTest {

    @Test
    fun processRelaunch_activeSessionDetected_recoveryManagerResumes() = runBlocking {
        val activeSession = AttendanceSessionEntity(
            id = 300L,
            classId = 30L,
            sessionDate = "2026-08-08",
            timeZoneId = "UTC",
            startedAt = 1000L,
            endedAt = null,
            status = SessionStatus.ACTIVE,
            mode = SessionMode.FACE,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val sessionDao = FakeSessionDao(activeSession)
        val studentDao = FakeStudentDao()
        val recordDao = FakeAttendanceRecordDao()
        val checker = FakeDeviceSecurityChecker().apply { SetupCheck.entries.forEach { add(it) } }
        val lockTaskController = FakeLockTaskController()
        val authenticator = FakeTeacherAuthenticator().apply { nextResult = AuthResult.Success(AuthMethod.PIN) }
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

        val active = recoveryManager.getActiveSessionSummary()
        assertEquals(300L, active?.id)

        val resumeResult = recoveryManager.resumeSession()
        assertTrue(resumeResult is RecoveryResumeResult.Success)
    }
}
