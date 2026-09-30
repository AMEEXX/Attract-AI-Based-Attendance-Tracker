package com.attract.attendance.feature.app

import com.attract.attendance.data.repository.CreateClassCommand
import com.attract.attendance.data.repository.CreateStudentCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttractViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun faceAttendanceRequested_setsLockdownMessage() = runTest {
        val state = AttractUiState()
        assertEquals(AppScreen.Loading, state.screen)
        assertNull(state.teacher)
        assertTrue(state.classes.isEmpty())
    }

    @Test
    fun pendingRosterImport_canBeCancelled() = runTest {
        val state = AttractUiState(pendingRosterImport = PendingRosterImport(1L, emptyList()))
        assertNotNull(state.pendingRosterImport)

        val cancelled = state.copy(pendingRosterImport = null)
        assertNull(cancelled.pendingRosterImport)
    }

    @Test
    fun lockdownEvent_updatesMessageState() = runTest {
        val state = AttractUiState(screen = AppScreen.ClassWorkspace(10L))
        val updatedState = state.copy(message = "Lockdown active")
        assertEquals("Lockdown active", updatedState.message)
        assertEquals(AppScreen.ClassWorkspace(10L), updatedState.screen)
    }
}
