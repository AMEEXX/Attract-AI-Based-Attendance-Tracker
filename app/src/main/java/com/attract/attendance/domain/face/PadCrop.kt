package com.attract.attendance.domain.face

/**
 * Pure crop calculator for presentation-attack detection (MiniFASNet scales 2.7 and 4.0, doc-20 WP6.3).
 * Mirrors Silent-Face-Anti-Spoofing CropImage._get_new_box to guarantee input parity.
 */
object PadCrop {

    data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = maxOf(0, right - left)
        val height: Int get() = maxOf(0, bottom - top)
    }

    /**
     * Calculates expanded bounding box clamped inside image dimensions.
     */
    fun box(srcW: Int, srcH: Int, faceBox: FaceBox, scale: Float): CropRect {
        val boxW = faceBox.width
        val boxH = faceBox.height
        val maxScaleH = if (boxH > 0f) (srcH - 1f) / boxH else scale
        val maxScaleW = if (boxW > 0f) (srcW - 1f) / boxW else scale
        val effScale = minOf(maxScaleH, minOf(maxScaleW, scale))

        val newW = boxW * effScale
        val newH = boxH * effScale
        val cx = (faceBox.left + faceBox.right) / 2f
        val cy = (faceBox.top + faceBox.bottom) / 2f

        var left = cx - newW / 2f
        var top = cy - newH / 2f
        var right = cx + newW / 2f
        var bottom = cy + newH / 2f

        if (left < 0f) {
            right -= left
            left = 0f
        }
        if (top < 0f) {
            bottom -= top
            top = 0f
        }
        if (right > srcW.toFloat()) {
            val shift = right - srcW.toFloat()
            left = maxOf(0f, left - shift)
            right = srcW.toFloat()
        }
        if (bottom > srcH.toFloat()) {
            val shift = bottom - srcH.toFloat()
            top = maxOf(0f, top - shift)
            bottom = srcH.toFloat()
        }

        return CropRect(
            left = left.toInt().coerceIn(0, srcW),
            top = top.toInt().coerceIn(0, srcH),
            right = right.toInt().coerceIn(0, srcW),
            bottom = bottom.toInt().coerceIn(0, srcH)
        )
    }
}
