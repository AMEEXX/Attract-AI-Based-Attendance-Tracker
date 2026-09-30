package com.attract.attendance.data.importexport

import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.ClassSectionEntity
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.data.local.TeacherEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExporterTest {

    private val exporter = BackupExporter()

    @Test
    fun toJson_emptySnapshot_serializesCorrectFormat() {
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = emptyList(),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList()
        )

        val json = exporter.toJson(snapshot)

        assertTrue(json.contains("\"format\":\"attract-backup-v1\""))
        assertTrue(json.contains("\"generatedAt\":1700000000000"))
        assertTrue(json.contains("\"teachers\":[]"))
        assertTrue(json.contains("\"classes\":[]"))
        assertTrue(json.contains("\"students\":[]"))
        assertTrue(json.contains("\"sessions\":[]"))
        assertTrue(json.contains("\"records\":[]"))
    }

    @Test
    fun toJson_populatedSnapshot_escapesJsonCharactersAndFormatsEntities() {
        val teacher = TeacherEntity(
            id = 1L,
            displayName = "Dr. \"Alan\" Turing\nProfessor",
            pinHash = "hash123",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val classItem = ClassSectionEntity(
            id = 10L,
            teacherId = 1L,
            name = "CS101",
            subject = "Intro to CS",
            section = "A",
            semesterBatch = "2026",
            requiredAttendancePercent = 75,
            archived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val student = StudentEntity(
            id = 100L,
            classId = 10L,
            name = "John Doe",
            rollNumber = "R-001",
            serialNumber = "S1",
            enrollmentStatus = EnrollmentStatus.ENROLLED,
            enrolledAt = 1000L,
            eligibleFromSessionId = 1L,
            archived = false,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val session = AttendanceSessionEntity(
            id = 500L,
            classId = 10L,
            sessionDate = "2026-08-08",
            timeZoneId = "UTC",
            startedAt = 2000L,
            endedAt = 3000L,
            status = SessionStatus.ENDED,
            mode = SessionMode.FACE,
            createdAt = 2000L,
            updatedAt = 3000L
        )

        val record = AttendanceRecordEntity(
            id = 1000L,
            sessionId = 500L,
            studentId = 100L,
            status = AttendanceStatus.PRESENT,
            checkInTime = 2500L,
            attendanceMethod = AttendanceSource.AI_RECOGNITION,
            matchConfidence = 0.98f,
            recognitionMetadata = "meta",
            createdAt = 2500L,
            updatedAt = 2500L
        )

        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = listOf(teacher),
            classes = listOf(classItem),
            students = listOf(student),
            sessions = listOf(session),
            records = listOf(record)
        )

        val json = exporter.toJson(snapshot)

        assertTrue(json.contains("\\\"Alan\\\""))
        assertTrue(json.contains("\\nProfessor"))
        assertTrue(json.contains("\"name\":\"CS101\""))
        assertTrue(json.contains("\"rollNumber\":\"R-001\""))
        assertTrue(json.contains("\"status\":\"ENDED\""))
        assertTrue(json.contains("\"attendanceMethod\":\"AI_RECOGNITION\""))
        assertTrue(json.contains("\"matchConfidence\":0.98"))
    }
}
