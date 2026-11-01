package com.attract.attendance.data.importexport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads one page image with a Gemini multimodal model and returns raw rows (no repair applied yet).
 *
 * Why a vision model: it understands the table layout, copes with handwriting, skew and shadows,
 * and can tell a serial-number column from a roll-number column by meaning.
 * The prompt tells it to mark unreadable characters with '?' instead of guessing, so that
 * [RollNumberRepair] (deterministic, testable) does the pattern-based reconstruction.
 *
 * Setup: Firebase project + google-services.json, Gemini Developer API enabled under Firebase AI Logic,
 * App Check on. Can also use a custom Gemini API key directly.
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

    suspend fun extract(page: Bitmap, customApiKey: String? = null): List<RawRosterRow> {
        val modelsToTry = (listOf(modelName) + FALLBACK_MODELS).distinct()
        var lastError: Exception? = null

        val effectiveApiKey = customApiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: com.attract.attendance.BuildConfig.GEMINI_API_KEY.trim().takeIf { it.isNotBlank() }

        // 1. If an effective Gemini API key is available (custom or embedded via local.properties), use direct REST API
        if (!effectiveApiKey.isNullOrBlank()) {
            var successfulCall = false
            for (targetModel in modelsToTry) {
                try {
                    val rows = extractViaRest(page, effectiveApiKey, targetModel)
                    successfulCall = true
                    if (rows.isNotEmpty()) {
                        return rows
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (!successfulCall && lastError != null) throw lastError
            return emptyList()
        }

        // 2. Otherwise use Firebase AI with GenerativeBackend.googleAI()
        var successfulCall = false
        for (targetModel in modelsToTry) {
            try {
                val currentModel = getModel(targetModel)
                val response = currentModel.generateContent(
                    content {
                        image(page)
                        text("Extract every student row from this attendance sheet into JSON with key \"rows\".")
                    }
                )
                successfulCall = true
                val rows = parse(response.text.orEmpty())
                if (rows.isNotEmpty()) {
                    return rows
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                val friendlyException = if (msg.contains("API_KEY_SERVICE_BLOCKED") || msg.contains("PERMISSION_DENIED") || msg.contains("blocked")) {
                    IllegalStateException(
                        "Google Cloud blocked Generative Language API on this key (API_KEY_SERVICE_BLOCKED). " +
                        "Enable 'Generative Language API' in Google Cloud Console or tap 'Set Gemini API Key' to use a free Gemini API key.",
                        e
                    )
                } else {
                    e
                }
                lastError = friendlyException
            }
        }
        if (!successfulCall && lastError != null) throw lastError
        return emptyList()
    }

    private suspend fun extractViaRest(page: Bitmap, apiKey: String, targetModel: String): List<RawRosterRow> =
        withContext(Dispatchers.IO) {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$targetModel:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 90_000 // 90 seconds per model attempt
                setRequestProperty("Content-Type", "application/json")
            }

            val bos = ByteArrayOutputStream()
            page.compress(Bitmap.CompressFormat.JPEG, 90, bos)
            val base64Image = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)

            val requestJson = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().apply {
                        put(JSONObject().put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Image)
                        }))
                        put(JSONObject().put("text", "Extract every student row from this attendance sheet into JSON with key \"rows\"."))
                    })
                ))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0)
                })
            }

            conn.outputStream.use { os ->
                os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errText = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Gemini API error ($responseCode): $errText")
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")

            val sb = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.optBoolean("thought", false)) continue
                    val textPart = part.optString("text")
                    if (!textPart.isNullOrEmpty()) {
                        sb.append(textPart)
                    }
                }
            }
            parse(sb.toString())
        }

    internal fun parse(json: String): List<RawRosterRow> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Strip markdown code fences if present (e.g. ```json ... ```)
        val sanitized = trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        // If the response is a direct JSON array of student rows
        if (sanitized.startsWith("[")) {
            val arr = try {
                JSONArray(sanitized)
            } catch (_: Exception) {
                val start = sanitized.indexOf('[')
                val end = sanitized.lastIndexOf(']')
                if (start != -1 && end > start) {
                    try { JSONArray(sanitized.substring(start, end + 1)) } catch (_: Exception) { null }
                } else null
            }
            if (arr != null) {
                return parseRowArray(arr)
            }
        }

        // Otherwise find root JSON object between outermost '{' and '}'
        val obj = try {
            JSONObject(sanitized)
        } catch (_: Exception) {
            val start = sanitized.indexOf('{')
            val end = sanitized.lastIndexOf('}')
            if (start != -1 && end > start) {
                try { JSONObject(sanitized.substring(start, end + 1)) } catch (_: Exception) { null }
            } else null
        } ?: return emptyList()

        // Check standard "rows" key, then common alternatives ("students", "roster", or first array)
        val rows = obj.optJSONArray("rows")
            ?: obj.optJSONArray("students")
            ?: obj.optJSONArray("roster")
            ?: run {
                val keys = obj.keys()
                var foundArr: JSONArray? = null
                while (keys.hasNext()) {
                    val key = keys.next()
                    val candidateArr = obj.optJSONArray(key)
                    if (candidateArr != null) {
                        foundArr = candidateArr
                        break
                    }
                }
                foundArr
            } ?: return emptyList()

        return parseRowArray(rows)
    }

    private fun parseRowArray(rows: JSONArray): List<RawRosterRow> {
        return (0 until rows.length()).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            val roll = o.optString("roll").trim()
            val name = o.optString("name").trim()
            if (roll.isEmpty() && name.isEmpty()) return@mapNotNull null
            val conf = o.optString("nameConfidence", "").lowercase()
            RawRosterRow(
                serial = o.optString("serial").trim(),
                roll = roll,
                name = name,
                nameUncertain = conf == "low" || conf == "0" || conf == "false"
            )
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        val FALLBACK_MODELS = listOf("gemini-3.5-flash", "gemini-3.8-flash", "gemini-3.1-flash-lite")

        private val SYSTEM_PROMPT = """
            You read photographed class attendance sheets and rosters. Return a JSON object with a "rows" array
            containing one entry per student row, top to bottom, in exactly the order they appear on the sheet.

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
