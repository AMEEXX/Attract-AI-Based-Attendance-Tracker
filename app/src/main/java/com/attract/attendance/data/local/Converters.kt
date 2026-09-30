package com.attract.attendance.data.local

import androidx.room.TypeConverter
import com.attract.attendance.core.model.AttendanceMethod
import com.attract.attendance.core.model.AttendanceStatus
import com.attract.attendance.core.model.EnrollmentStatus
import com.attract.attendance.core.model.SessionMode
import com.attract.attendance.core.model.SessionStatus

class Converters {
    @TypeConverter fun enrollmentStatusToString(value: EnrollmentStatus): String = value.name
    @TypeConverter fun enrollmentStatusFromString(value: String): EnrollmentStatus = EnrollmentStatus.valueOf(value)
    @TypeConverter fun attendanceStatusToString(value: AttendanceStatus): String = value.name
    @TypeConverter fun attendanceStatusFromString(value: String): AttendanceStatus = AttendanceStatus.valueOf(value)
    @TypeConverter fun attendanceMethodToString(value: AttendanceMethod): String = value.name
    @TypeConverter fun attendanceMethodFromString(value: String): AttendanceMethod = AttendanceMethod.valueOf(value)
    @TypeConverter fun sessionModeToString(value: SessionMode): String = value.name
    @TypeConverter fun sessionModeFromString(value: String): SessionMode = SessionMode.valueOf(value)
    @TypeConverter fun sessionStatusToString(value: SessionStatus): String = value.name
    @TypeConverter fun sessionStatusFromString(value: String): SessionStatus = SessionStatus.valueOf(value)
}
