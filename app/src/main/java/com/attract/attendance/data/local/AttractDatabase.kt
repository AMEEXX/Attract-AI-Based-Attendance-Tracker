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
    version = 2,
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
                db.execSQL(
                    "ALTER TABLE face_templates ADD COLUMN embedding_dim INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun create(context: Context): AttractDatabase = Room.databaseBuilder(
            context.applicationContext,
            AttractDatabase::class.java,
            "attract.db",
        )
            .addMigrations(MIGRATION_1_2)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_one_active_face_session " +
                            "ON attendance_sessions(status) WHERE status = 'ACTIVE' AND mode = 'FACE'",
                    )
                }
            }).build()
    }
}
