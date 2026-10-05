package com.attract.attendance.domain

import com.attract.attendance.core.model.AttendanceSource
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.SessionStatus
import com.attract.attendance.data.local.AttendanceRecordDao
import com.attract.attendance.data.local.AttendanceRecordEntity
import com.attract.attendance.data.local.SessionDao
import com.attract.attendance.data.local.StudentDao

sealed interface FinalizeSessionResult {
    data class Success(val presentCount: Int, val absentCount: Int) : FinalizeSessionResult
    data object SessionNotFoundOrEnded : FinalizeSessionResult
}

class FinalizeFaceSessionCommand(
    private val sessionDao: SessionDao,
    private val studentDao: StudentDao,
    private val attendanceRecordDao: AttendanceRecordDao,
) {
    suspend fun execute(
        sessionId: Long,
        timestamp: Long = System.currentTimeMillis(),
    ): FinalizeSessionResult {
        val session = sessionDao.find(sessionId) ?: return FinalizeSessionResult.SessionNotFoundOrEnded
        if (session.status != SessionStatus.ACTIVE) {
            return FinalizeSessionResult.SessionNotFoundOrEnded
        }

        val students = studentDao.activeForClass(session.classId)
        val existingRecords = attendanceRecordDao.forSession(sessionId)
        val recordedStudentIds = existingRecords.map { it.studentId }.toSet()

        val missingStudents = students.filter { student ->
            student.id !in recordedStudentIds
        }

        if (missingStudents.isNotEmpty()) {
            val absentEntities = missingStudents.map { student ->
                AttendanceRecordEntity(
                    sessionId = sessionId,
                    studentId = student.id,
                    status = AttendanceStatus.ABSENT,
                    checkInTime = null,
                    attendanceMethod = AttendanceSource.AI_RECOGNITION,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
            }

            attendanceRecordDao.insertAll(absentEntities)
        }

        val updatedRows = sessionDao.finish(
            sessionId = sessionId,
            status = SessionStatus.ENDED,
            endedAt = timestamp,
            updatedAt = timestamp,
        )

        if (updatedRows == 0) {
            return FinalizeSessionResult.SessionNotFoundOrEnded
        }

        val allRecords = attendanceRecordDao.forSession(sessionId)
        val presentCount = allRecords.count { it.status == AttendanceStatus.PRESENT }
        val absentCount = allRecords.count { it.status == AttendanceStatus.ABSENT }

        return FinalizeSessionResult.Success(presentCount, absentCount)
    }
}
