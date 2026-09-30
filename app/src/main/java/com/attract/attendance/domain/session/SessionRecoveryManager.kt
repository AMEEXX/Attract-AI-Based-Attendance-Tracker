package com.attract.attendance.domain.session

import com.attract.attendance.data.local.AttendanceRecordDao
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.SessionDao
import com.attract.attendance.data.local.StudentDao
import com.attract.attendance.domain.FinalizeFaceSessionCommand
import com.attract.attendance.domain.FinalizeSessionResult
import com.attract.attendance.lockdown.domain.AuthReason
import com.attract.attendance.lockdown.domain.AuthResult
import com.attract.attendance.lockdown.domain.DeviceSecurityChecker
import com.attract.attendance.lockdown.domain.LockTaskController
import com.attract.attendance.lockdown.domain.SetupCheck
import com.attract.attendance.lockdown.domain.TeacherAuthenticator

sealed interface RecoveryResumeResult {
    data class Success(val activeSession: AttendanceSessionEntity, val presentCount: Int) : RecoveryResumeResult
    data class Blocked(val missingChecks: Set<SetupCheck>) : RecoveryResumeResult
    data class AuthFailed(val reason: String) : RecoveryResumeResult
    data object NoActiveSession : RecoveryResumeResult
}

sealed interface RecoveryEndResult {
    data class Success(val presentCount: Int, val absentCount: Int) : RecoveryEndResult
    data class AuthFailed(val reason: String) : RecoveryEndResult
    data object NoActiveSession : RecoveryEndResult
}

class SessionRecoveryManager(
    private val sessionDao: SessionDao,
    private val studentDao: StudentDao,
    private val attendanceRecordDao: AttendanceRecordDao,
    private val securityChecker: DeviceSecurityChecker,
    private val lockTaskController: LockTaskController,
    private val teacherAuthenticator: TeacherAuthenticator,
    private val finalizeFaceSessionCommand: FinalizeFaceSessionCommand,
) {
    suspend fun getActiveSessionSummary(): AttendanceSessionEntity? {
        return sessionDao.activeFaceSession()
    }

    suspend fun resumeSession(): RecoveryResumeResult {
        val activeSession = sessionDao.activeFaceSession() ?: return RecoveryResumeResult.NoActiveSession

        val authResult = teacherAuthenticator.authenticate(AuthReason.RECOVERY_RESUME)
        if (authResult !is AuthResult.Success) {
            return RecoveryResumeResult.AuthFailed("Teacher authentication cancelled or failed")
        }

        val checks = securityChecker.check()
        val requiredForRecovery = setOf(
            SetupCheck.CAMERA_PERMISSION,
            SetupCheck.TEACHER_PIN_CONFIGURED,
            SetupCheck.SECURE_DEVICE_LOCK,
            SetupCheck.SCREEN_PINNING_PROBED,
            SetupCheck.MODEL_RESOURCES_VERIFIED,
        )
        val missing = requiredForRecovery - checks
        if (missing.isNotEmpty()) {
            return RecoveryResumeResult.Blocked(missing)
        }

        lockTaskController.start()
        val presentCount = attendanceRecordDao.presentCount(activeSession.id)

        return RecoveryResumeResult.Success(activeSession, presentCount)
    }

    suspend fun endSession(): RecoveryEndResult {
        val activeSession = sessionDao.activeFaceSession() ?: return RecoveryEndResult.NoActiveSession

        val authResult = teacherAuthenticator.authenticate(AuthReason.RECOVERY_END)
        if (authResult !is AuthResult.Success) {
            return RecoveryEndResult.AuthFailed("Teacher authentication cancelled or failed")
        }

        val finalizeResult = finalizeFaceSessionCommand.execute(activeSession.id)
        if (finalizeResult !is FinalizeSessionResult.Success) {
            return RecoveryEndResult.AuthFailed("Finalization failed")
        }

        lockTaskController.stop()

        return RecoveryEndResult.Success(finalizeResult.presentCount, finalizeResult.absentCount)
    }
}
