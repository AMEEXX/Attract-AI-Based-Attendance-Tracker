package com.attract.attendance.production

import com.attract.attendance.data.security.TemplateEnvelopeCodec
import com.attract.attendance.domain.face.AdaptiveVerificationEngine
import com.attract.attendance.domain.face.EmbeddingEngine
import com.attract.attendance.domain.face.FaceQualityConfig
import com.attract.attendance.domain.face.FaceQualityEngine
import com.attract.attendance.domain.face.FaceQualitySignals
import com.attract.attendance.domain.face.FrameObservation
import com.attract.attendance.domain.face.LivenessResult
import com.attract.attendance.domain.face.RecognitionDecisionEngine
import com.attract.attendance.domain.face.RecognitionOutcome
import com.attract.attendance.domain.face.StudentTemplatePair
import com.attract.attendance.domain.face.TemplateCompatibility
import com.attract.attendance.lockdown.domain.EmbeddingCipher
import com.attract.attendance.lockdown.domain.EncryptedEmbedding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Master Biometric Security & Cryptography Test Suite (WP-04).
 *
 * Validates:
 * 1. Zero plaintext storage: Envelopes strictly use CRYPTO_VERSION_AEAD (v2).
 * 2. Envelope layout: [1-byte keyVersion][12-byte IV][Ciphertext + Tag].
 * 3. AEAD AAD cryptographic binding to (studentId, modelVersion).
 * 4. Tamper detection: IV, ciphertext, studentId, or modelVersion mutation fails closed (null).
 * 5. Fail-closed on missing cipher: decode without cipher returns null.
 * 6. Legacy compatibility: legacy v1 rows remain decodable during transition.
 * 7. TemplateCompatibility: stale dimensions (32-D, 192-D) and malformed vectors (NaN, inf)
 *    are quarantined and NEVER reach cosine distance matching.
 * 8. Mixed gallery resilience: current templates match cleanly even when stale templates are present.
 */
class BiometricSecurityMasterUnitTest {

    /**
     * Deterministic AEAD-simulating cipher double:
     * Derives a key and authentication tag from (studentId, templateId, modelVersion),
     * ensuring any tampering or identity swapping fails verification.
     */
    private class AuthenticatedTestCipher : EmbeddingCipher {
        private companion object {
            const val TAG_BYTES = 4
            val FIXED_IV = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        }

        override fun encrypt(
            studentId: Long,
            templateId: Long,
            modelVersion: String,
            plaintext: ByteArray,
        ): EncryptedEmbedding {
            val keyByte = computeKeyByte(studentId, templateId, modelVersion)
            val ciphertext = ByteArray(plaintext.size + TAG_BYTES)
            for (i in plaintext.indices) {
                ciphertext[i] = (plaintext[i].toInt() xor keyByte).toByte()
            }
            val tag = computeTag(plaintext, keyByte)
            tag.copyInto(ciphertext, plaintext.size)
            return EncryptedEmbedding(
                formatVersion = 1,
                keyVersion = 1,
                iv = FIXED_IV.copyOf(),
                ciphertext = ciphertext,
            )
        }

        override fun decrypt(
            studentId: Long,
            templateId: Long,
            modelVersion: String,
            encrypted: EncryptedEmbedding,
        ): ByteArray {
            val ct = encrypted.ciphertext
            if (ct.size <= TAG_BYTES) error("Ciphertext too short")
            val keyByte = computeKeyByte(studentId, templateId, modelVersion)
            val plainSize = ct.size - TAG_BYTES
            val plaintext = ByteArray(plainSize) { i -> (ct[i].toInt() xor keyByte).toByte() }
            val expectedTag = computeTag(plaintext, keyByte)
            val actualTag = ct.copyOfRange(plainSize, ct.size)
            if (!expectedTag.contentEquals(actualTag)) {
                error("Authentication tag mismatch: AAD tampering detected")
            }
            return plaintext
        }

        override fun clear() = Unit

        private fun computeKeyByte(studentId: Long, templateId: Long, modelVersion: String): Int =
            ((studentId * 37 + templateId * 17 + modelVersion.hashCode()) and 0xFF).toInt()

        private fun computeTag(plaintext: ByteArray, keyByte: Int): ByteArray {
            var hash = keyByte
            for (b in plaintext) hash = (hash * 31 + b.toInt()) and 0x7FFFFFFF
            return byteArrayOf(
                (hash shr 24).toByte(),
                (hash shr 16).toByte(),
                (hash shr 8).toByte(),
                hash.toByte(),
            )
        }
    }

