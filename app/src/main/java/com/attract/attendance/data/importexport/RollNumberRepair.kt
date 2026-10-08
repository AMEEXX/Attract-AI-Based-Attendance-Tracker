package com.attract.attendance.data.importexport

/** One row exactly as the reader saw it. A '?' inside [roll] marks one illegible character. */
data class RawRosterRow(
    val serial: String = "",
    val roll: String,
    val name: String,
    val nameUncertain: Boolean = false
)

/**
 * OK       – matches the sheet's own ID pattern, untouched.
 * FIXED    – corrected using only the pattern (e.g. 'O' read for '0', missing leading 'B' restored).
 * INFERRED – value derived from the rows above and below. Always shown to the teacher for review.
 * SUSPECT  – does not fit; left as read, with a note.
 * MISSING  – nothing readable.
 */
enum class RollStatus { OK, FIXED, INFERRED, SUSPECT, MISSING }

data class RepairedRow(
    val serial: String,
    val roll: String,
    val name: String,
    val status: RollStatus,
    val nameUncertain: Boolean,
    val note: String = ""
) {
    /** True when a human should look at this row before it is saved. */
    val needsReview: Boolean
        get() = nameUncertain ||
            status == RollStatus.INFERRED ||
            status == RollStatus.SUSPECT ||
            status == RollStatus.MISSING
}

/**
 * Turns noisy roll numbers into clean ones by learning the format from the sheet itself.
 *
 * Example: if most rolls look like B123001, B123002 … the learner knows "B1230" is a fixed prefix and the
 * last two characters are the running number. Then:
 *   "123006"  -> "B123006"   (leading character lost)
 *   "B1230O8" -> "B123008"   (letter O where a digit must be)
 *   "B12?005" -> "B123005"   (illegible character inside the fixed prefix)
 *   ""        -> "B123004"   (only if the rows above and below are 003 and 005)
 *
 * It deliberately never invents characters in the running-number part from the pattern alone; that part is
 * only filled when the neighbouring rows pin it down exactly, and such rows are flagged INFERRED.
 */
object RollNumberRepair {

    private const val MIN_ROWS_TO_LEARN = 4
    private const val CONSTANT_SHARE = 0.8

    // Characters OCR commonly swaps. Only applied where the sheet's own pattern says which kind is expected.
    private val TO_DIGIT = mapOf(
        'O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'L' to '1',
        'Z' to '2', 'S' to '5', 'G' to '6', 'B' to '8'
    )
    private val TO_LETTER = mapOf(
        '0' to 'O', '1' to 'I', '2' to 'Z', '5' to 'S', '6' to 'G', '8' to 'B'
    )

    fun repair(rows: List<RawRosterRow>): List<RepairedRow> {
        val rolls = rows.map { clean(it.roll) }
        val template = Template.learn(rolls)
        val out = rows.mapIndexed { i, row -> classify(row, rolls[i], template) }.toMutableList()
        fillFromSequence(out)
        flagDuplicates(out)
        return out
    }

    /** Human-readable pattern such as "B1230##" (# = digit, @ = letter), or null if no pattern could be learned. */
    fun describePattern(rolls: List<String>): String? =
        Template.learn(rolls.map { clean(it) })?.describe()

    // ---------------------------------------------------------------------------------------------
    // Step 1: per-row repair against the learned pattern
    // ---------------------------------------------------------------------------------------------

    private fun classify(row: RawRosterRow, roll: String, t: Template?): RepairedRow {
        fun make(value: String, status: RollStatus, note: String = "") =
            RepairedRow(row.serial, value, row.name, status, row.nameUncertain, note)

        if (roll.isEmpty()) return make("", RollStatus.MISSING, "Roll number not readable")

        if (t == null) {
            return if ('?' in roll) make(roll, RollStatus.SUSPECT, "Some characters unreadable")
            else make(roll, RollStatus.OK)
        }

        if (roll.length == t.length) {
            val fixed = t.fixAll(roll)
                ?: return make(roll, RollStatus.SUSPECT, "Does not fit the sheet's ID pattern (${t.describe()})")
            return when {
                '?' in fixed -> make(fixed, RollStatus.SUSPECT, "Some characters unreadable")
                fixed != roll -> make(fixed, RollStatus.FIXED, "Read as $roll; corrected to fit the ID pattern")
                else -> make(roll, RollStatus.OK)
            }
        }

        if (roll.length < t.length) {
            t.restoreLeading(roll)?.let {
                return make(it, RollStatus.FIXED, "Restored missing leading characters of $roll")
            }
        } else if (roll.length == t.length + 1) {
            t.dropStrayCharacter(roll)?.let {
                return make(it, RollStatus.FIXED, "Removed a stray character from $roll")
            }
        }
        return make(roll, RollStatus.SUSPECT, "Length differs from the other roll numbers (${t.describe()})")
    }

