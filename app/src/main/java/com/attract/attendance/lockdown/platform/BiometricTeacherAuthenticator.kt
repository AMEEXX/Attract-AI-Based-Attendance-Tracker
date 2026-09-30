package com.attract.attendance.lockdown.platform

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.attract.attendance.lockdown.domain.AuthMethod
import com.attract.attendance.lockdown.domain.AuthReason
import com.attract.attendance.lockdown.domain.AuthResult
import com.attract.attendance.lockdown.domain.SecurityEvent
import com.attract.attendance.lockdown.domain.SecurityEventCategory
import com.attract.attendance.lockdown.domain.SecurityEventLogger
import com.attract.attendance.lockdown.domain.TeacherAuthenticator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class BiometricTeacherAuthenticator(
    private val activity: FragmentActivity,
    private val pinHasher: com.attract.attendance.data.security.PinHasher,
    private val storedPinHash: String,
    private val executor: Executor,
    private val logger: SecurityEventLogger,
) : TeacherAuthenticator {

    @RequiresApi(Build.VERSION_CODES.P)
    override suspend fun authenticate(reason: AuthReason): AuthResult {
        logger.log(
            SecurityEvent(
                category = SecurityEventCategory.AUTH_ATTEMPT,
                result = "STARTED",
                deviceInfo = getDeviceInfo(),
                safeErrorCode = "BIOMETRIC_${reason.name}",
            )
        )

        return try {
            val biometricResult = tryBiometric()
            when (biometricResult) {
                is AuthResult.Success -> {
                    logger.log(
                        SecurityEvent(
                            category = SecurityEventCategory.AUTH_SUCCESS,
                            result = "BIOMETRIC",
                            deviceInfo = getDeviceInfo(),
                            safeErrorCode = null,
                        )
                    )
                    biometricResult
                }
                AuthResult.Cancelled -> {
                    logger.log(
                        SecurityEvent(
                            category = SecurityEventCategory.AUTH_CANCELLED,
                            result = "USER_CANCELLED",
                            deviceInfo = getDeviceInfo(),
                            safeErrorCode = null,
                        )
                    )
                    pinFallback()
                }
                else -> {
                    pinFallback()
                }
            }
        } catch (e: Exception) {
            logger.log(
                SecurityEvent(
                    category = SecurityEventCategory.AUTH_FAILED,
                    result = "BIOMETRIC_ERROR",
                    deviceInfo = getDeviceInfo(),
                    safeErrorCode = e::class.java.simpleName,
                )
            )
            pinFallback()
        }
    }

    private fun tryBiometric(): AuthResult {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Teacher verification")
            .setSubtitle("Verify identity to continue")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Use PIN")
            .build()

        var authResult: AuthResult = AuthResult.Failed
        val latch = CountDownLatch(1)

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authResult = AuthResult.Success(AuthMethod.BIOMETRIC)
                latch.countDown()
            }

            override fun onAuthenticationFailed() {
                authResult = AuthResult.Failed
                latch.countDown()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authResult = if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    AuthResult.Cancelled
                } else {
                    AuthResult.Failed
                }
                latch.countDown()
            }
        })

        biometricPrompt.authenticate(promptInfo)

        if (!latch.await(10, TimeUnit.SECONDS)) {
            authResult = AuthResult.Cancelled
        }

        return authResult
    }

    private fun pinFallback(): AuthResult {
        logger.log(
            SecurityEvent(
                category = SecurityEventCategory.AUTH_ATTEMPT,
                result = "PIN_FALLBACK",
                deviceInfo = getDeviceInfo(),
                safeErrorCode = null,
            )
        )

        val pin = readPinSecurely()
        val verified = pin != null && pinHasher.verify(pin, storedPinHash)

        return if (verified) {
            AuthResult.Success(AuthMethod.PIN)
        } else {
            AuthResult.Failed
        }
    }

    private fun readPinSecurely(): CharArray? {
        return null
    }

    private fun getDeviceInfo(): String =
        "OEM=${Build.MANUFACTURER};Model=${Build.MODEL}"
}

