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

data class CreateClassCommand(
    val name: String,
    val subject: String = "",
    val section: String = "",
    val semesterBatch: String = "",
    val requiredAttendancePercent: Int = 75,
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
        /** Student was already PRESENT in this session — no duplicate created. */
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
     * Persists an AI-recognition attendance mark IMMEDIATELY (LLD-06: Recognizing →
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
        rows.map(::toStudentSummary)
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
                val eligibilityBoundary = sessions.latestIdForClass(command.classId)?.plus(1)
                val id = students.insert(
                    StudentEntity(
                        classId = command.classId,
                        name = name,
                        rollNumber = roll,
                        serialNumber = command.serialNumber.cleanOptional(),
                        eligibleFromSessionId = eligibilityBoundary,
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
            val eligibilityBoundary = sessions.latestIdForClass(classId)?.plus(1)
            roster.indices.forEach { index ->
                val item = roster[index]
                val (name, roll) = prepared[index]
                students.insert(
                    StudentEntity(
                        classId = classId,
                        name = name,
                        rollNumber = roll,
                        serialNumber = item.serialNumber?.cleanOptional(),
                        eligibleFromSessionId = eligibilityBoundary,
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
            // source (AI_RECOGNITION / MANUAL) — never duplicated, never overwritten.
            val existingRows = records.forSession(sessionId)
            val existingByStudent = existingRows.associateBy { it.studentId }
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
     * NEVER returned â€” they can never reach TemplateMatcher.cosineSimilarity.
     */
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
     * Safe to call repeatedly (idempotent once swept). Never deletes raw rows â€” history
     * is retained with active=0 per LLD-10 retention rules.
     */
    suspend fun retireIncompatibleTemplates(classId: Long): StaleTemplateReport {
        return try {
            database.withTransaction {
                val rows = templates.activeForClass(classId)
                var deactivated = 0
                val possiblyOrphaned = mutableSetOf<Long>()
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
                        possiblyOrphaned.add(row.studentId)
                    }
                }
                // A student needs re-enrollment only if NO active+compatible template remains.
                val needingReEnrollment = possiblyOrphaned.filter { sid ->
                    templates.forStudent(sid).isEmpty()
                }
                // Flip their enrollment status so the existing enrollment sheet offers them.
                for (sid in needingReEnrollment) {
                    students.find(sid)?.let { s ->
                        if (s.enrollmentStatus == EnrollmentStatus.ENROLLED) {
                            students.update(
                                s.copy(
                                    enrollmentStatus = EnrollmentStatus.NOT_ENROLLED,
                                    updatedAt = nowMillis(),
                                )
                            )
                        }
                    }
                }
                if (deactivated > 0) {
                    android.util.Log.w(
                        "ATTRACT_FACE",
                        "Retired $deactivated incompatible face templates; ${needingReEnrollment.size} student(s) need re-enrollment.",
                    )
                }
                StaleTemplateReport(deactivated, needingReEnrollment)
            }
        } catch (error: Throwable) {
            android.util.Log.e("ATTRACT_FACE", "retireIncompatibleTemplates failed", error)
            StaleTemplateReport(0, emptyList())
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
            // Per LLD-10 re-enrollment: replace old templates â€” deactivate stale rows so
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
     * if none exists. The database row is the single source of truth — no in-memory
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
        rows.map { SessionSummary(it.id, it.classId, it.sessionDate, it.status, it.mode, it.presentCount, it.absentCount) }
    }

    fun observeSessionDaysForMonth(classId: Long, yearMonthPrefix: String): Flow<Set<Int>> =
        sessions.observeSessionDaysForMonth(classId, yearMonthPrefix).map { it.toSet() }

    fun observeSessionsForDate(classId: Long, dateString: String): Flow<List<SessionSummary>> =
        sessions.observeSessionsForDate(classId, dateString).map { rows ->
            rows.map { SessionSummary(it.id, it.classId, it.sessionDate, it.status, it.mode, it.presentCount, it.absentCount) }
        }

    suspend fun classReportRows(classId: Long): List<ClassReportStudentRow> = records.classReport(classId)

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

    fun observeSessionStudents(classId: Long, sessionId: Long): Flow<List<Pair<StudentSummary, AttendanceStatus?>>> =
        records.observeStudentsForSession(classId, sessionId).map { rows -> rows.map(::toStudentAttendance) }

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

