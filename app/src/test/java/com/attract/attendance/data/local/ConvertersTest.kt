package com.attract.attendance.data.local

import com.attract.attendance.core.model.AttendanceMethod
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun enrollmentStatus_convertsBidirectionally() {
        for (status in EnrollmentStatus.entries) {
            val str = converters.enrollmentStatusToString(status)
            val converted = converters.enrollmentStatusFromString(str)
            assertEquals(status, converted)
        }
    }

    @Test
    fun attendanceStatus_convertsBidirectionally() {
        for (status in AttendanceStatus.entries) {
            val str = converters.attendanceStatusToString(status)
            val converted = converters.attendanceStatusFromString(str)
            assertEquals(status, converted)
        }
    }

    @Test
    fun attendanceMethod_convertsBidirectionally() {
        for (method in AttendanceMethod.entries) {
            val str = converters.attendanceMethodToString(method)
            val converted = converters.attendanceMethodFromString(str)
            assertEquals(method, converted)
        }
    }

    @Test
    fun sessionMode_convertsBidirectionally() {
        for (mode in SessionMode.entries) {
            val str = converters.sessionModeToString(mode)
            val converted = converters.sessionModeFromString(str)
            assertEquals(mode, converted)
        }
    }

    @Test
    fun sessionStatus_convertsBidirectionally() {
        for (status in SessionStatus.entries) {
            val str = converters.sessionStatusToString(status)
            val converted = converters.sessionStatusFromString(str)
            assertEquals(status, converted)
        }
    }
}
