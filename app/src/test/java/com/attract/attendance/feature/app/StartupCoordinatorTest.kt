package com.attract.attendance.feature.app

import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceSessionEntity
import com.attract.attendance.data.local.SessionDao
import com.attract.attendance.data.local.TeacherDao
import com.attract.attendance.data.local.TeacherEntity
import com.attract.attendance.lockdown.domain.FakeDeviceSecurityChecker
import com.attract.attendance.lockdown.domain.SetupCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupCoordinatorTest {

    @Test
    fun noTeacher_returnsOnboarding() = runTest {
        val teacherDao = FakeTeacherDao(firstTeacher = null)
        val sessionDao = FakeSessionDao(activeSession = null)
        val securityChecker = FakeDeviceSecurityChecker()

        val destination = StartupCoordinator(teacherDao, sessionDao, securityChecker).resolve()

        assertEquals(StartupDestination.Onboarding, destination)
    }

    @Test
    fun teacherWithEmptyPin_returnsOnboarding() = runTest {
        val teacher = TeacherEntity(id = 1, displayName = "Test", pinHash = "", createdAt = 0, updatedAt = 0)
        val teacherDao = FakeTeacherDao(firstTeacher = teacher)
        val sessionDao = FakeSessionDao(activeSession = null)
        val securityChecker = FakeDeviceSecurityChecker()

        val destination = StartupCoordinator(teacherDao, sessionDao, securityChecker).resolve()

        assertEquals(StartupDestination.Onboarding, destination)
    }

    @Test
    fun missingSecurityChecks_returnsBlockingSetup() = runTest {
        val teacher = TeacherEntity(id = 1, displayName = "Test", pinHash = "hash", createdAt = 0, updatedAt = 0)
        val teacherDao = FakeTeacherDao(firstTeacher = teacher)
        val sessionDao = FakeSessionDao(activeSession = null)
        val securityChecker = FakeDeviceSecurityChecker()
        securityChecker.add(SetupCheck.CAMERA_PERMISSION)

        val destination = StartupCoordinator(teacherDao, sessionDao, securityChecker).resolve()

        assertTrue(destination is StartupDestination.BlockingSetup)
        val blocking = destination as StartupDestination.BlockingSetup
        assertEquals(5, blocking.failedChecks.size)
    }

    @Test
    fun activeSessionExists_returnsRecoverSession() = runTest {
        val teacher = TeacherEntity(id = 1, displayName = "Test", pinHash = "hash", createdAt = 0, updatedAt = 0)
        val teacherDao = FakeTeacherDao(firstTeacher = teacher)
        val activeSession = AttendanceSessionEntity(
            id = 42,
            classId = 1,
            sessionDate = "2026-08-08",
            timeZoneId = "UTC",
            mode = SessionMode.FACE,
            status = SessionStatus.ACTIVE,
            startedAt = 100,
            endedAt = null,
            createdAt = 100,
            updatedAt = 100,
        )
        val sessionDao = FakeSessionDao(activeSession = activeSession)
        val securityChecker = FakeDeviceSecurityChecker().apply {
            SetupCheck.entries.forEach { add(it) }
        }

        val destination = StartupCoordinator(teacherDao, sessionDao, securityChecker).resolve()

        assertEquals(StartupDestination.RecoverSession(42), destination)
    }

    @Test
    fun allReadyNoActiveSession_returnsTeacherDashboard() = runTest {
        val teacher = TeacherEntity(id = 1, displayName = "Test", pinHash = "hash", createdAt = 0, updatedAt = 0)
        val teacherDao = FakeTeacherDao(firstTeacher = teacher)
        val sessionDao = FakeSessionDao(activeSession = null)
        val securityChecker = FakeDeviceSecurityChecker().apply {
            SetupCheck.entries.forEach { add(it) }
        }

        val destination = StartupCoordinator(teacherDao, sessionDao, securityChecker).resolve()

        assertEquals(StartupDestination.TeacherDashboard, destination)
    }

}

private class FakeTeacherDao(private val firstTeacher: TeacherEntity?) : TeacherDao {
    override fun observeFirst(): Flow<TeacherEntity?> = flowOf(firstTeacher)
    override suspend fun first(): TeacherEntity? = firstTeacher
    override suspend fun insert(teacher: TeacherEntity): Long = 1
    override suspend fun all(): List<TeacherEntity> = listOfNotNull(firstTeacher)
}

private class FakeSessionDao(private val activeSession: AttendanceSessionEntity?) : SessionDao {
    override suspend fun activeFaceSession(): AttendanceSessionEntity? = activeSession
    override suspend fun activeForClass(classId: Long): AttendanceSessionEntity? = activeSession
    override fun observeActiveForClass(classId: Long): Flow<AttendanceSessionEntity?> = flowOf(activeSession)
    override fun observeActiveSummaryForClass(classId: Long): Flow<com.attract.attendance.data.local.ActiveSessionRow?> = flowOf(null)
    override suspend fun all(): List<AttendanceSessionEntity> = listOfNotNull(activeSession)
    override suspend fun find(sessionId: Long): AttendanceSessionEntity? = activeSession
    override suspend fun latestIdForClass(classId: Long): Long? = activeSession?.id
    override suspend fun insert(value: AttendanceSessionEntity): Long = value.id
    override suspend fun finish(sessionId: Long, status: SessionStatus, endedAt: Long, updatedAt: Long): Int = 1
    override fun observeEndedForClass(classId: Long): Flow<List<com.attract.attendance.data.local.SessionRow>> = flowOf(emptyList())
    override suspend fun deleteEnded(sessionId: Long): Int = 0
    override fun observeSessionDaysForMonth(classId: Long, yearMonthPrefix: String): Flow<List<Int>> = flowOf(emptyList())
    override fun observeSessionsForDate(classId: Long, dateString: String): Flow<List<com.attract.attendance.data.local.SessionRow>> = flowOf(emptyList())
}
