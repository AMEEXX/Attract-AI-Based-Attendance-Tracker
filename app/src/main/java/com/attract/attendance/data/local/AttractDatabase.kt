package com.attract.attendance.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        TeacherEntity::class,
        ClassSectionEntity::class,
        StudentEntity::class,
        FaceTemplateEntity::class,
        AttendanceSessionEntity::class,
        AttendanceRecordEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AttractDatabase : RoomDatabase() {
    abstract fun teacherDao(): TeacherDao
    abstract fun classDao(): ClassDao
    abstract fun studentDao(): StudentDao
    abstract fun sessionDao(): SessionDao
    abstract fun attendanceRecordDao(): AttendanceRecordDao
    abstract fun faceTemplateDao(): FaceTemplateDao

    companion object {
        /** v1→v2: biometric template dimension tracking (LLD-10 migration amendment). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_one_active_face_session")
                db.execSQL(
                    "ALTER TABLE face_templates ADD COLUMN embedding_dim INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** v2→v3: ArcFace 512-D migration — deactivate incompatible legacy templates & mark orphaned students. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_one_active_face_session")
                db.execSQL(
                    "UPDATE face_templates SET active = 0 WHERE embedding_dim != 512",
                )
                db.execSQL(
                    """
                    UPDATE students SET enrollment_status = 'REENROLL_REQUIRED'
                    WHERE enrollment_status = 'ENROLLED'
                      AND id NOT IN (
                          SELECT DISTINCT student_id FROM face_templates
                          WHERE active = 1 AND embedding_dim = 512
                      )
                    """.trimIndent(),
                )
            }
        }

        /** v3→v4: ArcFace 512-D repair — mark orphaned students REENROLL_REQUIRED and track pose_bucket. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_one_active_face_session")
                db.execSQL(
                    "ALTER TABLE face_templates ADD COLUMN pose_bucket TEXT NOT NULL DEFAULT 'FRONTAL'",
                )
                db.execSQL(
                    """
                    UPDATE students SET enrollment_status = 'REENROLL_REQUIRED'
                    WHERE enrollment_status = 'ENROLLED'
                      AND id NOT IN (
                          SELECT DISTINCT student_id FROM face_templates
                          WHERE active = 1 AND embedding_dim = 512
                      )
                    """.trimIndent(),
                )
            }
        }

        /** v4→v5: ArcFace 512-D BGR + relative yaw rebuild (WP-A, D-007). Incompatible templates purged; students reset to NOT_ENROLLED. Attendance history preserved. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_one_active_face_session")
                db.execSQL("DELETE FROM face_templates")
                db.execSQL("UPDATE students SET enrollment_status = 'NOT_ENROLLED', enrolled_at = NULL")
                db.execSQL(
                    "DELETE FROM attendance_records WHERE session_id IN (SELECT id FROM attendance_sessions WHERE status = 'ACTIVE' AND mode = 'FACE')"
                )
                db.execSQL("DELETE FROM attendance_sessions WHERE status = 'ACTIVE' AND mode = 'FACE'")
            }
        }

        /** v5→v6: Total planned classes / target classes per class section. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Must drop index_one_active_face_session so Room schema validation passes;
                // onOpen() will recreate it after validation.
                db.execSQL("DROP INDEX IF EXISTS index_one_active_face_session")
                try {
                    val cursor = db.query("PRAGMA table_info(class_sections)")
                    var columnExists = false
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex != -1 && cursor.getString(nameIndex) == "total_planned_sessions") {
                            columnExists = true
                            break
                        }
                    }
                    cursor.close()

                    if (!columnExists) {
                        db.execSQL(
                            "ALTER TABLE class_sections ADD COLUMN total_planned_sessions INTEGER NOT NULL DEFAULT 30"
                        )
                    }
                } catch (e: Throwable) {
                    com.attract.attendance.util.AppLog.w("AttractDatabase", "MIGRATION_5_6 failed to inspect/add column", e)
                    try {
                        db.execSQL(
                            "ALTER TABLE class_sections ADD COLUMN total_planned_sessions INTEGER NOT NULL DEFAULT 30"
                        )
                    } catch (_: Throwable) {}
                }
            }
        }

        const val DB_NAME = "attract.db"
        const val CURRENT_VERSION = 6

        /**
         * Before any migration runs, take an automatic snapshot of the existing database file.
         * Keeps the last 3 snapshots in files/backups/ (PR-01 data safety).
         */
        fun takePreMigrationSnapshotIfNeeded(context: Context, targetVersion: Int = CURRENT_VERSION) {
            try {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists() || dbFile.length() == 0L) return

                val currentVersion = try {
                    val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                    val v = db.version
                    db.close()
                    v
                } catch (_: Throwable) {
                    0
                }

                if (currentVersion in 1 until targetVersion) {
                    val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
                    val backupFile = File(backupDir, "attract-pre-v$currentVersion-${System.currentTimeMillis()}.db")
                    dbFile.copyTo(backupFile, overwrite = true)
                    com.attract.attendance.util.AppLog.i("AttractDatabase", "Created pre-migration snapshot: v$currentVersion -> v$targetVersion")

                    // Prune snapshots older than the last 3
                    val allSnapshots = backupDir.listFiles { _, name ->
                        name.startsWith("attract-pre-v") && name.endsWith(".db")
                    }?.sortedBy { it.lastModified() }
                    if (allSnapshots != null && allSnapshots.size > 3) {
                        allSnapshots.take(allSnapshots.size - 3).forEach { it.delete() }
                    }
                }
            } catch (t: Throwable) {
                com.attract.attendance.util.AppLog.w("AttractDatabase", "Could not take pre-migration snapshot", t)
            }
        }

        /**
         * Before restoring any backup, take an automatic snapshot of the existing database (PR-01 §3 / PR-03 §5).
         * Keeps the last 3 snapshots in files/backups/.
         */
        fun takePreRestoreSnapshot(context: Context) {
            try {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists() || dbFile.length() == 0L) return
                val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
                val backupFile = File(backupDir, "attract-pre-restore-${System.currentTimeMillis()}.db")
                dbFile.copyTo(backupFile, overwrite = true)
                com.attract.attendance.util.AppLog.i("AttractDatabase", "Created pre-restore snapshot: ${backupFile.name}")

                val allSnapshots = backupDir.listFiles { _, name ->
                    name.startsWith("attract-pre-restore-") && name.endsWith(".db")
                }?.sortedBy { it.lastModified() }
                if (allSnapshots != null && allSnapshots.size > 3) {
                    allSnapshots.take(allSnapshots.size - 3).forEach { it.delete() }
                }
            } catch (t: Throwable) {
                com.attract.attendance.util.AppLog.w("AttractDatabase", "Could not take pre-restore snapshot", t)
            }
        }
        fun restoreLatestSnapshot(context: Context): Boolean {
            return try {
                val backupDir = File(context.filesDir, "backups")
                val latest = backupDir.listFiles { _, name ->
                    name.startsWith("attract-pre-v") && name.endsWith(".db")
                }?.maxByOrNull { it.lastModified() } ?: return false

                val dbFile = context.getDatabasePath(DB_NAME)
                latest.copyTo(dbFile, overwrite = true)
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                com.attract.attendance.util.AppLog.i("AttractDatabase", "Restored latest snapshot successfully")
                true
            } catch (t: Throwable) {
                com.attract.attendance.util.AppLog.e("AttractDatabase", "Failed to restore database snapshot", t)
                false
            }
        }

        /**
         * List all available pre-migration database snapshots.
         */
        fun getAvailableSnapshots(context: Context): List<File> {
            val backupDir = File(context.filesDir, "backups")
            return backupDir.listFiles { _, name ->
                name.startsWith("attract-pre-v") && name.endsWith(".db")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        }

        /**
         * Run PRAGMA integrity_check on the local database (PR-01).
         */
        fun checkDatabaseIntegrity(context: Context): Boolean {
            return try {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists()) return true
                val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                val cursor = db.rawQuery("PRAGMA integrity_check", null)
                var ok = false
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0)
                    ok = result.equals("ok", ignoreCase = true)
                    com.attract.attendance.util.AppLog.i("AttractDatabase", "PRAGMA integrity_check completed: $ok")
                }
                cursor.close()
                db.close()
                ok
            } catch (t: Throwable) {
                com.attract.attendance.util.AppLog.e("AttractDatabase", "Integrity check failed with error", t)
                false
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
        )

        fun create(context: Context): AttractDatabase {
            val appContext = context.applicationContext
            // Pre-migration safety snapshot: copy attract.db to files/backups/ before Room applies migrations
            takePreMigrationSnapshotIfNeeded(appContext, CURRENT_VERSION)

            return Room.databaseBuilder(
                appContext,
                AttractDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(*ALL_MIGRATIONS)
                // PR-01: Remove destructive migration fallback. Missing migrations must fail tests/builds, never wipe user data.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS index_one_active_face_session " +
                                "ON attendance_sessions(status) WHERE status = 'ACTIVE' AND mode = 'FACE'",
                        )
                    }
                }).build()
        }
    }
}
