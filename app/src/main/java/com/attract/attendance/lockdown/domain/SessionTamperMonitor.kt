package com.attract.attendance.lockdown.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SessionTamperMonitor(
    private val lockTaskController: LockTaskController,
    private val authenticator: TeacherAuthenticator,
    private val logger: SecurityEventLogger,
    private val scope: CoroutineScope,
) {
    private val _tamperEvents = MutableSharedFlow<TamperEvent>(extraBufferCapacity = 64)
    val tamperEvents: SharedFlow<TamperEvent> = _tamperEvents.asSharedFlow()


    private var stopJob: Job? = null
    private var isMonitoring = false
    private var lastAuthWasStop = false

    data class TamperEvent(
        val timestamp: java.time.Instant = java.time.Instant.now(),
        val message: String = "Lock task state dropped unexpectedly",
    )

    suspend fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        lastAuthWasStop = false

        scope.launch {
            lockTaskController.observeState().collect { state ->
                if (state is LockTaskState.Unlocked && isMonitoring) {
                    if (lastAuthWasStop) {
                        lastAuthWasStop = false
                    } else {
                        val event = TamperEvent()
                        logger.log(
                            SecurityEvent(
                                category = SecurityEventCategory.TAMPER_DETECTED,
                                result = "LOCK_TASK_LOST",
                                deviceInfo = getDeviceInfo(),
                                safeErrorCode = "TAMPER_001",
                            )
                        )
                        _tamperEvents.emit(event)
                    }
                }
            }
        }
    }


    suspend fun noteAuthenticatedStop() {
        lastAuthWasStop = true
    }

    suspend fun stopMonitoring() {
        isMonitoring = false
        lastAuthWasStop = false
    }

    private fun getDeviceInfo(): String =
        "OEM=${android.os.Build.MANUFACTURER};Model=${android.os.Build.MODEL}"
}
