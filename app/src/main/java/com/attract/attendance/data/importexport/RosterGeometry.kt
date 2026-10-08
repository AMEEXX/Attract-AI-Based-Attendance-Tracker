package com.attract.attendance.data.importexport

import kotlin.math.abs

/** An OCR word with its bounding box in image pixels. Keeps this file free of Android/ML Kit types. */
data class OcrWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Float get() = (top + bottom) / 2f
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/**
 * Offline fallback. Rebuilds table rows from word positions instead of trusting the recogniser's own
 * line/block grouping (which tends to read a table column-by-column).
 */
object RosterGeometry {

    // Whole-word matches only. The old code used substring matching, which silently dropped real names such as
    // "Sheetal" (contains "sheet") or "Namrata"-style names that happen to contain a header word.
    private val HEADER_WORDS = setOf(
        "s", "sl", "no", "sno", "slno", "serial", "roll", "rollno", "reg", "registration", "enrollment", "enroll",
        "id", "number", "name", "student", "students", "signature", "sign", "present", "absent", "date", "time",
        "class", "section", "semester", "subject", "faculty", "teacher", "department", "dept", "college",
        "university", "school", "remarks", "total", "percentage", "batch", "page", "attendance", "sheet",
        "roster", "list"
    )

    fun rowsFromWords(words: List<OcrWord>): List<RawRosterRow> {
        if (words.isEmpty()) return emptyList()
        val medianHeight = words.map { it.height }.sorted()[words.size / 2].toFloat()

        // 1. Cluster words into visual rows by vertical centre.
        val rows = ArrayList<MutableList<OcrWord>>()
        var rowMean = 0f
        for (w in words.sortedBy { it.centerY }) {
            if (rows.isNotEmpty() && abs(w.centerY - rowMean) <= medianHeight * 0.6f) {
                rows.last().add(w)
                rowMean = rows.last().map { it.centerY }.average().toFloat()
            } else {
                rows.add(mutableListOf(w))
                rowMean = w.centerY
            }
        }

        // 2. Each row: left-to-right cells (a wide horizontal gap = a new table column) -> serial / roll / name.
        return rows.mapNotNull { rowToRaw(it.sortedBy { w -> w.left }, medianHeight) }
    }

    private fun rowToRaw(words: List<OcrWord>, medianHeight: Float): RawRosterRow? {
        val cells = ArrayList<StringBuilder>()
        var previousRight = Int.MIN_VALUE
        for (w in words) {
            val t = w.text.trim().trim { it in "|_[]{}:;" || it.isWhitespace() }
            if (t.isEmpty()) continue
            if (cells.isEmpty() || w.left - previousRight > medianHeight * 1.2f) cells.add(StringBuilder(t))
            else cells.last().append(' ').append(t)
            previousRight = w.right
        }
        if (cells.isEmpty()) return null
        val texts = cells.map { it.toString() }

        val headerCells = texts.count { isHeaderCell(it) }
        if (headerCells >= 1 && headerCells * 2 >= texts.size) return null

        val idCells = texts.filter { isIdLike(it) }
        val serial: String
        val roll: String
        if (idCells.size >= 2 && idCells[0].replace(" ", "").let { it.length <= 3 && it.all(Char::isDigit) }) {
            serial = idCells[0].replace(" ", "")
            roll = idCells[1].replace(" ", "")
        } else {
            serial = ""
            roll = idCells.firstOrNull()?.replace(" ", "") ?: ""
        }

        val name = texts
            .filter { c -> c.count { it.isLetter() } >= 2 && c.none { it.isDigit() } && !isHeaderCell(c) }
            .maxByOrNull { c -> c.count { it.isLetter() } }
            ?: return null

        return RawRosterRow(serial = serial, roll = roll, name = name)
    }

    private fun isHeaderCell(cell: String): Boolean {
        val tokens = cell.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        return tokens.isNotEmpty() && tokens.all { it in HEADER_WORDS }
    }

    /** Looks like an ID or serial: digits dominate, only letters/digits/-// allowed ("B123001", "21CS045", "7"). */
    private fun isIdLike(cell: String): Boolean {
        val c = cell.replace(" ", "")
        if (c.isEmpty() || c.length > 20) return false
        if (!c.all { it.isLetterOrDigit() || it == '-' || it == '/' }) return false
        val digits = c.count { it.isDigit() }
        val letters = c.count { it.isLetter() }
        return digits >= 1 && digits >= letters
    }

    // ---------------------------------------------------------------------------------------------
    // Names
    // ---------------------------------------------------------------------------------------------

    /**
     * Light-touch clean-up. Names have no pattern, so this never "fixes" spelling.
     * Only title-cases when the whole name is ALL CAPS or all lowercase, and keeps initials, apostrophes and
     * hyphens ("K. Sahoo", "D'Souza"). The previous cleanName() lower-cased everything and produced "D'souza"/"A.k.".
     */
    fun normalizeName(raw: String): String {
        var s = raw.replace(Regex("[|_\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim(',', '-', '\'', ':', ';')
            .trim()
        if (s.any { it.isLetter() } && (s == s.uppercase() || s == s.lowercase())) s = titleCase(s)
        return s
    }

    private fun titleCase(s: String): String {
        val sb = StringBuilder(s.length)
        var startOfWord = true
        for (ch in s.lowercase()) {
            sb.append(if (startOfWord && ch.isLetter()) ch.uppercaseChar() else ch)
            startOfWord = ch == ' ' || ch == '-' || ch == '\'' || ch == '.'
        }
        return sb.toString()
    }
}
