package com.attract.attendance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TeacherEntity::class,
        ClassSectionEntity::class,
        StudentEntity::class,
        FaceTemplateEntity::class,
        AttendanceSessionEntity::class,
        AttendanceRecordEntity::class,
    ],
    version = 4,
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

        fun create(context: Context): AttractDatabase = Room.databaseBuilder(
            context.applicationContext,
            AttractDatabase::class.java,
            "attract.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigrationOnDowngrade()
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
