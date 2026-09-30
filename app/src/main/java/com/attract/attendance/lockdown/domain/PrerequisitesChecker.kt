package com.attract.attendance.lockdown.domain

class PrerequisitesChecker(
    private val securityChecker: DeviceSecurityChecker,
) {
    suspend fun evaluate(): PrerequisiteResult {
        val available = securityChecker.check()
        val required = setOf(
            SetupCheck.CAMERA_PERMISSION,
            SetupCheck.TEACHER_PIN_CONFIGURED,
            SetupCheck.SECURE_DEVICE_LOCK,
            SetupCheck.SCREEN_PINNING_PROBED,
            SetupCheck.MODEL_RESOURCES_VERIFIED,
            SetupCheck.NO_ACTIVE_FACE_SESSION,
        )
        val missing = required - available
        return if (missing.isEmpty()) {
            PrerequisiteResult.Ready
        } else {
            PrerequisiteResult.Blocked(missing)
        }
    }
}
