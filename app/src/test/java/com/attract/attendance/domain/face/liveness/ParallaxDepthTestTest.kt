package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult
import com.attract.attendance.domain.face.Point3
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unit tests for passive 3D motion-parallax depth verification (doc-20 WP7.1).
 */
class ParallaxDepthTestTest {

    private fun createCanonicalFaceLandmarks(zFactor: Float = 1.0f): List<Point3> {
        // Canonical 3D human head with realistic facial proportions relative to IOD (0.30):
        // Eyes at (0.35, 0.45) and (0.65, 0.45) -> IOD = 0.30
        // Nose tip protruding forward (z = +0.25)
        // Cheeks/ears receding backward (z = -0.25)
        return List(478) { idx ->
            when (idx) {
                // Nose points (protrude forward)
                1 -> Point3(0.50f, 0.52f, 0.25f * zFactor)
                4, 5, 6, 168, 195, 197 -> Point3(0.50f, 0.44f, 0.18f * zFactor)
                // Left & Right eyes (reference plane)
                33 -> Point3(0.35f, 0.45f, 0.0f)
                263 -> Point3(0.65f, 0.45f, 0.0f)
                133 -> Point3(0.42f, 0.45f, 0.02f * zFactor)
                362 -> Point3(0.58f, 0.45f, 0.02f * zFactor)
                // Forehead & Chin
                10 -> Point3(0.50f, 0.18f, 0.05f * zFactor)
                152 -> Point3(0.50f, 0.82f, 0.02f * zFactor)
                // Outer jaw / ears (recede backward)
                234, 127, 93, 132, 58 -> Point3(0.20f, 0.50f, -0.22f * zFactor)
                454, 356, 323, 361, 288 -> Point3(0.80f, 0.50f, -0.22f * zFactor)
                // Forehead sides
                67, 109, 21 -> Point3(0.32f, 0.25f, -0.05f * zFactor)
                297, 338, 251 -> Point3(0.68f, 0.25f, -0.05f * zFactor)
                // Cheeks / Mouth
                172, 136, 365, 397 -> Point3(0.50f, 0.70f, 0.08f * zFactor)
                else -> {
                    val angle = idx * (2.0 * Math.PI / 478.0)
                    val x = 0.5f + 0.25f * cos(angle).toFloat()
                    val y = 0.5f + 0.35f * sin(angle).toFloat()
                    Point3(x, y, 0.0f)
                }
            }
        }
    }

    private fun rotateY(pts: List<Point3>, angleRad: Double): List<Point3> {
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        return pts.map { p ->
            val cx = p.x - 0.5f
            val cz = p.z
            val rx = cx * cosA + cz * sinA
            val rz = -cx * sinA + cz * cosA
            Point3(rx + 0.5f, p.y, rz)
        }
    }

    @Test
    fun insufficientFrames_returnsInconclusive() {
        val frame = FaceMeshResult(
            landmarks = createCanonicalFaceLandmarks(),
            yawDeg = 0f,
            pitchDeg = 0f,
            rollDeg = 0f,
            eyeBlinkLeft = 0f,
            eyeBlinkRight = 0f,
            jawOpen = 0f,
            mouthSmile = 0f,
            faceCount = 1
        )
        val res = ParallaxDepthTest.evaluate(listOf(frame))
        assertTrue(res.depthInconclusive)
        assertNull(res.depthScore)
    }

    @Test
    fun insufficientYawDelta_returnsInconclusive() {
        val pts = createCanonicalFaceLandmarks()
        val f1 = FaceMeshResult(landmarks = pts, yawDeg = 0f, pitchDeg = 0f, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f, jawOpen = 0f, mouthSmile = 0f, faceCount = 1)
        val f2 = FaceMeshResult(landmarks = pts, yawDeg = 1.5f, pitchDeg = 0f, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f, jawOpen = 0f, mouthSmile = 0f, faceCount = 1)

        val res = ParallaxDepthTest.evaluate(listOf(f1, f2), minYawDeltaDeg = 4.0f)
        assertTrue(res.depthInconclusive)
        assertNull(res.depthScore)
    }

    @Test
    fun true3DFace_withAngularDelta_yieldsHighDepthScore() {
        val basePts = createCanonicalFaceLandmarks(zFactor = 1.0f)
        val rotPts = rotateY(basePts, Math.toRadians(8.0))

        val f1 = FaceMeshResult(landmarks = basePts, yawDeg = 0f, pitchDeg = 0f, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f, jawOpen = 0f, mouthSmile = 0f, faceCount = 1)
        val f2 = FaceMeshResult(landmarks = rotPts, yawDeg = 8.0f, pitchDeg = 0f, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f, jawOpen = 0f, mouthSmile = 0f, faceCount = 1)

        val res = ParallaxDepthTest.evaluate(listOf(f1, f2), minYawDeltaDeg = 4.0f)
        assertFalse(res.depthInconclusive)
        assertNotNull(res.depthScore)
        assertTrue("Expected depthScore >= 0.50f for true 3D face, was ${res.depthScore}", (res.depthScore ?: 0f) >= 0.50f)
    }

    @Test
    fun flatPlanarSurface_yieldsLowDepthScore() {
        // Flat face: all z = 0. Under affine homography, points conform to a single plane
        val flatBase = createCanonicalFaceLandmarks(zFactor = 0.0f)
        val h = Homography.solve4Points(
            listOf(flatBase[10], flatBase[152], flatBase[234], flatBase[454]),
            listOf(
                Point3(flatBase[10].x + 0.02f, flatBase[10].y, 0f),
                Point3(flatBase[152].x - 0.01f, flatBase[152].y, 0f),
                Point3(flatBase[234].x, flatBase[234].y + 0.01f, 0f),
                Point3(flatBase[454].x + 0.01f, flatBase[454].y, 0f),
            )
        )!!

        val transformed = flatBase.map { p ->
            val (tx, ty) = h.transform(p.x.toDouble(), p.y.toDouble())
            Point3(tx.toFloat(), ty.toFloat(), 0f)
        }

        val f1 = FaceMeshResult(landmarks = flatBase, yawDeg = 0f, pitchDeg = 0f, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f, jawOpen = 0f, mouthSmile = 0f, faceCount = 1)
        val f2 = FaceMeshResult(landmarks = transformed, yawDeg = 6.0f, pitchDeg = 0f, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f, jawOpen = 0f, mouthSmile = 0f, faceCount = 1)

        val res = ParallaxDepthTest.evaluate(listOf(f1, f2), minYawDeltaDeg = 4.0f)
        assertFalse(res.depthInconclusive)
        assertNotNull(res.depthScore)
        assertTrue("Expected depthScore < 0.20f for flat surface, was ${res.depthScore}", (res.depthScore ?: 0f) < 0.20f)
    }
}
