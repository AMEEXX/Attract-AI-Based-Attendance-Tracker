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
        // Doc 22: 15 deg = exp8 real-pan protocol (p5 residual 0.042 >= locked 0.035)
        val rotPts = rotateY(basePts, Math.toRadians(15.0))

        val f1 = FaceMeshResult(landmarks = basePts, yawDeg = 0f, pitchDeg = 0f, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f, jawOpen = 0f, mouthSmile = 0f, faceCount = 1)
        val f2 = FaceMeshResult(landmarks = rotPts, yawDeg = 15.0f, pitchDeg = 0f, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f, jawOpen = 0f, mouthSmile = 0f, faceCount = 1)

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

/**
 * Doc 22 WP-E additions: locked defaults (exp8), depthScoreFor mapping, pitch-counts-as-delta,
 * and the rule that an inconclusive depth result can NEVER become LOW risk.
 */
class ParallaxDepthTestDoc22Test {

    private fun face(zFactor: Float = 1f): List<Point3> = List(478) { idx ->
        when (idx) {
            1 -> Point3(0.50f, 0.52f, 0.25f * zFactor)
            4, 5, 6, 168, 195, 197 -> Point3(0.50f, 0.44f, 0.18f * zFactor)
            33 -> Point3(0.35f, 0.45f, 0f)
            263 -> Point3(0.65f, 0.45f, 0f)
            133 -> Point3(0.42f, 0.45f, 0.02f * zFactor)
            362 -> Point3(0.58f, 0.45f, 0.02f * zFactor)
            10 -> Point3(0.50f, 0.18f, 0.05f * zFactor)
            152 -> Point3(0.50f, 0.82f, 0.02f * zFactor)
            234, 127, 93, 132, 58 -> Point3(0.20f, 0.50f, -0.22f * zFactor)
            454, 356, 323, 361, 288 -> Point3(0.80f, 0.50f, -0.22f * zFactor)
            67, 109, 21 -> Point3(0.32f, 0.25f, -0.05f * zFactor)
            297, 338, 251 -> Point3(0.68f, 0.25f, -0.05f * zFactor)
            172, 136, 365, 397 -> Point3(0.50f, 0.70f, 0.08f * zFactor)
            else -> {
                val a = idx * (2.0 * Math.PI / 478.0)
                Point3(0.5f + 0.25f * cos(a).toFloat(), 0.5f + 0.35f * sin(a).toFloat(), 0f)
            }
        }
    }

    private fun rotY(pts: List<Point3>, deg: Double): List<Point3> {
        val c = cos(Math.toRadians(deg)).toFloat(); val s = sin(Math.toRadians(deg)).toFloat()
        return pts.map { p -> val x = p.x - 0.5f; Point3(x * c + p.z * s + 0.5f, p.y, -x * s + p.z * c) }
    }

    private fun rotX(pts: List<Point3>, deg: Double): List<Point3> {
        val c = cos(Math.toRadians(deg)).toFloat(); val s = sin(Math.toRadians(deg)).toFloat()
        return pts.map { p -> val y = p.y - 0.5f; Point3(p.x, y * c - p.z * s + 0.5f, y * s + p.z * c) }
    }

    private fun mesh(pts: List<Point3>, yaw: Float = 0f, pitch: Float = 0f) = FaceMeshResult(
        landmarks = pts, yawDeg = yaw, pitchDeg = pitch, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f,
    )

    @Test
    fun defaults_areExp8Values() {
        org.junit.Assert.assertEquals(8.0f, ParallaxDepthTest.DEFAULT_MIN_POSE_DELTA_DEG, 0f)
        org.junit.Assert.assertEquals(0.035f, ParallaxDepthTest.DEFAULT_MIN_PARALLAX_RESIDUAL, 0f)
    }

    @Test
    fun depthScoreFor_mapping() {
        val m = ParallaxDepthTest.DEFAULT_MIN_PARALLAX_RESIDUAL
        org.junit.Assert.assertEquals(0.50f, ParallaxDepthTest.depthScoreFor(m, m), 1e-6f)
        org.junit.Assert.assertEquals(0.20f, ParallaxDepthTest.depthScoreFor(0.4f * m, m), 1e-6f)
        org.junit.Assert.assertEquals(0f, ParallaxDepthTest.depthScoreFor(0f, m), 0f)
        org.junit.Assert.assertEquals(1f, ParallaxDepthTest.depthScoreFor(10f * m, m), 0f)
        org.junit.Assert.assertEquals(0f, ParallaxDepthTest.depthScoreFor(-1f, m), 0f)
        // default parameter = locked residual
        org.junit.Assert.assertEquals(0.50f, ParallaxDepthTest.depthScoreFor(m), 1e-6f)
    }

