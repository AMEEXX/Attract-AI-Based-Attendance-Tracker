package com.attract.attendance.data.security

import android.content.Context
import android.content.SharedPreferences

interface PinLockoutStorage {
    fun getFailedAttempts(): Int
    fun setFailedAttempts(attempts: Int)
    fun getLockoutUntilMillis(): Long
    fun setLockoutUntilMillis(untilMs: Long)
    fun clear()
}

class PrefsPinLockoutStorage(private val prefs: SharedPreferences) : PinLockoutStorage {
    companion object {
        private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until_ms"

        fun fromContext(context: Context): PrefsPinLockoutStorage {
            val prefs = context.getSharedPreferences("attract_pin_security", Context.MODE_PRIVATE)
            return PrefsPinLockoutStorage(prefs)
        }
    }

    override fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    override fun setFailedAttempts(attempts: Int) {
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
    }

    override fun getLockoutUntilMillis(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)

    override fun setLockoutUntilMillis(untilMs: Long) {
        prefs.edit().putLong(KEY_LOCKOUT_UNTIL, untilMs).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKOUT_UNTIL).apply()
    }
}

class InMemoryPinLockoutStorage : PinLockoutStorage {
    private var attempts: Int = 0
    private var untilMs: Long = 0L

    override fun getFailedAttempts(): Int = attempts
    override fun setFailedAttempts(attempts: Int) { this.attempts = attempts }
    override fun getLockoutUntilMillis(): Long = untilMs
    override fun setLockoutUntilMillis(untilMs: Long) { this.untilMs = untilMs }
    override fun clear() {
        attempts = 0
        untilMs = 0L
    }
}

/**
 * Manages persistent failed PIN attempts and exponential backoff across app restarts (PR-03).
 */
class PinLockoutManager(
    private val storage: PinLockoutStorage,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        const val MAX_FREE_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 30_000L // 30 seconds
        const val MAX_LOCKOUT_MS = 900_000L  // 15 minutes
    }

    fun isLockedOut(): Boolean {
        val now = timeProvider()
        val lockoutUntil = storage.getLockoutUntilMillis()
        return now < lockoutUntil
    }

    fun getRemainingLockoutSeconds(): Long {
        val now = timeProvider()
        val lockoutUntil = storage.getLockoutUntilMillis()
        return if (now < lockoutUntil) (lockoutUntil - now + 999) / 1000 else 0L
    }

    fun getFailedAttempts(): Int = storage.getFailedAttempts()

    fun recordSuccessfulAttempt() {
        storage.clear()
    }

    fun recordFailedAttempt(): Long {
        val attempts = storage.getFailedAttempts() + 1
        storage.setFailedAttempts(attempts)
        if (attempts >= MAX_FREE_ATTEMPTS) {
            val exponent = (attempts - MAX_FREE_ATTEMPTS).coerceAtMost(5)
            val duration = (BASE_LOCKOUT_MS * (1L shl exponent)).coerceAtMost(MAX_LOCKOUT_MS)
            val untilMs = timeProvider() + duration
            storage.setLockoutUntilMillis(untilMs)
            return duration / 1000
        }
        return 0L
    }
}