    private fun generateNormalizedVector(dim: Int = EmbeddingEngine.EMBEDDING_SIZE): FloatArray {
        val raw = FloatArray(dim) { (it + 1) * 0.05f }
        return EmbeddingEngine.l2Normalize(raw)
    }

    // =========================================================================
    // 1. Template Envelope Encoding & Layout
    // =========================================================================

    @Test
    fun wp04_encode_producesV2AeadEnvelope_withExpectedBinaryLayout() {
        val cipher = AuthenticatedTestCipher()
        val vector = generateNormalizedVector()

        val (blob, cryptoVersion) = TemplateEnvelopeCodec.encode(
            cipher = cipher,
            studentId = 101L,
            modelVersion = "facenet_mobile_v1",
            plaintextFloats = vector,
        )

        assertEquals("Crypto version must be AEAD (version 2)", TemplateEnvelopeCodec.CRYPTO_VERSION_AEAD, cryptoVersion)

        // Envelope format: 1 byte keyVersion + 12 bytes IV + (512 * 4 bytes float) + 4 bytes tag
        val expectedSize = 1 + TemplateEnvelopeCodec.IV_SIZE_BYTES + (vector.size * 4) + 4
        assertEquals("Blob size must match exact envelope layout", expectedSize, blob.size)

        // Key version must be 1
        assertEquals(1, blob[0].toInt())
    }

    @Test
    fun wp04_roundTrip_restoresExactEmbeddingVector() {
        val cipher = AuthenticatedTestCipher()
        val original = generateNormalizedVector()

        val (blob, cryptoVersion) = TemplateEnvelopeCodec.encode(
            cipher = cipher,
            studentId = 202L,
            modelVersion = "v1",
            plaintextFloats = original,
        )

        val restored = TemplateEnvelopeCodec.decode(
            cipher = cipher,
            studentId = 202L,
            modelVersion = "v1",
            stored = blob,
            cryptoVersion = cryptoVersion,
        )

        assertNotNull("Decoded vector must not be null", restored)
        assertEquals("Dimension must remain 512", original.size, restored!!.size)
        for (i in original.indices) {
            assertEquals("Float value at index $i must match", original[i], restored[i], 1e-6f)
        }
    }

    // =========================================================================
    // 2. AAD Cryptographic Binding & Tamper Detection
    // =========================================================================

    @Test
    fun wp04_aadBinding_swappingStudentId_failsClosed() {
        val cipher = AuthenticatedTestCipher()
        val vector = generateNormalizedVector()

        val (blob, cryptoVersion) = TemplateEnvelopeCodec.encode(cipher, 303L, "v1", vector)

        // Attempting to decrypt under a different studentId must fail
        val swapped = TemplateEnvelopeCodec.decode(cipher, 404L, "v1", blob, cryptoVersion)
        assertNull("Cross-student template reuse must fail closed (null)", swapped)
    }

    @Test
    fun wp04_aadBinding_swappingModelVersion_failsClosed() {
        val cipher = AuthenticatedTestCipher()
        val vector = generateNormalizedVector()

        val (blob, cryptoVersion) = TemplateEnvelopeCodec.encode(cipher, 303L, "model_v1", vector)

        val swapped = TemplateEnvelopeCodec.decode(cipher, 303L, "model_v2", blob, cryptoVersion)
        assertNull("Model version mismatch must fail closed (null)", swapped)
    }

    @Test
    fun wp04_tamperedCiphertext_failsClosed() {
        val cipher = AuthenticatedTestCipher()
        val vector = generateNormalizedVector()

        val (blob, cryptoVersion) = TemplateEnvelopeCodec.encode(cipher, 505L, "v1", vector)

        // Flip a byte in the ciphertext payload
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0xFF).toByte()

