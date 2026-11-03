package com.attract.attendance.domain.face

import android.graphics.Bitmap

/**
 * Immutable timed frame bundle capturing the camera frame, YOLO detections,
 * computed quality signals, and aligned crop from a single atomic capture.
 */
data class FrameBundle(
    val frameId: Long,
    val timestampNanos: Long,
    val lifecycleGeneration: Long = 0L,
    val trackId: Int = 1,
    val lensFacing: Int = 0, // 0 = FRONT, 1 = BACK
    val rotationDegrees: Int = 0,
    val isMirrored: Boolean = true,
    val detections: List<YoloFaceDetector.DetectedFace> = emptyList(),
    val qualitySignals: FaceQualitySignals? = null,
    val alignedCrop: Bitmap? = null,
    val fusedCrop: Bitmap? = null,
    val wideCrop: Bitmap? = null,
    val padCrops: Pair<IntArray, IntArray>? = null,
    val meshResult: FaceMeshResult? = null,
    /**
     * Doc 22: presentation-attack signals for THIS frame. Pixel statistics are measured on the INNER 1.0x
     * aligned face crop; [PresentationAttackSignals.padScore] is MiniFASNetV2 on the 2.7x wide crop.
     */
    val patSignals: PresentationAttackSignals? = null,
    val trackFrameCount: Int = 1,
    val error: String? = null,
) {
    val hasFace: Boolean get() = qualitySignals?.isSingleFace == true && alignedCrop != null
}
