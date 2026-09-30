package com.attract.attendance.lockdown.domain

import java.time.Instant

enum class AuthReason {
    END_SESSION,
    RECOVERY_RESUME,
    RECOVERY_END,
    TEACHER_ASSISTANCE,
    FIRST_IDENTITY_CONFIRM,
    RE_ENROLLMENT,
    PROFILE_MANAGEMENT,
}

enum class AuthMethod {
    BIOMETRIC,
    PIN,
}

sealed interface AuthResult {
    data class Success(val method: AuthMethod, val timestamp: Instant = Instant.now()) : AuthResult
    data object Cancelled : AuthResult
    data object Failed : AuthResult
    data object Unavailable : AuthResult
}

sealed interface LockTaskState {
    data object Locked : LockTaskState
    data object Unlocked : LockTaskState
}

sealed interface LockTaskResult {
    data object Started : LockTaskResult
    data object AlreadyLocked : LockTaskResult
    data class Error(val message: String) : LockTaskResult
}

enum class SetupCheck {
    CAMERA_PERMISSION,
    TEACHER_PIN_CONFIGURED,
    SECURE_DEVICE_LOCK,
    SCREEN_PINNING_PROBED,
    MODEL_RESOURCES_VERIFIED,
    NO_ACTIVE_FACE_SESSION,
}

sealed interface PrerequisiteResult {
    data object Ready : PrerequisiteResult
    data class Blocked(val missing: Set<SetupCheck>) : PrerequisiteResult
}

data class EncryptedEmbedding(
    val formatVersion: Int,
    val keyVersion: Int,
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedEmbedding) return false
        return formatVersion == other.formatVersion &&
            keyVersion == other.keyVersion &&
            iv.contentEquals(other.iv) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + keyVersion
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}
