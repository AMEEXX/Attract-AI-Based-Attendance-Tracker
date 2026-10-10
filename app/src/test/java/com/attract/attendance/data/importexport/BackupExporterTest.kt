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

        assertTrue(json.contains("\"format\":\"attract-backup-v2\""))
        assertTrue(json.contains("\"generatedAt\":1700000000000"))
        assertTrue(json.contains("\"teachers\":[]"))
        assertTrue(json.contains("\"classes\":[]"))
        assertTrue(json.contains("\"students\":[]"))
        assertTrue(json.contains("\"sessions\":[]"))
        assertTrue(json.contains("\"records\":[]"))
        assertTrue(json.contains("\"faceTemplates\":[]"))
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

        val faceTemplate = BackupFaceTemplate(
            id = 1L,
            studentId = 100L,
            modelVersion = "v1",
            embeddingDim = 512,
            poseBucket = "FRONTAL",
            qualityScore = 0.99f,
            embeddingBase64 = "AQIDBA==",
            capturedAt = 1500L,
            source = "enrollment",
            active = true,
        )

        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = listOf(teacher),
            classes = listOf(classItem),
            students = listOf(student),
            sessions = listOf(session),
            records = listOf(record),
            faceTemplates = listOf(faceTemplate),
        )

        val json = exporter.toJson(snapshot)

        assertTrue(json.contains("\\\"Alan\\\""))
        assertTrue(json.contains("\\nProfessor"))
        assertTrue(json.contains("\"name\":\"CS101\""))
        assertTrue(json.contains("\"rollNumber\":\"R-001\""))
        assertTrue(json.contains("\"status\":\"ENDED\""))
        assertTrue(json.contains("\"attendanceMethod\":\"AI_RECOGNITION\""))
        assertTrue(json.contains("\"matchConfidence\":0.98"))
        // PR-03 / SDD §66: Biometric face templates are excluded from backup exports
        assertTrue(json.contains("\"faceTemplates\":[]"))
    }

    @Test
    fun faceTemplates_excludedFromExportPerSdd66() {
        val templates = listOf(
            BackupFaceTemplate(
                id = 10L,
                studentId = 100L,
                modelVersion = "facenet_mobile_v1",
                embeddingDim = 512,
                poseBucket = "FRONTAL",
                qualityScore = 0.97f,
                embeddingBase64 = "base64frontal==",
                capturedAt = 5000L,
                source = "enrollment_frame_1",
                active = true,
            ),
        )

        val originalSnapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = emptyList(),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList(),
            faceTemplates = templates,
        )

        val json = exporter.toJson(originalSnapshot)
        val restored = exporter.fromJson(json)

        // Face templates must be stripped on export
        org.junit.Assert.assertTrue(restored.faceTemplates.isEmpty())
    }

    @Test
    fun encryptedBackup_roundTripWithPin_succeedsAndIsUnreadableAsPlaintext() {
        val teacher = TeacherEntity(
            id = 1L,
            displayName = "Dr. Alan Turing",
            pinHash = "hash123",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = listOf(teacher),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList(),
        )

        val pin = "1234".toCharArray()
        val plaintextJson = exporter.toJson(snapshot)
        val encryptedEnvelope = BackupCrypto.encrypt(plaintextJson, pin)

        // Must be unreadable in text editor (no cleartext names or PII)
        org.junit.Assert.assertFalse(encryptedEnvelope.contains("Dr. Alan Turing"))
        org.junit.Assert.assertTrue(encryptedEnvelope.contains(BackupCrypto.FORMAT_ENCRYPTED_V2))

        // Decrypt with correct PIN
        val decryptedJson = BackupCrypto.decrypt(encryptedEnvelope, "1234".toCharArray())
        val restoredSnapshot = exporter.fromJson(decryptedJson)

        org.junit.Assert.assertEquals(1, restoredSnapshot.teachers.size)
        org.junit.Assert.assertEquals("Dr. Alan Turing", restoredSnapshot.teachers[0].displayName)
    }

    @Test(expected = Exception::class)
    fun encryptedBackup_wrongPin_failsDecryption() {
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = emptyList(),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList(),
        )
        val encryptedEnvelope = BackupCrypto.encrypt(exporter.toJson(snapshot), "1234".toCharArray())
        BackupCrypto.decrypt(encryptedEnvelope, "9999".toCharArray())
    }

    @Test
    fun encryptedBackup_twelvePlusCharPassphrase_succeeds() {
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = listOf(
                TeacherEntity(id = 1L, displayName = "Teacher 1", pinHash = "hash", createdAt = 0L, updatedAt = 0L)
            ),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList(),
        )
        val passphrase = "Secure-Passphrase-2026!".toCharArray()
        val encrypted = BackupCrypto.encrypt(exporter.toJson(snapshot), passphrase)
        val decrypted = BackupCrypto.decrypt(encrypted, passphrase)
        val restored = exporter.fromJson(decrypted)
        org.junit.Assert.assertEquals("Teacher 1", restored.teachers[0].displayName)
    }

    @Test(expected = Exception::class)
    fun encryptedBackup_tamperedCiphertext_failsDecryption() {
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = emptyList(),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList(),
        )
        val passphrase = "Secure-Passphrase-2026!".toCharArray()
        val encrypted = BackupCrypto.encrypt(exporter.toJson(snapshot), passphrase)
        
        // Tamper with the envelope JSON
        val tampered = encrypted.replace("ciphertext\": \"", "ciphertext\": \"AAAA")
        BackupCrypto.decrypt(tampered, passphrase)
    }
}
