package com.attract.attendance.util

import android.util.Log
import com.attract.attendance.BuildConfig

/**
 * AppLog — Centralized application logging facade (PR-02).
 *
 * - In Debug builds: logs to android.util.Log.
 * - In Release builds: all logging is stripped via R8 (-assumenosideeffects).
 * - PII Safety: Student names, roll numbers, face embeddings, and confidence scores must NEVER be logged.
 */
object AppLog {

    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message())
        }
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    inline fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message())
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
    }
}
