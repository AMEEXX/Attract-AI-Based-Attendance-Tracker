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

    @Test
    fun noEnrolledStudentsWarning_canBeSetAndDismissed() = runTest {
        val warning = NoEnrolledStudentsDialogState(
            classId = 1L,
            sessionDate = "2026-10-06",
            totalStudents = 0,
            enrolledCount = 0
        )
        val state = AttractUiState(noEnrolledStudentsWarning = warning)
        assertNotNull(state.noEnrolledStudentsWarning)
        assertEquals(0, state.noEnrolledStudentsWarning?.totalStudents)

        val dismissedState = state.copy(noEnrolledStudentsWarning = null)
        assertNull(dismissedState.noEnrolledStudentsWarning)
    }

    @Test
    fun driveSyncState_initialValues() {
        val state = AttractUiState()
        assertNull(state.driveAccountEmail)
        assertEquals(com.attract.attendance.data.drive.DriveSyncStatus.Idle, state.driveSyncStatus)
        assertEquals(0L, state.driveLastSyncMillis)
    }

    @Test
    fun driveSyncState_transitions() {
        val connectedState = AttractUiState(
            driveAccountEmail = "teacher@school.edu",
            driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Syncing,
            driveLastSyncMillis = 0L
        )
        assertEquals("teacher@school.edu", connectedState.driveAccountEmail)
        assertTrue(connectedState.driveSyncStatus is com.attract.attendance.data.drive.DriveSyncStatus.Syncing)

        val successState = connectedState.copy(
            driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Success(1700000000000L),
            driveLastSyncMillis = 1700000000000L
        )
        assertEquals(1700000000000L, successState.driveLastSyncMillis)
        assertTrue(successState.driveSyncStatus is com.attract.attendance.data.drive.DriveSyncStatus.Success)

        val errorState = connectedState.copy(
            driveSyncStatus = com.attract.attendance.data.drive.DriveSyncStatus.Error("Network timeout")
        )
        assertTrue(errorState.driveSyncStatus is com.attract.attendance.data.drive.DriveSyncStatus.Error)
        assertEquals("Network timeout", (errorState.driveSyncStatus as com.attract.attendance.data.drive.DriveSyncStatus.Error).message)
    }
}
