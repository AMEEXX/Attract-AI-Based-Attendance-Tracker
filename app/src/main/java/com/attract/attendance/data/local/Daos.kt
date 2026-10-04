package com.attract.attendance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import kotlinx.coroutines.flow.Flow

data class DashboardClassRow(
    val id: Long,
    val name: String,
    val subject: String?,
    val section: String?,
    val semesterBatch: String?,
    val requiredAttendancePercent: Int,
    val activeStudentCount: Int,
    val endedSessionCount: Int,
)

data class SessionRow(
    val id: Long,
    val classId: Long,
    val sessionDate: String,
    val status: SessionStatus,
    val mode: SessionMode,
    val presentCount: Int,
    val absentCount: Int,
)

data class ActiveSessionRow(
    val id: Long,
    val classId: Long,
    val sessionDate: String,
    val mode: SessionMode,
    val presentCount: Int,
)

data class SessionStudentRow(
    val id: Long,
    val classId: Long,
    val name: String,
    val rollNumber: String,
    val serialNumber: String?,
    val enrollmentStatus: EnrollmentStatus,
    val attendanceStatus: AttendanceStatus?,
)

data class ClassReportStudentRow(
    val id: Long,
    val name: String,
    val rollNumber: String,
    val serialNumber: String?,
    val enrollmentStatus: EnrollmentStatus,
    val presentSessions: Int,
    val eligibleSessions: Int,
)

data class SessionExportRow(
    val sessionId: Long,
    val sessionDate: String,
    val mode: SessionMode,
    val studentName: String,
    val rollNumber: String,
    val serialNumber: String?,
    val status: AttendanceStatus,
    val attendanceMethod: com.attract.attendance.core.model.AttendanceMethod,
    val checkInTime: Long?,
)

@Dao
interface TeacherDao {
    @Query("SELECT * FROM teachers ORDER BY id LIMIT 1")
    fun observeFirst(): Flow<TeacherEntity?>

    @Query("SELECT * FROM teachers ORDER BY id LIMIT 1")
    suspend fun first(): TeacherEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(teacher: TeacherEntity): Long

    @Query("SELECT * FROM teachers ORDER BY id")
    suspend fun all(): List<TeacherEntity>
}

@Dao
interface ClassDao {
    @Query(
        """
        SELECT c.id, c.name, c.subject, c.section, c.semester_batch AS semesterBatch,
               c.required_attendance_percent AS requiredAttendancePercent,
               COUNT(DISTINCT CASE WHEN s.archived = 0 THEN s.id END) AS activeStudentCount,
               COUNT(DISTINCT CASE WHEN ses.status = 'ENDED' THEN ses.id END) AS endedSessionCount
        FROM class_sections c
        LEFT JOIN students s ON s.class_id = c.id
        LEFT JOIN attendance_sessions ses ON ses.class_id = c.id
        WHERE c.archived = 0
        GROUP BY c.id
        ORDER BY c.updated_at DESC, c.id DESC
        """,
    )
    fun observeActiveSummaries(): Flow<List<DashboardClassRow>>

    @Query("SELECT * FROM class_sections WHERE id = :classId LIMIT 1")
    suspend fun find(classId: Long): ClassSectionEntity?

    @Query(
        """
        SELECT c.id, c.name, c.subject, c.section, c.semester_batch AS semesterBatch,
               c.required_attendance_percent AS requiredAttendancePercent,
               COUNT(DISTINCT CASE WHEN s.archived = 0 THEN s.id END) AS activeStudentCount,
               COUNT(DISTINCT CASE WHEN ses.status = 'ENDED' THEN ses.id END) AS endedSessionCount
        FROM class_sections c
        LEFT JOIN students s ON s.class_id = c.id
        LEFT JOIN attendance_sessions ses ON ses.class_id = c.id
        WHERE c.id = :classId AND c.archived = 0
        GROUP BY c.id
        """,
    )
    suspend fun findSummary(classId: Long): DashboardClassRow?

