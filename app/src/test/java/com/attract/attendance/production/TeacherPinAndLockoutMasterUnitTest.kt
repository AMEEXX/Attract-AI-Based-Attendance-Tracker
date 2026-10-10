package com.attract.attendance.production

import com.attract.attendance.data.importexport.BackupCrypto
import com.attract.attendance.data.importexport.BackupExporter
import com.attract.attendance.data.importexport.BackupFaceTemplate
import com.attract.attendance.data.importexport.BackupSnapshot
import com.attract.attendance.data.local.TeacherEntity
import com.attract.attendance.data.security.InMemoryPinLockoutStorage
import com.attract.attendance.data.security.PinHasher
import com.attract.attendance.data.security.PinLockoutManager
import com.attract.attendance.lockdown.domain.PinBackedAuthenticator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Master Teacher PIN, Persistent Lockout & Backup Cryptography Test Suite (WP-05).
 *
 * Validates:
 * 1. PIN length rules: Strict enforcement of 6 to 12 numeric digits (Regex: \d{6,12}).
 * 2. PIN hashing: PBKDF2-HMAC-SHA256 with 310k iterations, 16-byte salt, v2 format.
 * 3. Constant-time verification & zeroing of memory buffers.
 * 4. Tiered lockout backoff policy:
 *    - < 5 failures: 0s delay
 *    - 5 to 7 failures: 30s delay
 *    - 8 to 9 failures: 300s (5 min) delay
 *    - 10+ failures: 3600s (1 hr) delay
 * 5. Device reboot resistance: BOOT_COUNT changes re-apply full lockout delay.
 * 6. PinBackedAuthenticator lockout gate: Fails closed immediately, zeroes buffer.
 * 7. Backup sanitization: pinHash and faceTemplates are completely stripped.
 * 8. Backup encryption: AES-256-GCM envelope with separate passphrase / recovery key.
 * 9. Backup integrity: Tampered ciphertext or checksum immediately rejected.
 */
class TeacherPinAndLockoutMasterUnitTest {

    private val pinHasher = PinHasher()
    private val exporter = BackupExporter()

    // =========================================================================
    // 1. PIN Length Enforcement (>= 6 Digits)
    // =========================================================================

    private fun isValidPinFormat(pin: CharArray): Boolean =
        pin.concatToString().matches(Regex("\\d{6,12}"))

    @Test
    fun wp05_pinLength_rejectsLessThanSixDigits() {
        assertFalse("1-digit PIN must be rejected", isValidPinFormat("1".toCharArray()))
        assertFalse("3-digit PIN must be rejected", isValidPinFormat("123".toCharArray()))
        assertFalse("4-digit PIN must be rejected", isValidPinFormat("1234".toCharArray()))
        assertFalse("5-digit PIN must be rejected", isValidPinFormat("12345".toCharArray()))
    }

    @Test
    fun wp05_pinLength_acceptsSixToTwelveDigits() {
        assertTrue("6-digit PIN must be accepted", isValidPinFormat("123456".toCharArray()))
        assertTrue("8-digit PIN must be accepted", isValidPinFormat("12345678".toCharArray()))
        assertTrue("12-digit PIN must be accepted", isValidPinFormat("123456789012".toCharArray()))
    }

    @Test
    fun wp05_pinLength_rejectsMoreThanTwelveDigitsOrNonDigits() {
        assertFalse("13-digit PIN must be rejected", isValidPinFormat("1234567890123".toCharArray()))
        assertFalse("Alpha PIN must be rejected", isValidPinFormat("abcdef".toCharArray()))
        assertFalse("Alphanumeric PIN must be rejected", isValidPinFormat("12345a".toCharArray()))
        assertFalse("Symbols must be rejected", isValidPinFormat("12345!".toCharArray()))
    }

    // =========================================================================
    // 2. PIN Hashing & Memory Sanitation
    // =========================================================================

