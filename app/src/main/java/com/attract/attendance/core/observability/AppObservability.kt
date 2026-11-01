package com.attract.attendance.core.observability

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * PII-safe observability and crash reporting wrapper (PR-09).
 *
 * Strict Privacy Guarantee:
 * - Breadcrumbs contain ONLY state machine names and non-PII identifiers (classId, sessionId, attemptId).
 * - NEVER logs student names, roll numbers, teacher names, PINs, face embeddings, or secrets.
 * - All calls are safe no-ops if Firebase is not available or throws.
 */
object AppObservability {

    private const val TAG = "AppObservability"

    // Allowlist of safe parameter keys for breadcrumbs and custom attributes
    private val ALLOWED_KEYS = setOf(
        "screen_name",
        "session_state",
        "app_version",
        "class_id",
        "session_id",
        "attempt_id",
        "origin_tab",
        "item_count",
        "error_type"
    )

    // Denylist patterns: if a key contains any of these substrings, it is dropped
    private val PII_KEY_PATTERNS = listOf(
        "name", "student", "roll", "serial", "teacher", "pin", "hash",
        "embedding", "secret", "password", "token", "key", "email", "credential"
    )

    fun initialize(appVersion: String) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("app_version", appVersion)
        } catch (t: Throwable) {
            Log.w(TAG, "Crashlytics initialization skipped: ${t.message}")
        }
    }

    /**
     * Records a screen navigation breadcrumb with PII-safe metadata.
     */
    fun logScreenTransition(screenName: String, metadata: Map<String, Any> = emptyMap()) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("screen_name", screenName)
            val scrubbedMeta = scrubMetadata(metadata)
            val logMessage = if (scrubbedMeta.isEmpty()) {
                "Nav: $screenName"
            } else {
                "Nav: $screenName $scrubbedMeta"
            }
            crashlytics.log(logMessage)
        } catch (_: Throwable) {
            // Safe fallback: never crash observability callers
        }
    }

    /**
     * Records an attendance session state transition breadcrumb.
     */
    fun logSessionState(stateName: String, metadata: Map<String, Any> = emptyMap()) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("session_state", stateName)
            val scrubbedMeta = scrubMetadata(metadata)
            val logMessage = if (scrubbedMeta.isEmpty()) {
                "SessionState: $stateName"
            } else {
                "SessionState: $stateName $scrubbedMeta"
            }
            crashlytics.log(logMessage)
        } catch (_: Throwable) {
            // Safe fallback
        }
    }

    /**
     * Records a non-fatal exception with optional non-PII context.
     */
    fun recordNonFatal(throwable: Throwable, context: String? = null) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            context?.let { crashlytics.log("NonFatalContext: $it") }
            crashlytics.recordException(throwable)
        } catch (_: Throwable) {
            // Safe fallback
        }
    }

    private fun scrubMetadata(metadata: Map<String, Any>): Map<String, Any> {
        if (metadata.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Any>()
        for ((key, value) in metadata) {
            val lowerKey = key.lowercase()
            val isPii = PII_KEY_PATTERNS.any { lowerKey.contains(it) }
            if (isPii) continue
            if (lowerKey in ALLOWED_KEYS || lowerKey.endsWith("_id") || lowerKey.endsWith("_count")) {
                result[lowerKey] = value
            }
        }
        return result
    }
}