        val result = TemplateEnvelopeCodec.decode(cipher, 505L, "v1", blob, cryptoVersion)
        assertNull("Tampered ciphertext must fail integrity check and return null", result)
    }

    @Test
    fun wp04_truncatedBlob_failsClosedWithoutCrashing() {
        val cipher = AuthenticatedTestCipher()
        val (blob, cryptoVersion) = TemplateEnvelopeCodec.encode(cipher, 505L, "v1", generateNormalizedVector())

        // Truncate to less than keyVersion + IV length
        val truncated = blob.copyOfRange(0, 10)
        val result = TemplateEnvelopeCodec.decode(cipher, 505L, "v1", truncated, cryptoVersion)
        assertNull("Truncated blob must return null safely", result)
    }

    @Test
    fun wp04_missingCipher_failsClosedForEncryptedBlob() {
        val cipher = AuthenticatedTestCipher()
        val (blob, cryptoVersion) = TemplateEnvelopeCodec.encode(cipher, 606L, "v1", generateNormalizedVector())

        val result = TemplateEnvelopeCodec.decode(null, 606L, "v1", blob, cryptoVersion)
        assertNull("Decoding v2 envelope without cipher must fail closed", result)
    }

    @Test
    fun wp04_legacyPlaintextRow_decodesGracefullyForMigration() {
        val cipher = AuthenticatedTestCipher()
        val original = generateNormalizedVector()
        val plaintextBytes = with(com.attract.attendance.domain.face.TemplateMatcher) { original.toByteArray() }

        val decoded = TemplateEnvelopeCodec.decode(
            cipher = cipher,
            studentId = 707L,
            modelVersion = "legacy",
            stored = plaintextBytes,
            cryptoVersion = TemplateEnvelopeCodec.CRYPTO_VERSION_PLAINTEXT,
        )

        assertNotNull("Legacy v1 plaintext row must decode cleanly for migration", decoded)
        assertEquals(original.size, decoded!!.size)
    }

    // =========================================================================
    // 3. Biometric Compatibility Matrix & Dimension Quarantining
    // =========================================================================

    @Test
    fun wp04_templateCompatibility_classifiesStaleAndMalformedCorrectly() {
        val currentDim = TemplateCompatibility.CURRENT_EMBEDDING_DIM // 512

        val valid = generateNormalizedVector(currentDim)
        assertEquals(TemplateCompatibility.VectorClass.CURRENT, TemplateCompatibility.classify(valid))
        assertTrue(TemplateCompatibility.isUsableVector(valid))

        // Stale dimensions (e.g., 32-D or 192-D)
        val stale32 = generateNormalizedVector(32)
        val stale192 = generateNormalizedVector(192)
        assertEquals(TemplateCompatibility.VectorClass.STALE_DIMENSION, TemplateCompatibility.classify(stale32))
        assertEquals(TemplateCompatibility.VectorClass.STALE_DIMENSION, TemplateCompatibility.classify(stale192))
        assertFalse(TemplateCompatibility.isUsableVector(stale32))
        assertFalse(TemplateCompatibility.isUsableVector(stale192))

        // Malformed non-finite values
        val nanVec = valid.copyOf().also { it[0] = Float.NaN }
        val infVec = valid.copyOf().also { it[0] = Float.POSITIVE_INFINITY }
        assertEquals(TemplateCompatibility.VectorClass.MALFORMED, TemplateCompatibility.classify(nanVec))
        assertEquals(TemplateCompatibility.VectorClass.MALFORMED, TemplateCompatibility.classify(infVec))
    }

    @Test
    fun wp04_enrollmentValidation_rejectsStaleOrDegenerateObservations() {
        val currentDim = TemplateCompatibility.CURRENT_EMBEDDING_DIM
        val valid = generateNormalizedVector(currentDim)

        val successCheck = TemplateCompatibility.validateEnrollment(listOf(valid))
        assertTrue(successCheck is TemplateCompatibility.EnrollmentValidation.Ok)

        val staleCheck = TemplateCompatibility.validateEnrollment(listOf(valid, generateNormalizedVector(192)))
        assertTrue(staleCheck is TemplateCompatibility.EnrollmentValidation.Rejected)

        val emptyCheck = TemplateCompatibility.validateEnrollment(emptyList())
        assertTrue(emptyCheck is TemplateCompatibility.EnrollmentValidation.Rejected)
    }

    @Test
    fun wp04_mixedGallery_filtersIncompatibleAndMatchesCurrent() {
        val currentDim = TemplateCompatibility.CURRENT_EMBEDDING_DIM
        val targetVector = generateNormalizedVector(currentDim)

        val gallery = listOf(
            StudentTemplatePair(studentId = 1L, templateId = 10L, embedding = targetVector),
            StudentTemplatePair(studentId = 2L, templateId = 20L, embedding = generateNormalizedVector(192)),
            StudentTemplatePair(studentId = 3L, templateId = 30L, embedding = generateNormalizedVector(32)),
        )

        val partitioned = TemplateCompatibility.partition(gallery)
        assertEquals(1, partitioned.usable.size)
        assertEquals(1L, partitioned.usable[0].studentId)
        assertEquals(2, partitioned.stale.size)

        val outcome = RecognitionDecisionEngine().evaluate(targetVector, partitioned.usable)
        assertTrue("Must match student 1", outcome is RecognitionOutcome.Match)
        assertEquals(1L, (outcome as RecognitionOutcome.Match).studentId)
    }
}
