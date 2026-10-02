package com.attract.attendance.domain.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Aligns a face using facial landmarks to the canonical ArcFace 112x112 position.
 *
 * ArcFace models are trained on aligned faces. Alignment normalizes scale, in-plane rotation,
 * and translation, drastically boosting verification accuracy across varying poses.
 */
object FaceAligner {

    const val ALIGNED_SIZE = 112

    /**
     * Canonical ArcFace 112x112 reference landmark coordinates.
     */
    val CANONICAL_LANDMARKS = listOf(
        PointF(38.2946f, 51.6963f),  // left eye
        PointF(73.5318f, 51.5014f),  // right eye
        PointF(56.0252f, 71.7366f),  // nose tip
        PointF(41.5493f, 92.3655f),  // left mouth corner
        PointF(70.7299f, 92.2041f)   // right mouth corner
    )

    /**
     * Aligns [bitmap] using the given [landmarks] (in bitmap coordinates).
     *
     * @param bitmap Original frame containing the face
     * @param landmarks 5 facial landmarks in order: [leftEye, rightEye, nose, leftMouth, rightMouth]
     * @return 112x112 aligned face bitmap ready for ArcFace embedding extraction
     */
    fun align(bitmap: Bitmap, landmarks: List<PointF>): Bitmap {
        require(landmarks.size >= 5) {
            "FaceAligner requires 5 landmarks, got ${landmarks.size}"
        }

        val matrix = computeSimilarityMatrix(landmarks)
        val alignedBitmap = Bitmap.createBitmap(ALIGNED_SIZE, ALIGNED_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(alignedBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)

        return alignedBitmap
    }

    /**
     * Computes the affine similarity transform matrix mapping detected eye landmarks to
     * canonical eye positions.
     */
    fun computeSimilarityMatrix(landmarks: List<PointF>): Matrix {
        val srcLeft = landmarks[0]
        val srcRight = landmarks[1]
        val dstLeft = CANONICAL_LANDMARKS[0]
        val dstRight = CANONICAL_LANDMARKS[1]

        val srcDx = srcRight.x - srcLeft.x
        val srcDy = srcRight.y - srcLeft.y
        val srcDist = sqrt(srcDx * srcDx + srcDy * srcDy)
        val srcAngle = Math.toDegrees(atan2(srcDy.toDouble(), srcDx.toDouble())).toFloat()

        val dstDx = dstRight.x - dstLeft.x
        val dstDy = dstRight.y - dstLeft.y
        val dstDist = sqrt(dstDx * dstDx + dstDy * dstDy)
        val dstAngle = Math.toDegrees(atan2(dstDy.toDouble(), dstDx.toDouble())).toFloat()

        val scale = dstDist / max(srcDist, 1e-6f)
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