    @Query("SELECT * FROM class_sections ORDER BY id")
    suspend fun all(): List<ClassSectionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: ClassSectionEntity): Long

    @Query("UPDATE class_sections SET archived = :archived, updated_at = :updatedAt WHERE id = :classId")
    suspend fun setArchived(classId: Long, archived: Boolean, updatedAt: Long): Int
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE class_id = :classId AND archived = 0 ORDER BY roll_number COLLATE NOCASE")
    fun observeActiveForClass(classId: Long): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE class_id = :classId AND archived = 0 ORDER BY roll_number COLLATE NOCASE")
    suspend fun activeForClass(classId: Long): List<StudentEntity>

    @Query("SELECT * FROM students WHERE id = :studentId LIMIT 1")
    suspend fun find(studentId: Long): StudentEntity?

    @Query("SELECT * FROM students ORDER BY class_id, roll_number COLLATE NOCASE")
    suspend fun all(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE class_id = :classId AND roll_number = :rollNumber AND archived = 0 LIMIT 1")
    suspend fun findActiveByRoll(classId: Long, rollNumber: String): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: StudentEntity): Long

    @Query("UPDATE students SET archived = :archived, updated_at = :updatedAt WHERE id = :studentId")
    suspend fun setArchived(studentId: Long, archived: Boolean, updatedAt: Long): Int

    @androidx.room.Update
    suspend fun update(student: StudentEntity)

    @Query("UPDATE students SET enrollment_status = 'NOT_ENROLLED', enrolled_at = NULL WHERE class_id = :classId")
    suspend fun resetEnrollmentForClass(classId: Long): Int

    @Query("UPDATE students SET enrollment_status = 'NOT_ENROLLED', enrolled_at = NULL")
    suspend fun resetAllEnrollments(): Int
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM attendance_sessions WHERE status = 'ACTIVE' AND mode = 'FACE' LIMIT 1")
    suspend fun activeFaceSession(): AttendanceSessionEntity?

    @Query("SELECT * FROM attendance_sessions WHERE class_id = :classId AND status = 'ACTIVE' LIMIT 1")
    suspend fun activeForClass(classId: Long): AttendanceSessionEntity?

    @Query("SELECT * FROM attendance_sessions WHERE class_id = :classId AND status = 'ACTIVE' LIMIT 1")
    fun observeActiveForClass(classId: Long): Flow<AttendanceSessionEntity?>

    @Query(
        """
        SELECT ses.id, ses.class_id AS classId, ses.session_date AS sessionDate, ses.mode,
               COALESCE(SUM(CASE WHEN r.status = 'PRESENT' THEN 1 ELSE 0 END), 0) AS presentCount
        FROM attendance_sessions ses
        LEFT JOIN attendance_records r ON r.session_id = ses.id
        WHERE ses.class_id = :classId AND ses.status = 'ACTIVE'
        GROUP BY ses.id
        LIMIT 1
        """,
    )
    fun observeActiveSummaryForClass(classId: Long): Flow<ActiveSessionRow?>

    @Query("SELECT * FROM attendance_sessions ORDER BY started_at DESC, id DESC")
    suspend fun all(): List<AttendanceSessionEntity>

    @Query("SELECT * FROM attendance_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun find(sessionId: Long): AttendanceSessionEntity?

    @Query("SELECT MAX(id) FROM attendance_sessions WHERE class_id = :classId")
    suspend fun latestIdForClass(classId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: AttendanceSessionEntity): Long

    @Query("UPDATE attendance_sessions SET status = :status, ended_at = :endedAt, updated_at = :updatedAt WHERE id = :sessionId AND status = 'ACTIVE'")
    suspend fun finish(sessionId: Long, status: SessionStatus, endedAt: Long, updatedAt: Long): Int

    @Query("DELETE FROM attendance_sessions WHERE class_id = :classId AND status = 'ACTIVE' AND mode = 'FACE'")
    suspend fun deleteActiveFaceSessionsForClass(classId: Long): Int

    @Query("DELETE FROM attendance_sessions WHERE status = 'ACTIVE' AND mode = 'FACE'")
    suspend fun deleteAllActiveFaceSessions(): Int

    @Query(
        """
        SELECT ses.id, ses.class_id AS classId, ses.session_date AS sessionDate, ses.status, ses.mode,
               COALESCE(SUM(CASE WHEN r.status = 'PRESENT' THEN 1 ELSE 0 END), 0) AS presentCount,
               COALESCE(SUM(CASE WHEN r.status = 'ABSENT' THEN 1 ELSE 0 END), 0) AS absentCount
        FROM attendance_sessions ses
        LEFT JOIN attendance_records r ON r.session_id = ses.id
        WHERE ses.class_id = :classId AND ses.status = 'ENDED'
        GROUP BY ses.id
        ORDER BY ses.session_date DESC, ses.started_at DESC, ses.id DESC
        """,
    )
    fun observeEndedForClass(classId: Long): Flow<List<SessionRow>>

    @Query("DELETE FROM attendance_sessions WHERE id = :sessionId AND status = 'ENDED'")
    suspend fun deleteEnded(sessionId: Long): Int

    @Query("SELECT DISTINCT CAST(SUBSTR(session_date, 9, 2) AS INTEGER) FROM attendance_sessions WHERE class_id = :classId AND status = 'ENDED' AND session_date LIKE :yearMonthPrefix || '%'")
    fun observeSessionDaysForMonth(classId: Long, yearMonthPrefix: String): Flow<List<Int>>

    @Query(
        """
        SELECT ses.id, ses.class_id AS classId, ses.session_date AS sessionDate, ses.status, ses.mode,
               COALESCE(SUM(CASE WHEN r.status = 'PRESENT' THEN 1 ELSE 0 END), 0) AS presentCount,
               COALESCE(SUM(CASE WHEN r.status = 'ABSENT' THEN 1 ELSE 0 END), 0) AS absentCount
        FROM attendance_sessions ses
        LEFT JOIN attendance_records r ON r.session_id = ses.id
        WHERE ses.class_id = :classId AND ses.session_date = :dateString AND ses.status = 'ENDED'
        GROUP BY ses.id
        ORDER BY ses.started_at ASC
        """,
    )
    fun observeSessionsForDate(classId: Long, dateString: String): Flow<List<SessionRow>>
}

