package com.attract.attendance.data.importexport

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.attract.attendance.core.model.RosterStudent
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Same public API as before (parseFromUris / parseFromBitmaps), so OcrImportSheet needs no change.
 *
 * Pipeline per page:  Gemini (structured rows)  ->  on failure/offline: ML Kit words + row clustering
 * Then for the whole roster:  merge pages -> clean names -> RollNumberRepair -> RosterStudent.
 *
 * RosterStudent needs two new defaulted fields so the confirmation dialog can highlight rows:
 *     val needsReview: Boolean = false,
 *     val reviewNote: String = ""
 */
class OcrRosterParser(
    private val cloud: GeminiRosterExtractor? = GeminiRosterExtractor()
) {
    enum class Engine { NONE, CLOUD, ON_DEVICE, MIXED }

    /** Which engine produced the last result; show "Read with AI" / "Read offline" in the UI. */
    @Volatile
    var lastEngine: Engine = Engine.NONE
        private set

    @Volatile
    var lastCloudError: String? = null
        private set

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private class Run {
        var cloudUsable = true
        var usedCloud = false
        var usedDevice = false
    }

    suspend fun parseFromUris(context: Context, uris: List<Uri>): List<RosterStudent> =
        withContext(Dispatchers.IO) {
            val run = Run()
            val pages = ArrayList<List<RawRosterRow>>()
            for (uri in uris) {
                val bitmap = try {
                    loadUprightScaled(context, uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot load $uri", e)
                    continue
                }
                try {
                    pages.add(readPage(bitmap, run))
                } finally {
                    bitmap.recycle()
                }
            }
            finish(pages, run)
        }

    suspend fun parseFromBitmaps(bitmaps: List<Bitmap>): List<RosterStudent> =
        withContext(Dispatchers.IO) {
            val run = Run()
            val pages = bitmaps.map { readPage(it, run) }
            finish(pages, run)
        }

    // ---------------------------------------------------------------------------------------------

    private suspend fun readPage(bitmap: Bitmap, run: Run): List<RawRosterRow> {
        if (cloud != null && run.cloudUsable) {
            try {
                val rows = cloud.extract(bitmap)
                if (rows.isNotEmpty()) {
                    run.usedCloud = true
                    return rows
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorDetails = "${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "Cloud read failed, using on-device OCR fallback: $errorDetails", e)
                lastCloudError = errorDetails
                run.cloudUsable = false   // don't wait for a timeout on every remaining page
            }
        }
        run.usedDevice = true
        return try {
            rowsFromVisionText(recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitTask())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "On-device OCR failed", e)
            emptyList()
        }
    }

    internal fun rowsFromVisionText(text: Text): List<RawRosterRow> {
        val words = text.textBlocks
            .flatMap { block -> block.lines.flatMap { line -> line.elements } }
            .mapNotNull { e ->
                e.boundingBox?.let { OcrWord(e.text, it.left, it.top, it.right, it.bottom) }
            }
        return RosterGeometry.rowsFromWords(words)
    }

    private fun finish(pages: List<List<RawRosterRow>>, run: Run): List<RosterStudent> {
        lastEngine = when {
            run.usedCloud && run.usedDevice -> Engine.MIXED
            run.usedCloud -> Engine.CLOUD
            run.usedDevice -> Engine.ON_DEVICE
            else -> Engine.NONE
        }

        val merged = dropRowsRepeatedAcrossPages(pages.flatten()).map { row ->
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

    private companion object {
        const val TAG = "OcrRosterParser"
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { exception -> continuation.resumeWithException(exception) }
    addOnCanceledListener { continuation.cancel() }
}
