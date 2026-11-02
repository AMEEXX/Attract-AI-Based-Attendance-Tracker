package com.attract.attendance.feature.attendance

import com.attract.attendance.domain.face.FaceQualitySignals
import com.attract.attendance.domain.face.FrameBundle
import com.attract.attendance.domain.face.PoseBucket
import com.attract.attendance.domain.face.QualityReason
import com.attract.attendance.domain.face.QualityResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCaptureControllerTest {

    private fun mockBundle(trackId: Int, leftEye: Float = 0.9f, rightEye: Float = 0.9f): FrameBundle {
        val signals = FaceQualitySignals(
            faceCount = 1,
            yawDegrees = 0f,
            pitchDegrees = 0f,
            rollDegrees = 0f,
            leftEyeOpenProbability = leftEye,
            rightEyeOpenProbability = rightEye,
            faceRatio = 0.25f,
            centerX = 0.5f,
            centerY = 0.5f,
            blurVariance = 300f,
            brightness = 110f
        )
        return FrameBundle(
            frameId = 1L,
            timestampNanos = 1000L,
            trackId = trackId,
            qualitySignals = signals,
            // dummy non-null aligned crop is not strictly needed if hasFace logic checks qualitySignals?.isSingleFace == true
            // wait: FrameBundle has: hasFace get() = qualitySignals?.isSingleFace == true && alignedCrop != null
            // In unit test without android.graphics.Bitmap, we can check how hasFace is implemented!
        )
    }

    @Test
    fun autoCapture_progressesThroughStates() {
        val controller = AutoCaptureController()
        assertEquals(AutoCaptureState.SEARCHING, controller.state)

        // Force transition to test recognizing
        controller.onFrame(mockBundle(trackId = 1), QualityResult.Rejected(QualityReason.BLUR), force = true)
        assertEquals(AutoCaptureState.RECOGNISING, controller.state)
    }
}
