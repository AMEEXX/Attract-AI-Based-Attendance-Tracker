package com.attract.attendance.app

import android.app.Application
import com.attract.attendance.data.importexport.AttendanceExporter
import com.attract.attendance.data.importexport.BackupExporter
import com.attract.attendance.data.importexport.CsvRosterImporter
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.security.PinHasher

class AttractApplication : Application() {
    var container: AppContainer? = null
        private set
    var startupError: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("AttractCrash", "Fatal crash on thread ${thread.name}", throwable)
            try {
                val prefs = getSharedPreferences("attract_crash_log", MODE_PRIVATE)
                prefs.edit()
                    .putString("last_crash", android.util.Log.getStackTraceString(throwable))
                    .putLong("crash_time", System.currentTimeMillis())
                    .commit()
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            val firebaseAppCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            if (com.attract.attendance.BuildConfig.DEBUG) {
                firebaseAppCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
                )
            } else {
                firebaseAppCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } catch (t: Throwable) {
            android.util.Log.w("AttractApplication", "Firebase App Check init failed: ${t.message}")
        }

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
}

class AppContainer(application: Application) {
    val database by lazy { AttractDatabase.create(application) }

    // Keystore-backed AEAD cipher for face templates at rest (LLD-13).
    // Safely fallback to null if Keystore is unavailable or throws on device.
    private val embeddingCipher: com.attract.attendance.lockdown.domain.EmbeddingCipher? by lazy {
        try {
            com.attract.attendance.lockdown.data.crypto.KeystoreEmbeddingCipher(
                alias = "attract_face_template_key",
                keyVersion = 1,
            )
        } catch (t: Throwable) {
            android.util.Log.w("AppContainer", "Keystore embedding cipher unavailable, falling back to plaintext templates", t)
            null
        }
    }

    val repository: AttractRepository by lazy {
        AttractRepository(database, PinHasher(), embeddingCipher = embeddingCipher)
    }
    val csvRosterImporter by lazy { CsvRosterImporter(application.contentResolver) }
    val attendanceExporter by lazy { AttendanceExporter(application.contentResolver) }
    val backupExporter by lazy { BackupExporter(application.contentResolver) }
}
