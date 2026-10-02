package com.attract.attendance.domain.session

import com.attract.attendance.core.model.SessionMode
import java.util.UUID

data class SessionContext(
    val sessionId: Long,
    val classId: Long,
    val mode: SessionMode = SessionMode.FACE,
)

data class InteractionContext(
    val interactionId: Long,
    val attemptId: Long,
    val galleryVersion: Long = 0L,
    val lifecycleGeneration: Long = 0L,
)

enum class TeacherAuthAction {
    FIRST_ENROLLMENT,
    RE_ENROLLMENT,
    TEACHER_ASSISTED_CHECKIN,
}

data class TeacherAuthorizationGrant(
    val sessionId: Long,
    val classId: Long,
    val studentId: Long,
    val action: TeacherAuthAction,
    val interactionId: Long,
    val expiresAtMillis: Long,
    val token: String = UUID.randomUUID().toString(),
) {
    fun isValid(
        currentClassId: Long,
        targetStudentId: Long,
        expectedAction: TeacherAuthAction,
        currentSessionId: Long = sessionId,
        currentInteractionId: Long = interactionId,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        return sessionId == currentSessionId &&
            classId == currentClassId &&
            studentId == targetStudentId &&
            action == expectedAction &&
            interactionId == currentInteractionId &&
            currentTimeMillis <= expiresAtMillis
    }
}
