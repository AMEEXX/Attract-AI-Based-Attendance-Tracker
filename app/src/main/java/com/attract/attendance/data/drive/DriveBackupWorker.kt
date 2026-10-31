package com.attract.attendance.data.drive

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.attract.attendance.app.AttractApplication
import com.attract.attendance.core.model.EnrollmentStatus
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
        val accountEmail = prefs.accountEmail

        if (accountEmail.isNullOrBlank()) {
            // Not connected to Google Drive or account missing, finish safely
            return@withContext Result.success()
        }

        try {
            val app = applicationContext as? AttractApplication
            val repository = app?.container?.repository
                ?: AttractRepository(
                    database = AttractDatabase.create(applicationContext),
                    pinHasher = PinHasher(),
                    embeddingCipher = try {
                        com.attract.attendance.lockdown.data.crypto.KeystoreEmbeddingCipher(
                            alias = "attract_face_template_key",
                            keyVersion = 1,
                        )
                    } catch (_: Throwable) { null }
                )
            val backupExporter = app?.container?.backupExporter ?: BackupExporter()

            val snapshot = repository.backupSnapshot()

            // SAFETY: Never overwrite a good Drive backup with an empty/degraded one
            val hasFaceData = snapshot.faceTemplates.isNotEmpty()
            val hasEnrolledStudents = snapshot.students.any {
                it.enrollmentStatus == EnrollmentStatus.ENROLLED
            }

            if (hasEnrolledStudents && !hasFaceData) {
                // Enrolled students exist but NO face templates could be decrypted.
                // This means the Keystore key was likely invalidated.
                // DO NOT overwrite the Drive backup — it may contain the good data.
                Log.w(
                    "DriveBackupWorker",
                    "SKIPPING backup: ${snapshot.students.size} students exist but 0 " +
                        "face templates could be exported. Keystore key may be invalidated."
                )
                prefs.lastSyncError = "Backup skipped: face data could not be decrypted"
                return@withContext Result.success()  // Don't retry — retrying won't help
            }

            val jsonString = backupExporter.toJson(snapshot)

            val driveHelper = DriveServiceHelper(applicationContext)
            val uploadResult = driveHelper.uploadBackupJson(accountEmail, jsonString)

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
