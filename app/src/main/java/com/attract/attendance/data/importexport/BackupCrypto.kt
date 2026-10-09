package com.attract.attendance.data.importexport

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Encrypted Backup Envelope Engine (PR-03 / SDD §66).
 *
 * Secures database snapshots with AES-256-GCM encryption derived from the teacher PIN
 * via PBKDF2-HMAC-SHA256 with 310,000 iterations, 16-byte salt, 96-bit IV, and SHA-256 checksum.
 * Prevents cleartext PII leakage to Google Drive or local storage.
 */
object BackupCrypto {
    const val FORMAT_ENCRYPTED_V2 = "attract-backup-v2-encrypted"
    const val FORMAT_PLAINTEXT_V2 = "attract-backup-v2"
    const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val ITERATIONS = 310_000
    const val SALT_BYTES = 16
    const val KEY_BITS = 256
    const val IV_BYTES = 12
    const val TAG_BITS = 128

    fun sha256Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }

    fun deriveKey(pin: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        return try {
            val keyBytes = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Encrypts plaintext JSON payload with AES-256-GCM using key derived from PIN.
     * Computes SHA-256 integrity checksum on plaintext.
     * Returns JSON envelope containing metadata and ciphertext (unreadable in text editor).
     */
    fun encrypt(plaintextJson: String, pin: CharArray): String {
        val plaintextBytes = plaintextJson.toByteArray(Charsets.UTF_8)
        val checksum = sha256Hex(plaintextBytes)

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)

        val secretKey = deriveKey(pin, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintextBytes)

        val json = JSONObject()
        json.put("format", FORMAT_ENCRYPTED_V2)
        json.put("version", 2)
        json.put("kdf", PBKDF2_ALGORITHM)
        json.put("iterations", ITERATIONS)
        json.put("salt", Base64.getEncoder().encodeToString(salt))
        json.put("iv", Base64.getEncoder().encodeToString(iv))
        json.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
        json.put("checksum", checksum)
        return json.toString(2)
    }

    /**
     * Decrypts encrypted envelope with PIN, verifies AES-GCM tag and SHA-256 integrity checksum.
     */
    fun decrypt(envelopeJson: String, pin: CharArray): String {
        val root = JSONObject(envelopeJson)
        val format = root.optString("format")
        require(format == FORMAT_ENCRYPTED_V2) { "Unsupported backup envelope format: $format" }

        val iterations = root.optInt("iterations", ITERATIONS)
        val salt = Base64.getDecoder().decode(root.getString("salt"))
        val iv = Base64.getDecoder().decode(root.getString("iv"))
        val ciphertext = Base64.getDecoder().decode(root.getString("ciphertext"))
        val expectedChecksum = root.getString("checksum")

        val secretKey = deriveKey(pin, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
        val decryptedBytes = cipher.doFinal(ciphertext)

        val actualChecksum = sha256Hex(decryptedBytes)
        if (!MessageDigest.isEqual(actualChecksum.toByteArray(), expectedChecksum.toByteArray())) {
            throw SecurityException("Backup integrity verification failed: SHA-256 checksum mismatch")
        }

        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun isEncryptedEnvelope(content: String): Boolean {
        return content.trimStart().startsWith("{") && content.contains("\"$FORMAT_ENCRYPTED_V2\"")
    }
}
