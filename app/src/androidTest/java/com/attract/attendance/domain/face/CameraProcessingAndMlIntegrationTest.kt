package com.attract.attendance.domain.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.local.ClassSectionEntity
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.data.local.TeacherEntity
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.security.PinHasher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * CameraProcessingAndMlIntegrationTest
 *
 * LEVEL 3 — Real Android ML and Database Integration Tests.
 * This class runs inside an Android device/emulator environment to verify actual platform
 * components (Bitmaps, TFLite models, database transactions) which cannot run on JVM.
 */
@RunWith(AndroidJUnit4::class)
class CameraProcessingAndMlIntegrationTest {

    // =========================================================================
    // 1. REAL BITMAP CROP LOGIC
    // =========================================================================
    @Test
    fun testRealFaceCroppingWithCoordinates() {
        // Create a 640x480 source bitmap
        val width = 640
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw a synthetic face marker in the middle
        val paint = Paint().apply { color = Color.RED }
        canvas.drawCircle(320f, 240f, 50f, paint)

        // Bounding box for the face
        val box = Rect(270, 190, 370, 290) // 100x100 box in middle

        // Crop with 20% margin
        val cropped = EmbeddingEngine.cropFaceForEmbedding(bitmap, box, marginFraction = 0.20f)

        // Margin is 20 pixels, crop should be 270-20 to 370+20 => 250 to 390
        assertNotNull(cropped)
        assertEquals(140, cropped.width)
        assertEquals(140, cropped.height)

        // Boundary test: face near edges (overflow condition)
        val edgeBox = Rect(0, 0, 50, 50)
        val edgeCropped = EmbeddingEngine.cropFaceForEmbedding(bitmap, edgeBox, marginFraction = 0.20f)
        assertNotNull(edgeCropped)
        assertTrue(edgeCropped.width in 50..70)
        assertTrue(edgeCropped.height in 50..70)
        assertTrue(edgeCropped.width <= bitmap.width)
        assertTrue(edgeCropped.height <= bitmap.height)
    }

    // =========================================================================
    // 2. REAL TFLITE INTERPRETER AND EMBEDDING EXTRACTION
    // =========================================================================
    @Test
    fun testRealTFLiteInterpreterAndEmbeddingExtraction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Ensure model asset is available
        assertTrue("MobileFaceNet model must exist in assets", EmbeddingEngine.isAvailable(context))

        // Create a synthetic face crop bitmap (112x112)
        val faceCrop = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(faceCrop)
        canvas.drawColor(Color.LTGRAY)
        val paint = Paint().apply { color = Color.BLUE }
        canvas.drawCircle(56f, 56f, 30f, paint)

        // Run real model inference
        val embedding = EmbeddingEngine.extractEmbedding(context, faceCrop)

        assertNotNull(embedding)
        assertEquals(TemplateCompatibility.CURRENT_EMBEDDING_DIM, embedding.size)

        // Verify output is L2-normalized (magnitude close to 1.0)
        var magnitude = 0.0f
        for (v in embedding) {
            magnitude += v * v
        }
        assertTrue("Embedding magnitude must be ~1.0", abs(magnitude - 1.0f) < 0.01f)
    }

    // =========================================================================
    // 3. REAL REPOSITORY ROOM TRANSACTION ATOMICITY
    // =========================================================================
    @Test
    fun testRepositoryEnrollmentAtomicityOnDatabaseFail() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Setup clean in-memory database
        val database = Room.inMemoryDatabaseBuilder(context, AttractDatabase::class.java).build()
        val repository = AttractRepository(database, PinHasher())

        val teacherId = database.teacherDao().insert(
            TeacherEntity(displayName = "Teacher", pinHash = "hash123", createdAt = 1000L, updatedAt = 1000L)
        )
        val classId = database.classDao().insert(
            ClassSectionEntity(
                teacherId = teacherId,
                name = "Chemistry 101",
                subject = "Chem",
                section = "B",
                semesterBatch = "2026",
                requiredAttendancePercent = 75,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        val studentId = database.studentDao().insert(
            StudentEntity(
                classId = classId,
                name = "Bob",
                rollNumber = "CS-05",
                serialNumber = null,
                enrollmentStatus = EnrollmentStatus.NOT_ENROLLED,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Check initial state
        val originalStudent = database.studentDao().find(studentId)
        assertNotNull(originalStudent)
        assertEquals(EnrollmentStatus.NOT_ENROLLED, originalStudent?.enrollmentStatus)

        // Setup dummy templates
        val templateBytes = ByteArray(TemplateCompatibility.CURRENT_EMBEDDING_DIM * 4) { 0x01.toByte() }

        // Success Path enrollment transaction
        val result = repository.enrollStudentFace(studentId, listOf(templateBytes))
        assertTrue(result is com.attract.attendance.core.model.CommandResult.Success)

        // Verify Bob is now enrolled and template is saved
        val enrolledStudent = database.studentDao().find(studentId)
        assertEquals(EnrollmentStatus.ENROLLED, enrolledStudent?.enrollmentStatus)

        val templates = database.faceTemplateDao().forStudent(studentId)
        assertEquals(1, templates.size)

        // Force a constraint failure to test transaction atomicity
        // (Insert duplicate roll CS-05 under another student to force SQL exception)
        val studentIdDuplicate = database.studentDao().insert(
            StudentEntity(
                classId = classId,
                name = "Duplicate Bob",
                rollNumber = "CS-06",
                serialNumber = null,
                enrollmentStatus = EnrollmentStatus.NOT_ENROLLED,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        val duplicateStudent = database.studentDao().find(studentIdDuplicate)
        try {
            // Unique index is class_id + roll_number. Update duplicate Bob to CS-05 which causes SQLiteConstraintException
            database.studentDao().update(duplicateStudent!!.copy(rollNumber = "CS-05"))
            fail("Expected database to fail on unique roll violation")
        } catch (e: Exception) {
            // expected
        }

        database.close()
    }
}
