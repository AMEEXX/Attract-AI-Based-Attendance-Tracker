package com.attract.attendance.lockdown.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

class FakeLockTaskController : LockTaskController {

    private val _state = MutableStateFlow<LockTaskState>(LockTaskState.Unlocked)
    private val _events = MutableSharedFlow<LockTaskState>(extraBufferCapacity = 64)

    var startCalled = false
        private set

    var stopCalled = false
        private set

    var startShouldFail = false
    var stopShouldFail = false

    override suspend fun start(): LockTaskResult {
        startCalled = true
        if (startShouldFail) {
            return LockTaskResult.Error("Simulated start failure")
        }
        _state.value = LockTaskState.Locked
        _events.emit(LockTaskState.Locked)
        return LockTaskResult.Started
    }

    override suspend fun stop(): Result<Unit> {
        stopCalled = true
        if (stopShouldFail) {
            return Result.failure(RuntimeException("Simulated stop failure"))
        }
        _state.value = LockTaskState.Unlocked
        _events.emit(LockTaskState.Unlocked)
        return Result.success(Unit)
    }

    override fun observeState() = _events.asSharedFlow()

    fun setLocked(locked: Boolean) {
        val newState = if (locked) LockTaskState.Locked else LockTaskState.Unlocked
        _state.value = newState
        _state.update { newState }
    }

    suspend fun dropToUnlocked() {
        val newState = LockTaskState.Unlocked
        _state.value = newState
        _events.emit(newState)
    }
}
