package com.attract.attendance.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stores a versioned, salted PIN verifier. The PIN itself is never persisted or logged. */
class PinHasher(
    private val random: SecureRandom = SecureRandom(),
) {
    private val keyStore = runCatching { 
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }.getOrNull()

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
        if (parts.size != 4 || (parts[0] != FORMAT_VERSION && parts[0] != FORMAT_V1)) {
            false
        } else {
            val iterations = parts[1].toInt()
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            
            // If checking a v1 hash (pre-pepper), we just use standard PBKDF2 without pepper
            val applyPepper = parts[0] == FORMAT_VERSION
            
            val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
            val pbkdf2 = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
            
            val actual = if (applyPepper) applyKeystorePepper(pbkdf2) else pbkdf2
            MessageDigest.isEqual(actual, expected)
        }
    }.getOrDefault(false)

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        val pbkdf2 = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return applyKeystorePepper(pbkdf2)
    }

    private fun applyKeystorePepper(input: ByteArray): ByteArray {
        if (keyStore == null) return input // Fallback for JVM unit tests
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(getOrCreatePepperKey())
        return mac.doFinal(input)
    }

    private fun getOrCreatePepperKey(): java.security.Key {
        requireNotNull(keyStore) { "AndroidKeyStore is not available" }
        if (!keyStore.containsAlias(PEPPER_KEY_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    PEPPER_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).build()
            )
            keyGen.generateKey()
        }
        return keyStore.getKey(PEPPER_KEY_ALIAS, null)
    }

    companion object {
        const val FORMAT_V1 = "v1"
        const val FORMAT_VERSION = "v2"
        const val ITERATIONS = 310_000
        const val SALT_BYTES = 16
        const val KEY_BITS = 256
        private const val PEPPER_KEY_ALIAS = "attract_pin_pepper_key"
    }
}
