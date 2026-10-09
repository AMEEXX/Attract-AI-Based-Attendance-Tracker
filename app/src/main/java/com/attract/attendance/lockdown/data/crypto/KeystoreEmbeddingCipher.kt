package com.attract.attendance.lockdown.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.attract.attendance.util.AppLog
import com.attract.attendance.lockdown.domain.EncryptedEmbedding
import com.attract.attendance.lockdown.domain.EmbeddingCipher
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreEmbeddingCipher(
    private val alias: String,
    private val keyVersion: Int,
    private val formatVersion: Int = 1,
    private val secureRandom: SecureRandom = SecureRandom(),
) : EmbeddingCipher {

    companion object {
        const val TEMPLATE_FORMAT = "float32_be"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val IV_SIZE_BITS = 96
        private const val TAG_SIZE_BITS = 128
    }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!ks.containsAlias(alias)) {
                createKey()
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize Keystore key", e)
        }
    }

    private fun createKey() {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setKeySize(256)
            .setDigests(KeyProperties.DIGEST_NONE)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(false)
            .build()

        val keygen = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)
        keygen.init(spec)
        keygen.generateKey()
    }

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun buildAad(studentId: Long, templateId: Long, modelVersion: String): ByteArray {
        return "$TEMPLATE_FORMAT|$studentId|$templateId|$modelVersion".toByteArray(Charsets.UTF_8)
    }

    private fun generateIv(): ByteArray {
        val iv = ByteArray(IV_SIZE_BITS / 8)
        secureRandom.nextBytes(iv)
        return iv
    }

    override fun encrypt(studentId: Long, templateId: Long, modelVersion: String, plaintext: ByteArray): EncryptedEmbedding {
        val key = try {
            getKey()
        } catch (e: UnrecoverableKeyException) {
            recreateKey()
            getKey()
        }

        val cipher = javax.crypto.Cipher.getInstance("$KEY_ALGORITHM/$BLOCK_MODE/$PADDING")
        val iv = generateIv()
        val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, spec)
        cipher.updateAAD(buildAad(studentId, templateId, modelVersion))
        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedEmbedding(
            formatVersion = formatVersion,
            keyVersion = keyVersion,
            iv = iv,
            ciphertext = ciphertext,
        )
    }

    override fun decrypt(studentId: Long, templateId: Long, modelVersion: String, encrypted: EncryptedEmbedding): ByteArray {
        val key = try {
            getKey()
        } catch (e: UnrecoverableKeyException) {
            throw TemplateUnavailableException("Keystore key invalid or lost", e)
        }

        val cipher = javax.crypto.Cipher.getInstance("$KEY_ALGORITHM/$BLOCK_MODE/$PADDING")
        val spec = GCMParameterSpec(TAG_SIZE_BITS, encrypted.iv)
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, spec)
        cipher.updateAAD(buildAad(studentId, templateId, modelVersion))

        try {
            return cipher.doFinal(encrypted.ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw TemplateUnavailableException("Authentication tag mismatch", e)
        }
    }

    override fun clear() {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            ks.deleteEntry(alias)
        } catch (e: Exception) {
            AppLog.w("KeystoreCipher", "Failed to delete key: ${e.message}")
        }
    }

    private fun recreateKey(): SecretKey {
        try {
            createKey()
            return getKey()
        } catch (e: Exception) {
            throw RuntimeException("Failed to recreate Keystore key", e)
        }
    }
}

class TemplateUnavailableException(message: String, cause: Throwable) : RuntimeException(message, cause)
