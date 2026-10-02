package com.attract.attendance.data.security

import com.attract.attendance.lockdown.domain.EmbeddingCipher
import com.attract.attendance.lockdown.domain.EncryptedEmbedding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEnvelopeCodecTest {

    /**
     * Deterministic test double: XOR stream keyed by AAD binding plus a checksum tag,
     * so wrong-key decryption and ciphertext tampering fail like AEAD would.
     */
    private class XorCipher : EmbeddingCipher {
        override fun encrypt(studentId: Long, templateId: Long, modelVersion: String, plaintext: ByteArray): EncryptedEmbedding {
            val iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
            val k = key(studentId, modelVersion).toInt()
            val ct = ByteArray(plaintext.size + TAG_SIZE) { i ->
                if (i < plaintext.size) (plaintext[i].toInt() xor k).toByte() else 0
            }
            tag(plaintext, k).copyInto(ct, plaintext.size)
            return EncryptedEmbedding(1, 7, iv, ct)
        }

        override fun decrypt(studentId: Long, templateId: Long, modelVersion: String, encrypted: EncryptedEmbedding): ByteArray {
            val k = key(studentId, modelVersion).toInt()
            val ct = encrypted.ciphertext
            if (ct.size <= TAG_SIZE) throw IllegalStateException("ciphertext too short")
            val plain = ByteArray(ct.size - TAG_SIZE) { (ct[it].toInt() xor k).toByte() }
            if (!tag(plain, k).contentEquals(ct.copyOfRange(plain.size, ct.size))) {
                throw IllegalStateException("authentication tag mismatch")
            }
            return plain
        }

        override fun clear() = Unit

        private fun tag(plain: ByteArray, k: Int): ByteArray {
            var acc = k
            for (b in plain) acc = (acc * 31 + b.toInt()) and 0xFF
            return byteArrayOf(acc.toByte(), ((acc shr 3) or k).toByte())
        }

        private fun key(studentId: Long, modelVersion: String): Byte =
            ((studentId * 31 + modelVersion.hashCode()) and 0xFF).toByte()

        private companion object {
            const val TAG_SIZE = 2
        }
    }

    private fun sampleFloats(): FloatArray {
        val dim = com.attract.attendance.domain.face.EmbeddingEngine.EMBEDDING_SIZE
        val raw = FloatArray(dim) { (it - dim / 2f) / dim.toFloat() }
        return com.attract.attendance.domain.face.EmbeddingEngine.l2Normalize(raw)
    }

    @Test
    fun encode_withCipher_producesAeadEnvelope() {
        val dim = com.attract.attendance.domain.face.EmbeddingEngine.EMBEDDING_SIZE
        val (blob, version) = TemplateEnvelopeCodec.encode(XorCipher(), studentId = 5L, modelVersion = "v1", plaintextFloats = sampleFloats())
        assertEquals(TemplateEnvelopeCodec.CRYPTO_VERSION_AEAD, version)
        assertEquals(1 + TemplateEnvelopeCodec.IV_SIZE_BYTES + dim * 4 + 2, blob.size)
    }

    @Test
    fun roundTrip_withCipher_restoresNormalizedFloats() {
        val dim = com.attract.attendance.domain.face.EmbeddingEngine.EMBEDDING_SIZE
        val original = sampleFloats()
        val (blob, version) = TemplateEnvelopeCodec.encode(XorCipher(), 5L, "v1", original)
        val decoded = TemplateEnvelopeCodec.decode(XorCipher(), 5L, "v1", blob, version)
        assertNotNull(decoded)
        assertEquals(dim, decoded!!.size)
        original.forEachIndexed { i, v -> assertEquals(v, decoded[i], 1e-6f) }
    }

    @Test
    fun decode_boundToDifferentStudent_failsClosed_returnsNull() {
        val (blob, version) = TemplateEnvelopeCodec.encode(XorCipher(), 5L, "v1", sampleFloats())
        // Row swapped onto another student's id must not decrypt to usable data.
        assertNull(TemplateEnvelopeCodec.decode(XorCipher(), 6L, "v1", blob, version))
    }

    @Test
    fun decode_tamperedBlob_failsClosed() {
        val (blob, version) = TemplateEnvelopeCodec.encode(XorCipher(), 5L, "v1", sampleFloats())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() + 1).toByte()
        assertNull(TemplateEnvelopeCodec.decode(XorCipher(), 5L, "v1", blob, version))
    }

    @Test
    fun decode_truncatedBlob_failsClosed() {
        val (blob, version) = TemplateEnvelopeCodec.encode(XorCipher(), 5L, "v1", sampleFloats())
        assertNull(TemplateEnvelopeCodec.decode(XorCipher(), 5L, "v1", blob.copyOfRange(0, 8), version))
    }

    @Test
    fun encode_withoutCipher_legacyPlaintextPassthrough() {
        val original = sampleFloats()
        val (blob, version) = TemplateEnvelopeCodec.encode(null, 5L, "v1", original)
        assertEquals(TemplateEnvelopeCodec.CRYPTO_VERSION_PLAINTEXT, version)
        val decoded = TemplateEnvelopeCodec.decode(null, 5L, "v1", blob, version)
        assertNotNull(decoded)
        assertTrue(decoded!!.contentEquals(original))
    }

    @Test
    fun decode_legacyPlaintextRow_stillReadableAfterUpgrade() {
        val dim = com.attract.attendance.domain.face.EmbeddingEngine.EMBEDDING_SIZE
        val legacy = with(com.attract.attendance.domain.face.TemplateMatcher) { sampleFloats().toByteArray() }
        val decoded = TemplateEnvelopeCodec.decode(XorCipher(), 5L, "v1", legacy, TemplateEnvelopeCodec.CRYPTO_VERSION_PLAINTEXT)
        assertNotNull(decoded)
        assertEquals(dim, decoded!!.size)
    }
}
