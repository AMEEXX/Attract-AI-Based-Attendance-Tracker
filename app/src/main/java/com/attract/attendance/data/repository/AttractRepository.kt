package com.attract.attendance.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.attract.attendance.core.model.AppError
import com.attract.attendance.core.model.ActiveSessionSummary
import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.ClassSummary
import com.attract.attendance.core.model.CommandResult
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.core.model.SessionSummary
import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.core.model.TeacherProfile
import com.attract.attendance.core.model.RollNumberComparator
import com.attract.attendance.core.model.RosterStudent
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.AttractDatabase
import com.attract.attendance.data.local.ClassSectionEntity
import com.attract.attendance.data.local.ClassReportStudentRow
import com.attract.attendance.data.local.SessionExportRow
import com.attract.attendance.data.local.SessionStudentRow
import com.attract.attendance.data.local.StudentEntity
import com.attract.attendance.data.local.TeacherEntity
import com.attract.attendance.data.local.FaceTemplateEntity
import com.attract.attendance.data.importexport.BackupSnapshot
import com.attract.attendance.data.security.PinHasher
import com.attract.attendance.domain.AttendanceRules
import com.attract.attendance.domain.ValidationException
import com.attract.attendance.domain.Validators
import java.time.Clock
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CreateClassCommand(
    val name: String,
    val subject: String = "",
    val section: String = "",
    val semesterBatch: String = "",
    val requiredAttendancePercent: Int = 75,
    val totalPlannedSessions: Int = 30,
)

data class CreateStudentCommand(
    val classId: Long,
    val name: String,
    val rollNumber: String,
    val serialNumber: String = "",
)

/**
 * The one persistence boundary for the MVP. UI code never writes Room entities directly.
 * Every command rechecks the relevant database state inside its transaction.
 */