@Dao
interface AttendanceRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: AttendanceRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(values: List<AttendanceRecordEntity>)

    @Query("SELECT * FROM attendance_records WHERE session_id = :sessionId")
    suspend fun forSession(sessionId: Long): List<AttendanceRecordEntity>

    @Query(
        """
        SELECT s.id, s.class_id AS classId, s.name, s.roll_number AS rollNumber,
               s.serial_number AS serialNumber, s.enrollment_status AS enrollmentStatus,
               r.status AS attendanceStatus
        FROM students s
        LEFT JOIN attendance_records r ON r.student_id = s.id AND r.session_id = :sessionId
        WHERE s.class_id = :classId
        ORDER BY s.roll_number COLLATE NOCASE
        """,
    )
    fun observeStudentsForSession(classId: Long, sessionId: Long): Flow<List<SessionStudentRow>>

    @Query("UPDATE attendance_records SET status = :status, check_in_time = :checkInTime, updated_at = :updatedAt WHERE session_id = :sessionId AND student_id = :studentId")
    suspend fun updateStatus(sessionId: Long, studentId: Long, status: AttendanceStatus, checkInTime: Long?, updatedAt: Long): Int

    @Query("DELETE FROM attendance_records WHERE session_id = :sessionId")
    suspend fun deleteForSession(sessionId: Long): Int

    @Query(
        """
        DELETE FROM attendance_records WHERE session_id IN (
            SELECT id FROM attendance_sessions WHERE class_id = :classId AND status = 'ACTIVE' AND mode = 'FACE'
        )
        """
    )
    suspend fun deleteActiveFaceSessionRecordsForClass(classId: Long): Int

    @Query(
        """
        DELETE FROM attendance_records WHERE session_id IN (
            SELECT id FROM attendance_sessions WHERE status = 'ACTIVE' AND mode = 'FACE'
        )
        """
    )
    suspend fun deleteAllActiveFaceSessionRecords(): Int

    @Query("SELECT COUNT(*) FROM attendance_records WHERE session_id = :sessionId AND status = 'PRESENT'")
    suspend fun presentCount(sessionId: Long): Int

    @Query("SELECT * FROM attendance_records ORDER BY session_id, student_id")
    suspend fun all(): List<AttendanceRecordEntity>

    @Query(
        """
        SELECT s.id, s.name, s.roll_number AS rollNumber, s.serial_number AS serialNumber,
               s.enrollment_status AS enrollmentStatus,
               COALESCE(SUM(CASE WHEN r.status = 'PRESENT' THEN 1 ELSE 0 END), 0) AS presentSessions,
               COUNT(r.id) AS eligibleSessions
        FROM students s
        LEFT JOIN attendance_records r ON r.student_id = s.id
        LEFT JOIN attendance_sessions ses ON ses.id = r.session_id AND ses.status = 'ENDED'
        WHERE s.class_id = :classId AND s.archived = 0
        GROUP BY s.id
        ORDER BY s.roll_number COLLATE NOCASE
        """,
    )
    suspend fun classReport(classId: Long): List<ClassReportStudentRow>

    @Query(
        """
        SELECT ses.id AS sessionId, ses.session_date AS sessionDate, ses.mode AS mode,
               s.name AS studentName, s.roll_number AS rollNumber, s.serial_number AS serialNumber,
               r.status AS status, r.attendance_method AS attendanceMethod, r.check_in_time AS checkInTime
        FROM attendance_records r
        INNER JOIN attendance_sessions ses ON ses.id = r.session_id
        INNER JOIN students s ON s.id = r.student_id
        WHERE ses.class_id = :classId AND ses.status = 'ENDED'
        ORDER BY ses.started_at DESC, s.roll_number COLLATE NOCASE
        """,
    )
    suspend fun sessionExportRows(classId: Long): List<SessionExportRow>
}

