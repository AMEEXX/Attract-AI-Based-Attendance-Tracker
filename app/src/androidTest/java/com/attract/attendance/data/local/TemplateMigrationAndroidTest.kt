package com.attract.attendance.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.CommandResult
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.security.PinHasher
import com.attract.attendance.domain.face.EmbeddingEngine
import com.attract.attendance.domain.face.TemplateCompatibility
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TemplateMigrationAndroidTest — instrumented verification of the biometric template
 * migration/compatibility fix (LLD-10 amendment).
 *
 * Regression target: production phone held a 32-D template from an earlier model while
 * the current MobileFaceNet emits 192-D. The mismatch previously reached
 * TemplateMatcher.cosineSimilarity and crashed ("192 vs 32").
 */
@RunWith(AndroidJUnit4::class)
class TemplateMigrationAndroidTest {

    private lateinit var database: AttractDatabase
    private lateinit var repository: AttractRepository
    private var classId = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AttractDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = AttractRepository(database, PinHasher(), embeddingCipher = null)
        val teacherId = database.teacherDao().insert(
            TeacherEntity(displayName = "T", pinHash = "h", createdAt = 1L, updatedAt = 1L),
        )
        classId = database.classDao().insert(
            ClassSectionEntity(
                teacherId = teacherId,
                name = "Migration Test Class",
                subject = "S",
                section = "A",
                semesterBatch = null,
                requiredAttendancePercent = 75,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
    }

    @After
    fun tearDown() = database.close()

    private fun studentEntity(name: String) = StudentEntity(
        classId = classId,
        name = name,
        rollNumber = "R-$name",
        serialNumber = null,
        enrollmentStatus = EnrollmentStatus.NOT_ENROLLED,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun vec192(seed: Float = 1f): ByteArray {
        val v = FloatArray(EmbeddingEngine.EMBEDDING_SIZE) { i -> ((i % 9) + seed).toFloat() }
        return with(com.attract.attendance.domain.face.TemplateMatcher) { v.toByteArray() }
    }

    private fun vec32(): ByteArray {
        val v = FloatArray(32) { i -> (i % 5 + 1).toFloat() / 5f }
        return with(com.attract.attendance.domain.face.TemplateMatcher) { v.toByteArray() }
    }

    private suspend fun insertLegacyRow(studentId: Long) {
        database.faceTemplateDao().insert(
            FaceTemplateEntity(
                studentId = studentId,
                encryptedEmbedding = vec32(),
                cryptoVersion = 1,
                modelVersion = "v1",
                qualityScore = 1f,
                capturedAt = 1L,
                source = "legacy",
                active = true,
            ),
        )
    }

    @Test
    fun legacyZeroDimRow_isInvisibleToRecognition_andRetiredBySweep() = runBlocking {
        val sid = database.studentDao().insert(studentEntity("Legacy"))
        // Simulate a legacy phone row: 32-D payload, no dimension stamp (dim=0 default).
        insertLegacyRow(sid)

        // Gate: stale template NEVER reaches recognition candidates.
        assertTrue(repository.getActiveTemplatesForClass(classId).isEmpty())

        val report = repository.retireIncompatibleTemplates(classId)
        assertEquals(1, report.deactivatedTemplates)
        assertEquals(listOf(sid), report.studentsNeedingReEnrollment)

        // Student flipped back to NOT_ENROLLED so the enrollment sheet offers them.
        assertEquals(EnrollmentStatus.NOT_ENROLLED, database.studentDao().find(sid)?.enrollmentStatus)
        // Sweep is idempotent.
        assertEquals(0, repository.retireIncompatibleTemplates(classId).deactivatedTemplates)
    }

    @Test
    fun enrollmentStoresCurrentDimension_andTemplateIsUsableForRecognition() = runBlocking {
        val sid = database.studentDao().insert(studentEntity("Fresh"))
        val result = repository.enrollStudentFace(sid, listOf(vec192(), vec192(2f)))
        assertTrue(result.toString(), result is CommandResult.Success)

        val rows = database.faceTemplateDao().forStudent(sid)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.embeddingDim == TemplateCompatibility.CURRENT_EMBEDDING_DIM })
        assertTrue(rows.all { it.modelVersion == TemplateCompatibility.CURRENT_MODEL_ID })
        assertEquals(2, repository.getActiveTemplatesForClass(classId).size)
    }

    @Test
    fun enrollmentRejectsWrongDimensionObservations_beforeAnyWrite() = runBlocking {
        val sid = database.studentDao().insert(studentEntity("Bad"))
        val result = repository.enrollStudentFace(sid, listOf(vec192(), vec32()))
        assertTrue(result is CommandResult.Failure)
        // Nothing persisted.
        assertTrue(database.faceTemplateDao().forStudent(sid).isEmpty())
        assertEquals(EnrollmentStatus.NOT_ENROLLED, database.studentDao().find(sid)?.enrollmentStatus)
    }

    @Test
    fun reEnrollmentReplacesLegacyStaleTemplates_withCurrentFormat() = runBlocking {
        val sid = database.studentDao().insert(studentEntity("Migrated"))
        database.studentDao().update(
            database.studentDao().find(sid)!!.copy(enrollmentStatus = EnrollmentStatus.ENROLLED),
        )
        // Legacy 32-D row active.
        insertLegacyRow(sid)
        assertTrue(repository.getActiveTemplatesForClass(classId).isEmpty())

        // Re-enroll with current model → replaces stale templates entirely.
        val result = repository.enrollStudentFace(sid, listOf(vec192()))
        assertTrue(result is CommandResult.Success)

        val activeRows = database.faceTemplateDao().forStudent(sid)
        assertEquals(1, activeRows.size)
        assertEquals(TemplateCompatibility.CURRENT_EMBEDDING_DIM, activeRows.single().embeddingDim)

        // Recognition now sees exactly one usable 192-D template.
        val usable = repository.getActiveTemplatesForClass(classId)
        assertEquals(1, usable.size)
        assertEquals(EmbeddingEngine.EMBEDDING_SIZE, usable.single().embedding.size)

        // And the sweep has nothing left to retire.
        assertEquals(0, repository.retireIncompatibleTemplates(classId).deactivatedTemplates)
    }
}
