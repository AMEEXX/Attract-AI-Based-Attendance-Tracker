package com.attract.attendance.domain.face

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object TemplateMatcher {

    /**
     * Computes the Cosine Similarity between two float embedding vectors.
     * Returns a float value in the range [-1.0, 1.0], where 1.0 represents identical vectors.
     */
    fun cosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        require(vectorA.size == vectorB.size) {
            "Embedding dimensions must match (A: ${vectorA.size}, B: ${vectorB.size})"
        }

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }

        if (normA == 0.0f || normB == 0.0f) {
            return 0.0f
        }

        return dotProduct / (sqrt(normA) * sqrt(normB))
    }

    /**
     * Finds the best matching template from a list of candidate embeddings.
     * Returns a Pair of (bestCandidateIndex, highestSimilarityScore).
     */
    fun findBestMatch(
        targetEmbedding: FloatArray,
        candidates: List<FloatArray>,
    ): Pair<Int, Float> {
        if (candidates.isEmpty()) return Pair(-1, 0.0f)

        var bestIndex = -1
        var highestScore = -1.0f

        for (i in candidates.indices) {
            val score = cosineSimilarity(targetEmbedding, candidates[i])
            if (score > highestScore) {
                highestScore = score
                bestIndex = i
            }
        }

        return Pair(bestIndex, highestScore)
    }

    fun FloatArray.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(this.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (value in this) {
            buffer.putFloat(value)
        }
        return buffer.array()
    }

    fun ByteArray.toFloatArray(): FloatArray {
        if (this.isEmpty()) return FloatArray(0)
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(this.size / 4)
        for (i in result.indices) {
            result[i] = buffer.float
        }
        return result
    }
}
