package com.attract.attendance.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.AttendanceMethod
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * IT-01: RoomDatabaseIntegrationTest
 * Instrumented integration tests verifying Room database operations, DAOs, transactions, and archiving.
 */
@RunWith(AndroidJUnit4::class)
class RoomDatabaseIntegrationTest {
    private lateinit var database: AttractDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AttractDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun classDao_insertAndQuery_returnsCorrectClass() = runBlocking {
        val teacherId = database.teacherDao().insert(
            TeacherEntity(displayName = "Prof Smith", pinHash = "hash123", createdAt = 1000L, updatedAt = 1000L)
        )
        val classId = database.classDao().insert(
            ClassSectionEntity(
                teacherId = teacherId,
                name = "Computer Science 101",
                subject = "CS",
                section = "A",
                semesterBatch = "2026",
                requiredAttendancePercent = 75,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        val retrieved = database.classDao().find(classId)
        assertNotNull(retrieved)
        assertEquals("Computer Science 101", retrieved?.name)
        assertEquals("CS", retrieved?.subject)
    }

    @Test
    fun studentDao_insertMultiple_queryByClass() = runBlocking {
        val teacherId = database.teacherDao().insert(TeacherEntity(displayName = "Teacher", pinHash = "hash", createdAt = 1000L, updatedAt = 1000L))
        val classId = database.classDao().insert(ClassSectionEntity(teacherId = teacherId, name = "Math", subject = null, section = null, semesterBatch = null, requiredAttendancePercent = 75, createdAt = 1000L, updatedAt = 1000L))

        for (i in 1..50) {
            database.studentDao().insert(
                StudentEntity(
                    classId = classId,
                    name = "Student $i",
                    rollNumber = "CS-$i",
                    serialNumber = "S-$i",
                    enrollmentStatus = EnrollmentStatus.ENROLLED,
                    createdAt = 1000L,
                    updatedAt = 1000L
                )
            )
        }

        val students = database.studentDao().activeForClass(classId)
        assertEquals(50, students.size)
    }

    @Test
    fun faceTemplateDao_insertEncryptedBlob_retrieveIntact() = runBlocking {
        val teacherId = database.teacherDao().insert(TeacherEntity(displayName = "Teacher", pinHash = "hash", createdAt = 1000L, updatedAt = 1000L))
        val classId = database.classDao().insert(ClassSectionEntity(teacherId = teacherId, name = "Math", subject = null, section = null, semesterBatch = null, requiredAttendancePercent = 75, createdAt = 1000L, updatedAt = 1000L))
        val studentId = database.studentDao().insert(StudentEntity(classId = classId, name = "Alice", rollNumber = "CS-01", serialNumber = null, enrollmentStatus = EnrollmentStatus.ENROLLED, createdAt = 1000L, updatedAt = 1000L))

        val dummyCiphertext = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val templateId = database.faceTemplateDao().insert(
            FaceTemplateEntity(
                studentId = studentId,
                encryptedEmbedding = dummyCiphertext,
                cryptoVersion = 1,
                modelVersion = "1.0",
                qualityScore = 0.95f,
                capturedAt = 1000L,
                source = "ENROLLMENT"
            )
        )

        assertTrue(templateId > 0)
        val templates = database.faceTemplateDao().forStudent(studentId)
        assertEquals(1, templates.size)
        assertEquals(4, templates[0].encryptedEmbedding.size)
    }

    @Test
    fun classDao_archiveClass_hiddenFromActiveQuery() = runBlocking {
        val teacherId = database.teacherDao().insert(TeacherEntity(displayName = "Teacher", pinHash = "hash", createdAt = 1000L, updatedAt = 1000L))
        val classId = database.classDao().insert(ClassSectionEntity(teacherId = teacherId, name = "History", subject = null, section = null, semesterBatch = null, requiredAttendancePercent = 75, createdAt = 1000L, updatedAt = 1000L))

        database.classDao().setArchived(classId, true, 2000L)
        val all = database.classDao().all()
        val activeClasses = all.filter { !it.archived }
        assertTrue(activeClasses.none { it.id == classId })
    }
}