class AttractRepository(
    private val database: AttractDatabase,
    private val pinHasher: PinHasher,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val embeddingCipher: com.attract.attendance.lockdown.domain.EmbeddingCipher? = null,
) {
    private val teachers = database.teacherDao()
    private val classes = database.classDao()
    private val students = database.studentDao()
    private val sessions = database.sessionDao()
    private val records = database.attendanceRecordDao()
    private val templates = database.faceTemplateDao()

    private companion object {
        const val MODEL_VERSION = "v1"
    }

    // ------------------------------------------------------------------
    // Fallback attendance (LLD-06 amendment, 2026-08): teacher-selected manual
    // marking after a biometric UNKNOWN. Separate concern from the biometric
    // pipeline; reuses the production RecordPresentCommand so eligibility,
    // already-present and single-record guarantees are IDENTICAL to recognition.
    // ------------------------------------------------------------------
    sealed interface FallbackMarkResult {
        /** Exactly one PRESENT record now exists for (session, student). */
        data class Marked(val recordId: Long, val studentName: String) : FallbackMarkResult
        /** Student was already PRESENT in this session â€” no duplicate created. */
        data class AlreadyPresent(val studentName: String) : FallbackMarkResult
        data object NoActiveSession : FallbackMarkResult
        data object StudentNotFound : FallbackMarkResult
        data object StudentNotEligible : FallbackMarkResult
        data class Failed(val cause: String) : FallbackMarkResult
    }

    /**
     * Marks [studentId] PRESENT in the ACTIVE face session of [classId] with source
     * MANUAL (teacher fallback after an unrecognized face).
     *
     * Eligibility rules enforced by the production command:
     *  - student exists, not archived, belongs to the session's class
     *  - respects eligibleFromSessionId gating
     *  - session is ACTIVE
     *  - idempotent: already-PRESENT returns AlreadyPresent without a duplicate.
     */
    /**
     * Persists an AI-recognition attendance mark IMMEDIATELY (LLD-06: Recognizing â†’
     * PersistingPresent). Uses the SAME production command and uniqueness guarantees as
     * the manual fallback so both paths converge on one consistent Room state and the
     * end-of-session reconciliation can never collide or duplicate.
     */
    suspend fun markRecognizedPresent(
        classId: Long,
        studentId: Long,
        confidence: Float?,
    ): FallbackMarkResult {
        android.util.Log.i(
            "ATTRACT_ATTENDANCE_FALLBACK",
            "markRecognizedPresent: classId=$classId studentId=$studentId confidence=$confidence",
        )
        return try {
            database.withTransaction {
                val session = sessions.activeForClass(classId)
                if (session == null || session.status != SessionStatus.ACTIVE || session.mode != SessionMode.FACE) {
                    return@withTransaction FallbackMarkResult.NoActiveSession
                }
                val student = students.find(studentId)
                    ?: return@withTransaction FallbackMarkResult.StudentNotFound
                val command = com.attract.attendance.domain.RecordPresentCommand(
                    sessionDao = sessions,
                    studentDao = students,
                    attendanceRecordDao = records,
                )
                when (val result = command.execute(
                    sessionId = session.id,
                    studentId = studentId,
                    method = AttendanceSource.AI_RECOGNITION,
                    confidence = confidence,
                    metadata = "adaptive_recognition",
                )) {
                    is com.attract.attendance.domain.RecordPresentResult.Success -> {
                        android.util.Log.i(
                            "ATTRACT_ATTENDANCE_FALLBACK",
                            "AI mark PRESENT: sessionId=${session.id} studentId=$studentId recordId=${result.recordId}",
                        )
                        FallbackMarkResult.Marked(result.recordId, student.name)
                    }
                    com.attract.attendance.domain.RecordPresentResult.AlreadyPresent ->
                        FallbackMarkResult.AlreadyPresent(student.name)
                    com.attract.attendance.domain.RecordPresentResult.StudentNotEligible ->
                        FallbackMarkResult.StudentNotEligible
                    com.attract.attendance.domain.RecordPresentResult.SessionNotActive ->
                        FallbackMarkResult.NoActiveSession
                    com.attract.attendance.domain.RecordPresentResult.StudentNotFound ->
                        FallbackMarkResult.StudentNotFound
                }
            }
        } catch (error: Throwable) {
            android.util.Log.e("ATTRACT_ATTENDANCE_FALLBACK", "markRecognizedPresent FAILED", error)
            FallbackMarkResult.Failed("${error::class.simpleName}: ${error.message}")
        }
    }
    suspend fun markTeacherAssistedPresent(
        classId: Long,
        studentId: Long,
        grant: com.attract.attendance.domain.session.TeacherAuthorizationGrant,
    ): FallbackMarkResult {
        android.util.Log.i(
            "ATTRACT_ATTENDANCE_FALLBACK",
            "markTeacherAssistedPresent: classId=$classId studentId=$studentId grantToken=${grant.token}",
        )
        return try {
            database.withTransaction {
                val session = sessions.activeForClass(classId)
                if (session == null || session.status != SessionStatus.ACTIVE || session.mode != SessionMode.FACE) {
                    android.util.Log.w("ATTRACT_ATTENDANCE_FALLBACK", "No active FACE session for classId=$classId")
                    return@withTransaction FallbackMarkResult.NoActiveSession
                }
                val now = nowMillis()
                if (!grant.isValid(
                        currentSessionId = session.id,
                        currentClassId = classId,
                        targetStudentId = studentId,
                        expectedAction = com.attract.attendance.domain.session.TeacherAuthAction.TEACHER_ASSISTED_CHECKIN,
                        currentInteractionId = grant.interactionId,
                        currentTimeMillis = now,
                    )
                ) {
                    android.util.Log.e("ATTRACT_ATTENDANCE_FALLBACK", "markTeacherAssistedPresent: REJECTED invalid grant: $grant")
                    return@withTransaction FallbackMarkResult.Failed("Unauthorized: Teacher authorization grant is invalid or expired.")
                }
                val student = students.find(studentId)
                if (student == null) {
                    android.util.Log.w("ATTRACT_ATTENDANCE_FALLBACK", "Student $studentId not found")
                    return@withTransaction FallbackMarkResult.StudentNotFound
                }
                val command = com.attract.attendance.domain.RecordPresentCommand(
                    sessionDao = sessions,
                    studentDao = students,
                    attendanceRecordDao = records,
                )
                when (val result = command.execute(
                    sessionId = session.id,
                    studentId = studentId,
                    method = AttendanceSource.TEACHER_ASSISTED,
                    confidence = null,
                    metadata = "teacher_assisted_grant_${grant.token}",
                )) {
                    is com.attract.attendance.domain.RecordPresentResult.Success -> {
                        android.util.Log.i(
                            "ATTRACT_ATTENDANCE_FALLBACK",
                            "Marked PRESENT: sessionId=${session.id} studentId=$studentId " +
                                "name=${student.name} recordId=${result.recordId} source=TEACHER_ASSISTED",
                        )
                        FallbackMarkResult.Marked(result.recordId, student.name)
                    }
                    com.attract.attendance.domain.RecordPresentResult.AlreadyPresent -> {
                        android.util.Log.i(
                            "ATTRACT_ATTENDANCE_FALLBACK",
                            "Already PRESENT: sessionId=${session.id} studentId=$studentId name=${student.name}",
                        )
                        FallbackMarkResult.AlreadyPresent(student.name)
                    }
                    com.attract.attendance.domain.RecordPresentResult.StudentNotEligible -> {
                        android.util.Log.w(
                            "ATTRACT_ATTENDANCE_FALLBACK",
                            "Student $studentId (${student.name}) not eligible for session ${session.id}",
                        )
                        FallbackMarkResult.StudentNotEligible
                    }
                    com.attract.attendance.domain.RecordPresentResult.SessionNotActive ->
                        FallbackMarkResult.NoActiveSession
                    com.attract.attendance.domain.RecordPresentResult.StudentNotFound ->
                        FallbackMarkResult.StudentNotFound
                }
            }
        } catch (error: Throwable) {
            android.util.Log.e("ATTRACT_ATTENDANCE_FALLBACK", "markTeacherAssistedPresent FAILED", error)
            FallbackMarkResult.Failed("${error::class.simpleName}: ${error.message}")
        }
    }

    suspend fun markFallbackPresent(classId: Long, studentId: Long): FallbackMarkResult {
        android.util.Log.i(
            "ATTRACT_ATTENDANCE_FALLBACK",
            "markFallbackPresent: classId=$classId studentId=$studentId",
        )
        return try {
            database.withTransaction {
                val session = sessions.activeForClass(classId)
                if (session == null || session.status != SessionStatus.ACTIVE || session.mode != SessionMode.FACE) {
                    android.util.Log.w("ATTRACT_ATTENDANCE_FALLBACK", "No active FACE session for classId=$classId")
                    return@withTransaction FallbackMarkResult.NoActiveSession
                }
                val student = students.find(studentId)
                if (student == null) {
                    android.util.Log.w("ATTRACT_ATTENDANCE_FALLBACK", "Student $studentId not found")
                    return@withTransaction FallbackMarkResult.StudentNotFound
                }
                val command = com.attract.attendance.domain.RecordPresentCommand(
                    sessionDao = sessions,
                    studentDao = students,
                    attendanceRecordDao = records,
                )
                when (val result = command.execute(
                    sessionId = session.id,
                    studentId = studentId,
                    method = AttendanceSource.MANUAL,
                    confidence = null,
                    metadata = "teacher_fallback_after_unknown",
                )) {
                    is com.attract.attendance.domain.RecordPresentResult.Success -> {
                        android.util.Log.i(
                            "ATTRACT_ATTENDANCE_FALLBACK",
                            "Marked PRESENT: sessionId=${session.id} studentId=$studentId " +
                                "name=${student.name} recordId=${result.recordId} source=MANUAL",
                        )
                        FallbackMarkResult.Marked(result.recordId, student.name)
                    }
                    com.attract.attendance.domain.RecordPresentResult.AlreadyPresent -> {
                        android.util.Log.i(
                            "ATTRACT_ATTENDANCE_FALLBACK",
                            "Already PRESENT: sessionId=${session.id} studentId=$studentId name=${student.name}",
                        )
                        FallbackMarkResult.AlreadyPresent(student.name)
                    }
                    com.attract.attendance.domain.RecordPresentResult.StudentNotEligible -> {
                        android.util.Log.w(
                            "ATTRACT_ATTENDANCE_FALLBACK",
                            "Student $studentId (${student.name}) not eligible for session ${session.id}",
                        )
                        FallbackMarkResult.StudentNotEligible
                    }
                    com.attract.attendance.domain.RecordPresentResult.SessionNotActive ->
                        FallbackMarkResult.NoActiveSession
                    com.attract.attendance.domain.RecordPresentResult.StudentNotFound ->
                        FallbackMarkResult.StudentNotFound
                }
            }
        } catch (error: Throwable) {
            android.util.Log.e("ATTRACT_ATTENDANCE_FALLBACK", "markFallbackPresent FAILED", error)
            FallbackMarkResult.Failed("${error::class.simpleName}: ${error.message}")
        }
    }

    fun observeTeacher(): Flow<TeacherProfile?> = teachers.observeFirst().map { entity ->
        entity?.let { TeacherProfile(it.id, it.displayName) }
    }

    suspend fun registerTeacher(displayName: String, pin: CharArray): CommandResult<TeacherProfile> {
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) return CommandResult.Failure(AppError.Validation("displayName", "Your name is required."))
        if (!pin.concatToString().matches(Regex("\\d{4,12}"))) {
            return CommandResult.Failure(AppError.Validation("pin", "Choose a 4 to 12 digit PIN."))
        }
        return try {
            database.withTransaction {
                val existing = teachers.first()
                if (existing != null) return@withTransaction CommandResult.Failure(AppError.Validation("setup", "This device is already set up."))
                val now = nowMillis()
                val id = teachers.insert(TeacherEntity(displayName = cleanName, pinHash = pinHasher.hash(pin), createdAt = now, updatedAt = now))
                CommandResult.Success(TeacherProfile(id, cleanName))
            }
        } catch (error: Throwable) {
            CommandResult.Failure(AppError.Storage(error))
        } finally {
            pin.fill('\u0000')
        }
    }

    suspend fun authenticate(pin: CharArray): Boolean = try {
        val teacher = teachers.first() ?: return false
        pinHasher.verify(pin, teacher.pinHash)
    } finally {
        pin.fill('\u0000')
    }

    fun observeClasses(): Flow<List<ClassSummary>> = classes.observeActiveSummaries().map { rows ->
        rows.map {
            ClassSummary(
                id = it.id,
                name = it.name,
                subject = it.subject,
                section = it.section,
                semesterBatch = it.semesterBatch,
                requiredAttendancePercent = it.requiredAttendancePercent,
                totalPlannedSessions = it.totalPlannedSessions,
                activeStudentCount = it.activeStudentCount,
                endedSessionCount = it.endedSessionCount,
            )
        }
    }

    suspend fun createClass(command: CreateClassCommand): CommandResult<Long> {
        val name = command.name.validatedWith(Validators::className) ?: return validationFailure(command.name, Validators::className)
        val percentage = command.requiredAttendancePercent.validatedWith(Validators::percentage)
            ?: return CommandResult.Failure(AppError.Validation("requiredAttendancePercent", "Required attendance must be between 0 and 100."))
        return try {
            database.withTransaction {
                val teacher = teachers.first() ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
                val now = nowMillis()
                val id = classes.insert(
                    ClassSectionEntity(
                        teacherId = teacher.id,
                        name = name,
                        subject = command.subject.cleanOptional(),
                        section = command.section.cleanOptional(),
                        semesterBatch = command.semesterBatch.cleanOptional(),
                        requiredAttendancePercent = percentage,
                        totalPlannedSessions = command.totalPlannedSessions,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                CommandResult.Success(id)
            }
        } catch (error: Throwable) {
            CommandResult.Failure(AppError.Storage(error))
        }
    }

    suspend fun archiveClass(classId: Long, archived: Boolean): CommandResult<Unit> = try {
        database.withTransaction {
            if (classes.find(classId) == null) return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (sessions.activeForClass(classId) != null) return@withTransaction CommandResult.Failure(AppError.ActiveSessionExists)
            classes.setArchived(classId, archived, nowMillis())
            CommandResult.Success(Unit)
        }
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    fun observeStudents(classId: Long): Flow<List<StudentSummary>> = students.observeActiveForClass(classId).map { rows ->
        rows.map(::toStudentSummary).sortedWith { a, b -> RollNumberComparator.compare(a.rollNumber, b.rollNumber) }
    }

    suspend fun getClass(classId: Long): ClassSectionEntity? = classes.find(classId)

    suspend fun getClassSummary(classId: Long): ClassSummary? {
        val row = classes.findSummary(classId) ?: return null
        return ClassSummary(
            id = row.id,
            name = row.name,
            subject = row.subject,
            section = row.section,
            semesterBatch = row.semesterBatch,
            requiredAttendancePercent = row.requiredAttendancePercent,
            totalPlannedSessions = row.totalPlannedSessions,
            activeStudentCount = row.activeStudentCount,
            endedSessionCount = row.endedSessionCount,
        )
    }

    suspend fun discardSession(classId: Long): CommandResult<Unit> = try {
        database.withTransaction {
            val existing = sessions.activeForClass(classId) ?: sessions.activeFaceSession()
            if (existing != null) {
                sessions.finish(existing.id, SessionStatus.ABORTED, nowMillis(), nowMillis())
            }
            CommandResult.Success(Unit)
        }
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    fun observeActiveSession(classId: Long): Flow<ActiveSessionSummary?> = sessions.observeActiveSummaryForClass(classId).map { row ->
        row?.let { ActiveSessionSummary(it.id, it.classId, it.sessionDate, it.mode, it.presentCount) }
    }

    suspend fun addStudent(command: CreateStudentCommand): CommandResult<Long> {
        val name = command.name.validatedWith(Validators::studentName) ?: return validationFailure(command.name, Validators::studentName)
        val roll = command.rollNumber.validatedWith(Validators::rollNumber) ?: return validationFailure(command.rollNumber, Validators::rollNumber)
        return try {
            database.withTransaction {
                val classSection = classes.find(command.classId)
                    ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
                if (classSection.archived) return@withTransaction CommandResult.Failure(AppError.NotFound)
                if (sessions.activeForClass(command.classId) != null) return@withTransaction CommandResult.Failure(AppError.ActiveSessionExists)
                val now = nowMillis()
                val id = students.insert(
                    StudentEntity(
                        classId = command.classId,
                        name = name,
                        rollNumber = roll,
                        serialNumber = command.serialNumber.cleanOptional(),
                        eligibleFromSessionId = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                CommandResult.Success(id)
            }
        } catch (error: SQLiteConstraintException) {
            CommandResult.Failure(AppError.DuplicateRollNumber)
        } catch (error: Throwable) {
            CommandResult.Failure(AppError.Storage(error))
        }
    }

    suspend fun archiveStudent(studentId: Long, archived: Boolean): CommandResult<Unit> = try {
        database.withTransaction {
            val student = students.find(studentId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (sessions.activeForClass(student.classId) != null) return@withTransaction CommandResult.Failure(AppError.ActiveSessionExists)
            students.setArchived(studentId, archived, nowMillis())
            CommandResult.Success(Unit)
        }
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    suspend fun importRoster(classId: Long, roster: List<RosterStudent>): CommandResult<Int> = try {
        database.withTransaction {
            if (roster.isEmpty()) return@withTransaction CommandResult.Failure(AppError.Validation("file", "The roster has no students."))
            val classSection = classes.find(classId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (classSection.archived) return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (sessions.activeForClass(classId) != null) return@withTransaction CommandResult.Failure(AppError.ActiveSessionExists)
            val prepared = roster.map { item ->
                val name = item.name.validatedWith(Validators::studentName)
                    ?: return@withTransaction validationFailure(item.name, Validators::studentName)
                val roll = item.rollNumber.validatedWith(Validators::rollNumber)
                    ?: return@withTransaction validationFailure(item.rollNumber, Validators::rollNumber)
                name to roll
            }
            if (prepared.map { it.second }.toSet().size != prepared.size) {
                return@withTransaction CommandResult.Failure(AppError.DuplicateRollNumber)
            }
            val now = nowMillis()
            roster.indices.forEach { index ->
                val item = roster[index]
                val (name, roll) = prepared[index]
                students.insert(
                    StudentEntity(
                        classId = classId,
                        name = name,
                        rollNumber = roll,
                        serialNumber = item.serialNumber?.cleanOptional(),
                        eligibleFromSessionId = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            CommandResult.Success(roster.size)
        }
    } catch (error: SQLiteConstraintException) {
        CommandResult.Failure(AppError.DuplicateRollNumber)
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    suspend fun saveManualAttendance(classId: Long, presentStudentIds: Set<Long>, targetDate: String? = null): CommandResult<Long> = try {
        database.withTransaction {
            if (sessions.activeForClass(classId) != null || sessions.activeFaceSession() != null) {
                return@withTransaction CommandResult.Failure(AppError.AnotherSessionActive)
            }
            val classSection = classes.find(classId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (classSection.archived) return@withTransaction CommandResult.Failure(AppError.NotFound)
            val now = nowMillis()
            val zonedNow = ZonedDateTime.now(clock)
            val sessionId = sessions.insert(
                AttendanceSessionEntity(
                    classId = classId,
                    sessionDate = targetDate ?: zonedNow.toLocalDate().toString(),
                    timeZoneId = zonedNow.zone.id,
                    startedAt = now,
                    status = SessionStatus.ACTIVE,
                    mode = SessionMode.MANUAL,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val eligible = students.activeForClass(classId).filter { AttendanceRules.isEligible(it.eligibleFromSessionId, sessionId) }
            if (!eligible.map { it.id }.containsAll(presentStudentIds)) {
                return@withTransaction CommandResult.Failure(AppError.WrongClass)
            }
            records.insertAll(
                eligible.map { student ->
                    val present = student.id in presentStudentIds
                    AttendanceRecordEntity(
                        sessionId = sessionId,
                        studentId = student.id,
                        status = if (present) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT,
                        checkInTime = if (present) now else null,
                        attendanceMethod = AttendanceSource.MANUAL,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
            sessions.finish(sessionId, SessionStatus.ENDED, now, now)
            CommandResult.Success(sessionId)
        }
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    suspend fun saveFaceAttendance(classId: Long, presentStudentIds: Set<Long>, targetDate: String? = null): CommandResult<Long> = try {
        database.withTransaction {
            val existing = sessions.activeForClass(classId)
            if (existing != null && (existing.status != SessionStatus.ACTIVE || existing.mode != SessionMode.FACE)) {
                return@withTransaction CommandResult.Failure(AppError.AnotherSessionActive)
            }
            val classSection = classes.find(classId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (classSection.archived) return@withTransaction CommandResult.Failure(AppError.NotFound)
            val now = nowMillis()
            val zonedNow = ZonedDateTime.now(clock)
            val sessionId = existing?.id ?: sessions.insert(
                AttendanceSessionEntity(
                    classId = classId,
                    sessionDate = targetDate ?: zonedNow.toLocalDate().toString(),
                    timeZoneId = zonedNow.zone.id,
                    startedAt = now,
                    status = SessionStatus.ACTIVE,
                    mode = SessionMode.FACE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val eligible = students.activeForClass(classId).filter { AttendanceRules.isEligible(it.eligibleFromSessionId, sessionId) }
            if (!eligible.map { it.id }.containsAll(presentStudentIds)) {
                return@withTransaction CommandResult.Failure(AppError.WrongClass)
            }
            // RECONCILING end-save (LLD-06): recognition/fallback marks are persisted
            // IMMEDIATELY during the session; this final pass only fills in the students
            // that have NO record yet (mostly ABSENT). Existing rows keep their original
            // source (AI_RECOGNITION / MANUAL) â€” never duplicated, never overwritten.
            val existingRows = records.forSession(sessionId)
            val existingByStudent = existingRows.associateBy { it.studentId }
            // Reconcile students who were marked absent in an auto-saved/interrupted session but are now marked present
            for (studentId in presentStudentIds) {
                val existing = existingByStudent[studentId]
                if (existing != null && existing.status != AttendanceStatus.PRESENT) {
                    records.updateStatus(sessionId, studentId, AttendanceStatus.PRESENT, now, now)
                }
            }
            val missing = eligible.filter { it.id !in existingByStudent }
            records.insertAll(
                missing.map { student ->
                    val present = student.id in presentStudentIds
                    AttendanceRecordEntity(
                        sessionId = sessionId,
                        studentId = student.id,
                        status = if (present) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT,
                        checkInTime = if (present) now else null,
                        attendanceMethod = if (present) AttendanceSource.AI_RECOGNITION else AttendanceSource.MANUAL,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
            sessions.finish(sessionId, SessionStatus.ENDED, now, now)
            CommandResult.Success(sessionId)
        }
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    suspend fun activeFaceSession(): AttendanceSessionEntity? = sessions.activeFaceSession()

    /**
     * Loads recognition candidates for a class, DECODE-CHECKED for biometric format
     * compatibility (LLD-10 migration amendment).
     *
     * Templates whose decrypted embedding does not match the current model output
     * (e.g. 32-D vectors from an earlier prototype model on an upgraded phone) are
     * NEVER returned Ã¢â‚¬â€ they can never reach TemplateMatcher.cosineSimilarity.
     */
    /**
     * Loads the template gallery for a class and returns a typed [com.attract.attendance.domain.face.GalleryLoadResult] (WP03 / R11).
     *
     * Ensures storage errors, Keystore invalidation, and template corruption are never
     * misclassified as an empty gallery or an ordinary Unknown face.
     */
    suspend fun loadGallery(classId: Long): com.attract.attendance.domain.face.GalleryLoadResult {
        return try {
            val classStudents = students.activeForClass(classId)
            val enrolledStudents = classStudents.filter { it.enrollmentStatus == EnrollmentStatus.ENROLLED }
            val rows = templates.activeForClass(classId)

            if (enrolledStudents.isEmpty() && rows.isEmpty()) {
                return com.attract.attendance.domain.face.GalleryLoadResult.EmptyHealthy
            }

            val usable = mutableListOf<com.attract.attendance.domain.face.StudentTemplatePair>()
            var staleCount = 0
            var malformedCount = 0
            var cryptoErrorCount = 0
            val affectedStudentIds = mutableSetOf<Long>()

            for (row in rows) {
                val floats = try {
                    com.attract.attendance.data.security.TemplateEnvelopeCodec.decode(
                        cipher = embeddingCipher,
                        studentId = row.studentId,
                        modelVersion = row.modelVersion,
                        stored = row.encryptedEmbedding,
                        cryptoVersion = row.cryptoVersion,
                    )
                } catch (e: Exception) {
                    cryptoErrorCount++
                    null
                }

                if (floats == null) {
                    malformedCount++
                    affectedStudentIds.add(row.studentId)
                    continue
                }

                when (com.attract.attendance.domain.face.TemplateCompatibility.classify(floats)) {
                    com.attract.attendance.domain.face.TemplateCompatibility.VectorClass.CURRENT -> {
                        usable.add(
                            com.attract.attendance.domain.face.StudentTemplatePair(
                                studentId = row.studentId,
                                templateId = row.id,
                                embedding = floats
                            )
                        )
                    }
                    com.attract.attendance.domain.face.TemplateCompatibility.VectorClass.STALE_DIMENSION -> {
                        staleCount++
                        affectedStudentIds.add(row.studentId)
                    }
                    else -> {
                        malformedCount++
                        affectedStudentIds.add(row.studentId)
                    }
                }
            }

            // Check if crypto is completely unavailable or key invalidated
            if (rows.isNotEmpty() && cryptoErrorCount == rows.size) {
                return com.attract.attendance.domain.face.GalleryLoadResult.Unavailable(
                    errorCategory = "CRYPTO_KEYSTORE_UNAVAILABLE",
                    message = "Biometric encryption key unavailable or invalidated. Teacher re-authentication required."
                )
            }

            val studentsWithUsable = usable.map { it.studentId }.toSet()
            val orphaned = enrolledStudents.filter { it.id !in studentsWithUsable }.map { it.id }
            affectedStudentIds.addAll(orphaned)

            val healthSummary = com.attract.attendance.domain.face.GalleryHealthSummary(
                totalEnrolledStudents = enrolledStudents.size,
                studentsWithTemplates = studentsWithUsable.size,
                activeTemplatesCount = rows.size,
                staleTemplatesCount = staleCount,
                malformedTemplatesCount = malformedCount
            )

            if (affectedStudentIds.isNotEmpty()) {
                return com.attract.attendance.domain.face.GalleryLoadResult.NeedsRepair(
                    affectedStudentIds = affectedStudentIds.toList(),
                    reason = "Gallery has $staleCount stale and $malformedCount malformed templates; ${orphaned.size} enrolled students lack usable templates."
                )
            }

            com.attract.attendance.domain.face.GalleryLoadResult.Ready(
                profileId = com.attract.attendance.domain.face.BiometricModelProfile.CURRENT.profileId,
                version = nowMillis(),
                templates = usable,
                healthSummary = healthSummary
            )
        } catch (e: Exception) {
            android.util.Log.e("ATTRACT_FACE", "loadGallery failed", e)
            com.attract.attendance.domain.face.GalleryLoadResult.Unavailable(
                errorCategory = "GALLERY_STORAGE_ERROR",
                message = e.message ?: "Database error loading gallery",
                cause = e
            )
        }
    }

    suspend fun getActiveTemplatesForClass(classId: Long): List<com.attract.attendance.domain.face.StudentTemplatePair> {
        val rows = templates.activeForClass(classId)
        val usable = mutableListOf<com.attract.attendance.domain.face.StudentTemplatePair>()
        var incompatible = 0
        for (row in rows) {
            // Fail-closed decode: a tampered/undecryptable template is skipped, never
            // fed as garbage into similarity matching (LLD-13).
            val floats = com.attract.attendance.data.security.TemplateEnvelopeCodec.decode(
                cipher = embeddingCipher,
                studentId = row.studentId,
                modelVersion = row.modelVersion,
                stored = row.encryptedEmbedding,
                cryptoVersion = row.cryptoVersion,
            ) ?: continue
            // Biometric-format gate (LLD-10): wrong dimension or non-finite values never
            // participate in matching.
            if (!com.attract.attendance.domain.face.TemplateCompatibility.isUsableVector(floats)) {
                incompatible++
                continue
            }
            usable.add(
                com.attract.attendance.domain.face.StudentTemplatePair(
                    studentId = row.studentId,
                    templateId = row.id,
                    embedding = floats
                )
            )
        }
        if (incompatible > 0) {
            android.util.Log.w(
                "ATTRACT_FACE",
                "Template compatibility: $incompatible/${rows.size} templates ignored " +
                    "(stale dimension/malformed). Re-enrollment required for affected students.",
            )
        }
        return usable
    }

    data class StaleTemplateReport(
        val deactivatedTemplates: Int,
        val studentsNeedingReEnrollment: List<Long>,
    )

    /**
     * Deactivates every active template whose decrypted format is incompatible with the
     * current model profile and reports which enrolled students lost ALL usable templates
     * (they must re-enroll before they can be recognized again).
     *
     * Safe to call repeatedly (idempotent once swept). Never deletes raw rows Ã¢â‚¬â€ history
     * is retained with active=0 per LLD-10 retention rules.
     */
    suspend fun retireIncompatibleTemplates(classId: Long): StaleTemplateReport {
        return database.withTransaction {
            val rows = templates.activeForClass(classId)
            var deactivated = 0
            for (row in rows) {
                val floats = com.attract.attendance.data.security.TemplateEnvelopeCodec.decode(
                    cipher = embeddingCipher,
                    studentId = row.studentId,
                    modelVersion = row.modelVersion,
                    stored = row.encryptedEmbedding,
                    cryptoVersion = row.cryptoVersion,
                )
                val compatible = floats != null &&
                    com.attract.attendance.domain.face.TemplateCompatibility.isUsableVector(floats)
                if (!compatible) {
                    templates.setInactive(row.id)
                    deactivated++
                }
            }

            // ALL-ROSTER RECONCILIATION: Check EVERY enrolled student in this class,
            // not merely those touched during this sweep (fixes R04 orphaned students).
            val classStudents = students.activeForClass(classId)
            val enrolledStudents = classStudents.filter { it.enrollmentStatus == EnrollmentStatus.ENROLLED }
            val needingReEnrollment = mutableListOf<Long>()

            for (s in enrolledStudents) {
                val activeStudentTemplates = templates.forStudent(s.id).filter { it.active }
                val hasUsable = activeStudentTemplates.any { t ->
                    if (t.embeddingDim != 0 && t.embeddingDim != com.attract.attendance.domain.face.TemplateCompatibility.CURRENT_EMBEDDING_DIM) {
                        false
                    } else {
                        val decoded = com.attract.attendance.data.security.TemplateEnvelopeCodec.decode(
                            cipher = embeddingCipher,
                            studentId = s.id,
                            modelVersion = t.modelVersion,
                            stored = t.encryptedEmbedding,
                            cryptoVersion = t.cryptoVersion,
                        )
                        decoded != null && com.attract.attendance.domain.face.TemplateCompatibility.isUsableVector(decoded)
                    }
                }

                if (!hasUsable) {
                    needingReEnrollment.add(s.id)
                    students.update(
                        s.copy(
                            enrollmentStatus = EnrollmentStatus.REENROLL_REQUIRED,
                            updatedAt = nowMillis(),
                        )
                    )
                }
            }

            if (deactivated > 0 || needingReEnrollment.isNotEmpty()) {
                android.util.Log.w(
                    "ATTRACT_FACE",
                    "Retired $deactivated face templates; ${needingReEnrollment.size} student(s) marked REENROLL_REQUIRED.",
                )
            }
            StaleTemplateReport(deactivated, needingReEnrollment)
        }
    }

    suspend fun attendanceRecordsForSession(sessionId: Long): List<AttendanceRecordEntity> =
        records.forSession(sessionId)

    fun observeSessionStudentRows(classId: Long, sessionId: Long): Flow<List<SessionStudentRow>> =
        records.observeStudentsForSession(classId, sessionId)

    /**
     * Resets biometric data (WP-A).
     * Deletes face templates, resets students to NOT_ENROLLED with enrolledAt = null,
     * and removes any ACTIVE face sessions and their in-flight records.
     * Past attendance history for ENDED sessions is preserved.
     */
    suspend fun resetBiometricData(classId: Long? = null): CommandResult<Unit> = try {
        database.withTransaction {
            if (classId != null) {
                records.deleteActiveFaceSessionRecordsForClass(classId)
                sessions.deleteActiveFaceSessionsForClass(classId)
                templates.deleteForClass(classId)
                students.resetEnrollmentForClass(classId)
            } else {
                records.deleteAllActiveFaceSessionRecords()
                sessions.deleteAllActiveFaceSessions()
                templates.deleteAll()
                students.resetAllEnrollments()
            }
        }
        CommandResult.Success(Unit)
    } catch (e: Exception) {
        android.util.Log.e("ATTRACT_FACE", "resetBiometricData failed", e)
        CommandResult.Failure(AppError.Storage(e))
    }

    private val classEnrollmentLocks = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()
    private fun getEnrollmentLock(classId: Long): Mutex =
        classEnrollmentLocks.computeIfAbsent(classId) { Mutex() }

    /**
     * Self-service student enrollment and optional attendance check-in (D-007, WP-D).
     *
     * 1. Validates the batch.
     * 2. Checks duplicates against active class gallery (skipping corrupt templates, not failing on NeedsRepair).
     * 3. Executes within a database transaction and per-class Mutex:
     *    - Verifies student is NOT_ENROLLED, matches class, not archived.
     *    - If [sessionId] is provided, verifies session is ACTIVE and FACE mode.
     *    - Encrypts and persists 3 templates.
     *    - Updates student status to ENROLLED.
     *    - If [sessionId] is provided, inserts or updates attendance record to PRESENT with source ENROLLMENT.
     */
    suspend fun selfEnrollAndCheckIn(
        sessionId: Long?,
        classId: Long,
        studentId: Long,
        batch: com.attract.attendance.domain.face.ValidatedEnrollmentBatch,
    ): com.attract.attendance.domain.face.EnrollmentResult {
        val lock = getEnrollmentLock(classId)
        return lock.withLock {
            val candidateEmbeddings = batch.samples.map { it.embedding }
            val galleryResult = loadGallery(classId)
            val activeTemplates = when (galleryResult) {
                is com.attract.attendance.domain.face.GalleryLoadResult.Ready -> galleryResult.templates
                is com.attract.attendance.domain.face.GalleryLoadResult.EmptyHealthy -> emptyList()
                is com.attract.attendance.domain.face.GalleryLoadResult.NeedsRepair -> emptyList()
                is com.attract.attendance.domain.face.GalleryLoadResult.Unavailable -> emptyList()
            }

            val dupResult = com.attract.attendance.domain.face.DuplicateCheckService.checkDuplicate(
                candidateSamples = candidateEmbeddings,
                galleryTemplates = activeTemplates,
                excludeStudentId = studentId,
            )
            if (dupResult is com.attract.attendance.domain.face.DuplicateResult.Suspicious) {
                return@withLock com.attract.attendance.domain.face.EnrollmentResult.DuplicateSuspected(
                    dupResult.existingStudentId,
                    dupResult.score,
                )
            }

            try {
                database.withTransaction {
                    val student = students.find(studentId)
                        ?: return@withTransaction com.attract.attendance.domain.face.EnrollmentResult.Failed("Student not found")
                    if (student.classId != classId) {
                        return@withTransaction com.attract.attendance.domain.face.EnrollmentResult.Ineligible("Student does not belong to this class")
                    }
                    if (student.archived) {
                        return@withTransaction com.attract.attendance.domain.face.EnrollmentResult.Ineligible("Student is archived")
                    }
                    if (student.enrollmentStatus == EnrollmentStatus.ENROLLED) {
                        return@withTransaction com.attract.attendance.domain.face.EnrollmentResult.AlreadyEnrolled(studentId)
                    }

                    if (sessionId != null) {
                        val session = sessions.find(sessionId)
                            ?: return@withTransaction com.attract.attendance.domain.face.EnrollmentResult.Failed("Session not found")
                        if (session.status != SessionStatus.ACTIVE || session.mode != SessionMode.FACE) {
                            return@withTransaction com.attract.attendance.domain.face.EnrollmentResult.Failed("Session is not active in FACE mode")
                        }
                    }

                    val now = nowMillis()
                    val templateIds = mutableListOf<Long>()

                    batch.samples.forEach { sample ->
                        val (blob, cryptoVersion) = com.attract.attendance.data.security.TemplateEnvelopeCodec.encode(
                            cipher = embeddingCipher,
                            studentId = studentId,
                            modelVersion = batch.profileId,
                            plaintextFloats = sample.embedding,
                        )
                        val id = templates.insert(
                            FaceTemplateEntity(
                                studentId = studentId,
                                encryptedEmbedding = blob,
                                cryptoVersion = cryptoVersion,
                                modelVersion = batch.profileId,
                                embeddingDim = sample.embedding.size,
                                qualityScore = sample.qualityScore,
                                capturedAt = now,
                                source = "self_enrollment",
                                active = true,
                            )
                        )
                        templateIds.add(id)
                    }

                    students.update(
                        student.copy(
                            enrollmentStatus = EnrollmentStatus.ENROLLED,
                            enrolledAt = now,
                            updatedAt = now,
                        )
                    )

                    var attendanceRecordId: Long? = null
                    if (sessionId != null) {
                        val existingRecords = records.forSession(sessionId)
                        val existingRecord = existingRecords.find { it.studentId == studentId }
                        attendanceRecordId = if (existingRecord != null) {
                            if (existingRecord.status != AttendanceStatus.PRESENT) {
                                records.updateStatus(sessionId, studentId, AttendanceStatus.PRESENT, now, now)
                            }
                            existingRecord.id
                        } else {
                            records.insert(
                                AttendanceRecordEntity(
                                    sessionId = sessionId,
                                    studentId = studentId,
                                    status = AttendanceStatus.PRESENT,
                                    checkInTime = now,
                                    attendanceMethod = AttendanceSource.ENROLLMENT,
                                    createdAt = now,
                                    updatedAt = now,
                                )
                            )
                        }
                    }

                    com.attract.attendance.domain.face.EnrollmentResult.Committed(
                        studentId = studentId,
                        templateIds = templateIds,
                        attendanceRecordId = attendanceRecordId,
                        galleryVersion = now,
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ATTRACT_FACE", "selfEnrollAndCheckIn failed", e)
                com.attract.attendance.domain.face.EnrollmentResult.Failed(e.message ?: "Database transaction error")
            }
        }
    }

    suspend fun selfEnroll(
        classId: Long,
        studentId: Long,
        batch: com.attract.attendance.domain.face.ValidatedEnrollmentBatch,
    ): com.attract.attendance.domain.face.EnrollmentResult =
        selfEnrollAndCheckIn(sessionId = null, classId = classId, studentId = studentId, batch = batch)

    /** Legacy delegation for backward compatibility */
    suspend fun firstEnrollAndCheckIn(
        sessionContext: com.attract.attendance.domain.session.SessionContext,
        studentId: Long,
        batch: com.attract.attendance.domain.face.ValidatedEnrollmentBatch,
        authorizationGrant: com.attract.attendance.domain.session.TeacherAuthorizationGrant? = null,
    ): com.attract.attendance.domain.face.EnrollmentResult =
        selfEnrollAndCheckIn(
            sessionId = sessionContext.sessionId,
            classId = sessionContext.classId,
            studentId = studentId,
            batch = batch,
        )

    /**
     * Standalone enrollment: persists templates without creating an attendance session or record (D-007, WP-D).
     */
    suspend fun standaloneEnrollStudentFace(
        classId: Long,
        studentId: Long,
        batch: com.attract.attendance.domain.face.ValidatedEnrollmentBatch,
        authorizationGrant: com.attract.attendance.domain.session.TeacherAuthorizationGrant? = null,
    ): com.attract.attendance.domain.face.EnrollmentResult =
        selfEnroll(classId, studentId, batch)

    /**
     * Re-enrollment / profile repair: replaces old templates atomically after teacher authorization (D-001, R18).
     */
    suspend fun reEnrollStudentFace(
        classId: Long,
        studentId: Long,
        batch: com.attract.attendance.domain.face.ValidatedEnrollmentBatch,
        authorizationGrant: com.attract.attendance.domain.session.TeacherAuthorizationGrant?
    ): com.attract.attendance.domain.face.EnrollmentResult {
        val grant = authorizationGrant
            ?: return com.attract.attendance.domain.face.EnrollmentResult.ApprovalExpired("Teacher authorization required for profile repair")
        if (!grant.isValid(
                currentClassId = classId,
                targetStudentId = studentId,
                expectedAction = com.attract.attendance.domain.session.TeacherAuthAction.RE_ENROLLMENT,
                currentSessionId = grant.sessionId
            )
        ) {
            return com.attract.attendance.domain.face.EnrollmentResult.ApprovalExpired("Teacher authorization grant is invalid or expired")
        }

        val candidateEmbeddings = batch.samples.map { it.embedding }
        val galleryResult = loadGallery(classId)
        val activeTemplates = when (galleryResult) {
            is com.attract.attendance.domain.face.GalleryLoadResult.Ready -> galleryResult.templates
            is com.attract.attendance.domain.face.GalleryLoadResult.EmptyHealthy -> emptyList()
            is com.attract.attendance.domain.face.GalleryLoadResult.NeedsRepair -> emptyList()
            is com.attract.attendance.domain.face.GalleryLoadResult.Unavailable -> {
                return com.attract.attendance.domain.face.EnrollmentResult.Failed("Duplicate check unavailable: ${galleryResult.message}")
            }
        }

        val dupResult = com.attract.attendance.domain.face.DuplicateCheckService.checkDuplicate(
            candidateSamples = candidateEmbeddings,
            galleryTemplates = activeTemplates,
            excludeStudentId = studentId
        )
        if (dupResult is com.attract.attendance.domain.face.DuplicateResult.Suspicious) {
            return com.attract.attendance.domain.face.EnrollmentResult.DuplicateSuspected(dupResult.existingStudentId, dupResult.score)
        }

        return try {
            database.withTransaction {
                val student = students.find(studentId)
                    ?: return@withTransaction com.attract.attendance.domain.face.EnrollmentResult.Failed("Student not found")
                val now = nowMillis()
                templates.deactivateForStudent(studentId)
                val templateIds = mutableListOf<Long>()

                batch.samples.forEach { sample ->
                    val (blob, cryptoVersion) = com.attract.attendance.data.security.TemplateEnvelopeCodec.encode(
                        cipher = embeddingCipher,
                        studentId = studentId,
                        modelVersion = batch.profileId,
                        plaintextFloats = sample.embedding,
                    )
                    val id = templates.insert(
                        FaceTemplateEntity(
                            studentId = studentId,
                            encryptedEmbedding = blob,
                            cryptoVersion = cryptoVersion,
                            modelVersion = batch.profileId,
                            embeddingDim = sample.embedding.size,
                            qualityScore = sample.qualityScore,
                            capturedAt = now,
                            source = "re_enrollment",
                            active = true
                        )
                    )
                    templateIds.add(id)
                }

                students.update(
                    student.copy(
                        enrollmentStatus = EnrollmentStatus.ENROLLED,
                        enrolledAt = now,
                        updatedAt = now
                    )
                )

                com.attract.attendance.domain.face.EnrollmentResult.Committed(
                    studentId = studentId,
                    templateIds = templateIds,
                    attendanceRecordId = null,
                    galleryVersion = now
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ATTRACT_FACE", "reEnrollStudentFace failed", e)
            com.attract.attendance.domain.face.EnrollmentResult.Failed(e.message ?: "Database transaction error")
        }
    }

    suspend fun enrollStudentFace(
        studentId: Long,
        embeddings: List<ByteArray>,
        qualityScores: List<Float> = emptyList(),
    ): CommandResult<Unit> {
        // Biometric-format gate (LLD-10): enrollment ALWAYS persists the current model
        // format. Wrong-dimension/malformed observations are rejected before any write —
        // never stored just because the user reached frame 3.
        val floatsList = embeddings.map { bytes ->
            with(com.attract.attendance.domain.face.TemplateMatcher) { bytes.toFloatArray() }
        }
        when (val check = com.attract.attendance.domain.face.TemplateCompatibility.validateEnrollment(floatsList)) {
            is com.attract.attendance.domain.face.TemplateCompatibility.EnrollmentValidation.Rejected -> {
                android.util.Log.e("ATTRACT_FACE", "Enrollment rejected: ${check.reason}")
                return CommandResult.Failure(AppError.Validation("embedding", check.reason))
            }
            is com.attract.attendance.domain.face.TemplateCompatibility.EnrollmentValidation.Ok -> Unit
        }
        return try {
            database.withTransaction {
            val student = students.find(studentId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            val now = nowMillis()
            // Per LLD-10 re-enrollment: replace old templates Ã¢â‚¬â€ deactivate stale rows so
            // outdated embeddings never participate in future matches. This also replaces
            // stale-dimension (e.g. 32-D legacy) templates with current 192-D format.
            templates.deactivateForStudent(studentId)
            floatsList.forEachIndexed { index, floats ->
                val (blob, cryptoVersion) = com.attract.attendance.data.security.TemplateEnvelopeCodec.encode(
                    cipher = embeddingCipher,
                    studentId = studentId,
                    modelVersion = com.attract.attendance.domain.face.TemplateCompatibility.CURRENT_MODEL_ID,
                    plaintextFloats = floats,
                )
                templates.insert(
                    FaceTemplateEntity(
                        studentId = studentId,
                        encryptedEmbedding = blob,
                        cryptoVersion = cryptoVersion,
                        modelVersion = com.attract.attendance.domain.face.TemplateCompatibility.CURRENT_MODEL_ID,
                        embeddingDim = floats.size,
                        qualityScore = qualityScores.getOrNull(index) ?: 1.0f,
                        capturedAt = now,
                        source = "inline_enrollment",
                        active = true
                    )
                )
            }
            students.update(
                student.copy(
                    enrollmentStatus = EnrollmentStatus.ENROLLED,
                    enrolledAt = now,
                    updatedAt = now
                )
            )
            android.util.Log.i(
                "ATTRACT_FACE",
                "Enrollment OK: studentId=$studentId name=${student.name} templates=${floatsList.size} " +
                    "dim=${floatsList.first().size} model=${com.attract.attendance.domain.face.TemplateCompatibility.CURRENT_MODEL_ID}",
            )

            // If an active session exists for this student's class, log attendance under ENROLLMENT
            val activeSession = sessions.activeForClass(student.classId)
            if (activeSession != null && activeSession.status == SessionStatus.ACTIVE) {
                records.insert(
                    AttendanceRecordEntity(
                        sessionId = activeSession.id,
                        studentId = studentId,
                        status = AttendanceStatus.PRESENT,
                        checkInTime = now,
                        attendanceMethod = AttendanceSource.ENROLLMENT,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            CommandResult.Success(Unit)
        }
        } catch (error: Throwable) {
            CommandResult.Failure(AppError.Storage(error))
        }
    }

    /**
     * THE canonical session entry point for the attendance screen (LLD-06 amendment
     * 2026-08-26): resolves the CURRENT ACTIVE FACE SESSION for [classId], creating it
     * if none exists. The database row is the single source of truth â€” no in-memory
     * `isSessionActive` flag anywhere.
     *
     * Returns Success(activeSessionId) when an ACTIVE FACE session for this class exists
     * (pre-existing or newly created). Failure(AnotherSessionActive) only when a DIFFERENT
     * class's face session is running (teacher must end it first).
     */
    suspend fun ensureFaceSession(classId: Long): CommandResult<Long> = try {
        database.withTransaction {
            val existing = sessions.activeForClass(classId)
            if (existing != null && existing.status == SessionStatus.ACTIVE && existing.mode == SessionMode.FACE) {
                android.util.Log.i("ATTRACT_ATTENDANCE_PIPELINE", "ensureFaceSession: reusing sessionId=${existing.id} classId=$classId")
                return@withTransaction CommandResult.Success(existing.id)
            }
            if (sessions.activeFaceSession() != null) {
                // Another class owns the global single-active-session slot.
                return@withTransaction CommandResult.Failure(AppError.AnotherSessionActive)
            }
            val classSection = classes.find(classId)
                ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (classSection.archived) return@withTransaction CommandResult.Failure(AppError.NotFound)
            val now = nowMillis()
            val zonedNow = ZonedDateTime.now(clock)
            val id = sessions.insert(
                AttendanceSessionEntity(
                    classId = classId,
                    sessionDate = zonedNow.toLocalDate().toString(),
                    timeZoneId = zonedNow.zone.id,
                    startedAt = now,
                    status = SessionStatus.ACTIVE,
                    mode = SessionMode.FACE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            android.util.Log.i("ATTRACT_ATTENDANCE_PIPELINE", "ensureFaceSession: CREATED sessionId=$id classId=$classId date=${zonedNow.toLocalDate()}")
            CommandResult.Success(id)
        }
    } catch (error: SQLiteConstraintException) {
        CommandResult.Failure(AppError.AnotherSessionActive)
    } catch (error: Throwable) {
        android.util.Log.e("ATTRACT_ATTENDANCE_PIPELINE", "ensureFaceSession FAILED", error)
        CommandResult.Failure(AppError.Storage(error))
    }

    suspend fun startFaceSession(classId: Long): CommandResult<Long> = try {
        database.withTransaction {
            if (sessions.activeForClass(classId) != null || sessions.activeFaceSession() != null) {
                return@withTransaction CommandResult.Failure(AppError.AnotherSessionActive)
            }
            val classSection = classes.find(classId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (classSection.archived) return@withTransaction CommandResult.Failure(AppError.NotFound)
            val now = nowMillis()
            val zonedNow = ZonedDateTime.now(clock)
            val id = sessions.insert(
                AttendanceSessionEntity(
                    classId = classId,
                    sessionDate = zonedNow.toLocalDate().toString(),
                    timeZoneId = zonedNow.zone.id,
                    startedAt = now,
                    status = SessionStatus.ACTIVE,
                    mode = SessionMode.FACE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            CommandResult.Success(id)
        }
    } catch (error: SQLiteConstraintException) {
        CommandResult.Failure(AppError.AnotherSessionActive)
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    suspend fun startAssistedSession(classId: Long): CommandResult<Long> = try {
        database.withTransaction {
            if (sessions.activeForClass(classId) != null || sessions.activeFaceSession() != null) {
                return@withTransaction CommandResult.Failure(AppError.AnotherSessionActive)
            }
            val classSection = classes.find(classId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (classSection.archived) return@withTransaction CommandResult.Failure(AppError.NotFound)
            val now = nowMillis()
            val zonedNow = ZonedDateTime.now(clock)
            val id = sessions.insert(
                AttendanceSessionEntity(
                    classId = classId,
                    sessionDate = zonedNow.toLocalDate().toString(),
                    timeZoneId = zonedNow.zone.id,
                    startedAt = now,
                    status = SessionStatus.ACTIVE,
                    mode = SessionMode.ASSISTED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            CommandResult.Success(id)
        }
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    suspend fun recordTeacherAssistedPresent(sessionId: Long, studentId: Long): CommandResult<Unit> = try {
        database.withTransaction {
            val session = sessions.find(sessionId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (session.status != SessionStatus.ACTIVE) return@withTransaction CommandResult.Failure(AppError.SessionNotActive)
            val student = students.find(studentId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (student.classId != session.classId) return@withTransaction CommandResult.Failure(AppError.WrongClass)
            if (student.archived || !AttendanceRules.isEligible(student.eligibleFromSessionId, session.id)) {
                return@withTransaction CommandResult.Failure(AppError.NotEligible)
            }
            val now = nowMillis()
            records.insert(
                AttendanceRecordEntity(
                    sessionId = sessionId,
                    studentId = studentId,
                    status = AttendanceStatus.PRESENT,
                    checkInTime = now,
                    attendanceMethod = AttendanceSource.TEACHER_ASSISTED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            CommandResult.Success(Unit)
        }
    } catch (error: SQLiteConstraintException) {
        CommandResult.Failure(AppError.AlreadyPresent)
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    suspend fun recordTeacherAssistedPresentByRoll(sessionId: Long, rawRollNumber: String): CommandResult<Unit> {
        val rollNumber = rawRollNumber.validatedWith(Validators::rollNumber)
            ?: return validationFailure(rawRollNumber, Validators::rollNumber)
        return try {
            database.withTransaction {
                val session = sessions.find(sessionId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
                val student = students.findActiveByRoll(session.classId, rollNumber)
                    ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
                if (session.status != SessionStatus.ACTIVE) return@withTransaction CommandResult.Failure(AppError.SessionNotActive)
                if (!AttendanceRules.isEligible(student.eligibleFromSessionId, session.id)) {
                    return@withTransaction CommandResult.Failure(AppError.NotEligible)
                }
                val now = nowMillis()
                records.insert(
                    AttendanceRecordEntity(
                        sessionId = session.id,
                        studentId = student.id,
                        status = AttendanceStatus.PRESENT,
                        checkInTime = now,
                        attendanceMethod = AttendanceSource.TEACHER_ASSISTED,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                CommandResult.Success(Unit)
            }
        } catch (error: SQLiteConstraintException) {
            CommandResult.Failure(AppError.AlreadyPresent)
        } catch (error: Throwable) {
            CommandResult.Failure(AppError.Storage(error))
        }
    }

    suspend fun finalizeFaceSession(sessionId: Long): CommandResult<Unit> = finalizeLiveSession(sessionId)

    suspend fun finalizeLiveSession(sessionId: Long): CommandResult<Unit> = try {
        database.withTransaction {
            val session = sessions.find(sessionId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (session.status == SessionStatus.ENDED) return@withTransaction CommandResult.Failure(AppError.AlreadyFinalized)
            if (session.status != SessionStatus.ACTIVE || session.mode !in setOf(SessionMode.FACE, SessionMode.ASSISTED)) {
                return@withTransaction CommandResult.Failure(AppError.SessionNotActive)
            }
            val now = nowMillis()
            val existingStudentIds = records.forSession(sessionId).mapTo(mutableSetOf()) { it.studentId }
            val missingRecords = students.activeForClass(session.classId)
                .asSequence()
                .filter { AttendanceRules.isEligible(it.eligibleFromSessionId, sessionId) }
                .filterNot { it.id in existingStudentIds }
                .map { student ->
                    AttendanceRecordEntity(
                        sessionId = sessionId,
                        studentId = student.id,
                        status = AttendanceStatus.ABSENT,
                        checkInTime = null,
                        attendanceMethod = if (session.mode == SessionMode.FACE) AttendanceSource.AI_RECOGNITION else AttendanceSource.TEACHER_ASSISTED,
                        createdAt = now,
                        updatedAt = now,
                    )
                }.toList()
            if (missingRecords.isNotEmpty()) records.insertAll(missingRecords)
            if (sessions.finish(sessionId, SessionStatus.ENDED, now, now) != 1) {
                return@withTransaction CommandResult.Failure(AppError.SessionNotActive)
            }
            CommandResult.Success(Unit)
        }
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    fun observeEndedSessions(classId: Long): Flow<List<SessionSummary>> = sessions.observeEndedForClass(classId).map { rows ->
        rows.map { SessionSummary(it.id, it.classId, it.sessionDate, it.status, it.mode, it.presentCount, it.absentCount, it.startedAt, it.endedAt) }
    }

    fun observeSessionDaysForMonth(classId: Long, yearMonthPrefix: String): Flow<Set<Int>> =
        sessions.observeSessionDaysForMonth(classId, yearMonthPrefix).map { it.toSet() }

    fun observeSessionsForDate(classId: Long, dateString: String): Flow<List<SessionSummary>> =
        sessions.observeSessionsForDate(classId, dateString).map { rows ->
            rows.map { SessionSummary(it.id, it.classId, it.sessionDate, it.status, it.mode, it.presentCount, it.absentCount, it.startedAt, it.endedAt) }
        }

    suspend fun resumeFaceSession(sessionId: Long): CommandResult<AttendanceSessionEntity> = try {
        database.withTransaction {
            val session = sessions.find(sessionId)
                ?: return@withTransaction CommandResult.Failure(AppError.NotFound)

            val otherActive = sessions.activeFaceSession()
            if (otherActive != null && otherActive.id != sessionId) {
                return@withTransaction CommandResult.Failure(AppError.AnotherSessionActive)
            }

            val now = nowMillis()
            val cutoff = now - (24 * 60 * 60 * 1000L)
            val sessionTime = session.endedAt ?: session.startedAt
            if (sessionTime > 0 && sessionTime < cutoff) {
                return@withTransaction CommandResult.Failure(AppError.Validation("session", "This session has expired (older than 24 hours)."))
            }

            if (session.status != SessionStatus.ACTIVE) {
                sessions.reactivate(sessionId, now)
            }
            CommandResult.Success(session.copy(status = SessionStatus.ACTIVE, endedAt = null, updatedAt = now))
        }
    } catch (e: Throwable) {
        CommandResult.Failure(AppError.Storage(e))
    }

    suspend fun finalizeStaleSessions() {
        val now = nowMillis()
        val cutoff = now - (24 * 60 * 60 * 1000L)
        val stale = sessions.findStaleActiveSessions(cutoff)
        for (session in stale) {
            runCatching {
                val eligible = students.activeForClass(session.classId)
                val existingRows = records.forSession(session.id)
                val existingIds = existingRows.map { it.studentId }.toSet()
                val missing = eligible.filter { it.id !in existingIds }
                if (missing.isNotEmpty()) {
                    records.insertAll(
                        missing.map { student ->
                            AttendanceRecordEntity(
                                sessionId = session.id,
                                studentId = student.id,
                                status = AttendanceStatus.ABSENT,
                                checkInTime = null,
                                attendanceMethod = AttendanceSource.MANUAL,
                                createdAt = now,
                                updatedAt = now,
                            )
                        }
                    )
                }
                sessions.finish(session.id, SessionStatus.ENDED, now, now)
            }
        }
    }

    suspend fun classReportRows(classId: Long): List<ClassReportStudentRow> =
        records.classReport(classId).sortedWith { a, b -> RollNumberComparator.compare(a.rollNumber, b.rollNumber) }

    suspend fun sessionExportRows(classId: Long): List<SessionExportRow> = records.sessionExportRows(classId)

    suspend fun backupSnapshot(): BackupSnapshot = database.withTransaction {
        BackupSnapshot(
            generatedAt = nowMillis(),
            teachers = teachers.all(),
            classes = classes.all(),
            students = students.all(),
            sessions = sessions.all(),
            records = records.all(),
        )
    }

    suspend fun restoreBackup(snapshot: BackupSnapshot): CommandResult<Unit> = try {
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            // Delete in reverse-dependency (leaf-to-root) order to prevent foreign key constraint violations
            db.execSQL("DELETE FROM attendance_records")
            db.execSQL("DELETE FROM face_templates")
            db.execSQL("DELETE FROM attendance_sessions")
            db.execSQL("DELETE FROM students")
            db.execSQL("DELETE FROM class_sections")
            db.execSQL("DELETE FROM teachers")

            // Insert in dependency (root-to-leaf) order
            if (snapshot.teachers.isNotEmpty()) teachers.insertAll(snapshot.teachers)
            if (snapshot.classes.isNotEmpty()) classes.insertAll(snapshot.classes)
            if (snapshot.students.isNotEmpty()) students.insertAll(snapshot.students)
            if (snapshot.sessions.isNotEmpty()) sessions.insertAll(snapshot.sessions)
            if (snapshot.records.isNotEmpty()) records.insertAll(snapshot.records)
            CommandResult.Success(Unit)
        }
    } catch (e: Throwable) {
        android.util.Log.e("AttractRepository", "Restore failed", e)
        CommandResult.Failure(AppError.Storage(e))
    }

    fun observeSessionStudents(classId: Long, sessionId: Long): Flow<List<Pair<StudentSummary, AttendanceStatus?>>> =
        records.observeStudentsForSession(classId, sessionId).map { rows ->
            rows.map(::toStudentAttendance).sortedWith { a, b -> RollNumberComparator.compare(a.first.rollNumber, b.first.rollNumber) }
        }

    fun observeStudentAttendanceStats(classId: Long, studentId: Long): Flow<Pair<Int, Int>> =
        kotlinx.coroutines.flow.combine(
            records.observePresentCountForStudent(studentId),
            sessions.observeEndedSessionCountForClass(classId)
        ) { present, total ->
            present to total
        }

    suspend fun correctAttendance(sessionId: Long, studentId: Long, newStatus: AttendanceStatus): CommandResult<Unit> = try {
        database.withTransaction {
            val session = sessions.find(sessionId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (session.status != SessionStatus.ENDED) return@withTransaction CommandResult.Failure(AppError.SessionNotActive)
            val updated = records.updateStatus(
                sessionId = sessionId,
                studentId = studentId,
                status = newStatus,
                checkInTime = if (newStatus == AttendanceStatus.PRESENT) nowMillis() else null,
                updatedAt = nowMillis(),
            )
            if (updated == 0) CommandResult.Failure(AppError.NotEligible) else CommandResult.Success(Unit)
        }
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    suspend fun deleteEndedSession(sessionId: Long): CommandResult<Unit> = try {
        database.withTransaction {
            val session = sessions.find(sessionId) ?: return@withTransaction CommandResult.Failure(AppError.NotFound)
            if (session.status != SessionStatus.ENDED) return@withTransaction CommandResult.Failure(AppError.SessionNotActive)
            records.deleteForSession(sessionId)
            sessions.deleteEnded(sessionId)
            CommandResult.Success(Unit)
        }
    } catch (error: Throwable) {
        CommandResult.Failure(AppError.Storage(error))
    }

    private fun toStudentSummary(entity: StudentEntity) = StudentSummary(
        id = entity.id,
        classId = entity.classId,
        name = entity.name,
        rollNumber = entity.rollNumber,
        serialNumber = entity.serialNumber,
        enrollmentStatus = entity.enrollmentStatus,
    )

    private fun toStudentAttendance(row: SessionStudentRow): Pair<StudentSummary, AttendanceStatus?> =
        StudentSummary(
            id = row.id,
            classId = row.classId,
            name = row.name,
            rollNumber = row.rollNumber,
            serialNumber = row.serialNumber,
            enrollmentStatus = row.enrollmentStatus,
        ) to row.attendanceStatus

    private fun nowMillis(): Long = clock.millis()

    private fun String.cleanOptional(): String? = trim().takeIf(String::isNotBlank)

    private fun String.validatedWith(validator: (String) -> Result<String>): String? = validator(this).getOrNull()

    private fun Int.validatedWith(validator: (Int) -> Result<Int>): Int? = validator(this).getOrNull()

    private fun validationFailure(raw: String, validator: (String) -> Result<String>): CommandResult.Failure {
        val error = validator(raw).exceptionOrNull() as? ValidationException
        return CommandResult.Failure(error?.error ?: AppError.Validation("input", "Enter a valid value."))
    }
}

