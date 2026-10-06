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

            // Query existing attract_backup.json in appDataFolder
            val fileList = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'attract_backup.json' and trashed = false")
                .setFields("files(id, name, modifiedTime)")
                .execute()

            val existingFile = fileList.files?.firstOrNull()

            if (existingFile != null) {
                drive.files().update(existingFile.id, null, mediaContent).execute()
            } else {
                val metadata = com.google.api.services.drive.model.File().apply {
                    name = "attract_backup.json"
                    parents = listOf("appDataFolder")
                }
                drive.files().create(metadata, mediaContent)
                    .setFields("id")
                    .execute()
            }
            System.currentTimeMillis()
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

            val existingFile = fileList.files?.firstOrNull() ?: return@runCatching null
            val outputStream = ByteArrayOutputStream()
            drive.files().get(existingFile.id).executeMediaAndDownloadTo(outputStream)
            outputStream.toString("UTF-8")
        }
    }
}
