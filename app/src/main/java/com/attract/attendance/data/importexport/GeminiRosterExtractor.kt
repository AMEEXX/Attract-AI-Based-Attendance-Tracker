package com.attract.attendance.data.importexport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import org.json.JSONObject

/**
 * Reads one page image with a multimodal model and returns raw rows (no repair applied yet).
 *
 * Why a vision model instead of ML Kit text recognition: it understands the table layout, copes with
 * handwriting, skew and shadows, and can tell a serial-number column from a roll-number column by meaning.
 * The prompt tells it to mark unreadable characters with '?' instead of guessing, so that
 * [RollNumberRepair] (deterministic, testable) does the pattern-based reconstruction.
 *
 * Setup: Firebase project + google-services.json, Gemini Developer API enabled under Firebase AI Logic,
 * App Check on. No API key is shipped in the APK.
 */
class GeminiRosterExtractor(private val modelName: String = DEFAULT_MODEL) {

    private val schema = Schema.obj(
        mapOf(
            "rows" to Schema.array(
                Schema.obj(
                    mapOf(
                        "serial" to Schema.string(),
                        "roll" to Schema.string(),
                        "name" to Schema.string(),
                        "nameConfidence" to Schema.enumeration(listOf("high", "low"))
                    )
                )
            )
        )
    )

    private fun getModel(targetModel: String) =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = targetModel,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                temperature = 0f
            },
            systemInstruction = content { text(SYSTEM_PROMPT) }
        )

    suspend fun extract(page: Bitmap): List<RawRosterRow> {
        val modelsToTry = (listOf(modelName) + FALLBACK_MODELS).distinct()
        var lastError: Exception? = null

        for (targetModel in modelsToTry) {
            try {
                val currentModel = getModel(targetModel)
                val response = currentModel.generateContent(
                    content {
                        image(page)
                        text("Extract every student row from this attendance sheet.")
                    }
                )
                val rows = parse(response.text.orEmpty())
                if (rows.isNotEmpty()) {
                    return rows
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("GeminiRosterExtractor", "Model '$targetModel' attempt failed: ${e.message}")
            }
        }
        if (lastError != null) throw lastError
        return emptyList()
    }

    internal fun parse(json: String): List<RawRosterRow> {
        if (json.isBlank()) return emptyList()
        val rows = JSONObject(json).optJSONArray("rows") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            val roll = o.optString("roll").trim()
            val name = o.optString("name").trim()
            if (roll.isEmpty() && name.isEmpty()) return@mapNotNull null
            RawRosterRow(
                serial = o.optString("serial").trim(),
                roll = roll,
                name = name,
                nameUncertain = o.optString("nameConfidence") == "low"
            )
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        val FALLBACK_MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")

        private val SYSTEM_PROMPT = """
            You read photographed class attendance sheets and rosters. Return one entry per student row, top to
            bottom, in exactly the order they appear on the sheet.

            For each row return:
            - serial: the value from the serial / S.No / Sl.No column if the sheet has one, otherwise "".
            - roll: the student's roll number / registration number / ID, copied character by character as printed
              or written. IDs are often alphanumeric (for example B123001): keep every letter and digit. Never put
              the serial number here.
            - name: the student's full name as written. Do not translate, shorten, reorder or "correct" it. Keep
              initials and punctuation (for example "K. Sahoo", "D'Souza"). Names follow no pattern, so read every
              letter carefully, including the ends of long names.
            - nameConfidence: "low" if any letter of the name is hard to read or you chose between plausible
              readings; otherwise "high".

            Rules:
            - If one character of a roll number is smudged, cut off or unreadable, write "?" in its place
              (for example "B1?3001"). If the whole roll number is unreadable, return "". Do NOT guess characters
              and do NOT continue a numbering pattern yourself; a later step does that safely.
            - Include every student row, even when the name or roll number is partly unreadable. Never skip, merge
              or invent rows.
            - Ignore titles, headers, dates, totals, stamps, ticks/signature columns and margin notes.
            - If the page is rotated or skewed, read it as a person would after turning it upright.
        """.trimIndent()
    }
}

/**
 * Decodes [uri] upright (EXIF applied) and no larger than [maxSide] pixels on the long edge.
 * ~2400px keeps small handwriting legible without sending a 12 MP photo.
 */
internal fun loadUprightScaled(context: Context, uri: Uri, maxSide: Int = 2400): Bitmap {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2

    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Cannot read image $uri")

    val rotation = resolver.openInputStream(uri)?.use { stream ->
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f

    val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
    if (rotation == 0f && scale == 1f) return decoded

    val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
    val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (out !== decoded) decoded.recycle()
    return out
}
