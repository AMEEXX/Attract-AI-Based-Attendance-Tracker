package com.attract.attendance.data.drive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attract.attendance.app.AttractApplication
import com.attract.attendance.data.importexport.BackupExporter
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.security.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = DriveBackupPreferences(applicationContext)
        val authManager = DriveAuthManager(applicationContext)
        val account = authManager.getLastSignedInAccount()

        if (account == null) {
            // Not connected to Google Drive or missing permissions, finish safely
            return@withContext Result.success()
        }

        try {
            val app = applicationContext as? AttractApplication
            val repository = app?.container?.repository
                ?: AttractRepository(AttractDatabase.create(applicationContext), PinHasher())
            val backupExporter = app?.container?.backupExporter ?: BackupExporter()

            val snapshot = repository.backupSnapshot()
            val jsonString = backupExporter.toJson(snapshot)

            val driveHelper = DriveServiceHelper(applicationContext)
            val uploadResult = driveHelper.uploadBackupJson(account, jsonString)

            if (uploadResult.isSuccess) {
                val timestamp = uploadResult.getOrThrow()
                prefs.lastSyncMillis = timestamp
                prefs.lastSyncError = null
                Result.success()
            } else {
                val error = uploadResult.exceptionOrNull()?.message ?: "Backup upload failed"
                prefs.lastSyncError = error
                Result.retry()
            }
        } catch (t: Throwable) {
            prefs.lastSyncError = t.message ?: "Backup worker error"
            Result.retry()
        }
    }
}
