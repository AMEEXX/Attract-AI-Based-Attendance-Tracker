package com.attract.attendance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.attract.attendance.core.model.AttendanceMethod
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus

@Entity(tableName = "teachers")
data class TeacherEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "pin_hash") val pinHash: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "class_sections",
    foreignKeys = [ForeignKey(
        entity = TeacherEntity::class,
        parentColumns = ["id"],
        childColumns = ["teacher_id"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("teacher_id"), Index("archived")],
)
data class ClassSectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "teacher_id") val teacherId: Long,
    val name: String,
    val subject: String?,
    val section: String?,
    @ColumnInfo(name = "semester_batch") val semesterBatch: String?,
    @ColumnInfo(name = "required_attendance_percent") val requiredAttendancePercent: Int,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "students",
    foreignKeys = [ForeignKey(
        entity = ClassSectionEntity::class,
        parentColumns = ["id"],
        childColumns = ["class_id"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [
        Index(value = ["class_id", "roll_number"], unique = true),
        Index(value = ["class_id", "archived", "enrollment_status"]),
    ],
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "class_id") val classId: Long,
    val name: String,
    @ColumnInfo(name = "roll_number") val rollNumber: String,
    @ColumnInfo(name = "serial_number") val serialNumber: String?,
    @ColumnInfo(name = "enrollment_status") val enrollmentStatus: EnrollmentStatus = EnrollmentStatus.NOT_ENROLLED,
    @ColumnInfo(name = "enrolled_at") val enrolledAt: Long? = null,
    @ColumnInfo(name = "eligible_from_session_id") val eligibleFromSessionId: Long? = null,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "face_templates",
    foreignKeys = [ForeignKey(
        entity = StudentEntity::class,
        parentColumns = ["id"],
        childColumns = ["student_id"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index(value = ["student_id", "active"])],
)
data class FaceTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "encrypted_embedding") val encryptedEmbedding: ByteArray,
    @ColumnInfo(name = "crypto_version") val cryptoVersion: Int,
    @ColumnInfo(name = "model_version") val modelVersion: String,
    /**
     * Embedding dimensionality of this template. 0 = legacy row enrolled before dimension
     * tracking existed (its true dimension must be derived after decryption). Templates
     * whose dim != current model output are STALE and never reach TemplateMatcher.
     */
    @ColumnInfo(name = "embedding_dim", defaultValue = "0") val embeddingDim: Int = 0,
    @ColumnInfo(name = "quality_score") val qualityScore: Float,
    @ColumnInfo(name = "captured_at") val capturedAt: Long,
    val source: String,
    val active: Boolean = true,
)

@Entity(
    tableName = "attendance_sessions",
    foreignKeys = [ForeignKey(
        entity = ClassSectionEntity::class,
        parentColumns = ["id"],
        childColumns = ["class_id"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index(value = ["class_id", "session_date", "status"])],
)
data class AttendanceSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "class_id") val classId: Long,
    @ColumnInfo(name = "session_date") val sessionDate: String,
    @ColumnInfo(name = "time_zone_id") val timeZoneId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val status: SessionStatus,
    val mode: SessionMode,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = AttendanceSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["session_id", "student_id"], unique = true),
        Index(value = ["student_id", "session_id"]),
    ],
)
data class AttendanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "student_id") val studentId: Long,
    val status: AttendanceStatus,
    @ColumnInfo(name = "check_in_time") val checkInTime: Long?,
    @ColumnInfo(name = "attendance_method") val attendanceMethod: AttendanceMethod,
    @ColumnInfo(name = "match_confidence") val matchConfidence: Float? = null,
    @ColumnInfo(name = "recognition_metadata") val recognitionMetadata: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