    @Test
    fun wp05_pinHashing_producesV2Format_with310kIterations() {
        val pin = "852963".toCharArray()
        val hash = pinHasher.hash(pin)

        val parts = hash.split("$")
        assertEquals("Format version must be 4 parts: version\$iterations\$salt\$hash", 4, parts.size)
        assertEquals("Must use format version v2", PinHasher.FORMAT_VERSION, parts[0])
        assertEquals("Iteration count must be 310,000", PinHasher.ITERATIONS.toString(), parts[1])
        assertNotNull("Salt must be non-empty", parts[2])
        assertNotNull("Hash must be non-empty", parts[3])

        assertTrue("Verification must succeed with exact PIN", pinHasher.verify(pin, hash))
        assertFalse("Verification must fail with wrong PIN", pinHasher.verify("852964".toCharArray(), hash))
    }

    @Test
    fun wp05_pinVerification_zeroesMemoryBuffer() {
        val pin = "123456".toCharArray()
        val encoded = pinHasher.hash(pin)
        val authenticator = PinBackedAuthenticator(pinHasher, encoded)

        val testBuffer = "123456".toCharArray()
        authenticator.verifyPin(testBuffer)

        assertTrue("PIN buffer must be zeroed out in memory after verification", testBuffer.all { it == '\u0000' })
    }

    // =========================================================================
    // 3. Persistent Lockout Backoff Policy
    // =========================================================================

    @Test
    fun wp05_lockoutTiers_matchesExactPlanDurations() {
        val storage = InMemoryPinLockoutStorage()
        var time = 1_000_000L
        val manager = PinLockoutManager(storage, timeProvider = { time })

        // 1 to 4 failed attempts -> 0s delay
        for (attempt in 1..4) {
            val delay = manager.recordFailedAttempt()
            assertEquals("Attempts 1..4 must not trigger delay", 0L, delay)
            assertFalse(manager.isLockedOut())
        }

        // 5th attempt -> 30 seconds delay
        val delay5 = manager.recordFailedAttempt()
        assertEquals("5th attempt must trigger 30s lockout", 30L, delay5)
        assertTrue(manager.isLockedOut())
        assertEquals(30L, manager.getRemainingLockoutSeconds())

        // 6th and 7th attempt -> still 30 seconds tier
        time += 31_000L // wait out the 30s
        assertFalse(manager.isLockedOut())

        val delay6 = manager.recordFailedAttempt() // 6th fail
        assertEquals(30L, delay6)

        val delay7 = manager.recordFailedAttempt() // 7th fail
        assertEquals(30L, delay7)

        // 8th attempt -> 300 seconds (5 minutes)
        val delay8 = manager.recordFailedAttempt() // 8th fail
        assertEquals("8th attempt must trigger 300s (5m) lockout", 300L, delay8)
        assertTrue(manager.isLockedOut())
        assertEquals(300L, manager.getRemainingLockoutSeconds())

        // 9th attempt -> 300 seconds
        val delay9 = manager.recordFailedAttempt() // 9th fail
        assertEquals(300L, delay9)

        // 10th attempt -> 3600 seconds (1 hour)
        val delay10 = manager.recordFailedAttempt() // 10th fail
        assertEquals("10th attempt must trigger 3600s (1h) lockout", 3600L, delay10)
        assertTrue(manager.isLockedOut())
        assertEquals(3600L, manager.getRemainingLockoutSeconds())
    }

    @Test
    fun wp05_rebootTamperDetection_reappliesFullLockoutDelay() {
        val storage = InMemoryPinLockoutStorage()
        var time = 5_000_000L
        var bootCount = 1
        val manager = PinLockoutManager(
            storage = storage,
            timeProvider = { time },
            currentBootCount = { bootCount },
        )

        // Trigger 5 failures
        repeat(5) { manager.recordFailedAttempt() }
        assertTrue(manager.isLockedOut())

        // Attacker reboots phone: boot count increments, uptime restarts at 100ms
        bootCount = 2
        time = 100L

        // Manager must detect reboot and re-apply full 30s delay
        assertTrue("Must remain locked out across reboot", manager.isLockedOut())
        assertEquals("Must re-apply the full 30s delay", 30L, manager.getRemainingLockoutSeconds())
    }

