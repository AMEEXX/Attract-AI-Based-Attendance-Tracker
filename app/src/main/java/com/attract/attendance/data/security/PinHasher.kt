package com.attract.attendance.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores a versioned, salted PIN verifier. The PIN itself is never persisted or logged. */
class PinHasher(
    private val random: SecureRandom = SecureRandom(),
) {
    fun hash(pin: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(pin, salt, ITERATIONS)
        return listOf(
            FORMAT_VERSION,
            ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(derived),
        ).joinToString("$")
    }

    fun verify(pin: CharArray, encoded: String): Boolean = runCatching {
        val parts = encoded.split("$")
        if (parts.size != 4 || parts[0] != FORMAT_VERSION) {
            false
        } else {
            val iterations = parts[1].toInt()
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            val actual = derive(pin, salt, iterations)
            MessageDigest.isEqual(actual, expected)
        }
    }.getOrDefault(false)

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val FORMAT_VERSION = "v1"
        const val ITERATIONS = 210_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
    }
}
