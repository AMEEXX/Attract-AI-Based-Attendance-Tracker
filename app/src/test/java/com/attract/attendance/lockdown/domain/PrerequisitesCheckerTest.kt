package com.attract.attendance.lockdown.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrerequisitesCheckerTest {

    @Test
    fun incompletePrerequisiteSet_blocksStartWithRemediation() = runTest {
        val checker = FakeDeviceSecurityChecker()
        checker.add(SetupCheck.CAMERA_PERMISSION)
        checker.add(SetupCheck.TEACHER_PIN_CONFIGURED)
        // Missing SECURE_DEVICE_LOCK, SCREEN_PINNING_PROBED, MODEL_RESOURCES_VERIFIED, NO_ACTIVE_FACE_SESSION

        val result = PrerequisitesChecker(checker).evaluate()

        assertTrue(result is PrerequisiteResult.Blocked)
        val blocked = result as PrerequisiteResult.Blocked
        assertEquals(4, blocked.missing.size)
        assertTrue(blocked.missing.contains(SetupCheck.SECURE_DEVICE_LOCK))
        assertTrue(blocked.missing.contains(SetupCheck.SCREEN_PINNING_PROBED))
        assertTrue(blocked.missing.contains(SetupCheck.MODEL_RESOURCES_VERIFIED))
        assertTrue(blocked.missing.contains(SetupCheck.NO_ACTIVE_FACE_SESSION))
    }

    @Test
    fun allPrerequisitesPresent_returnsReady() = runTest {
        val checker = FakeDeviceSecurityChecker()
        checker.add(SetupCheck.CAMERA_PERMISSION)
        checker.add(SetupCheck.TEACHER_PIN_CONFIGURED)
        checker.add(SetupCheck.SECURE_DEVICE_LOCK)
        checker.add(SetupCheck.SCREEN_PINNING_PROBED)
        checker.add(SetupCheck.MODEL_RESOURCES_VERIFIED)
        checker.add(SetupCheck.NO_ACTIVE_FACE_SESSION)

        val result = PrerequisitesChecker(checker).evaluate()

        assertTrue(result is PrerequisiteResult.Ready)
    }

    @Test
    fun emptySet_returnsBlockedWithAllRequired() = runTest {
        val checker = FakeDeviceSecurityChecker()

        val result = PrerequisitesChecker(checker).evaluate()

        assertTrue(result is PrerequisiteResult.Blocked)
        val blocked = result as PrerequisiteResult.Blocked
        assertEquals(6, blocked.missing.size)
    }
}

