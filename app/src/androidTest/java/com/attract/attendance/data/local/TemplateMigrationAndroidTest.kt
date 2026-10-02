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

    private fun vec512(seed: Float = 1f): ByteArray {
        val raw = FloatArray(EmbeddingEngine.EMBEDDING_SIZE) { i -> ((i % 9) + seed) }
        var sumSq = 0f
        for (f in raw) sumSq += f * f
        val norm = kotlin.math.sqrt(sumSq)
        val v = FloatArray(EmbeddingEngine.EMBEDDING_SIZE) { i -> raw[i] / norm }
        return with(com.attract.attendance.domain.face.TemplateMatcher) { v.toByteArray() }
    }

    private fun vec32(): ByteArray {
        val raw = FloatArray(32) { i -> (i % 5 + 1).toFloat() / 5f }
        var sumSq = 0f
        for (f in raw) sumSq += f * f
        val norm = kotlin.math.sqrt(sumSq)
        val v = FloatArray(32) { i -> raw[i] / norm }
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
        val sid = database.studentDao().insert(studentEntity("Legacy").copy(enrollmentStatus = EnrollmentStatus.ENROLLED))
        // Simulate a legacy phone row: 32-D payload, no dimension stamp (dim=0 default).
        insertLegacyRow(sid)

        // Gate: stale template NEVER reaches recognition candidates.
        assertTrue(repository.getActiveTemplatesForClass(classId).isEmpty())

        val report = repository.retireIncompatibleTemplates(classId)
        assertEquals(1, report.deactivatedTemplates)
        assertEquals(listOf(sid), report.studentsNeedingReEnrollment)

        // Student marked REENROLL_REQUIRED (R04 repair status).
        assertEquals(EnrollmentStatus.REENROLL_REQUIRED, database.studentDao().find(sid)?.enrollmentStatus)
        // Sweep is idempotent.
        assertEquals(0, repository.retireIncompatibleTemplates(classId).deactivatedTemplates)
    }

    @Test
    fun enrollmentStoresCurrentDimension_andTemplateIsUsableForRecognition() = runBlocking {
        val sid = database.studentDao().insert(studentEntity("Fresh"))
        val result = repository.enrollStudentFace(sid, listOf(vec512(), vec512(2f)))
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
        val result = repository.enrollStudentFace(sid, listOf(vec512(), vec32()))
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
        val result = repository.enrollStudentFace(sid, listOf(vec512()))
        assertTrue(result is CommandResult.Success)

        val activeRows = database.faceTemplateDao().forStudent(sid)
        assertEquals(1, activeRows.size)
        assertEquals(TemplateCompatibility.CURRENT_EMBEDDING_DIM, activeRows.single().embeddingDim)

        // Recognition now sees exactly one usable 512-D template.
        val usable = repository.getActiveTemplatesForClass(classId)
        assertEquals(1, usable.size)
        assertEquals(EmbeddingEngine.EMBEDDING_SIZE, usable.single().embedding.size)

        // And the sweep has nothing left to retire.
        assertEquals(0, repository.retireIncompatibleTemplates(classId).deactivatedTemplates)
    }

    @Test
    fun migration_2_3_deactivates192DAndMarksReenrollRequired() {
        val openHelper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null) // in-memory
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE students (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, class_id INTEGER NOT NULL, name TEXT NOT NULL, roll_number TEXT NOT NULL, serial_number INTEGER, enrollment_status TEXT NOT NULL, enrolled_at INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE face_templates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, student_id INTEGER NOT NULL, encrypted_embedding BLOB NOT NULL, crypto_version INTEGER NOT NULL, model_version TEXT NOT NULL, embedding_dim INTEGER NOT NULL DEFAULT 0, quality_score REAL NOT NULL, captured_at INTEGER NOT NULL, source TEXT NOT NULL, active INTEGER NOT NULL)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val db = openHelper.writableDatabase
        try {
            // Seed student enrolled with 192-D template
            db.execSQL("INSERT INTO students (id, class_id, name, roll_number, enrollment_status, created_at, updated_at) VALUES (1, 10, 'Alice', 'R1', 'ENROLLED', 100, 100)")
            db.execSQL("INSERT INTO face_templates (student_id, encrypted_embedding, crypto_version, model_version, embedding_dim, quality_score, captured_at, source, active) VALUES (1, X'00', 1, 'v1', 192, 1.0, 100, 'test', 1)")

            // Execute migration 2 -> 3
            AttractDatabase.MIGRATION_2_3.migrate(db)

            val cursorTemplate = db.query("SELECT active FROM face_templates WHERE student_id = 1")
            cursorTemplate.moveToFirst()
            assertEquals(0, cursorTemplate.getInt(0)) // deactivated
            cursorTemplate.close()

            val cursorStudent = db.query("SELECT enrollment_status FROM students WHERE id = 1")
            cursorStudent.moveToFirst()
            assertEquals("REENROLL_REQUIRED", cursorStudent.getString(0)) // marked REENROLL_REQUIRED
            cursorStudent.close()
        } finally {
            db.close()
        }
    }

    @Test
    fun migration_3_4_reconcilesOrphanedEnrolledStudents() {
        val openHelper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null) // in-memory
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE students (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, class_id INTEGER NOT NULL, name TEXT NOT NULL, roll_number TEXT NOT NULL, serial_number INTEGER, enrollment_status TEXT NOT NULL, enrolled_at INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE face_templates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, student_id INTEGER NOT NULL, encrypted_embedding BLOB NOT NULL, crypto_version INTEGER NOT NULL, model_version TEXT NOT NULL, embedding_dim INTEGER NOT NULL DEFAULT 0, quality_score REAL NOT NULL, captured_at INTEGER NOT NULL, source TEXT NOT NULL, active INTEGER NOT NULL)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val db = openHelper.writableDatabase
        try {
            // Seed student orphaned with NO active templates (only inactive)
            db.execSQL("INSERT INTO students (id, class_id, name, roll_number, enrollment_status, created_at, updated_at) VALUES (2, 10, 'Bob', 'R2', 'ENROLLED', 100, 100)")
            db.execSQL("INSERT INTO face_templates (student_id, encrypted_embedding, crypto_version, model_version, embedding_dim, quality_score, captured_at, source, active) VALUES (2, X'00', 1, 'v1', 192, 1.0, 100, 'test', 0)")

            // Execute migration 3 -> 4
            AttractDatabase.MIGRATION_3_4.migrate(db)

            val cursorStudent = db.query("SELECT enrollment_status FROM students WHERE id = 2")
            cursorStudent.moveToFirst()
            assertEquals("REENROLL_REQUIRED", cursorStudent.getString(0)) // marked REENROLL_REQUIRED
            cursorStudent.close()
        } finally {
            db.close()
        }
    }
}
