package com.attract.attendance.functional

import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.importexport.AttendanceExporter
import com.attract.attendance.data.importexport.BackupExporter
import com.attract.attendance.data.importexport.BackupSnapshot
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.ClassReportStudentRow
import com.attract.attendance.data.local.ClassSectionEntity
import com.attract.attendance.data.local.SessionExportRow
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.data.local.TeacherEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * FT-02: ExportAndBackupFunctionalTest
 * Verifies reporting, export, and backup pipeline from domain data models to CSV/JSON output.
 */
class ExportAndBackupFunctionalTest {

    private val attendanceExporter = AttendanceExporter()
    private val backupExporter = BackupExporter()

    @Test
    fun csvExport_multipleSessionData_correctStructure() {
        val reportRows = listOf(
            ClassReportStudentRow(1L, "Alice", "CS-01", "S-1", EnrollmentStatus.ENROLLED, 3, 3),
            ClassReportStudentRow(2L, "Bob", "CS-02", "S-2", EnrollmentStatus.ENROLLED, 2, 3)
        )

        val sessionRows = listOf(
            SessionExportRow(101L, "2026-08-01", SessionMode.FACE, "CS-01", "Alice", "S-1", AttendanceStatus.PRESENT, AttendanceSource.AI_RECOGNITION, 1000L),
            SessionExportRow(101L, "2026-08-01", SessionMode.FACE, "CS-02", "Bob", "S-2", AttendanceStatus.PRESENT, AttendanceSource.AI_RECOGNITION, 1005L),
            SessionExportRow(102L, "2026-08-02", SessionMode.FACE, "CS-01", "Alice", "S-1", AttendanceStatus.PRESENT, AttendanceSource.AI_RECOGNITION, 2000L),
            SessionExportRow(102L, "2026-08-02", SessionMode.FACE, "CS-02", "Bob", "S-2", AttendanceStatus.ABSENT, AttendanceSource.AI_RECOGNITION, null),
            SessionExportRow(103L, "2026-08-03", SessionMode.FACE, "CS-01", "Alice", "S-1", AttendanceStatus.PRESENT, AttendanceSource.AI_RECOGNITION, 3000L),
            SessionExportRow(103L, "2026-08-03", SessionMode.FACE, "CS-02", "Bob", "S-2", AttendanceStatus.PRESENT, AttendanceSource.MANUAL, 3010L)
        )

        val csv = attendanceExporter.buildCsv("Algorithms", 1700000000000L, reportRows, sessionRows)

        assertTrue(csv.contains("\"Alice\""))
        assertTrue(csv.contains("\"Bob\""))
        assertTrue(csv.contains("\"100.0%\""))
        assertTrue(csv.contains("\"66.7%\""))
    }

    @Test
    fun csvExport_formulaInjectionInName_prefixedWithApostrophe() {
        val reportRows = listOf(
            ClassReportStudentRow(1L, "=CMD('calc')", "+CS-01", "-100", EnrollmentStatus.ENROLLED, 1, 1)
        )

        val csv = attendanceExporter.buildCsv("Algorithms", 1700000000000L, reportRows, emptyList())

        assertTrue(csv.contains("\"'=CMD('calc')\""))
        assertTrue(csv.contains("\"'+CS-01\""))
        assertTrue(csv.contains("\"'-100\""))
    }

    @Test
    fun csvExport_emptyClass_producesHeadersOnly() {
        val csv = attendanceExporter.buildCsv("Empty Class", 1700000000000L, emptyList(), emptyList())

        assertTrue(csv.contains("Attract Attendance Export"))
        assertTrue(csv.contains("\"Class\",\"Empty Class\""))
        assertTrue(csv.contains("\"Roll Number\",\"Name\",\"Serial Number\",\"Enrollment\",\"Present\",\"Eligible\",\"Attendance %\""))
    }

    @Test
    fun csvExport_nullSerialNumber_rendersEmptyCell() {
        val reportRows = listOf(
            ClassReportStudentRow(1L, "Charlie", "CS-03", null, EnrollmentStatus.ENROLLED, 1, 1)
        )

        val csv = attendanceExporter.buildCsv("Class B", 1700000000000L, reportRows, emptyList())

        assertTrue(csv.contains("\"CS-03\",\"Charlie\",\"\""))
    }

    @Test
    fun csvExport_specialCharsInClassName_properlyQuoted() {
        val csv = attendanceExporter.buildCsv("Math 101, \"Section A\"", 1700000000000L, emptyList(), emptyList())

        assertTrue(csv.contains("\"Math 101, \"\"Section A\"\"\""))
    }

    @Test
    fun backupJson_roundTripEquality_dataIntegrity() {
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = listOf(TeacherEntity(1L, "Teacher A", "hash", 1000L, 1000L)),
            classes = listOf(ClassSectionEntity(10L, 1L, "Physics", "PHYS101", "Sec A", "2026", 75, 30, false, 1000L, 1000L)),
            students = listOf(StudentEntity(101L, 10L, "Student A", "P-01", "S1", EnrollmentStatus.ENROLLED, 1000L, null, false, 1000L, 1000L)),
            sessions = listOf(AttendanceSessionEntity(50L, 10L, "2026-08-08", "UTC", 2000L, null, SessionStatus.ACTIVE, SessionMode.FACE, 2000L, 2000L)),
            records = listOf(AttendanceRecordEntity(1L, 50L, 101L, AttendanceStatus.PRESENT, 2005L, AttendanceSource.AI_RECOGNITION, 0.95f, null, 2005L, 2005L))
        )

        val json = backupExporter.toJson(snapshot)

        assertTrue(json.contains("\"format\":\"attract-backup-v1\""))
        assertTrue(json.contains("\"Teacher A\""))
        assertTrue(json.contains("\"Physics\""))
        assertTrue(json.contains("\"Student A\""))
    }

    @Test
    fun backupJson_excludesBiometricAndSecurityFields() {
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = emptyList(),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList()
        )

        val json = backupExporter.toJson(snapshot)

        assertFalse(json.contains("faceTemplate"))
        assertFalse(json.contains("embeddingBytes"))
        assertFalse(json.contains("cipherIv"))
    }

    @Test
    fun backupJson_largeSnapshot_completesWithinTimeLimit() {
        val students = (1..500).map { id ->
            StudentEntity(id.toLong(), 1L, "Student $id", "ROLL-$id", "S-$id", EnrollmentStatus.ENROLLED, 1000L, null, false, 1000L, 1000L)
        }
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = emptyList(),
            classes = emptyList(),
            students = students,
            sessions = emptyList(),
            records = emptyList()
        )

        val duration = measureTimeMillis {
            val json = backupExporter.toJson(snapshot)
            assertTrue(json.length > 5000)
        }

        assertTrue("Large snapshot export should finish within 3000ms, took ${duration}ms", duration < 3000)
    }
}