    @Test
    fun poseDelta_isMaxOfYawAndPitch() {
        val a = mesh(face(), yaw = 1f, pitch = 2f)
        val b = mesh(face(), yaw = 4f, pitch = 12f)
        org.junit.Assert.assertEquals(10f, ParallaxDepthTest.poseDelta(a, b), 1e-6f)
    }

    @Test
    fun belowDefaultPoseDelta_isInconclusive_andReportsDelta() {
        val base = face()
        val res = ParallaxDepthTest.evaluate(listOf(mesh(base, yaw = 0f), mesh(rotY(base, 7.9), yaw = 7.9f)))
        assertTrue(res.depthInconclusive)
        assertNull(res.depthScore)
        org.junit.Assert.assertEquals(7.9f, res.poseDeltaDeg, 1e-4f)
    }

    @Test
    fun real3DTurn15Deg_isConclusiveLive() {
        // exp8 protocol: real 15 deg pan (p5 residual 0.042). The synthetic face needs ~11 deg to clear 0.035.
        val base = face()
        val res = ParallaxDepthTest.evaluate(listOf(mesh(base, yaw = 0f), mesh(rotY(base, 15.0), yaw = 15f)))
        assertFalse(res.depthInconclusive)
        assertTrue("residual ${res.residual}", res.residual >= ParallaxDepthTest.DEFAULT_MIN_PARALLAX_RESIDUAL)
        assertTrue((res.depthScore ?: 0f) >= RiskFusion.DEPTH_LIVE)
    }

    @Test
    fun pitchOnlyNod_countsAsPoseDelta_andIsLive() {
        val base = face()
        val res = ParallaxDepthTest.evaluate(listOf(mesh(base, pitch = 0f), mesh(rotX(base, 12.0), pitch = 12f)))
        assertFalse("pitch-only nod must count towards the 8 deg delta", res.depthInconclusive)
        assertTrue("depthScore ${res.depthScore}", (res.depthScore ?: 0f) >= RiskFusion.DEPTH_LIVE)
    }

    @Test
    fun flatPhotoTurnedAtDefaultDelta_isConclusiveFlat() {
        val flat = face(zFactor = 0f)
        // A planar photo turned by 10 deg projects as a planar (affine) map of the same plane.
        val turned = rotY(flat, 10.0).map { Point3(it.x, it.y, 0f) }
        val res = ParallaxDepthTest.evaluate(listOf(mesh(flat, yaw = 0f), mesh(turned, yaw = 10f)))
        assertFalse(res.depthInconclusive)
        assertTrue("depthScore ${res.depthScore}", (res.depthScore ?: 1f) < RiskFusion.DEPTH_FLAT)
    }

    @Test
    fun inconclusiveDepth_neverMapsToLowRisk() {
        val base = face()
        val res = ParallaxDepthTest.evaluate(listOf(mesh(base), mesh(base, yaw = 2f)))
        assertTrue(res.depthInconclusive)
        val policy = com.attract.attendance.domain.face.LivenessPolicy()
        for (pad in listOf(null, 0.01f, 0.5f, 0.99f)) {
            for (blink in listOf(false, true)) {
                val e = LivenessEvidence(
                    padScore = pad, depthScore = res.depthScore, depthInconclusive = res.depthInconclusive,
                    blinkSeen = blink,
                )
                org.junit.Assert.assertEquals(
                    "pad=$pad blink=$blink", LivenessRisk.MEDIUM, RiskFusion.decide(e, policy),
                )
            }
        }
    }

    @Test
    fun missingLandmarks_isInconclusive() {
        val short = List(10) { Point3(0.5f, 0.5f) }
        val res = ParallaxDepthTest.evaluate(listOf(mesh(short, yaw = 0f), mesh(short, yaw = 20f)))
        assertTrue(res.depthInconclusive)
        org.junit.Assert.assertEquals(20f, res.poseDeltaDeg, 1e-6f)
    }
}
