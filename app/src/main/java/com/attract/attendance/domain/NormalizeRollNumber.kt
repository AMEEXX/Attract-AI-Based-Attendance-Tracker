package com.attract.attendance.domain

object NormalizeRollNumber {
    /**
     * Normalizes a raw roll number string.
     * Trims leading/trailing whitespace and collapses multiple internal spaces into a single space.
     * Throws IllegalArgumentException if normalized result is empty.
     */
    fun normalizeForDisplay(raw: String): String {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        require(trimmed.isNotEmpty()) { "Roll number cannot be blank" }
        return trimmed
    }

    /**
     * Normalizes a roll number for unique index / equality comparison (lowercase).
     */
    fun normalizeForComparison(raw: String): String {
        return normalizeForDisplay(raw).lowercase()
    }
}