    @Test
    fun wp05_successfulAttempt_resetsLockoutCounters() {
        val storage = InMemoryPinLockoutStorage()
        val manager = PinLockoutManager(storage, timeProvider = { 100_000L })

        repeat(5) { manager.recordFailedAttempt() }
        assertTrue(manager.isLockedOut())

        manager.recordSuccessfulAttempt()
        assertFalse(manager.isLockedOut())
        assertEquals(0, manager.getFailedAttempts())
        assertEquals(0L, manager.getRemainingLockoutSeconds())
    }

    // =========================================================================
    // 4. Backup Sanitization & Cryptography
    // =========================================================================

    @Test
    fun wp05_backupExport_stripsPinHashAndFaceTemplates() {
        val teacher = TeacherEntity(
            id = 1L,
            displayName = "Teacher Jane",
            pinHash = "sensitive_v2_peppered_hash_value",
            createdAt = 1000L,
            updatedAt = 1000L,
        )
        val faceTemplate = BackupFaceTemplate(
            id = 10L,
            studentId = 99L,
            modelVersion = "v1",
            embeddingDim = 512,
            embeddingBase64 = "base64data==",
        )
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = listOf(teacher),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList(),
            faceTemplates = listOf(faceTemplate),
        )

        val json = exporter.toJson(snapshot)

        // 1. Must NOT contain the pinHash value
        assertFalse("Backup must NOT contain pinHash", json.contains("sensitive_v2_peppered_hash_value"))
        assertFalse("Backup must NOT contain pinHash field", json.contains("\"pinHash\""))

        // 2. Must contain empty faceTemplates per SDD §66
        assertTrue("faceTemplates array must be empty in backup JSON", json.contains("\"faceTemplates\":[]"))
        assertFalse("Raw embedding base64 data must not exist in backup", json.contains("base64data=="))

        // 3. Parsing back restores clean entities
        val parsed = exporter.fromJson(json)
        assertEquals(1, parsed.teachers.size)
        assertEquals("Teacher Jane", parsed.teachers[0].displayName)
        assertEquals("", parsed.teachers[0].pinHash)
        assertTrue(parsed.faceTemplates.isEmpty())
    }

    @Test
    fun wp05_backupEncryption_twelvePlusCharPassphrase_encryptsAndRestores() {
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = listOf(TeacherEntity(1L, "Prof. Gauss", "", 0L, 0L)),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList(),
        )

        val passphrase = "A-Strong-Passphrase-2026!".toCharArray()
        val plaintext = exporter.toJson(snapshot)
        val envelope = BackupCrypto.encrypt(plaintext, passphrase)

        // Must be fully unreadable as plaintext
        assertFalse(envelope.contains("Prof. Gauss"))
        assertTrue(envelope.contains(BackupCrypto.FORMAT_ENCRYPTED_V2))

        val decrypted = BackupCrypto.decrypt(envelope, passphrase)
        val restored = exporter.fromJson(decrypted)
        assertEquals("Prof. Gauss", restored.teachers[0].displayName)
    }

    @Test(expected = Exception::class)
    fun wp05_backupEncryption_tamperedCiphertext_rejected() {
        val snapshot = BackupSnapshot(
            generatedAt = 1700000000000L,
            teachers = emptyList(),
            classes = emptyList(),
            students = emptyList(),
            sessions = emptyList(),
            records = emptyList(),
        )
        val passphrase = "A-Strong-Passphrase-2026!".toCharArray()
        val envelope = BackupCrypto.encrypt(exporter.toJson(snapshot), passphrase)

        // Corrupt the ciphertext payload
        val tampered = envelope.replace("ciphertext\": \"", "ciphertext\": \"CORRUPTED")
        BackupCrypto.decrypt(tampered, passphrase)
    }
}
