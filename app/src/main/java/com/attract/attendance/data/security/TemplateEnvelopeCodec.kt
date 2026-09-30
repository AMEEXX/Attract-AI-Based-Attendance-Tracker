package com.attract.attendance.data.security

import com.attract.attendance.lockdown.domain.EmbeddingCipher
import com.attract.attendance.lockdown.domain.EncryptedEmbedding

/**
 * Envelope codec for face-template blobs at rest (LLD-10 / LLD-13).
 *
 * Stored layout (crypto_version = 2):
 *   [keyVersion: 1 byte][GCM IV: 12 bytes][AES-GCM ciphertext+tag]
 *
 * crypto_version = 1 (or 0) marks legacy PLAINTEXT little-endian float bytes written
 * before encryption was wired; reads fall back transparently so existing installs
 * keep working. New writes are ALWAYS AEAD-encrypted when a cipher is present —
 * never silently downgraded.
 *
 * The cipher binds ciphertext to (studentId, modelVersion) via AAD, preventing
 * cross-student template swapping. Per-row binding uses a constant bind id because
 * the Room row id is only known post-insert.
 */
object TemplateEnvelopeCodec {

    const val CRYPTO_VERSION_PLAINTEXT = 1
    const val CRYPTO_VERSION_AEAD = 2

    private const val KEY_VERSION_BYTES = 1
    const val IV_SIZE_BYTES = 12
    const val TEMPLATE_BIND_ID = 0L

    /** Returns (blob, cryptoVersion). Throws if the cipher fails — callers fail closed. */
    fun encode(
        cipher: EmbeddingCipher?,
        studentId: Long,
        modelVersion: String,
        plaintextFloats: FloatArray,
    ): Pair<ByteArray, Int> {
        if (cipher == null) {
            @Suppress("RedundantSuppression")
            return with(com.attract.attendance.domain.face.TemplateMatcher) {
                plaintextFloats.toByteArray()
            } to CRYPTO_VERSION_PLAINTEXT
        }
        val plaintext = with(com.attract.attendance.domain.face.TemplateMatcher) {
            plaintextFloats.toByteArray()
        }
        val encrypted = cipher.encrypt(studentId, TEMPLATE_BIND_ID, modelVersion, plaintext)
        val blob = ByteArray(KEY_VERSION_BYTES + encrypted.iv.size + encrypted.ciphertext.size)
        blob[0] = encrypted.keyVersion.toByte()
        encrypted.iv.copyInto(blob, KEY_VERSION_BYTES)
        encrypted.ciphertext.copyInto(blob, KEY_VERSION_BYTES + encrypted.iv.size)
        return blob to CRYPTO_VERSION_AEAD
    }

    /**
     * Decodes a stored template back to its normalized float embedding.
     * Returns null when decryption/integrity fails (fail-closed: the row is skipped,
     * never fed as garbage into similarity matching).
     */
    fun decode(
        cipher: EmbeddingCipher?,
        studentId: Long,
        modelVersion: String,
        stored: ByteArray,
        cryptoVersion: Int,
    ): FloatArray? {
        return runCatching {
            if (cryptoVersion >= CRYPTO_VERSION_AEAD && cipher != null) {
                if (stored.size <= KEY_VERSION_BYTES + IV_SIZE_BYTES) return@runCatching null
                val keyVersion = stored[0].toInt() and 0xFF
                val iv = stored.copyOfRange(KEY_VERSION_BYTES, KEY_VERSION_BYTES + IV_SIZE_BYTES)
                val ciphertext = stored.copyOfRange(KEY_VERSION_BYTES + IV_SIZE_BYTES, stored.size)
                val plaintext = cipher.decrypt(
                    studentId,
                    TEMPLATE_BIND_ID,
                    modelVersion,
                    EncryptedEmbedding(
                        formatVersion = 1,
                        keyVersion = keyVersion,
                        iv = iv,
                        ciphertext = ciphertext,
                    ),
                )
                with(com.attract.attendance.domain.face.TemplateMatcher) { plaintext.toFloatArray() }
            } else {
                // Legacy plaintext row (cryptoVersion < 2) or cipher unavailable.
                with(com.attract.attendance.domain.face.TemplateMatcher) { stored.toFloatArray() }
            }
        }.getOrNull()
    }
}
