package com.attract.attendance.functional

import com.attract.attendance.data.security.PinHasher
import com.attract.attendance.lockdown.data.crypto.AesGcmEmbeddingCipher
import com.attract.attendance.lockdown.domain.AuthMethod
import com.attract.attendance.lockdown.domain.AuthReason
import com.attract.attendance.lockdown.domain.AuthResult
import com.attract.attendance.lockdown.domain.FakeDeviceSecurityChecker
import com.attract.attendance.lockdown.domain.FakeLockTaskController
import com.attract.attendance.lockdown.domain.FakeSecurityEventLogger
import com.attract.attendance.lockdown.domain.FakeTeacherAuthenticator
import com.attract.attendance.lockdown.domain.PrerequisitesChecker
import com.attract.attendance.lockdown.domain.PrerequisiteResult
import com.attract.attendance.lockdown.domain.SessionTamperMonitor
import com.attract.attendance.lockdown.domain.SetupCheck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FT-01: SecurityPipelineFunctionalTest
 * Verifies the full authentication and security pipeline as an integrated workflow —
 * prerequisites -> PIN auth -> lock task start/stop -> tamper event propagation -> embedding cipher.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class SecurityPipelineFunctionalTest {

    @Test
    fun prerequisitesGate_allMet_allowsSessionStart() = runTest {
        val checker = FakeDeviceSecurityChecker().apply {
            SetupCheck.entries.forEach { add(it) }
        }
        val prerequisitesChecker = PrerequisitesChecker(checker)

        val result = prerequisitesChecker.evaluate()
        assertTrue(result is PrerequisiteResult.Ready)
    }

    @Test
    fun prerequisitesGate_cameraPermissionMissing_blocksStart() = runTest {
        val checker = FakeDeviceSecurityChecker().apply {
            SetupCheck.entries.forEach { add(it) }
            remove(SetupCheck.CAMERA_PERMISSION)
        }
        val prerequisitesChecker = PrerequisitesChecker(checker)

        val result = prerequisitesChecker.evaluate()
        assertTrue(result is PrerequisiteResult.Blocked)
        val blocked = result as PrerequisiteResult.Blocked
        assertTrue(blocked.missing.contains(SetupCheck.CAMERA_PERMISSION))
    }

    @Test
    fun prerequisitesGate_noPinConfigured_blocksStart() = runTest {
        val checker = FakeDeviceSecurityChecker().apply {
            SetupCheck.entries.forEach { add(it) }
            remove(SetupCheck.TEACHER_PIN_CONFIGURED)
        }
        val prerequisitesChecker = PrerequisitesChecker(checker)

        val result = prerequisitesChecker.evaluate()
        assertTrue(result is PrerequisiteResult.Blocked)
        val blocked = result as PrerequisiteResult.Blocked
        assertTrue(blocked.missing.contains(SetupCheck.TEACHER_PIN_CONFIGURED))
    }

    @Test
    fun pinAuth_correctPin_grantsOneCommand() = runTest {
        val pinHasher = PinHasher()
        val storedHash = pinHasher.hash("1234".toCharArray())

        val authenticator = FakeTeacherAuthenticator().apply {
            nextResult = if (pinHasher.verify("1234".toCharArray(), storedHash)) {
                AuthResult.Success(AuthMethod.PIN)
            } else {
                AuthResult.Failed
            }
        }

        val result = authenticator.authenticate(AuthReason.END_SESSION)
        assertTrue(result is AuthResult.Success)
        assertEquals(AuthMethod.PIN, (result as AuthResult.Success).method)
    }

    @Test
    fun pinAuth_wrongPin_returnsFailure() = runTest {
        val pinHasher = PinHasher()
        val storedHash = pinHasher.hash("1234".toCharArray())

        val authenticator = FakeTeacherAuthenticator().apply {
            nextResult = if (pinHasher.verify("9999".toCharArray(), storedHash)) {
                AuthResult.Success(AuthMethod.PIN)
            } else {
                AuthResult.Failed
            }
        }

        val result = authenticator.authenticate(AuthReason.END_SESSION)
        assertTrue(result is AuthResult.Failed)
    }

    @Test
    fun pinAuth_emptyPin_returnsFailure() = runTest {
        val pinHasher = PinHasher()
        val storedHash = pinHasher.hash("1234".toCharArray())

        val authenticator = FakeTeacherAuthenticator().apply {
            nextResult = if (pinHasher.verify("".toCharArray(), storedHash)) {
                AuthResult.Success(AuthMethod.PIN)
            } else {
                AuthResult.Failed
            }
        }

        val result = authenticator.authenticate(AuthReason.END_SESSION)
        assertTrue(result is AuthResult.Failed)
    }

    @Test
    fun lockTask_startAndStop_completeLifecycle() = runTest {
        val lockTaskController = FakeLockTaskController()
        assertFalse(lockTaskController.startCalled)

        val startResult = lockTaskController.start()
        assertTrue(startResult is com.attract.attendance.lockdown.domain.LockTaskResult.Started)
        assertTrue(lockTaskController.startCalled)

        val stopResult = lockTaskController.stop()
        assertTrue(stopResult.isSuccess)
        assertTrue(lockTaskController.stopCalled)
    }

    @Test
    fun tamperMonitor_dropDuringActiveLockTask_emitsTamperEvent() = runTest {
        val lockTaskController = FakeLockTaskController()
        val authenticator = FakeTeacherAuthenticator()
        val logger = FakeSecurityEventLogger()
        val testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))

        val monitor = SessionTamperMonitor(
            lockTaskController = lockTaskController,
            authenticator = authenticator,
            logger = logger,
            scope = testScope
        )

        monitor.startMonitoring()
        lockTaskController.start()
        lockTaskController.dropToUnlocked()

        val events = logger.getEventsForCategory(com.attract.attendance.lockdown.domain.SecurityEventCategory.TAMPER_DETECTED)
        assertEquals(1, events.size)
        assertEquals("LOCK_TASK_LOST", events.first().result)
    }

    @Test
    fun embeddingCipher_encryptDecryptInFakeSessionContext() = runTest {
        val cipher = AesGcmEmbeddingCipher(AesGcmEmbeddingCipher.generateKey())
        val plaintext = "test_embedding_bytes".toByteArray(Charsets.UTF_8)
        val studentId = 42L
        val templateId = 10L
        val modelVersion = "v1"

        val encrypted = cipher.encrypt(studentId, templateId, modelVersion, plaintext)
        assertTrue(encrypted.ciphertext.isNotEmpty())
        assertTrue(encrypted.iv.isNotEmpty())

        val decrypted = cipher.decrypt(studentId, templateId, modelVersion, encrypted)
        assertArrayEquals(plaintext, decrypted)
    }
}
