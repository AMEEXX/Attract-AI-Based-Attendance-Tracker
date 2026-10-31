package com.attract.attendance.data.importexport

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.attract.attendance.core.model.RosterStudent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parses class attendance sheets exclusively using Gemini Multimodal AI OCR.
 *
 * Pipeline per page:  Gemini (structured rows)
 * Then for the whole roster:  merge pages -> clean names -> RollNumberRepair -> RosterStudent.
 */
class OcrRosterParser(
    private val cloud: GeminiRosterExtractor = GeminiRosterExtractor()
) {
    enum class Engine { NONE, CLOUD }

    /** Which engine produced the last result; show "Read with AI" in the UI. */
    @Volatile
    var lastEngine: Engine = Engine.NONE
        private set

    @Volatile
    var lastCloudError: String? = null
        private set

    suspend fun parseFromUris(context: Context, uris: List<Uri>): List<RosterStudent> =
        withContext(Dispatchers.IO) {
            val customKey = context.getSharedPreferences("attract_ai_prefs", Context.MODE_PRIVATE)
                .getString("custom_gemini_api_key", null)
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val pages = ArrayList<List<RawRosterRow>>()
            for (uri in uris) {
                val bitmap = try {
                    loadUprightScaled(context, uri)
                } catch (e: Exception) {
                    continue
                }
                try {
                    pages.add(readPage(bitmap, customKey))
                } finally {
                    bitmap.recycle()
                }
            }
            finish(pages)
        }

    suspend fun parseFromBitmaps(bitmaps: List<Bitmap>, customKey: String? = null): List<RosterStudent> =
        withContext(Dispatchers.IO) {
            val pages = bitmaps.map { readPage(it, customKey) }
            finish(pages)
        }

    // ---------------------------------------------------------------------------------------------

    private suspend fun readPage(bitmap: Bitmap, customKey: String?): List<RawRosterRow> {
        try {
            val rows = cloud.extract(bitmap, customKey)
            if (rows.isNotEmpty()) {
                lastEngine = Engine.CLOUD
                return rows
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorDetails = "${e.javaClass.simpleName}: ${e.message}"
            lastCloudError = errorDetails
            throw e
        }
        return emptyList()
    }

    private fun finish(pages: List<List<RawRosterRow>>): List<RosterStudent> {
        val allRows = pages.flatten()
        if (allRows.isNotEmpty()) {
            lastEngine = Engine.CLOUD
        }

        val merged = dropRowsRepeatedAcrossPages(allRows).map { row ->
            val name = RosterGeometry.normalizeName(row.name)
            row.copy(
                name = name,
                // blank names and names with digits in them are OCR noise: make the teacher look
                nameUncertain = row.nameUncertain || name.isBlank() || name.any { it.isDigit() }
            )
        }

        return RollNumberRepair.repair(merged).mapIndexed { i, r ->
            RosterStudent(
                sourceRow = i + 1,
                name = r.name,
                rollNumber = r.roll,
                serialNumber = r.serial.ifBlank { (i + 1).toString() },
                needsReview = r.needsReview,
                reviewNote = buildReviewNote(r)
            )
        }
    }

    private fun buildReviewNote(r: RepairedRow): String {
        val parts = ArrayList<String>(2)
        if (r.nameUncertain) parts += "Check the name"
        if (r.note.isNotBlank() && r.status != RollStatus.OK) parts += r.note
        return parts.joinToString(". ")
    }

    /** Same student photographed twice (overlap between pages): same roll AND same name. Anything else is kept. */
    private fun dropRowsRepeatedAcrossPages(rows: List<RawRosterRow>): List<RawRosterRow> {
        val seen = HashSet<String>()
        return rows.filter { r ->
            val roll = r.roll.uppercase().filter { it.isLetterOrDigit() }
            val name = r.name.lowercase().filter { it.isLetter() }
            if (roll.isEmpty() || name.isEmpty()) true else seen.add("$roll|$name")
        }
    }
}
