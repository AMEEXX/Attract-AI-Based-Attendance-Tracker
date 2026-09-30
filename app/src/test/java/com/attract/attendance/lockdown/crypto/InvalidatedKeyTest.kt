package com.attract.attendance.lockdown.crypto

import com.attract.attendance.lockdown.data.crypto.AesGcmEmbeddingCipher
import com.attract.attendance.lockdown.domain.EncryptedEmbedding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class InvalidatedKeyTest {

    @Test
    fun decrypt_withDifferentKey_failsAuthentication() {
        val key1 = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val key2 = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        val cipher1 = AesGcmEmbeddingCipher(key1)
        val cipher2 = AesGcmEmbeddingCipher(key2)

        val plaintext = "secret-template".toByteArray()
        val encrypted = cipher1.encrypt(1L, 10L, "v1", plaintext)

        try {
            cipher2.decrypt(1L, 10L, "v1", encrypted)
            org.junit.Assert.fail("Expected AEADBadTagException with different key")
        } catch (e: javax.crypto.AEADBadTagException) {
        }
    }

    @Test
    fun reEncryptionWithKey1StillWorks_afterKey2Failure() {
        val key1 = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val key2 = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        val cipher1 = AesGcmEmbeddingCipher(key1)
        val cipher2 = AesGcmEmbeddingCipher(key2)

        val plaintext = "secret-template".toByteArray()
        val encrypted = cipher1.encrypt(1L, 10L, "v1", plaintext)

        val decrypted = cipher1.decrypt(1L, 10L, "v1", encrypted)
        assertArrayEquals(plaintext, decrypted)

        try {
            cipher2.decrypt(1L, 10L, "v1", encrypted)
            org.junit.Assert.fail("Expected AEADBadTagException")
        } catch (e: javax.crypto.AEADBadTagException) {
        }
    }
}
