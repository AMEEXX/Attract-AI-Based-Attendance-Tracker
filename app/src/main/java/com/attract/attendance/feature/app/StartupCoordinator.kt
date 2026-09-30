package com.attract.attendance.feature.app

import com.attract.attendance.data.local.SessionDao
import com.attract.attendance.data.local.TeacherDao
import com.attract.attendance.lockdown.domain.DeviceSecurityChecker
import com.attract.attendance.lockdown.domain.SetupCheck

sealed interface StartupDestination {
    data object Onboarding : StartupDestination
    data class RecoverSession(val sessionId: Long) : StartupDestination
    data object TeacherDashboard : StartupDestination
    data class BlockingSetup(val failedChecks: Set<SetupCheck>) : StartupDestination
}

class StartupCoordinator(
    private val teacherDao: TeacherDao,
    private val sessionDao: SessionDao,
    private val securityChecker: DeviceSecurityChecker,
) {
    suspend fun resolve(): StartupDestination {
        val teacher = teacherDao.first()
        if (teacher == null || teacher.pinHash.isBlank()) {
            return StartupDestination.Onboarding
        }

        val availableChecks = securityChecker.check()
        val required = setOf(
            SetupCheck.CAMERA_PERMISSION,
            SetupCheck.TEACHER_PIN_CONFIGURED,
            SetupCheck.SECURE_DEVICE_LOCK,
            SetupCheck.SCREEN_PINNING_PROBED,
            SetupCheck.MODEL_RESOURCES_VERIFIED,
            SetupCheck.NO_ACTIVE_FACE_SESSION,
        )
        val missing = required - availableChecks
        if (missing.isNotEmpty()) {
            return StartupDestination.BlockingSetup(missing)
        }

        val activeSession = sessionDao.activeFaceSession()
        if (activeSession != null) {
            return StartupDestination.RecoverSession(activeSession.id)
        }

        return StartupDestination.TeacherDashboard
    }
}
