package com.attract.attendance.lockdown.domain

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptedEmbeddingCipher : EmbeddingCipher {

    private val gcmSpec = GCMParameterSpec(128, ByteArray(12))

    private val keygen = KeyGenerator.getInstance("AES").apply {
        init(256)
    }

    private val secretKey: SecretKey = keygen.generateKey()
    private val formatVersion: Int = 1
    private val keyVersion: Int = 1
    private val modelVersion: String = "v1"

    override fun encrypt(studentId: Long, templateId: Long, modelVersion: String, plaintext: ByteArray): EncryptedEmbedding {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedEmbedding(formatVersion, keyVersion, iv, ciphertext)
    }

    override fun decrypt(studentId: Long, templateId: Long, modelVersion: String, encrypted: EncryptedEmbedding): ByteArray {
        val spec = GCMParameterSpec(128, encrypted.iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encrypted.ciphertext)
    }

    override fun clear() {
    }
}
