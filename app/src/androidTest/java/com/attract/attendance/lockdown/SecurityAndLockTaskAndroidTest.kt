package com.attract.attendance.lockdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.attract.attendance.lockdown.domain.FakeDeviceSecurityChecker
import com.attract.attendance.lockdown.domain.FakeLockTaskController
import com.attract.attendance.lockdown.domain.PrerequisitesChecker
import com.attract.attendance.lockdown.domain.PrerequisiteResult
import com.attract.attendance.lockdown.domain.SetupCheck
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

import com.attract.attendance.lockdown.domain.LockTaskResult

/**
 * IT-05: SecurityAndLockTaskAndroidTest
 * Instrumented test verifying LockTask state controller and security prerequisite checks on Android runtime.
 */
@RunWith(AndroidJUnit4::class)
class SecurityAndLockTaskAndroidTest {

    @Test
    fun lockTaskController_startAndStop_updatesLockState() = runBlocking {
        val controller = FakeLockTaskController()
        assertFalse(controller.startCalled)

        val startResult = controller.start()
        assertTrue(startResult is LockTaskResult.Started)
        assertTrue(controller.startCalled)

        val stopResult = controller.stop()
        assertTrue(stopResult.isSuccess)
        assertTrue(controller.stopCalled)
    }

    @Test
    fun prerequisitesChecker_evaluatesDeviceChecks() = runBlocking {
        val checker = FakeDeviceSecurityChecker().apply {
            SetupCheck.entries.forEach { add(it) }
        }
        val prerequisites = PrerequisitesChecker(checker)

        val result = prerequisites.evaluate()
        assertTrue(result is PrerequisiteResult.Ready)
    }
}
