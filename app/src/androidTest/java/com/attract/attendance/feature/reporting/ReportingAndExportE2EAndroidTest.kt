package com.attract.attendance.feature.reporting

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.AttendanceMethod
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.data.importexport.AttendanceExporter
import com.attract.attendance.data.importexport.BackupExporter
import com.attract.attendance.data.importexport.BackupSnapshot
import com.attract.attendance.data.local.ClassReportStudentRow
import com.attract.attendance.data.local.SessionExportRow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IT-06: ReportingAndExportE2EAndroidTest
 * Instrumented test verifying reporting, CSV export formatting, and backup snapshot serialization on Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class ReportingAndExportE2EAndroidTest {

    private val attendanceExporter = AttendanceExporter()
    private val backupExporter = BackupExporter()

    @Test
    fun csvExport_buildsValidCsvOutputOnAndroid() {
        val reportRows = listOf(
            ClassReportStudentRow(1L, "Android Student", "CS-100", "S-100", EnrollmentStatus.ENROLLED, 5, 5)
        )
        val sessionRows = listOf(
            SessionExportRow(50L, "2026-08-08", SessionMode.FACE, "CS-100", "Android Student", "S-100", AttendanceStatus.PRESENT, AttendanceMethod.AI_RECOGNITION, 1000L)
        )

        val csv = attendanceExporter.buildCsv("Android Class", 1700000000000L, reportRows, sessionRows)

        assertTrue(csv.contains("Attract Attendance Export"))
        assertTrue(csv.contains("\"Android Student\""))
        assertTrue(csv.contains("\"100.0%\""))
    }

    @Test
    fun backupJson_buildsValidJsonSnapshotOnAndroid() {
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = emptyList(),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList()
        )

        val json = backupExporter.toJson(snapshot)

        assertTrue(json.contains("\"format\":\"attract-backup-v1\""))
    }
}
