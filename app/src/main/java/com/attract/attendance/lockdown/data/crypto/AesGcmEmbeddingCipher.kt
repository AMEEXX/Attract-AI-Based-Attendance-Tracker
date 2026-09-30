package com.attract.attendance.lockdown.data.crypto

import com.attract.attendance.lockdown.domain.EmbeddingCipher
import com.attract.attendance.lockdown.domain.EncryptedEmbedding
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmEmbeddingCipher(
    private val secretKey: SecretKey,
    private val formatVersion: Int = 1,
    private val keyVersion: Int = 1,
) : EmbeddingCipher {


    companion object {
        const val AAD_SEPARATOR = "|"

        fun buildAad(
            templateFormat: String,
            studentId: Long,
            templateId: Long,
            modelVersion: String,
        ): ByteArray {
            return "$templateFormat${AAD_SEPARATOR}$studentId${AAD_SEPARATOR}$templateId${AAD_SEPARATOR}$modelVersion".toByteArray(Charsets.UTF_8)
        }

        fun generateKey(): SecretKey {
            val keygen = KeyGenerator.getInstance("AES")
            keygen.init(256)
            return keygen.generateKey()
        }
    }

    override fun encrypt(studentId: Long, templateId: Long, modelVersion: String, plaintext: ByteArray): EncryptedEmbedding {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        cipher.updateAAD(buildAad(formatVersion.toString(), studentId, templateId, modelVersion))
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedEmbedding(
            formatVersion = formatVersion,
            keyVersion = keyVersion,
            iv = iv,
            ciphertext = ciphertext,
        )
    }

    override fun decrypt(studentId: Long, templateId: Long, modelVersion: String, encrypted: EncryptedEmbedding): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, encrypted.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        cipher.updateAAD(buildAad(encrypted.formatVersion.toString(), studentId, templateId, modelVersion))
        return cipher.doFinal(encrypted.ciphertext)
    }

    override fun clear() {
    }
}
