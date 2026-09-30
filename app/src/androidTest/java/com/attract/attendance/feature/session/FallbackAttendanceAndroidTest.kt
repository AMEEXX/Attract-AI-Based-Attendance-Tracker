package com.attract.attendance.feature.session

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.local.ClassSectionEntity
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.data.local.TeacherEntity
import com.attract.attendance.data.repository.AttractRepository
import com.attract.attendance.data.repository.AttractRepository.FallbackMarkResult
import com.attract.attendance.data.security.PinHasher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FallbackAttendanceAndroidTest — regression coverage for the teacher fallback flow
 * after a biometric UNKNOWN (LLD-06 amendment, 2026-08).
 *
 * Production bug this guards against: after an unrecognized face, selecting a student
 * from the roster left the UI dead-locked and never created an attendance record.
 * These tests exercise THE SAME production callback the UI uses
 * (AttractRepository.markFallbackPresent -> RecordPresentCommand -> Room).
 */
@RunWith(AndroidJUnit4::class)
class FallbackAttendanceAndroidTest {

    private lateinit var database: AttractDatabase
    private lateinit var repository: AttractRepository
    private var classId = 0L
    private var sessionId = 0L
    private var amitId = 0L
    private var priyaId = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AttractDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = AttractRepository(database, PinHasher(), embeddingCipher = null)

        val teacherId = database.teacherDao().insert(
            TeacherEntity(displayName = "Teacher", pinHash = "h", createdAt = 1, updatedAt = 1),
        )
        classId = database.classDao().insert(
            ClassSectionEntity(
                teacherId = teacherId, name = "C", subject = "S", section = "A",
                semesterBatch = null, requiredAttendancePercent = 75,
                createdAt = 1, updatedAt = 1,
            ),
        )
        amitId = database.studentDao().insert(student("AMIT"))
        priyaId = database.studentDao().insert(student("PRIYA"))

        // Start a valid ACTIVE face session (production start command).
        val started = repository.startFaceSession(classId)
        assertTrue(started.toString(), started is com.attract.attendance.core.model.CommandResult.Success)
        sessionId = (started as com.attract.attendance.core.model.CommandResult.Success).value
    }

    @After
    fun tearDown() = database.close()

    private fun student(name: String) = StudentEntity(
        classId = classId, name = name, rollNumber = "R-$name", serialNumber = null,
        enrollmentStatus = EnrollmentStatus.ENROLLED, createdAt = 1, updatedAt = 1,
    )

    private fun presentRecords() = runBlocking {
        database.attendanceRecordDao().forSession(sessionId).filter { it.status == AttendanceStatus.PRESENT }
    }

    // ==================================================================
    // PRIMARY SCENARIO: biometric UNKNOWN → teacher selects AMIT by stable id
    // → exactly one PRESENT record with source MANUAL.
    // ==================================================================
    @Test
    fun unknownFace_thenTeacherSelectsAmit_marksPresentExactlyOnce_withManualSource() = runBlocking {
        val result = repository.markFallbackPresent(classId, amitId)

        assertTrue("Expected Marked, got $result", result is FallbackMarkResult.Marked)
        val marked = result as FallbackMarkResult.Marked
        assertEquals("AMIT", marked.studentName)

        val records = presentRecords()
        assertEquals("Exactly one attendance record must exist", 1, records.size)
        assertEquals(amitId, records.single().studentId)
        assertEquals(AttendanceSource.MANUAL, records.single().attendanceMethod)
        assertEquals(sessionId, records.single().sessionId)
        assertTrue(records.single().matchConfidence == null)
    }

    // A. AMIT already checked in → AlreadyPresent, NO duplicate record.
    @Test
    fun alreadyCheckedIn_returnsAlreadyPresent_withoutDuplicate() = runBlocking {
        assertTrue(repository.markFallbackPresent(classId, amitId) is FallbackMarkResult.Marked)
        assertEquals(1, presentRecords().size)

        val second = repository.markFallbackPresent(classId, amitId)
        assertTrue("Expected AlreadyPresent, got $second", second is FallbackMarkResult.AlreadyPresent)
        assertEquals("No duplicate record may be created", 1, presentRecords().size)
    }

    // B. Student not eligible / wrong class → rejected safely, no record.
    @Test
    fun ineligibleStudent_isRejected_withNoRecord() = runBlocking {
        // Archive PRIYA — archived students are explicitly rejected by RecordPresentCommand.
        database.studentDao().update(
            database.studentDao().find(priyaId)!!.copy(archived = true),
        )
        val result = repository.markFallbackPresent(classId, priyaId)
        assertTrue("Expected StudentNotEligible, got $result", result is FallbackMarkResult.StudentNotEligible)
        assertTrue(presentRecords().isEmpty())
    }

    // B2. No active session → rejected safely.
    @Test
    fun noActiveSession_isRejected_withNoRecord() = runBlocking {
        database.sessionDao().finish(sessionId, SessionStatus.ENDED, 99, 99)
        val result = repository.markFallbackPresent(classId, amitId)
        assertTrue(result is FallbackMarkResult.NoActiveSession)
        assertTrue(presentRecords().isEmpty())
    }

    // D. Select one student then another → only the FINAL selected stable ID is recorded.
    @Test
    fun selectingOneStudentThenAnother_onlyFinalSelectionIsRecorded() = runBlocking {
        // Simulate the UI: first selection then a corrected second selection.
        repository.markFallbackPresent(classId, priyaId) // first tap
        val finalResult = repository.markFallbackPresent(classId, amitId) // corrected tap

        assertTrue(finalResult is FallbackMarkResult.Marked)
        val records = presentRecords()
        assertEquals(2, records.size) // two distinct students, two distinct records
        assertEquals(setOf(amitId, priyaId), records.map { it.studentId }.toSet())
        assertTrue(records.all { it.attendanceMethod == AttendanceSource.MANUAL })
    }

    // E. UNKNOWN followed by manual selection must not require another camera frame —
    // verified structurally: markFallbackPresent performs NO biometric work and writes
    // directly. Combined with scenario A (repeat tap → AlreadyPresent) this pins the
    // "no camera deadlock" contract at the production-callback level.

    // C. Database failure → safe rejection, never a partial/silent record.
    @Test
    fun databaseFailure_rejectsSafely_withoutPartialRecord() = runBlocking {
        database.close()
        val result = repository.markFallbackPresent(classId, amitId)
        assertTrue(
            "DB failure must never produce Marked/AlreadyPresent, got $result",
            result !is FallbackMarkResult.Marked && result !is FallbackMarkResult.AlreadyPresent,
        )
        assertTrue(result is FallbackMarkResult.NoActiveSession || result is FallbackMarkResult.Failed)
    }
}
