package com.attract.attendance.domain.session

import com.attract.attendance.core.model.StudentSummary
import com.attract.attendance.domain.face.QualityResult
import com.attract.attendance.lockdown.domain.AuthResult

sealed interface SessionEvent {
    val attemptId: Long

    data class StartReady(override val attemptId: Long, val initialPresentCount: Int) : SessionEvent
    data class CheckInPressed(override val attemptId: Long) : SessionEvent
    data class FrameCandidate(override val attemptId: Long, val frameData: ByteArray) : SessionEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FrameCandidate) return false
            return attemptId == other.attemptId && frameData.contentEquals(other.frameData)
        }

        override fun hashCode(): Int {
            var result = attemptId.hashCode()
            result = 31 * result + frameData.contentHashCode()
            return result
        }
    }

    data class QualityEvaluated(override val attemptId: Long, val result: QualityResult) : SessionEvent

    data class LivenessEvaluated(override val attemptId: Long, val isLive: Boolean, val score: Float) : SessionEvent
    data class RecognitionEvaluated(
        override val attemptId: Long,
        val matchedStudent: StudentSummary?,
        val confidence: Float,
    ) : SessionEvent

    data class PersistCompleted(
        override val attemptId: Long,
        val student: StudentSummary,
        val isSuccess: Boolean,
    ) : SessionEvent

    data class EnrollmentRequested(override val attemptId: Long, val reason: String) : SessionEvent
    data class TeacherAssistRequested(override val attemptId: Long, val reason: String) : SessionEvent
    data class EndRequested(override val attemptId: Long) : SessionEvent
    data class AuthOutcome(override val attemptId: Long, val authResult: AuthResult) : SessionEvent
    data class Cancel(override val attemptId: Long) : SessionEvent
    data class Failure(override val attemptId: Long, val message: String) : SessionEvent
}
