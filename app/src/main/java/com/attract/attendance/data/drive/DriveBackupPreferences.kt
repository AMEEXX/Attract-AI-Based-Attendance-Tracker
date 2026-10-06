package com.attract.attendance.data.drive

import android.content.Context
import android.content.SharedPreferences

class DriveBackupPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("attract_drive_backup_prefs", Context.MODE_PRIVATE)

    var lastSyncMillis: Long
        get() = prefs.getLong(KEY_LAST_SYNC_MILLIS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_MILLIS, value).apply()

    var lastSyncError: String?
        get() = prefs.getString(KEY_LAST_SYNC_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_ERROR, value).apply()

    var isAutoSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, value).apply()

    var accountEmail: String?
        get() = prefs.getString(KEY_ACCOUNT_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT_EMAIL, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_LAST_SYNC_MILLIS = "last_sync_millis"
        private const val KEY_LAST_SYNC_ERROR = "last_sync_error"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
    }
}