    private class Template(
        val length: Int,
        val modal: CharArray,        // most common character at each position
        val constant: BooleanArray,  // true for the leading run of positions that are (almost) always identical
        val kind: CharArray          // 'D' digit, 'L' letter, 'O' fixed other char (- or /), 'X' mixed
    ) {
        fun fix(c: Char, pos: Int): Char? {
            if (c == '?') return if (constant[pos]) modal[pos] else '?'
            if (constant[pos]) {
                return if (c == modal[pos] || looksLike(c, modal[pos])) modal[pos] else null
            }
            return when (kind[pos]) {
                'D' -> if (c.isDigit()) c else TO_DIGIT[c]
                'L' -> if (c.isLetter()) c else TO_LETTER[c]
                'O' -> if (c == modal[pos]) c else null
                else -> c
            }
        }

        fun fixAll(roll: String): String? {
            val sb = StringBuilder(roll.length)
            for (i in roll.indices) sb.append(fix(roll[i], i) ?: return null)
            return sb.toString()
        }

        /** "123006" -> "B123006" when the missing leading characters are part of the fixed prefix. */
        fun restoreLeading(roll: String): String? {
            val missing = length - roll.length
            if (missing !in 1..3 || !constant[missing - 1]) return null
            return fixAll(String(modal, 0, missing) + roll)?.takeIf { '?' !in it }
        }

        /** One extra character: accept only if removing it gives exactly one valid result. */
        fun dropStrayCharacter(roll: String): String? {
            val candidates = roll.indices
                .mapNotNull { j -> fixAll(roll.removeRange(j, j + 1)) }
                .filter { '?' !in it }
                .toSet()
            return candidates.singleOrNull()
        }

        fun describe(): String = buildString {
            for (i in 0 until this@Template.length) {
                append(
                    when {
                        constant[i] -> modal[i]
                        kind[i] == 'D' -> '#'
                        kind[i] == 'L' -> '@'
                        kind[i] == 'O' -> modal[i]
                        else -> '*'
                    }
                )
            }
        }

        private fun looksLike(c: Char, target: Char): Boolean = when {
            c == target -> true
            target.isLetter() && c.isDigit() -> TO_DIGIT[target] == c
            target.isDigit() && c.isLetter() -> TO_DIGIT[c] == target
            else -> false
        }

        companion object {
            fun learn(rolls: List<String>): Template? {
                val usable = rolls.filter { it.isNotEmpty() && '?' !in it }
                if (usable.size < MIN_ROWS_TO_LEARN) return null

                val length = usable.groupingBy { it.length }.eachCount().maxByOrNull { it.value }!!.key
                val same = usable.filter { it.length == length }
                if (same.size < MIN_ROWS_TO_LEARN || same.size * 2 < usable.size) return null

                // Short all-digit values (1..60) are serial numbers; there is no structure to learn from them.
                if (length <= 3 && same.all { r -> r.all { it.isDigit() } }) return null

                val modal = CharArray(length)
                val constant = BooleanArray(length)
                val kind = CharArray(length)
                var leadingRun = true
                for (i in 0 until length) {
                    val top = same.groupingBy { it[i] }.eachCount().maxByOrNull { it.value }!!
                    modal[i] = top.key
                    val digits = same.count { it[i].isDigit() }
                    val letters = same.count { it[i].isLetter() }
                    val limit = same.size * CONSTANT_SHARE
                    kind[i] = when {
                        digits >= limit -> 'D'
                        letters >= limit -> 'L'
                        top.value >= limit -> 'O'
                        else -> 'X'
                    }
                    // Only a *leading* run of identical characters counts as the fixed prefix (batch/year/branch
                    // code). The last two positions are the running number and are never treated as fixed,
                    // otherwise a small roster (001-009) would "learn" that the tens digit is always 0.
                    leadingRun = leadingRun && i < length - 2 && top.value >= limit
                    constant[i] = leadingRun
                }
                return Template(length, modal, constant, kind)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Step 2: use the rows above and below
    // ---------------------------------------------------------------------------------------------

    private class Parsed(val prefix: String, val number: Long, val width: Int)

    private fun parse(roll: String): Parsed? {
        if (roll.isEmpty() || '?' in roll) return null
        val tail = roll.takeLastWhile { it.isDigit() }
        if (tail.isEmpty() || tail.length > 9) return null
        return Parsed(roll.dropLast(tail.length), tail.toLong(), tail.length)
    }

    private fun format(prefix: String, number: Long, width: Int): String? =
        if (number < 0) null else prefix + number.toString().padStart(width, '0')

    private fun fillFromSequence(rows: MutableList<RepairedRow>) {
        val anchors = rows.indices.filter { i ->
            (rows[i].status == RollStatus.OK || rows[i].status == RollStatus.FIXED) && parse(rows[i].roll) != null
        }
        if (anchors.size < MIN_ROWS_TO_LEARN) return
        val parsed = anchors.associateWith { parse(rows[it].roll)!! }

        // Is this sheet in running order? Look at consecutive readable rows only.
        val diffs = anchors.zipWithNext()
            .filter { (a, b) -> b == a + 1 && parsed[a]!!.prefix == parsed[b]!!.prefix }
            .map { (a, b) -> parsed[b]!!.number - parsed[a]!!.number }
        if (diffs.size < 3) return
        val top = diffs.groupingBy { it }.eachCount().maxByOrNull { it.value }!!
        val step = top.key
        if (step <= 0L || top.value < diffs.size * 0.6) return

        for (i in rows.indices) {
            val row = rows[i]
            if (row.status != RollStatus.SUSPECT && row.status != RollStatus.MISSING) continue

            val p = anchors.lastOrNull { it < i }
            val n = anchors.firstOrNull { it > i }
            val before = p?.let { parsed[it] }
            val after = n?.let { parsed[it] }

            if (p != null && n != null && before != null && after != null &&
                before.prefix == after.prefix && before.width == after.width &&
                after.number - before.number == step * (n - p)   // no gaps between the two anchors
            ) {
                val expected = format(before.prefix, before.number + step * (i - p), before.width) ?: continue
                val read = row.roll
                when {
                    read.isEmpty() || matchesWithWildcards(read, expected) ->
                        rows[i] = row.copy(
                            roll = expected,
                            status = RollStatus.INFERRED,
                            note = "Filled in from the rows above and below (${rows[p].roll} … ${rows[n].roll})"
                        )
                    read.length == expected.length && hamming(read, expected) <= 1 ->
                        rows[i] = row.copy(
                            roll = expected,
                            status = RollStatus.INFERRED,
                            note = "Read as $read; changed to $expected to fit the sequence"
                        )
                    else ->
                        rows[i] = row.copy(note = withSentence(row.note, "Sequence suggests $expected"))
                }
            } else if (row.roll.isEmpty() || '?' in row.roll) {
                // Only one neighbour, or the numbering has gaps: suggest, never auto-fill.
                val guess = when {
                    p != null && before != null -> format(before.prefix, before.number + step * (i - p), before.width)
                    n != null && after != null -> format(after.prefix, after.number - step * (n - i), after.width)
                    else -> null
                }
                if (guess != null) {
                    rows[i] = row.copy(note = withSentence(row.note, "Possibly $guess (from the neighbouring row only)"))
                }
            }
        }
    }

    private fun withSentence(existing: String, extra: String) =
        if (existing.isBlank()) extra else "$existing. $extra"

    private fun matchesWithWildcards(read: String, expected: String): Boolean =
        read.length == expected.length && read.indices.all { read[it] == '?' || read[it] == expected[it] }

    private fun hamming(a: String, b: String): Int = a.indices.count { a[it] != b[it] }

    // ---------------------------------------------------------------------------------------------
    // Step 3: never silently drop a student because two rolls collide
    // ---------------------------------------------------------------------------------------------

    private fun flagDuplicates(rows: MutableList<RepairedRow>) {
        val groups = rows.indices
            .filter { rows[it].roll.isNotEmpty() && '?' !in rows[it].roll }
            .groupBy { rows[it].roll }
        for ((roll, indices) in groups) {
            if (indices.size < 2) continue
            for (i in indices) {
                rows[i] = rows[i].copy(status = RollStatus.SUSPECT, note = "Duplicate roll number $roll")
            }
        }
    }

    private fun clean(raw: String): String = raw.uppercase().replace(Regex("[^A-Z0-9?/\\-]"), "")
}
