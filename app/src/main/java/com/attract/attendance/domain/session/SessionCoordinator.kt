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
import java.util.concurrent.atomic.AtomicLong

class SessionCoordinator(
    val sessionId: Long,
    private val recordPresentCommand: RecordPresentCommand,
) {
    private val attemptCounter = AtomicLong(1)
    private val _state = MutableStateFlow<SessionState>(
        SessionState.Initializing(sessionId = sessionId, attemptId = attemptCounter.get())
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val eventChannel = Channel<SessionEvent>(Channel.UNLIMITED)

    var currentAttemptId: Long
        get() = attemptCounter.get()
        private set(value) = attemptCounter.set(value)

    var currentPresentCount: Int = 0
        private set

    private var attemptFailCount = 0

    fun nextAttempt(): Long {
        attemptFailCount = 0
        return attemptCounter.incrementAndGet()
    }

    suspend fun processEvent(event: SessionEvent) {
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
                            _state.value = SessionState.Ready(sessionId, currentAttemptId, currentPresentCount)
                        }
                    }
                }
            }

            is SessionEvent.EnrollmentRequested -> {
                _state.value = SessionState.EnrollmentRequest(sessionId, currentAttemptId, event.reason)
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
        val newAttempt = nextAttempt()
        _state.value = SessionState.Ready(sessionId, newAttempt, currentPresentCount)
    }
}
