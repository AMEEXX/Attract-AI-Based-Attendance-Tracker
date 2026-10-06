package com.attract.attendance.data.importexport

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.attract.attendance.core.model.RosterStudent
import com.attract.attendance.domain.Validators
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrRosterParser {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val headerBlacklist = setOf(
        "attendance", "sheet", "roster", "roll", "rollno", "rollnumber",
        "name", "studentname", "student", "signature", "present", "absent",
        "date", "time", "class", "section", "semester", "subject", "faculty",
        "teacher", "department", "college", "university", "school", "serial",
        "slno", "sno", "remarks", "total", "percentage", "batch", "page"
    )

    suspend fun parseFromUris(context: Context, uris: List<Uri>): List<RosterStudent> = withContext(Dispatchers.IO) {
        val extractedStudents = mutableListOf<RosterStudent>()
        val seenRolls = mutableSetOf<String>()
        var globalRowIndex = 1

        for (uri in uris) {
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                val visionText = recognizer.process(inputImage).awaitTask()
                val pageStudents = parseVisionText(visionText, globalRowIndex)
                for (student in pageStudents) {
                    val normalizedRoll = Validators.normalizeRollNumber(student.rollNumber)
                    if (seenRolls.add(normalizedRoll)) {
                        extractedStudents.add(student)
                        globalRowIndex++
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("OcrRosterParser", "Failed to process image $uri: ${e.message}", e)
            }
        }

        extractedStudents
    }

    suspend fun parseFromBitmaps(bitmaps: List<Bitmap>): List<RosterStudent> = withContext(Dispatchers.IO) {
        val extractedStudents = mutableListOf<RosterStudent>()
        val seenRolls = mutableSetOf<String>()
        var globalRowIndex = 1

        for (bitmap in bitmaps) {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = recognizer.process(inputImage).awaitTask()
                val pageStudents = parseVisionText(visionText, globalRowIndex)
                for (student in pageStudents) {
                    val normalizedRoll = Validators.normalizeRollNumber(student.rollNumber)
                    if (seenRolls.add(normalizedRoll)) {
                        extractedStudents.add(student)
                        globalRowIndex++
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("OcrRosterParser", "Failed to process bitmap: ${e.message}", e)
            }
        }

        extractedStudents
    }

    internal fun parseVisionText(visionText: Text, startRowIndex: Int): List<RosterStudent> {
        val results = mutableListOf<RosterStudent>()
        var currentRow = startRowIndex

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                if (lineText.isBlank() || isHeaderLine(lineText)) continue

                val candidate = extractStudentFromLine(lineText, currentRow)
                if (candidate != null) {
                    results.add(candidate)
                    currentRow++
                }
            }
        }

        return results
    }

    private fun isHeaderLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ROOT)
        val cleanWords = lower.split(Regex("[\\s,;:.|\\-_/\\\\]+")).filter { it.isNotBlank() }
        if (cleanWords.isEmpty()) return true

        // If the majority of words are blacklist header keywords
        val matchCount = cleanWords.count { word -> headerBlacklist.contains(word) }
        return matchCount >= 2 || (cleanWords.size <= 3 && matchCount >= 1)
    }

    internal fun extractStudentFromLine(line: String, rowIndex: Int): RosterStudent? {
        val cleaned = line.replace("|", " ").replace(";", " ").trim()
        if (isHeaderLine(cleaned)) return null

        // 1. Remove explicit serial prefixes like "1.", "1)", "01.", "1 - ", "#1"
        val withoutSerial = cleaned.replaceFirst(Regex("""^\s*(?:#\s*\d{1,3}\s*|\d{1,3}\s*[\.\)\-:]\s*)"""), "").trim()

        // 2. Pattern A: Roll Number at start -> Name at end
        // e.g. "21CS001 Alex Sharma" or "101 Bhavna Patel" or "IT-2024-042 Drishya Nair"
        val rollFirstRegex = Regex("""^([A-Za-z0-9\-_/]{1,25})\s+([A-Za-z\s\.\']{2,60})$""")
        val matchRollFirst = rollFirstRegex.find(withoutSerial)
        if (matchRollFirst != null) {
            val (roll, name) = matchRollFirst.destructured
            val rollClean = roll.trim().uppercase(Locale.ROOT)
            val nameClean = cleanName(name)
            if (isValidStudentPair(nameClean, rollClean) && looksLikeRoll(rollClean) && looksLikeName(nameClean)) {
                return RosterStudent(rowIndex, nameClean, rollClean, rowIndex.toString())
            }
        }

        // 3. Pattern B: Name at start -> Roll at end
        // e.g. "Chirag Verma 21CS003" or "Aditi Rao 102"
        val nameFirstRegex = Regex("""^([A-Za-z\s\.\']{2,60})\s+([A-Za-z0-9\-_/]{1,25})$""")
        val matchNameFirst = nameFirstRegex.find(withoutSerial)
        if (matchNameFirst != null) {
            val (name, roll) = matchNameFirst.destructured
            val rollClean = roll.trim().uppercase(Locale.ROOT)
            val nameClean = cleanName(name)
            if (isValidStudentPair(nameClean, rollClean) && looksLikeRoll(rollClean) && looksLikeName(nameClean)) {
                return RosterStudent(rowIndex, nameClean, rollClean, rowIndex.toString())
            }
        }

        // 4. Pattern C: 3 parts space-separated where first part was a numeric serial without punctuation
        // e.g. "1 21CS001 Alex Sharma"
        val threePartsRegex = Regex("""^\d{1,3}\s+([A-Za-z0-9\-_/]{1,25})\s+([A-Za-z\s\.\']{2,60})$""")
        val matchThree = threePartsRegex.find(cleaned)
        if (matchThree != null) {
            val (roll, name) = matchThree.destructured
            val rollClean = roll.trim().uppercase(Locale.ROOT)
            val nameClean = cleanName(name)
            if (isValidStudentPair(nameClean, rollClean) && looksLikeRoll(rollClean) && looksLikeName(nameClean)) {
                return RosterStudent(rowIndex, nameClean, rollClean, rowIndex.toString())
            }
        }

        // 5. Fallback: Split by 2+ spaces (columnar format)
        val columnTokens = withoutSerial.split(Regex("\\s{2,}"))
        if (columnTokens.size >= 2) {
            val token0 = columnTokens[0].trim()
            val token1 = columnTokens[1].trim()
            if (looksLikeRoll(token0) && looksLikeName(token1)) {
                val nameClean = cleanName(token1)
                val rollClean = token0.uppercase(Locale.ROOT)
                if (isValidStudentPair(nameClean, rollClean)) {
                    return RosterStudent(rowIndex, nameClean, rollClean, rowIndex.toString())
                }
            } else if (looksLikeRoll(token1) && looksLikeName(token0)) {
                val nameClean = cleanName(token0)
                val rollClean = token1.uppercase(Locale.ROOT)
                if (isValidStudentPair(nameClean, rollClean)) {
                    return RosterStudent(rowIndex, nameClean, rollClean, rowIndex.toString())
                }
            }
        }

        return null
    }

    private fun looksLikeRoll(token: String): Boolean {
        return token.length in 1..25 && (token.any { it.isDigit() } || (token.length <= 6 && token.all { it.isLetterOrDigit() }))
    }

    private fun looksLikeName(token: String): Boolean {
        val letterCount = token.count { it.isLetter() }
        val digitCount = token.count { it.isDigit() }
        return letterCount >= 2 && digitCount == 0
    }

    private fun cleanName(name: String): String {
        return name.trim()
            .replace(Regex("\\s+"), " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }

    private fun isValidStudentPair(name: String, roll: String): Boolean {
        if (name.length < 2 || roll.length < 1) return false
        val lowerName = name.lowercase(Locale.ROOT)
        if (headerBlacklist.any { lowerName.contains(it) }) return false
        // Name should contain mostly letters
        val letterCount = name.count { it.isLetter() }
        if (letterCount < 2 || letterCount.toFloat() / name.length < 0.6f) return false
        return true
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }
