package com.attract.attendance.lockdown.platform

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import com.attract.attendance.lockdown.domain.LockTaskController
import com.attract.attendance.lockdown.domain.LockTaskResult
import com.attract.attendance.lockdown.domain.LockTaskState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class LockTaskControllerImpl(
    private val activity: Activity,
) : LockTaskController {

    private companion object {
        const val STATE_POLL_INTERVAL_MS = 250L
        const val STATE_CONFIRM_TIMEOUT_MS = 5_000L
    }

    private val _state = MutableStateFlow(currentLockState())

    override suspend fun start(): LockTaskResult {
        return try {
            activity.startLockTask()
            // Per LLD-13 (async lock-state confirmation): the OS state transition is NOT
            // synchronous on physical devices, and consumer devices may first show the
            // system pinning-confirmation dialog. Poll before declaring failure.
            val deadline = SystemClock.elapsedRealtime() + STATE_CONFIRM_TIMEOUT_MS
            var state = currentLockState()
            while (state != LockTaskState.Locked && SystemClock.elapsedRealtime() < deadline) {
                delay(STATE_POLL_INTERVAL_MS)
                state = currentLockState()
            }
            _state.value = state
            if (state == LockTaskState.Locked) {
                LockTaskResult.Started
            } else {
                LockTaskResult.Error("Lock task state was not Locked after startLockTask call")
            }
        } catch (e: Exception) {
            LockTaskResult.Error(e.message ?: "startLockTask threw exception")
        }
    }

    override suspend fun stop(): Result<Unit> {
        return try {
            activity.stopLockTask()
            _state.value = currentLockState()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeState(): Flow<LockTaskState> = _state.asStateFlow()

    fun updateState() {
        _state.value = currentLockState()
    }

    private fun currentLockState(): LockTaskState {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return LockTaskState.Unlocked
        return when (am.lockTaskModeState) {
            ActivityManager.LOCK_TASK_MODE_PINNED,
            ActivityManager.LOCK_TASK_MODE_LOCKED -> LockTaskState.Locked
            else -> LockTaskState.Unlocked
        }
    }

}
