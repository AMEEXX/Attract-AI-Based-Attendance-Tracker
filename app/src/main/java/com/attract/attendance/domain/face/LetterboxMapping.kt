package com.attract.attendance.domain.face

import kotlin.math.roundToInt

/**
 * Pure helper for aspect-ratio preserving letterboxing between camera frame and YOLO 640x640 canvas (doc-20 WP4.1).
 */
data class LetterboxMapping(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val targetWidth: Int = 640,
    val targetHeight: Int = 640,
) {
    fun toFrameX(xOnCanvas: Float): Float = (xOnCanvas - padX) / scale
    fun toFrameY(yOnCanvas: Float): Float = (yOnCanvas - padY) / scale

    fun toCanvasX(xOnFrame: Float): Float = xOnFrame * scale + padX
    fun toCanvasY(yOnFrame: Float): Float = yOnFrame * scale + padY

    companion object {
        fun create(frameWidth: Int, frameHeight: Int, targetSize: Int = 640): LetterboxMapping {
            val scale = targetSize.toFloat() / maxOf(frameWidth, frameHeight).toFloat()
            val newW = (frameWidth * scale).roundToInt()
            val newH = (frameHeight * scale).roundToInt()
            val padX = (targetSize - newW) / 2f
            val padY = (targetSize - newH) / 2f
            return LetterboxMapping(
                scale = scale,
                padX = padX,
                padY = padY,
                targetWidth = targetSize,
                targetHeight = targetSize
            )
        }
    }
}
