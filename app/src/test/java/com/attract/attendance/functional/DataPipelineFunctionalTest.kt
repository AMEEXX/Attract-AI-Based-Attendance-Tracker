package com.attract.attendance.functional

import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.data.importexport.AttendanceExporter
import com.attract.attendance.data.importexport.RosterParseResult
import com.attract.attendance.data.importexport.RosterTablePlanner
import com.attract.attendance.data.local.ClassReportStudentRow
import com.attract.attendance.data.local.SessionExportRow
import com.attract.attendance.domain.NormalizeRollNumber
import com.attract.attendance.test.FakeAttendanceRecordDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 2 Functional Test: Data Pipeline
 * Verifies end-to-end flow from raw roster input parsing -> roll number normalization
 * -> database DAO storage -> class summary report generation & CSV export output.
 */
class DataPipelineFunctionalTest {

    @Test
    fun completeDataPipeline_rosterToExport_executesSuccessfully() = runTest {
        // Step 1: Parse incoming raw roster table
        val planner = RosterTablePlanner()
        val rawRoster = listOf(
            listOf("Roll Number", "Student Name", "Serial Number"),
            listOf("  CS / 2026 - 001  ", "Alice Smith", "S-01"),
            listOf("cs-2026-002", "Bob Johnson", "S-02")
        )
        val parseResult = planner.createPlan(rawRoster)
        assertTrue(parseResult is RosterParseResult.Success)
        val students = (parseResult as RosterParseResult.Success).entries

        // Step 2: Normalize student roll numbers for database storage
        val normalizedStudents = students.map { student ->
            student.copy(rollNumber = NormalizeRollNumber.normalizeForDisplay(student.rollNumber))
        }
        assertEquals("CS / 2026 - 001", normalizedStudents[0].rollNumber)
        assertEquals("CS-2026-002", normalizedStudents[1].rollNumber)

        // Step 3: Insert into Fake DAO & generate attendance records
        val recordDao = FakeAttendanceRecordDao()
        val sessionExportRows = mutableListOf<SessionExportRow>()

        normalizedStudents.forEachIndexed { index, student ->
            val recordId = recordDao.insert(
                com.attract.attendance.data.local.AttendanceRecordEntity(
                    sessionId = 100L,
                    studentId = (index + 1).toLong(),
                    status = AttendanceStatus.PRESENT,
                    checkInTime = 1700000000000L + (index * 1000),
                    attendanceMethod = AttendanceSource.AI_RECOGNITION,
                    matchConfidence = 0.95f,
                    recognitionMetadata = "cos_sim=0.95",
                    createdAt = 1700000000000L,
                    updatedAt = 1700000000000L
                )
            )
            assertTrue(recordId > 0)

            sessionExportRows += SessionExportRow(
                sessionId = 100L,
                sessionDate = "2026-08-08",
                mode = SessionMode.FACE,
                studentName = student.name,
                rollNumber = student.rollNumber,
                serialNumber = student.serialNumber,
                status = AttendanceStatus.PRESENT,
                attendanceMethod = AttendanceSource.AI_RECOGNITION,
                checkInTime = 1700000000000L + (index * 1000)
            )
        }

        // Step 4: Generate Class Report Student Rows
        val reportRows = normalizedStudents.mapIndexed { index, student ->
            ClassReportStudentRow(
                id = (index + 1).toLong(),
                name = student.name,
                rollNumber = student.rollNumber,
                serialNumber = student.serialNumber,
                enrollmentStatus = EnrollmentStatus.ENROLLED,
                presentSessions = 1,
                eligibleSessions = 1
            )
        }

        // Step 5: Export CSV report and verify content structure
        val exporter = AttendanceExporter()
        val csvOutput = exporter.buildCsv(
            className = "Algorithms & Data Structures",
            generatedAtMillis = 1700000000000L,
            reportRows = reportRows,
            sessionRows = sessionExportRows
        )

        assertTrue(csvOutput.contains("Attract Attendance Export"))
        assertTrue(csvOutput.contains("\"Algorithms & Data Structures\""))
        assertTrue(csvOutput.contains("\"CS / 2026 - 001\""))
        assertTrue(csvOutput.contains("\"Alice Smith\""))
        assertTrue(csvOutput.contains("\"100.0%\""))
        assertTrue(csvOutput.contains("\"PRESENT\""))
        assertTrue(csvOutput.contains("\"FACE\""))
    }
}
