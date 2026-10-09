package com.attract.attendance.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * MigrationTest — PR-01 data safety test suite.
 *
 * Verifies that Room database migrations preserve attendance sessions and attendance records
 * across every version path (1->6, 2->6, 3->6, 4->6, 5->6) using exported JSON schemas.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AttractDatabase::class.java
    )

    private fun insertBaseV1Data(db: SupportSQLiteDatabase, sessionId: Long = 1L, recordId: Long = 1L, sessionStatus: String = "COMPLETED", sessionMode: String = "MANUAL") {
        db.execSQL(
            "INSERT OR IGNORE INTO teachers (id, display_name, pin_hash, created_at, updated_at) " +
                "VALUES (1, 'Test Teacher', 'hash', 1000, 1000)"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO class_sections (id, teacher_id, name, subject, section, semester_batch, required_attendance_percent, archived, created_at, updated_at) " +
                "VALUES (1, 1, 'CS 101', 'Computer Science', 'A', '2026', 75, 0, 1000, 1000)"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO students (id, class_id, name, roll_number, serial_number, enrollment_status, enrolled_at, eligible_from_session_id, archived, created_at, updated_at) " +
                "VALUES (1, 1, 'Alice Smith', 'CS-001', '1', 'NOT_ENROLLED', NULL, NULL, 0, 1000, 1000)"
        )
        db.execSQL(
            "INSERT INTO attendance_sessions (id, class_id, session_date, time_zone_id, started_at, ended_at, status, mode, created_at, updated_at) " +
                "VALUES ($sessionId, 1, '2026-10-09', 'UTC', 1000, 2000, '$sessionStatus', '$sessionMode', 1000, 1000)"
        )
        db.execSQL(
            "INSERT INTO attendance_records (id, session_id, student_id, status, check_in_time, attendance_method, match_confidence, recognition_metadata, created_at, updated_at) " +
                "VALUES ($recordId, $sessionId, 1, 'PRESENT', 1500, '$sessionMode', NULL, NULL, 1000, 1000)"
        )
    }

    private fun getTableRowCount(db: SupportSQLiteDatabase, table: String): Int {
        val cursor = db.query("SELECT COUNT(*) FROM $table")
        return try {
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } finally {
            cursor.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To6_preservesAttendanceRecordsAndSessions() {
        var db = helper.createDatabase(TEST_DB, 1)
        insertBaseV1Data(db)
        db.close()

        db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            *AttractDatabase.ALL_MIGRATIONS
        )

        assertEquals("attendance_sessions count must be preserved", 1, getTableRowCount(db, "attendance_sessions"))
        assertEquals("attendance_records count must be preserved", 1, getTableRowCount(db, "attendance_records"))

        // Verify class_sections got total_planned_sessions with default 30
        val cursor = db.query("SELECT total_planned_sessions FROM class_sections WHERE id = 1")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals(30, cursor.getInt(0))
        } finally {
            cursor.close()
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To6_preservesAttendanceRecordsAndSessions() {
        var db = helper.createDatabase(TEST_DB, 2)
        insertBaseV1Data(db)
        db.close()

        db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            *AttractDatabase.ALL_MIGRATIONS
        )

        assertEquals(1, getTableRowCount(db, "attendance_sessions"))
        assertEquals(1, getTableRowCount(db, "attendance_records"))
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To6_preservesAttendanceRecordsAndSessions() {
        var db = helper.createDatabase(TEST_DB, 3)
        insertBaseV1Data(db)
        db.close()

        db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            *AttractDatabase.ALL_MIGRATIONS
        )

        assertEquals(1, getTableRowCount(db, "attendance_sessions"))
        assertEquals(1, getTableRowCount(db, "attendance_records"))
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To6_preservesAttendanceRecordsAndSessions() {
        var db = helper.createDatabase(TEST_DB, 4)
        // Insert a completed session (should be preserved) and an active face session (should be pruned by 4->5)
        insertBaseV1Data(db, sessionId = 1L, recordId = 1L, sessionStatus = "COMPLETED", sessionMode = "MANUAL")
        insertBaseV1Data(db, sessionId = 2L, recordId = 2L, sessionStatus = "ACTIVE", sessionMode = "FACE")
        db.close()

        db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            *AttractDatabase.ALL_MIGRATIONS
        )

        // The completed session must be strictly preserved; only active face session is cleaned up
        assertEquals(1, getTableRowCount(db, "attendance_sessions"))
        assertEquals(1, getTableRowCount(db, "attendance_records"))
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6_preservesAttendanceRecordsAndAddsTotalPlannedSessions() {
        var db = helper.createDatabase(TEST_DB, 5)
        insertBaseV1Data(db)
        db.close()

        db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            *AttractDatabase.ALL_MIGRATIONS
        )

        assertEquals(1, getTableRowCount(db, "attendance_sessions"))
        assertEquals(1, getTableRowCount(db, "attendance_records"))

        val cursor = db.query("SELECT total_planned_sessions FROM class_sections WHERE id = 1")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals(30, cursor.getInt(0))
        } finally {
            cursor.close()
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migration1To2_addsEmbeddingDimColumn() {
        var db = helper.createDatabase(TEST_DB, 1)
        db.execSQL(
            "INSERT OR IGNORE INTO teachers (id, display_name, pin_hash, created_at, updated_at) " +
                "VALUES (1, 'T', 'h', 1, 1)"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO class_sections (id, teacher_id, name, subject, section, semester_batch, required_attendance_percent, archived, created_at, updated_at) " +
                "VALUES (1, 1, 'C', 'S', 'A', NULL, 75, 0, 1, 1)"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO students (id, class_id, name, roll_number, serial_number, enrollment_status, enrolled_at, eligible_from_session_id, archived, created_at, updated_at) " +
                "VALUES (1, 1, 'S', 'R', NULL, 'NOT_ENROLLED', NULL, NULL, 0, 1, 1)"
        )
        db.execSQL(
            "INSERT INTO face_templates (id, student_id, encrypted_embedding, crypto_version, model_version, quality_score, captured_at, source, active) " +
                "VALUES (1, 1, X'0001', 1, 'v1', 0.9, 1000, 'test', 1)"
        )
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 2, true, AttractDatabase.MIGRATION_1_2)
        val cursor = db.query("SELECT embedding_dim FROM face_templates WHERE id = 1")
        try {
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        } finally {
            cursor.close()
        }
        db.close()
    }
}
