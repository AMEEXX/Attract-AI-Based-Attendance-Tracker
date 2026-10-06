package com.attract.attendance.data.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackupScheduler {
    const val PERIODIC_BACKUP_WORK_NAME = "attract_drive_periodic_backup"
    const val IMMEDIATE_BACKUP_WORK_NAME = "attract_drive_immediate_backup"

    fun schedulePeriodicBackup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<DriveBackupWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 30,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    fun triggerImmediateBackup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_BACKUP_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeWorkRequest
        )
    }

    fun cancelPeriodicBackup(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_BACKUP_WORK_NAME)
    }
}
