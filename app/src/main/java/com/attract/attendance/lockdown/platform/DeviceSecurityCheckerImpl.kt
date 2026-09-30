package com.attract.attendance.lockdown.platform

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.attract.attendance.data.local.SessionDao
import com.attract.attendance.data.local.TeacherDao
import com.attract.attendance.lockdown.domain.DeviceSecurityChecker
import com.attract.attendance.lockdown.domain.SetupCheck

class DeviceSecurityCheckerImpl(
    private val context: Context,
    private val teacherDao: TeacherDao,
    private val sessionDao: SessionDao,
    private val screenPinningProbed: Boolean = true,
    private val modelResourcesVerified: Boolean = true,
) : DeviceSecurityChecker {

    override suspend fun check(): Set<SetupCheck> {
        val checks = mutableSetOf<SetupCheck>()

        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            checks.add(SetupCheck.CAMERA_PERMISSION)
        }

        val teacher = teacherDao.first()
        if (teacher != null && teacher.pinHash.isNotBlank()) {
            checks.add(SetupCheck.TEACHER_PIN_CONFIGURED)
        }

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isDeviceSecure == true) {
            checks.add(SetupCheck.SECURE_DEVICE_LOCK)
        }

        if (screenPinningProbed) {
            checks.add(SetupCheck.SCREEN_PINNING_PROBED)
        }

        if (modelResourcesVerified) {
            checks.add(SetupCheck.MODEL_RESOURCES_VERIFIED)
        }

        val activeSession = sessionDao.activeFaceSession()
        if (activeSession == null) {
            checks.add(SetupCheck.NO_ACTIVE_FACE_SESSION)
        }

        return checks
    }
}
