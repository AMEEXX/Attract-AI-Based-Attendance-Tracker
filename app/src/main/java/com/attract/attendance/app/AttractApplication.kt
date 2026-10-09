package com.attract.attendance.app

import android.app.Application
import com.attract.attendance.data.importexport.AttendanceExporter
import com.attract.attendance.data.importexport.BackupExporter
import com.attract.attendance.data.importexport.CsvRosterImporter
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.security.PinHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AttractApplication : Application() {
    var container: AppContainer? = null
        private set
    var startupError: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            com.attract.attendance.util.AppLog.e("AttractCrash", "Fatal crash on worker thread", throwable)
            try {
                val now = System.currentTimeMillis()
                val prefs = getSharedPreferences(PREFS_CRASH_LOG, MODE_PRIVATE)
                val rawHistory = prefs.getString(KEY_CRASH_HISTORY, "") ?: ""
                val recentTimes = (rawHistory.split(",").mapNotNull { it.toLongOrNull() } + now)
                    .filter { now - it <= CRASH_WINDOW_MS }

                val editor = prefs.edit()
                    .putString(KEY_LAST_CRASH, android.util.Log.getStackTraceString(throwable))
                    .putLong(KEY_LAST_CRASH_TIME, now)
                    .putString(KEY_CRASH_HISTORY, recentTimes.joinToString(","))

                // PR-01: 3 crashes within 60s triggers Safe Mode (never delete database)
                if (recentTimes.size >= CRASH_THRESHOLD) {
                    editor.putBoolean(KEY_SAFE_MODE, true)
                }
                editor.commit()
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // PR-01: Run PRAGMA integrity_check on startup in background thread
        CoroutineScope(Dispatchers.IO).launch {
            AttractDatabase.checkDatabaseIntegrity(this@AttractApplication)
        }

        AppCheckConfigurator.initialize(this)

        try {
            container = AppContainer(this)
        } catch (t: Throwable) {
            android.util.Log.e("AttractApplication", "Failed to create AppContainer", t)
            startupError = t
        }

        try {
            val prefs = com.attract.attendance.data.drive.DriveBackupPreferences(this)
            if (!prefs.accountEmail.isNullOrBlank() && prefs.isAutoSyncEnabled) {
                com.attract.attendance.data.drive.BackupScheduler.schedulePeriodicBackup(this)
            }
        } catch (t: Throwable) {
            android.util.Log.w("AttractApplication", "Could not check Drive account on startup", t)
        }
    }

    companion object {
        const val PREFS_CRASH_LOG = "attract_crash_log"
        const val KEY_LAST_CRASH = "last_crash"
        const val KEY_LAST_CRASH_TIME = "crash_time"
        const val KEY_CRASH_HISTORY = "crash_history"
        const val KEY_SAFE_MODE = "safe_mode"
        const val CRASH_WINDOW_MS = 60_000L
        const val CRASH_THRESHOLD = 3

        fun isSafeModeActive(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_CRASH_LOG, MODE_PRIVATE)
            return prefs.getBoolean(KEY_SAFE_MODE, false)
        }

        fun exitSafeMode(context: android.content.Context) {
            val prefs = context.getSharedPreferences(PREFS_CRASH_LOG, MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_SAFE_MODE, false).remove(KEY_CRASH_HISTORY).apply()
        }
    }
}

class AppContainer(application: Application) {
    val database by lazy { AttractDatabase.create(application) }

    // Keystore-backed AEAD cipher for face templates at rest (LLD-13).
    // PR-03: No plaintext fallback. If Keystore fails, face biometrics are disabled (fail closed).
    val embeddingCipher: com.attract.attendance.lockdown.domain.EmbeddingCipher? by lazy {
        try {
            com.attract.attendance.lockdown.data.crypto.KeystoreEmbeddingCipher(
                alias = "attract_face_template_key",
                keyVersion = 1,
            )
        } catch (t: Throwable) {
            com.attract.attendance.util.AppLog.w("AppContainer", "Keystore cipher unavailable; face biometric features disabled", t)
            null
        }
    }

    val isBiometricCryptoAvailable: Boolean get() = embeddingCipher != null

    val pinLockoutManager by lazy {
        com.attract.attendance.data.security.PinLockoutManager(
            com.attract.attendance.data.security.PrefsPinLockoutStorage.fromContext(application)
        )
    }

    val repository: AttractRepository by lazy {
        AttractRepository(database, PinHasher(), embeddingCipher = embeddingCipher, pinLockoutManager = pinLockoutManager)
    }
    val csvRosterImporter by lazy { CsvRosterImporter(application.contentResolver) }
    val attendanceExporter by lazy { AttendanceExporter(application.contentResolver) }
    val backupExporter by lazy { BackupExporter(application.contentResolver) }
}
