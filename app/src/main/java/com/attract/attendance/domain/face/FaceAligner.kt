package com.attract.attendance.domain.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Aligns a face using 5 facial landmarks to the canonical ArcFace 112x112 position
 * using a validated 5-point least-squares similarity transform (Umeyama algorithm).
 *
 * ArcFace models are trained on aligned faces. Alignment normalizes scale, in-plane rotation,
 * and translation, boosting verification accuracy across varying poses.
 */
object FaceAligner {

    const val ALIGNED_SIZE = 112
    const val PREPROCESSING_VERSION = "arcface_5pt_similarity_v4"

    /**
     * Canonical ArcFace 112x112 reference landmark coordinates.
     * [0]=left eye, [1]=right eye, [2]=nose tip, [3]=left mouth corner, [4]=right mouth corner.
     */
    val CANONICAL_LANDMARKS = listOf(
        PointF(38.2946f, 51.6963f),  // left eye
        PointF(73.5318f, 51.5014f),  // right eye
        PointF(56.0252f, 71.7366f),  // nose tip
        PointF(41.5493f, 92.3655f),  // left mouth corner
        PointF(70.7299f, 92.2041f),  // right mouth corner
    )

    private val dstMeanX = CANONICAL_LANDMARKS.map { it.x }.average().toFloat()
    private val dstMeanY = CANONICAL_LANDMARKS.map { it.y }.average().toFloat()

    /**
     * Aligns [bitmap] using the given [landmarks] (in bitmap coordinates).
     *
     * @param bitmap Original frame containing the face
     * @param landmarks 5 facial landmarks in order: [leftEye, rightEye, nose, leftMouth, rightMouth]
     * @return 112x112 aligned face bitmap ready for ArcFace embedding extraction
     */
    fun align(bitmap: Bitmap, landmarks: List<PointF>): Bitmap {
        val matrix = computeSimilarityMatrix(landmarks)
            ?: fallbackEyeSimilarityMatrix(landmarks)

        val alignedBitmap = Bitmap.createBitmap(ALIGNED_SIZE, ALIGNED_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(alignedBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return alignedBitmap
    }

    /**
     * Computes the 5-point least-squares similarity transform matrix mapping detected
     * landmarks to canonical positions.
     *
     * Returns null if landmarks are degenerate, non-finite, or have excessive reprojection residual.
     */
    fun computeSimilarityMatrix(landmarks: List<PointF>): Matrix? {
        if (landmarks.size < 5) return null

        // 1. Invariant check: all points must be finite
        if (!landmarks.take(5).all { it.x.isFinite() && it.y.isFinite() }) {
            return null
        }

        val leftEye = landmarks[0]
        val rightEye = landmarks[1]
        val eyeDistance = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y)
        if (eyeDistance < 8f) {
            // Degenerate face geometry
            return null
        }

        // 2. Compute centroids
        var srcMeanX = 0f
        var srcMeanY = 0f
        for (i in 0 until 5) {
            srcMeanX += landmarks[i].x
            srcMeanY += landmarks[i].y
        }
        srcMeanX /= 5f
        srcMeanY /= 5f

        // 3. 2D Least-Squares Similarity solution (Umeyama closed-form)
        var sumD = 0f
        var sumA = 0f
        var sumB = 0f

        for (i in 0 until 5) {
            val srcX = landmarks[i].x - srcMeanX
            val srcY = landmarks[i].y - srcMeanY
            val dstX = CANONICAL_LANDMARKS[i].x - dstMeanX
            val dstY = CANONICAL_LANDMARKS[i].y - dstMeanY

            sumD += srcX * srcX + srcY * srcY
            sumA += srcX * dstX + srcY * dstY
            sumB += srcX * dstY - srcY * dstX
        }

        if (sumD < 1e-4f) return null

        val a = sumA / sumD
        val b = sumB / sumD
        val scale = sqrt(a * a + b * b)

        // Sanity check on scale (must be positive and reasonable)
        if (scale < 0.01f || scale > 50f) return null

        val tx = dstMeanX - (a * srcMeanX - b * srcMeanY)
        val ty = dstMeanY - (b * srcMeanX + a * srcMeanY)

        // 4. Reprojection residual check
        var totalSquaredResidual = 0f
        for (i in 0 until 5) {
            val projX = a * landmarks[i].x - b * landmarks[i].y + tx
            val projY = b * landmarks[i].x + a * landmarks[i].y + ty
            val dx = projX - CANONICAL_LANDMARKS[i].x
            val dy = projY - CANONICAL_LANDMARKS[i].y
            totalSquaredResidual += dx * dx + dy * dy
        }
        val meanSquaredError = totalSquaredResidual / 5f

        // Rejection of scrambled / incompatible landmarks
        if (meanSquaredError > 450f) {
            return null
        }

        val matrix = Matrix()
        matrix.setValues(floatArrayOf(
            a, -b, tx,
            b,  a, ty,
            0f, 0f, 1f
        ))
        return matrix
    }

    /**
     * Robust 2-eye fallback when 5-point residual fails or mouth is occluded.
     */
    fun fallbackEyeSimilarityMatrix(landmarks: List<PointF>): Matrix {
        val srcLeft = landmarks[0]
        val srcRight = landmarks[1]
        val dstLeft = CANONICAL_LANDMARKS[0]
        val dstRight = CANONICAL_LANDMARKS[1]

        val srcDx = srcRight.x - srcLeft.x
        val srcDy = srcRight.y - srcLeft.y
        val srcDist = kotlin.math.max(hypot(srcDx, srcDy), 1e-4f)
        val srcAngle = Math.toDegrees(kotlin.math.atan2(srcDy.toDouble(), srcDx.toDouble())).toFloat()

        val dstDx = dstRight.x - dstLeft.x
        val dstDy = dstRight.y - dstLeft.y
        val dstDist = hypot(dstDx, dstDy)
        val dstAngle = Math.toDegrees(kotlin.math.atan2(dstDy.toDouble(), dstDx.toDouble())).toFloat()

        val scale = dstDist / srcDist
        val rotAngle = dstAngle - srcAngle

        val srcCenterX = (srcLeft.x + srcRight.x) / 2f
        val srcCenterY = (srcLeft.y + srcRight.y) / 2f
        val dstCenterX = (dstLeft.x + dstRight.x) / 2f
        val dstCenterY = (dstLeft.y + dstRight.y) / 2f

        val matrix = Matrix()
        matrix.postTranslate(-srcCenterX, -srcCenterY)
        matrix.postRotate(rotAngle)
        matrix.postScale(scale, scale)
        matrix.postTranslate(dstCenterX, dstCenterY)
        return matrix
    }
}
