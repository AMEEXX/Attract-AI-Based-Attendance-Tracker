package com.attract.attendance.domain

import com.attract.attendance.core.model.AppError
import java.util.Locale

object Validators {
    private const val maxClassNameLength = 120
    private const val maxStudentNameLength = 160
    private const val maxRollLength = 80

    fun normalizeRollNumber(raw: String): String = raw
        .trim()
        .replace(Regex("\\s+"), " ")
        .uppercase(Locale.ROOT)

    fun className(raw: String): Result<String> = raw.trim().validate("name", "Class name", maxClassNameLength)

    fun studentName(raw: String): Result<String> = raw.trim().validate("name", "Student name", maxStudentNameLength)

    fun rollNumber(raw: String): Result<String> = normalizeRollNumber(raw).validate("rollNumber", "Roll number", maxRollLength)

    fun percentage(value: Int): Result<Int> = if (value in 0..100) {
        Result.success(value)
    } else {
        Result.failure(ValidationException(AppError.Validation("requiredAttendancePercent", "Required attendance must be between 0 and 100.")))
    }

    private fun String.validate(field: String, label: String, maxLength: Int): Result<String> = when {
        isBlank() -> Result.failure(ValidationException(AppError.Validation(field, "$label is required.")))
        length > maxLength -> Result.failure(ValidationException(AppError.Validation(field, "$label is too long.")))
        else -> Result.success(this)
    }
}

class ValidationException(val error: AppError.Validation) : IllegalArgumentException(error.message)
