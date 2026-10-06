package com.attract.attendance.data.drive

sealed class DriveSyncStatus {
    data object Idle : DriveSyncStatus()
    data object Syncing : DriveSyncStatus()
    data class Success(val timestampMillis: Long) : DriveSyncStatus()
    data class Error(val message: String) : DriveSyncStatus()
}

data class DriveAccountInfo(
    val email: String,
    val displayName: String? = null
)
