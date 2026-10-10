package com.attract.attendance.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

interface PinLockoutStorage {
    fun getFailedAttempts(): Int
    fun setFailedAttempts(attempts: Int)
    fun getLockoutUntilMillis(): Long
    fun setLockoutUntilMillis(untilMs: Long)
    fun getBootCount(): Int
    fun setBootCount(bootCount: Int)
    fun clear()
}

class PrefsPinLockoutStorage(private val prefs: SharedPreferences) : PinLockoutStorage {
    companion object {
        private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until_ms"
        private const val KEY_BOOT_COUNT = "pin_boot_count"

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
    override fun getBootCount(): Int = prefs.getInt(KEY_BOOT_COUNT, 0)
    override fun setBootCount(bootCount: Int) {
        prefs.edit().putInt(KEY_BOOT_COUNT, bootCount).apply()
    }
    override fun clear() {
        prefs.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKOUT_UNTIL).remove(KEY_BOOT_COUNT).apply()
    }
}

class InMemoryPinLockoutStorage : PinLockoutStorage {
    private var attempts: Int = 0
    private var untilMs: Long = 0L
    private var bootCount: Int = 0

    override fun getFailedAttempts(): Int = attempts
    override fun setFailedAttempts(attempts: Int) { this.attempts = attempts }
    override fun getLockoutUntilMillis(): Long = untilMs
    override fun setLockoutUntilMillis(untilMs: Long) { this.untilMs = untilMs }
    override fun getBootCount(): Int = bootCount
    override fun setBootCount(bootCount: Int) { this.bootCount = bootCount }
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
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val currentBootCount: () -> Int = { 0 }
) {
    companion object {
        fun create(context: Context): PinLockoutManager {
            return PinLockoutManager(
                storage = PrefsPinLockoutStorage.fromContext(context),
                timeProvider = { SystemClock.elapsedRealtime() },
                currentBootCount = { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) }
            )
        }
    }

    private fun checkReboot() {
        val currentBoot = currentBootCount()
        if (currentBoot != storage.getBootCount() && storage.getFailedAttempts() >= 5) {
            // Re-apply the full delay if the device was rebooted to bypass lock
            val duration = calculateLockoutDuration(storage.getFailedAttempts())
            storage.setLockoutUntilMillis(timeProvider() + duration)
            storage.setBootCount(currentBoot)
        }
    }

    private fun calculateLockoutDuration(failures: Int): Long {
        return when {
            failures < 5 -> 0L
            failures < 8 -> 30_000L      // 30 seconds
            failures < 10 -> 300_000L    // 5 minutes
            else -> 3_600_000L           // 1 hour
        }
    }

    fun isLockedOut(): Boolean {
        checkReboot()
        val now = timeProvider()
        val lockoutUntil = storage.getLockoutUntilMillis()
        return now < lockoutUntil
    }

    fun getRemainingLockoutSeconds(): Long {
        checkReboot()
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
        val duration = calculateLockoutDuration(attempts)
        
        if (duration > 0) {
            storage.setLockoutUntilMillis(timeProvider() + duration)
            storage.setBootCount(currentBootCount())
            return duration / 1000
        }
        return 0L
    }
}
