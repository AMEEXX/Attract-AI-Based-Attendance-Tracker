package com.attract.attendance.domain.session

import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.domain.face.QualityResult

sealed interface SessionState {
    val sessionId: Long
    val attemptId: Long

    data class Initializing(
        override val sessionId: Long,
        override val attemptId: Long,
    ) : SessionState

    data class Ready(
        override val sessionId: Long,
        override val attemptId: Long,
        val presentCount: Int,
    ) : SessionState

    data class Acquiring(
        override val sessionId: Long,
        override val attemptId: Long,
    ) : SessionState

    data class QualityChecking(
        override val sessionId: Long,
        override val attemptId: Long,
    ) : SessionState

    data class LivenessChecking(
        override val sessionId: Long,
        override val attemptId: Long,
        val qualityResult: QualityResult,
    ) : SessionState

    data class Recognizing(
        override val sessionId: Long,
        override val attemptId: Long,
        val qualityResult: QualityResult,
    ) : SessionState


    data class PersistingPresent(
        override val sessionId: Long,
        override val attemptId: Long,
        val student: StudentSummary,
        val matchConfidence: Float,
    ) : SessionState

    data class SuccessFeedback(
        override val sessionId: Long,
        override val attemptId: Long,
        val studentName: String,
        val rollNumber: String,
    ) : SessionState

    data class EnrollmentRequest(
        override val sessionId: Long,
        override val attemptId: Long,
        val reason: String,
    ) : SessionState

    data class TeacherAssistance(
        override val sessionId: Long,
        override val attemptId: Long,
        val reason: String,
    ) : SessionState

    data class Ending(
        override val sessionId: Long,
        override val attemptId: Long,
    ) : SessionState

    data class Ended(
        override val sessionId: Long,
        override val attemptId: Long,
        val finalPresentCount: Int,
        val finalAbsentCount: Int,
    ) : SessionState

    data class Error(
        override val sessionId: Long,
        override val attemptId: Long,
        val message: String,
        val isRecoverable: Boolean,
    ) : SessionState
}
