package com.attract.attendance.data.drive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class DriveServiceHelper(private val context: Context) {

    private fun getDriveService(accountName: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context.applicationContext,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccountName = accountName
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Attract Attendance")
            .build()
    }

    suspend fun uploadBackupJson(
        accountName: String,
        jsonContent: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = getDriveService(accountName)
            val jsonBytes = jsonContent.toByteArray(Charsets.UTF_8)
            val mediaContent = ByteArrayContent("application/json", jsonBytes)
            val timestamp = System.currentTimeMillis()

            // 1. Always create a new timestamped backup file
            val versionedMetadata = com.google.api.services.drive.model.File().apply {
                name = "attract_backup_$timestamp.json"
                parents = listOf("appDataFolder")
            }
            drive.files().create(versionedMetadata, mediaContent)
                .setFields("id")
                .execute()

            // 2. Also update or create the "latest" pointer for quick restore
            val fileList = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'attract_backup.json' and trashed = false")
                .setFields("files(id, name)")
                .execute()

            val existingFile = fileList.files?.firstOrNull()

            if (existingFile != null) {
                drive.files().update(existingFile.id, null, mediaContent).execute()
            } else {
                val latestMetadata = com.google.api.services.drive.model.File().apply {
                    name = "attract_backup.json"
                    parents = listOf("appDataFolder")
                }
                drive.files().create(latestMetadata, mediaContent)
                    .setFields("id")
                    .execute()
            }

            // 3. Cleanup: keep only the 5 most recent timestamped backups
            try {
                val allBackups = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name contains 'attract_backup_' and trashed = false")
                    .setFields("files(id, name, createdTime)")
                    .setOrderBy("createdTime desc")
                    .execute()

                allBackups.files?.drop(5)?.forEach { oldFile ->
                    try {
                        drive.files().delete(oldFile.id).execute()
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
                // Non-critical cleanup failure
            }

            timestamp
        }
    }

    suspend fun downloadBackupJson(accountName: String): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = getDriveService(accountName)
            val fileList = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'attract_backup.json' and trashed = false")
                .setFields("files(id, name)")
                .execute()

            var targetFileId = fileList.files?.firstOrNull()?.id
            if (targetFileId == null) {
                // Fall back to most recent timestamped backup if attract_backup.json is not found
                val fallbackList = drive.files().list()
                    .setSpaces("appDataFolder")
                    .setQ("name contains 'attract_backup_' and trashed = false")
                    .setFields("files(id, name, createdTime)")
                    .setOrderBy("createdTime desc")
                    .execute()
                targetFileId = fallbackList.files?.firstOrNull()?.id
            }

            if (targetFileId == null) return@runCatching null

            val outputStream = ByteArrayOutputStream()
            drive.files().get(targetFileId).executeMediaAndDownloadTo(outputStream)
            outputStream.toString("UTF-8")
        }
    }
}
