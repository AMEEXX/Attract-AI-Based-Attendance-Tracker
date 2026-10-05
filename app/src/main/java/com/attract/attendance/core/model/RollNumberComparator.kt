package com.attract.attendance.core.model

/**
 * Natural ascending comparator for roll numbers.
 * Correctly sorts numeric and alphanumeric roll numbers, e.g.:
 * 1, 2, 3, ..., 10, 11
 * CS-1, CS-2, ..., CS-10
 */
object RollNumberComparator : Comparator<String?> {
    private val chunkPattern = Regex("(\\d+|\\D+)")

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 == null && s2 == null) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1

        val chunks1 = chunkPattern.findAll(s1.trim()).map { it.value }.toList()
        val chunks2 = chunkPattern.findAll(s2.trim()).map { it.value }.toList()

        val minSize = minOf(chunks1.size, chunks2.size)
        for (i in 0 until minSize) {
            val c1 = chunks1[i]
            val c2 = chunks2[i]

            val isDigit1 = c1.all { it.isDigit() }
            val isDigit2 = c2.all { it.isDigit() }

            val cmp = if (isDigit1 && isDigit2) {
                val num1 = c1.toBigIntegerOrNull()
                val num2 = c2.toBigIntegerOrNull()
                if (num1 != null && num2 != null) num1.compareTo(num2) else c1.compareTo(c2)
            } else {
                c1.compareTo(c2, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return chunks1.size.compareTo(chunks2.size)
    }
}
