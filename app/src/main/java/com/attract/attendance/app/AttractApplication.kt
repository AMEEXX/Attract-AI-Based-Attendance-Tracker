package com.attract.attendance.app

import android.app.Application
import android.os.StrictMode
import com.attract.attendance.BuildConfig
import com.attract.attendance.core.observability.AppObservability
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
            android.util.Log.e("AttractCrash", "Fatal crash on thread id=${thread.id}", throwable)
            try {
                val prefs = getSharedPreferences("attract_crash_log", MODE_PRIVATE)
                prefs.edit()
                    .putString("last_crash", android.util.Log.getStackTraceString(throwable))
                    .putLong("crash_time", System.currentTimeMillis())
                    .commit()
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        AppObservability.initialize(BuildConfig.VERSION_NAME)

        try {
            val firebaseAppCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
            AppCheckInitializer.init(firebaseAppCheck)
        } catch (t: Throwable) {
            android.util.Log.w("AttractApplication", "Firebase App Check init failed: ${t.message}")
        }

        // Pre-upgrade safety backup: if the app version changed, trigger backup
        // before opening the database (which may trigger migration)
        try {
            val upgradePrefs = getSharedPreferences("attract_upgrade_guard", MODE_PRIVATE)
            val lastVersionCode = upgradePrefs.getInt("last_version_code", -1)
            val currentVersionCode = com.attract.attendance.BuildConfig.VERSION_CODE
            if (lastVersionCode != -1 && lastVersionCode != currentVersionCode) {
                android.util.Log.i(
                    "AttractApplication",
                    "Version upgrade detected: $lastVersionCode -> $currentVersionCode. Triggering pre-upgrade backup."
                )
                val drivePrefs = com.attract.attendance.data.drive.DriveBackupPreferences(this)
                if (!drivePrefs.accountEmail.isNullOrBlank()) {
                    com.attract.attendance.data.drive.BackupScheduler.triggerImmediateBackup(this)
                }
            }
            upgradePrefs.edit().putInt("last_version_code", currentVersionCode).apply()
        } catch (t: Throwable) {
            android.util.Log.w("AttractApplication", "Pre-upgrade guard failed", t)
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
            android.util.Log.w("AppContainer", "Keystore cipher unavailable, falling back to plaintext templates", t)
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