@Dao
interface FaceTemplateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: FaceTemplateEntity): Long

    @Query("SELECT * FROM face_templates WHERE student_id = :studentId AND active = 1 ORDER BY id DESC")
    suspend fun forStudent(studentId: Long): List<FaceTemplateEntity>

    @Query("SELECT * FROM face_templates WHERE student_id = :studentId AND active = 1 ORDER BY id DESC")
    fun observeForStudent(studentId: Long): Flow<List<FaceTemplateEntity>>

    @Query(
        """
        SELECT ft.* FROM face_templates ft
        INNER JOIN students s ON s.id = ft.student_id
        WHERE s.class_id = :classId AND s.archived = 0 AND ft.active = 1
        """
    )
    suspend fun activeForClass(classId: Long): List<FaceTemplateEntity>

    @Query("UPDATE face_templates SET active = 0 WHERE id = :templateId")
    suspend fun setInactive(templateId: Long): Int

    @Query("UPDATE face_templates SET active = 0 WHERE student_id = :studentId AND active = 1")
    suspend fun deactivateForStudent(studentId: Long): Int

    @Query("DELETE FROM face_templates WHERE student_id = :studentId")
    suspend fun deleteForStudent(studentId: Long): Int

    @Query("DELETE FROM face_templates WHERE student_id IN (SELECT id FROM students WHERE class_id = :classId)")
    suspend fun deleteForClass(classId: Long): Int

    @Query("DELETE FROM face_templates")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM face_templates ORDER BY id")
    suspend fun all(): List<FaceTemplateEntity>

    /** Deactivates templates whose stored dimension differs from the current model output. */
    @Query(
        """
        UPDATE face_templates SET active = 0
        WHERE active = 1 AND embedding_dim != 0 AND embedding_dim != :currentDim
        """
    )
    suspend fun deactivateStaleDimension(currentDim: Int): Int

    /** Student ids that still have active templates after a stale-dimension sweep. */
    @Query(
        """
        SELECT DISTINCT ft.student_id FROM face_templates ft
        INNER JOIN students s ON s.id = ft.student_id
        WHERE s.class_id = :classId AND ft.active = 1
        """
    )
    suspend fun studentIdsWithActiveTemplates(classId: Long): List<Long>

    /** Student ids in a class that are marked ENROLLED but have zero usable (current-dim) templates. */
    @Query(
        """
        SELECT s.id FROM students s
        WHERE s.class_id = :classId AND s.archived = 0
          AND s.enrollment_status = 'ENROLLED'
          AND NOT EXISTS (
              SELECT 1 FROM face_templates ft
              WHERE ft.student_id = s.id AND ft.active = 1
                AND (ft.embedding_dim = :currentDim OR ft.embedding_dim = 0)
          )
        """
    )
    suspend fun enrolledStudentIdsNeedingReEnrollment(classId: Long, currentDim: Int): List<Long>
}

