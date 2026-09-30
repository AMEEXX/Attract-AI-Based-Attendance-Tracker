package com.attract.attendance.lockdown.crypto

import com.attract.attendance.lockdown.data.crypto.AesGcmEmbeddingCipher
import com.attract.attendance.lockdown.domain.EncryptedEmbedding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import javax.crypto.spec.SecretKeySpec

class EmbeddingCipherTest {

    private val cipher = AesGcmEmbeddingCipher(AesGcmEmbeddingCipher.generateKey())

    @Test
    fun encryptDecrypt_roundTrip_recoversExactBytes() {
        val plaintext = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f).let { floats ->
            java.nio.ByteBuffer.allocate(floats.size * 4).apply {
                floats.forEach { putFloat(it) }
            }.array()
        }

        val encrypted = cipher.encrypt(studentId = 1L, templateId = 10L, modelVersion = "v1", plaintext)
        val decrypted = cipher.decrypt(studentId = 1L, templateId = 10L, modelVersion = "v1", encrypted)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun decrypt_withDifferentStudentId_fails() {
        val plaintext = "test".toByteArray()
        val encrypted = cipher.encrypt(1L, 10L, "v1", plaintext)

        try {
            cipher.decrypt(2L, 10L, "v1", encrypted)
            org.junit.Assert.fail("Expected AEADBadTagException")
        } catch (e: javax.crypto.AEADBadTagException) {
        }
    }

    @Test
    fun decrypt_withDifferentTemplateId_fails() {
        val plaintext = "test".toByteArray()
        val encrypted = cipher.encrypt(1L, 10L, "v1", plaintext)

        try {
            cipher.decrypt(1L, 20L, "v1", encrypted)
            org.junit.Assert.fail("Expected AEADBadTagException")
        } catch (e: javax.crypto.AEADBadTagException) {
        }
    }

    @Test
    fun decrypt_withDifferentModelVersion_fails() {
        val plaintext = "test".toByteArray()
        val encrypted = cipher.encrypt(1L, 10L, "v1", plaintext)

        try {
            cipher.decrypt(1L, 10L, "v2", encrypted)
            org.junit.Assert.fail("Expected AEADBadTagException")
        } catch (e: javax.crypto.AEADBadTagException) {
        }
    }

    @Test
    fun decrypt_withAlteredCiphertext_fails() {
        val plaintext = "test".toByteArray()
        val encrypted = cipher.encrypt(1L, 10L, "v1", plaintext)

        val tamperedCiphertext = encrypted.ciphertext.copyOf()
        tamperedCiphertext[0] = tamperedCiphertext[0].inc()
        val tampered = EncryptedEmbedding(
            formatVersion = encrypted.formatVersion,
            keyVersion = encrypted.keyVersion,
            iv = encrypted.iv,
            ciphertext = tamperedCiphertext,
        )

        try {
            cipher.decrypt(1L, 10L, "v1", tampered)
            org.junit.Assert.fail("Expected AEADBadTagException")
        } catch (e: javax.crypto.AEADBadTagException) {
        }
    }

    @Test
    fun decrypt_withAlteredIv_fails() {
        val plaintext = "test".toByteArray()
        val encrypted = cipher.encrypt(1L, 10L, "v1", plaintext)

        val tamperedIv = encrypted.iv.copyOf()
        tamperedIv[0] = tamperedIv[0].inc()
        val tampered = EncryptedEmbedding(
            formatVersion = encrypted.formatVersion,
            keyVersion = encrypted.keyVersion,
            iv = tamperedIv,
            ciphertext = encrypted.ciphertext,
        )

        try {
            cipher.decrypt(1L, 10L, "v1", tampered)
            org.junit.Assert.fail("Expected AEADBadTagException")
        } catch (e: javax.crypto.AEADBadTagException) {
        }
    }

    @Test
    fun ciphertextSwappedBetweenTwoTemplatesOfSameStudent_fails() {
        val plaintext1 = "template1".toByteArray()
        val plaintext2 = "template2".toByteArray()

        val studentId = 1L
        val encrypted1 = cipher.encrypt(studentId, templateId = 10L, "v1", plaintext1)
        val encrypted2 = cipher.encrypt(studentId, templateId = 20L, "v1", plaintext2)

        try {
            cipher.decrypt(studentId, templateId = 10L, "v1", encrypted2)
            org.junit.Assert.fail("Expected AEADBadTagException when swapping template 20's ciphertext into template 10's AAD scope")
        } catch (e: javax.crypto.AEADBadTagException) {
        }

        try {
            cipher.decrypt(studentId, templateId = 20L, "v1", encrypted1)
            org.junit.Assert.fail("Expected AEADBadTagException when swapping template 10's ciphertext into template 20's AAD scope")
        } catch (e: javax.crypto.AEADBadTagException) {
        }
    }

    @Test
    fun aadBindsAllFourFieldsInCorrectOrder() {
        val aad1 = AesGcmEmbeddingCipher.buildAad("1", 1L, 10L, "v1")
        val aad2 = AesGcmEmbeddingCipher.buildAad("1", 1L, 20L, "v1")
        val aad3 = AesGcmEmbeddingCipher.buildAad("1", 2L, 10L, "v1")
        val aad4 = AesGcmEmbeddingCipher.buildAad("1", 1L, 10L, "v2")

        val allMatch = aad1.contentEquals(aad2) &&
            aad1.contentEquals(aad3) &&
            aad1.contentEquals(aad4)

        assertTrue(!allMatch)
    }

    @Test
    fun decryptDoesNotRequireBiometricPrompt() {
        val plaintext = "no-prompt-test".toByteArray()
        val encrypted = cipher.encrypt(1L, 10L, "v1", plaintext)

        val decrypted = cipher.decrypt(1L, 10L, "v1", encrypted)
        assertArrayEquals(plaintext, decrypted)
    }
}
