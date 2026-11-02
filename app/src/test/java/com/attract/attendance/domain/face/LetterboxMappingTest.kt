package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterboxMappingTest {

    @Test
    fun letterboxMapping_roundTripExact() {
        val mapping = LetterboxMapping.create(frameWidth = 1280, frameHeight = 720, targetSize = 640)
        // 1280x720: scale = 640 / 1280 = 0.5
        assertEquals(0.5f, mapping.scale, 0.001f)
        assertEquals(0f, mapping.padX, 0.001f)
        // newH = 360, padY = (640 - 360) / 2 = 140
        assertEquals(140f, mapping.padY, 0.001f)

        val origX = 300f
        val origY = 400f
        val canvasX = mapping.toCanvasX(origX)
        val canvasY = mapping.toCanvasY(origY)

        val backX = mapping.toFrameX(canvasX)
        val backY = mapping.toFrameY(canvasY)

        assertEquals(origX, backX, 0.001f)
        assertEquals(origY, backY, 0.001f)
    }
}
