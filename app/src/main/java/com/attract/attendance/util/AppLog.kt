package com.attract.attendance.util

import android.util.Log

/**
 * Privacy-hardened logging facade for Attract.
 *
 * Rules:
 * - In release builds, R8 strips android.util.Log and AppLog calls via -assumenosideeffects rules.
 * - NEVER log personally identifiable information (PII) such as student names or roll numbers,
 *   raw face embeddings, or confidence/quality scores.
 * - CI grep gate: Log\..*(name|roll|score|embedding) fails the build.
 */
object AppLog {
    @JvmStatic
    inline fun v(tag: String, message: String) {
        Log.v(tag, message)
    }

    @JvmStatic
    inline fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    @JvmStatic
    inline fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    @JvmStatic
    inline fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    @JvmStatic
    inline fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    @JvmStatic
    inline fun e(tag: String, message: String) {
        Log.e(tag, message)
    }

    @JvmStatic
    inline fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
