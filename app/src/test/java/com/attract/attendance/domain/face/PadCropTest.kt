package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PadCropTest {

    @Test
    fun padCrop_scalesAroundCenterAndClamps() {
        val srcW = 1280
        val srcH = 720
        val faceBox = FaceBox(500f, 200f, 700f, 400f) // 200x200 at center (600, 300)

        val crop = PadCrop.box(srcW, srcH, faceBox, scale = 2.7f)
        // 200 * 2.7 = 540
        assertEquals(540, crop.width)
        assertEquals(540, crop.height)
        assertTrue(crop.left >= 0)
        assertTrue(crop.right <= srcW)
        assertTrue(crop.top >= 0)
        assertTrue(crop.bottom <= srcH)
    }

    @Test
    fun padCrop_nearEdge_shiftsIntoImage() {
        val srcW = 640
        val srcH = 480
        val faceBox = FaceBox(10f, 10f, 110f, 110f) // 100x100 near top-left

        val crop = PadCrop.box(srcW, srcH, faceBox, scale = 2.0f)
        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(200, crop.width)
        assertEquals(200, crop.height)
    }
}
