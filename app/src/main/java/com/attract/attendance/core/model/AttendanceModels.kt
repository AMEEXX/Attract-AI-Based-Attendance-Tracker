package com.attract.attendance.core.model

import java.math.BigDecimal
import java.math.RoundingMode

enum class EnrollmentStatus { NOT_ENROLLED, ENROLLED, REENROLL_REQUIRED }
enum class AttendanceStatus { PRESENT, ABSENT }
enum class AttendanceSource { MANUAL, AI_RECOGNITION, ENROLLMENT, TEACHER_ASSISTED, CORRECTION, BULK_IMPORT, RESTORED, MIGRATED }
typealias AttendanceMethod = AttendanceSource
enum class SessionMode { FACE, MANUAL, ASSISTED }
enum class SessionStatus { ACTIVE, ENDED, ABORTED }

data class TeacherProfile(
    val id: Long,
    val displayName: String,
)

data class ClassSummary(
    val id: Long,
    val name: String,
    val subject: String?,
    val section: String?,
    val semesterBatch: String?,
    val requiredAttendancePercent: Int,
    val totalPlannedSessions: Int = 30,
    val activeStudentCount: Int,
    val endedSessionCount: Int,
)

data class StudentSummary(
    val id: Long,
    val classId: Long,
    val name: String,
    val rollNumber: String,
    val serialNumber: String?,
    val enrollmentStatus: EnrollmentStatus,
)

data class RosterStudent(
    val sourceRow: Int,
    val name: String,
    val rollNumber: String,
    val serialNumber: String?,
)

data class SessionSummary(
    val id: Long,
    val classId: Long,
    val sessionDate: String,
    val status: SessionStatus,
    val mode: SessionMode,
    val presentCount: Int,
    val absentCount: Int,
    val startedAt: Long = 0L,
    val endedAt: Long? = null,
) {
    val isResumable: Boolean
        get() {
            if (mode != SessionMode.FACE) return false
            val now = System.currentTimeMillis()
            val referenceTime = endedAt ?: startedAt
            if (referenceTime <= 0L) return false
            return (now - referenceTime) <= 24 * 60 * 60 * 1000L
        }
}

data class ActiveSessionSummary(
    val id: Long,
    val classId: Long,
    val sessionDate: String,
    val mode: SessionMode,
    val presentCount: Int,
)

data class AttendanceStudent(
    val student: StudentSummary,
    val status: AttendanceStatus?,
)

data class AttendancePercentage(
    val presentSessions: Int,
    val eligibleSessions: Int,
) {
    val display: String
        get() = if (eligibleSessions == 0) {
            "No classes held"
        } else {
            BigDecimal(presentSessions)
                .multiply(BigDecimal(100))
                .divide(BigDecimal(eligibleSessions), 1, RoundingMode.HALF_UP)
                .toPlainString() + "%"
        }
}

sealed interface AppError {
    data class Validation(val field: String, val message: String) : AppError
    data object DuplicateRollNumber : AppError
    data object AnotherSessionActive : AppError
    data object ActiveSessionExists : AppError
    data object SessionNotActive : AppError
    data object NotEligible : AppError
    data object WrongClass : AppError
    data object AlreadyPresent : AppError
    data object AlreadyFinalized : AppError
    data object NotFound : AppError
    data class Storage(val cause: Throwable? = null) : AppError
}

sealed interface CommandResult<out T> {
    data class Success<T>(val value: T) : CommandResult<T>
    data class Failure(val error: AppError) : CommandResult<Nothing>
}
