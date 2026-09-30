package com.attract.attendance.data.importexport

import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.data.local.ClassReportStudentRow
import com.attract.attendance.data.local.SessionExportRow
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceExporterTest {

    private val exporter = AttendanceExporter()

    @Test
    fun buildCsv_emptyRows_generatesHeadersAndMetadata() {
        val csv = exporter.buildCsv(
            className = "Computer Science 101",
            generatedAtMillis = 1700000000000L,
            reportRows = emptyList(),
            sessionRows = emptyList()
        )

        assertTrue(csv.contains("Attract Attendance Export"))
        assertTrue(csv.contains("\"Class\",\"Computer Science 101\""))
        assertTrue(csv.contains("\"Roll Number\",\"Name\",\"Serial Number\",\"Enrollment\",\"Present\",\"Eligible\",\"Attendance %\""))
        assertTrue(csv.contains("\"Session ID\",\"Date\",\"Mode\",\"Roll Number\",\"Name\",\"Serial Number\",\"Status\",\"Method\",\"Check-in Time\""))
    }

    @Test
    fun buildCsv_withReportAndSessionRows_formatsAndEscapesValues() {
        val reportRows = listOf(
            ClassReportStudentRow(
                id = 1L,
                name = "Alice \"The Great\" Smith",
                rollNumber = "CS-001",
                serialNumber = "S-1",
                enrollmentStatus = EnrollmentStatus.ENROLLED,
                presentSessions = 9,
                eligibleSessions = 10
            )
        )

        val sessionRows = listOf(
            SessionExportRow(
                sessionId = 101L,
                sessionDate = "2026-08-08",
                mode = SessionMode.FACE,
                rollNumber = "CS-001",
                studentName = "Alice \"The Great\" Smith",
                serialNumber = "S-1",
                status = AttendanceStatus.PRESENT,
                attendanceMethod = AttendanceSource.AI_RECOGNITION,
                checkInTime = 1700000000000L
            )
        )

        val csv = exporter.buildCsv(
            className = "Data Structures, Section A",
            generatedAtMillis = 1700000000000L,
            reportRows = reportRows,
            sessionRows = sessionRows
        )

        assertTrue(csv.contains("\"Alice \"\"The Great\"\" Smith\""))
        assertTrue(csv.contains("\"90.0%\""))
        assertTrue(csv.contains("\"101\""))
        assertTrue(csv.contains("\"PRESENT\""))
        assertTrue(csv.contains("\"FACE\""))
    }

    @Test
    fun buildCsv_formulaInjectionInName_prefixedWithApostrophe() {
        val reportRows = listOf(
            ClassReportStudentRow(
                id = 1L,
                name = "=SUM(1,2)",
                rollNumber = "+12345",
                serialNumber = "-999",
                enrollmentStatus = EnrollmentStatus.ENROLLED,
                presentSessions = 1,
                eligibleSessions = 1
            )
        )

        val csv = exporter.buildCsv(
            className = "@ClassHeader",
            generatedAtMillis = 1700000000000L,
            reportRows = reportRows,
            sessionRows = emptyList()
        )

        assertTrue(csv.contains("\"'=SUM(1,2)\""))
        assertTrue(csv.contains("\"'+12345\""))
        assertTrue(csv.contains("\"'-999\""))
        assertTrue(csv.contains("\"'@ClassHeader\""))
    }
}
