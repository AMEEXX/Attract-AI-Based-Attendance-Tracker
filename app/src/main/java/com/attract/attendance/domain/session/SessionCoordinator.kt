package com.attract.attendance.domain.session

import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.domain.RecordPresentCommand
import com.attract.attendance.domain.RecordPresentResult
import com.attract.attendance.domain.face.QualityResult
import com.attract.attendance.lockdown.domain.AuthResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

class SessionCoordinator(
    val sessionId: Long,
    private val recordPresentCommand: RecordPresentCommand,
    val classId: Long = 0L,
) {
    private val attemptCounter = AtomicLong(1)
    private val interactionCounter = AtomicLong(1)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SessionState>(
        SessionState.Initializing(sessionId = sessionId, attemptId = attemptCounter.get())
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val eventChannel = Channel<SessionEvent>(Channel.UNLIMITED)

    var currentAttemptId: Long
        get() = attemptCounter.get()
        private set(value) = attemptCounter.set(value)

    var currentInteractionId: Long
        get() = interactionCounter.get()
        private set(value) = interactionCounter.set(value)

    var currentPresentCount: Int = 0
        private set

    var activeGrant: TeacherAuthorizationGrant? = null
        private set

    private var attemptFailCount = 0

    fun nextAttempt(): Long {
        return attemptCounter.incrementAndGet()
    }

    fun nextInteraction(): Long {
        attemptFailCount = 0
        activeGrant = null
        attemptCounter.incrementAndGet()
        return interactionCounter.incrementAndGet()
    }

    suspend fun processEvent(event: SessionEvent) = mutex.withLock {
        if (event.attemptId != currentAttemptId) {
            return
        }

        val currentState = _state.value

        when (event) {
            is SessionEvent.StartReady -> {
                currentPresentCount = event.initialPresentCount
                _state.value = SessionState.Ready(sessionId, currentAttemptId, currentPresentCount)
            }

            is SessionEvent.CheckInPressed -> {
                if (currentState is SessionState.Ready) {
                    _state.value = SessionState.Acquiring(sessionId, currentAttemptId)
                }
            }

            is SessionEvent.QualityEvaluated -> {
                if (currentState is SessionState.Acquiring || currentState is SessionState.QualityChecking) {
                    if (event.result is QualityResult.Accepted) {
                        _state.value = SessionState.Recognizing(sessionId, currentAttemptId, event.result)
                    } else {
                        _state.value = SessionState.Ready(sessionId, currentAttemptId, currentPresentCount)
                    }
                }
            }

            is SessionEvent.RecognitionEvaluated -> {
                if (currentState is SessionState.Recognizing || currentState is SessionState.LivenessChecking) {
                    if (event.isAmbiguous) {
                        _state.value = SessionState.Ambiguous(
                            sessionId = sessionId,
                            attemptId = currentAttemptId,
                            reason = "Multiple close candidate matches",
                        )
                        return
                    }

                    val matchedStudent = event.matchedStudent
                    if (matchedStudent != null) {
                        _state.value = SessionState.PersistingPresent(
                            sessionId = sessionId,
                            attemptId = currentAttemptId,
                            student = matchedStudent,
                            matchConfidence = event.confidence,
                        )
                        // Trigger persistence execution
                        val recordResult = recordPresentCommand.execute(
                            sessionId = sessionId,
                            studentId = matchedStudent.id,
                            method = AttendanceSource.AI_RECOGNITION,
                            confidence = event.confidence,
                        )

                        if (recordResult is RecordPresentResult.Success || recordResult is RecordPresentResult.AlreadyPresent) {
                            if (recordResult is RecordPresentResult.Success) {
                                currentPresentCount++
                            }
                            _state.value = SessionState.SuccessFeedback(
                                sessionId = sessionId,
                                attemptId = currentAttemptId,
                                studentName = matchedStudent.name,
                                rollNumber = matchedStudent.rollNumber,
                                isAlreadyPresent = recordResult is RecordPresentResult.AlreadyPresent,
                            )
                        } else {
                            _state.value = SessionState.Error(
                                sessionId = sessionId,
                                attemptId = currentAttemptId,
                                message = "Persistence failed for ${matchedStudent.name}",
                                isRecoverable = true,
                            )
                        }
                    } else {
                        attemptFailCount++
                        if (attemptFailCount >= 2) {
                            _state.value = SessionState.TeacherAssistance(
                                sessionId = sessionId,
                                attemptId = currentAttemptId,
                                reason = "Unrecognized face after multiple attempts",
                            )
                        } else {
                            _state.value = SessionState.RetryFeedback(
                                sessionId = sessionId,
                                attemptId = currentAttemptId,
                                attemptIndex = attemptFailCount,
                                maxAttempts = 2,
                                message = "Couldn't verify clearly, please try again (Attempt $attemptFailCount/2).",
                            )
                        }
                    }
                }
            }

            is SessionEvent.RetryFeedbackExpired -> {
                if (currentState is SessionState.RetryFeedback) {
                    val newAttempt = nextAttempt()
                    _state.value = SessionState.Ready(sessionId, newAttempt, currentPresentCount)
                }
            }

            is SessionEvent.StudentSelectedForEnrollment -> {
                _state.value = SessionState.AwaitingTeacherApproval(
                    sessionId = sessionId,
                    attemptId = currentAttemptId,
                    targetStudent = event.targetStudent,
                )
            }

            is SessionEvent.TeacherApprovalGranted -> {
                activeGrant = event.grant
                when (currentState) {
                    is SessionState.AwaitingTeacherApproval -> {
                        _state.value = SessionState.EnrollmentCapture(
                            sessionId = sessionId,
                            attemptId = currentAttemptId,
                            targetStudent = currentState.targetStudent,
                            slot = 0,
                            grant = event.grant,
                        )
                    }
                    is SessionState.TeacherAssistance -> {
                        _state.value = SessionState.AssistedActionSelection(
                            sessionId = sessionId,
                            attemptId = currentAttemptId,
                            grant = event.grant,
                        )
                    }
                    else -> {}
                }
            }

            is SessionEvent.TeacherApprovalRejected -> {
                activeGrant = null
                val newAttempt = nextAttempt()
                _state.value = SessionState.Ready(sessionId, newAttempt, currentPresentCount)
            }

            is SessionEvent.EnrollmentSlotCaptured -> {
                if (currentState is SessionState.EnrollmentCapture) {
                    if (event.slot >= 2) {
                        _state.value = SessionState.EnrollmentValidation(
                            sessionId = sessionId,
                            attemptId = currentAttemptId,
                            targetStudent = currentState.targetStudent,
                            grant = currentState.grant,
                        )
                    } else {
                        _state.value = currentState.copy(slot = event.slot + 1)
                    }
                }
            }

            is SessionEvent.EnrollmentSlotRetake -> {
                if (currentState is SessionState.EnrollmentCapture) {
                    _state.value = currentState.copy(slot = event.slot)
                }
            }

            is SessionEvent.EnrollmentCompleted -> {
                currentPresentCount++
                _state.value = SessionState.SuccessFeedback(
                    sessionId = sessionId,
                    attemptId = currentAttemptId,
                    studentName = event.student.name,
                    rollNumber = event.student.rollNumber,
                )
            }

            is SessionEvent.EnrollmentRequested -> {
                _state.value = SessionState.EnrollmentSelection(sessionId, currentAttemptId)
            }

            is SessionEvent.TeacherAssistRequested -> {
                _state.value = SessionState.TeacherAssistance(sessionId, currentAttemptId, event.reason)
            }

            is SessionEvent.EndRequested -> {
                _state.value = SessionState.Ending(sessionId, currentAttemptId)
            }

            is SessionEvent.AuthOutcome -> {
                if (currentState is SessionState.Ending) {
                    if (event.authResult is AuthResult.Success) {
                        _state.value = SessionState.Ended(
                            sessionId = sessionId,
                            attemptId = currentAttemptId,
                            finalPresentCount = currentPresentCount,
                            finalAbsentCount = 0,
                        )
                    } else {
                        _state.value = SessionState.Ready(sessionId, currentAttemptId, currentPresentCount)
                    }
                }
            }

            is SessionEvent.Cancel -> {
                activeGrant = null
                val newAttempt = nextAttempt()
                _state.value = SessionState.Ready(sessionId, newAttempt, currentPresentCount)
            }

            is SessionEvent.Failure -> {
                _state.value = SessionState.Error(
                    sessionId = sessionId,
                    attemptId = currentAttemptId,
                    message = event.message,
                    isRecoverable = true,
                )
            }

            else -> {}
        }
    }

    fun resetToReady() {
        attemptFailCount = 0
        activeGrant = null
        val newAttempt = nextAttempt()
        _state.value = SessionState.Ready(sessionId, newAttempt, currentPresentCount)
    }
}
