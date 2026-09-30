package com.attract.attendance.lockdown.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionTamperMonitorTest {


    @Test
    fun unexpectedLockStateDrop_logsTamperDetected() = runTest {
        val controller = TestLockTaskController()
        val logger = FakeSecurityEventLogger()
        val authenticator = FakeTeacherAuthenticator()
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        val monitor = SessionTamperMonitor(controller, authenticator, logger, testScope)
        monitor.startMonitoring()

        controller.dropToUnlockedWithoutAuth()

        val events = logger.getEventsForCategory(SecurityEventCategory.TAMPER_DETECTED)
        assertEquals(1, events.size)
    }

    @Test
    fun authenticatedStop_doesNotTriggerTamper() = runTest {
        val controller = TestLockTaskController()
        val logger = FakeSecurityEventLogger()
        val authenticator = FakeTeacherAuthenticator()
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        val monitor = SessionTamperMonitor(controller, authenticator, logger, testScope)
        monitor.startMonitoring()

        monitor.noteAuthenticatedStop()
        controller.dropToUnlockedWithoutAuth()

        val events = logger.getEventsForCategory(SecurityEventCategory.TAMPER_DETECTED)
        assertEquals(0, events.size)
    }

    @Test
    fun tamperEventEmittedAsFlow() = runTest {
        val controller = TestLockTaskController()
        val logger = FakeSecurityEventLogger()
        val authenticator = FakeTeacherAuthenticator()
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        val monitor = SessionTamperMonitor(controller, authenticator, logger, testScope)
        monitor.startMonitoring()

        val collected = mutableListOf<SessionTamperMonitor.TamperEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            monitor.tamperEvents.collect { event ->
                collected.add(event)
            }
        }

        controller.dropToUnlockedWithoutAuth()

        assertEquals(1, collected.size)
        assertEquals("Lock task state dropped unexpectedly", collected[0].message)
        job.cancel()
    }

    @Test
    fun multipleUnscheduledDrops_logsMultipleTamperEvents() = runTest {
        val controller = TestLockTaskController()
        val logger = FakeSecurityEventLogger()
        val authenticator = FakeTeacherAuthenticator()
        val testScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        val monitor = SessionTamperMonitor(controller, authenticator, logger, testScope)
        monitor.startMonitoring()

        controller.lock()
        controller.dropToUnlockedWithoutAuth()

        monitor.noteAuthenticatedStop()
        controller.lock()
        controller.dropToUnlockedWithoutAuth()

        controller.lock()
        controller.dropToUnlockedWithoutAuth()

        val events = logger.getEventsForCategory(SecurityEventCategory.TAMPER_DETECTED)
        assertEquals("Expected 2 tamper events but got ${events.size}", 2, events.size)
    }
}


class TestLockTaskController : LockTaskController {

    private val _events = MutableSharedFlow<LockTaskState>(replay = 10, extraBufferCapacity = 10)

    override suspend fun start(): LockTaskResult = LockTaskResult.Started

    override suspend fun stop(): Result<Unit> = Result.success(Unit)

    override fun observeState(): Flow<LockTaskState> = _events

    fun lock() {
        _events.tryEmit(LockTaskState.Locked)
    }

    fun dropToUnlockedWithoutAuth() {
        _events.tryEmit(LockTaskState.Unlocked)
    }
}



