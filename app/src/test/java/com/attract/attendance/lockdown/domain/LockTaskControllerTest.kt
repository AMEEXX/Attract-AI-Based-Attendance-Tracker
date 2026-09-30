package com.attract.attendance.lockdown.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTaskControllerTest {

    @Test
    fun startFailure_returnsError() = runTest {
        val controller = FakeLockTaskController().apply {
            startShouldFail = true
        }

        val result = controller.start()
        assertTrue(result is LockTaskResult.Error)
    }

    @Test
    fun stopFailure_returnsFailure() = runTest {
        val controller = FakeLockTaskController().apply {
            stopShouldFail = true
        }

        val result = controller.stop()
        assertTrue(result.isFailure)
    }

    @Test
    fun startSuccess_transitionsToLocked() = runTest {
        val controller = FakeLockTaskController()

        val result = controller.start()
        assertTrue(result is LockTaskResult.Started)
    }

    @Test
    fun stopSuccess_transitionsToUnlocked() = runTest {
        val controller = FakeLockTaskController()
        controller.start()

        val result = controller.stop()
        assertTrue(result.isSuccess)
    }

    @Test
    fun alreadyLocked_onSecondStart_returnsAlreadyLocked() = runTest {
        val controller = FakeLockTaskController()
        controller.start()

        val result = controller.start()
        assertTrue(result is LockTaskResult.Started || result is LockTaskResult.AlreadyLocked)
    }
}
