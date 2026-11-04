package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult
import com.attract.attendance.domain.face.FaceQualitySignals
import com.attract.attendance.domain.face.LivenessReason
import com.attract.attendance.domain.face.LivenessResult
import com.attract.attendance.domain.face.Point3
import com.attract.attendance.domain.face.PresentationAttackSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Doc 22 WP-C: single-source history, 2-of-3 pixel veto, PAD median, MEDIUM -> ACTIVE_CHECK_REQUIRED. */
class LivenessOrchestratorTest {

    private val signals = FaceQualitySignals(
        faceCount = 1, yawDegrees = 0f, pitchDegrees = 0f, rollDegrees = 0f,
        leftEyeOpenProbability = null, rightEyeOpenProbability = null,
        faceRatio = 0.2f, centerX = 0.5f, centerY = 0.5f, blurVariance = 300f, brightness = 110f,
        faceLuma = 110f,
    )

    private val clean = PresentationAttackSignals(
        highFrequencyEnergy = 0.05f, specularHighlightRatio = 0.01f, centerTextureStdDev = 20f,
    )
    private val screenTrip = PresentationAttackSignals(
        highFrequencyEnergy = 0.40f, specularHighlightRatio = 0.01f, centerTextureStdDev = 20f,
    )

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

    private fun mesh(pts: List<Point3>, yaw: Float) = FaceMeshResult(
        landmarks = pts, yawDeg = yaw, pitchDeg = 0f, rollDeg = 0f, eyeBlinkLeft = 0f, eyeBlinkRight = 0f,
    )

    private var now = 1_000_000L
    private fun orch() = LivenessOrchestrator(clock = { now })

    // ---------------------------------------------------------------- MEDIUM is never Passed
    @Test
    fun noEvidence_isActiveCheckRequired_withTurnHeadGuidance() {
        val o = orch()
        o.onFrame(signals, clean, mesh(face(), 0f), padScore = 0.9f, nowMs = now)
        val v = o.evaluate(signals, now)
        assertEquals(LivenessRisk.MEDIUM, v.risk)
        assertTrue(v.result is LivenessResult.Rejected)
        assertEquals(LivenessReason.ACTIVE_CHECK_REQUIRED, (v.result as LivenessResult.Rejected).reason)
        assertEquals(LivenessGuidance.TURN_HEAD, v.guidance)
        assertTrue(v.evidence.depthInconclusive)
    }

    @Test
    fun real3DHeadTurnDuringPreview_withCleanPad_isLowPassed() {
        val o = orch()
        val base = face()
        o.onFrame(signals, clean, mesh(base, 0f), padScore = 0.9f, nowMs = now); now += 150
        o.onFrame(signals, clean, mesh(rotY(base, 7.0), 7f), padScore = 0.85f, nowMs = now); now += 150
        o.onFrame(signals, clean, mesh(rotY(base, 15.0), 15f), padScore = 0.95f, nowMs = now)
        val v = o.evaluate(signals, now)
        assertEquals(LivenessRisk.LOW, v.risk)
        assertEquals(LivenessResult.Passed, v.result)
        assertEquals(LivenessGuidance.NONE, v.guidance)
    }

    @Test
    fun liveDepthButSuspectPad_startsChallenge_andStaysMedium() {
        val o = orch()
        val base = face()
        o.onFrame(signals, clean, mesh(base, 0f), padScore = 0.01f, nowMs = now); now += 150
        o.onFrame(signals, clean, mesh(rotY(base, 15.0), 15f), padScore = 0.02f, nowMs = now); now += 150
        o.onFrame(signals, clean, mesh(rotY(base, 15.0), 15f), padScore = 0.03f, nowMs = now)
        val v = o.evaluate(signals, now)
        assertEquals(LivenessRisk.MEDIUM, v.risk)
        assertNotNull(o.currentChallenge)
        assertTrue(v.guidance != LivenessGuidance.NONE && v.guidance != LivenessGuidance.TURN_HEAD)
        assertEquals(LivenessReason.ACTIVE_CHECK_REQUIRED, (v.result as LivenessResult.Rejected).reason)
    }

    @Test
    fun flatPhotoMoved_isHigh() {
        val o = orch()
        val flat = face(zFactor = 0f)
        o.onFrame(signals, clean, mesh(flat, 0f), padScore = 0.9f, nowMs = now); now += 150
        o.onFrame(signals, clean, mesh(rotY(flat, 10.0).map { Point3(it.x, it.y, 0f) }, 10f), padScore = 0.9f, nowMs = now)
        val v = o.evaluate(signals, now)
        assertEquals(LivenessRisk.HIGH, v.risk)
        assertEquals(LivenessReason.PASSIVE_SPOOF_SUSPECTED, (v.result as LivenessResult.Rejected).reason)
    }

    // ---------------------------------------------------------------- 2-of-3 pixel veto
    @Test
    fun singlePixelTrip_doesNotVeto() {
        val o = orch()
        o.onFrame(signals, clean, null, nowMs = now)
        o.onFrame(signals, screenTrip, null, nowMs = now)
        o.onFrame(signals, clean, null, nowMs = now)
        assertNull(o.confirmedPixelVeto())
        assertEquals(LivenessRisk.MEDIUM, o.evaluate(signals, now).risk)
    }

    @Test
    fun twoOfThreePixelTrips_veto_isHighScreenReplay() {
        val o = orch()
        o.onFrame(signals, screenTrip, null, nowMs = now)
        o.onFrame(signals, clean, null, nowMs = now)
        o.onFrame(signals, screenTrip, null, nowMs = now)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, o.confirmedPixelVeto())
        val v = o.evaluate(signals, now)
        assertEquals(LivenessRisk.HIGH, v.risk)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, (v.result as LivenessResult.Rejected).reason)
    }

    @Test
    fun oldTripsSlideOutOfWindow() {
        val o = orch()
        o.onFrame(signals, screenTrip, null, nowMs = now)
        o.onFrame(signals, screenTrip, null, nowMs = now)
        o.onFrame(signals, clean, null, nowMs = now)
        o.onFrame(signals, clean, null, nowMs = now)
        assertNull("window is 3 frames: [trip, clean, clean]", o.confirmedPixelVeto())
    }

    @Test
    fun evaluate_isReadOnly_neverDoubleCountsATrip() {
        val o = orch()
        o.onFrame(signals, screenTrip, null, nowMs = now)
        repeat(5) { o.evaluate(signals, now) }
        assertNull("one trip evaluated many times must not become a 2-of-3 veto", o.confirmedPixelVeto())
        assertEquals(LivenessRisk.MEDIUM, o.evaluate(signals, now).risk)
    }

    @Test
    fun frameWithoutPixelSignals_neitherConfirmsNorClears() {
        val o = orch()
        o.onFrame(signals, screenTrip, null, nowMs = now)
        o.onFrame(signals, null, null, nowMs = now)
        o.onFrame(signals, screenTrip, null, nowMs = now)
        assertEquals(LivenessReason.SCREEN_REPLAY_SUSPECTED, o.confirmedPixelVeto())
    }

    // ---------------------------------------------------------------- PAD median of 3
    @Test
    fun padMedianOfLastThree() {
        val o = orch()
        listOf(0.9f, 0.01f, 0.8f, 0.7f).forEach { o.onFrame(signals, null, null, padScore = it, nowMs = now) }
        assertEquals(3, o.padFrameCount)
        assertEquals(0.7f, o.medianPadScore()!!, 1e-6f)  // last three: 0.01, 0.8, 0.7
    }

    @Test
    fun singleLowPadOutlier_isNotSuspect() {
        val o = orch()
        listOf(0.9f, 0.02f, 0.85f).forEach { o.onFrame(signals, null, null, padScore = it, nowMs = now) }
        assertTrue(o.medianPadScore()!! >= 0.10f)
    }

    @Test
    fun padScoreDefaultsFromPatSignals() {
        val o = orch()
        o.onFrame(signals, clean.copy(padScore = 0.42f), null, nowMs = now)
        assertEquals(0.42f, o.medianPadScore()!!, 1e-6f)
    }

    @Test
    fun nonFinitePadIgnored() {
        val o = orch()
        o.onFrame(signals, null, null, padScore = Float.NaN, nowMs = now)
        assertNull(o.medianPadScore())
    }

    // ---------------------------------------------------------------- history hygiene
    @Test
    fun trackChange_resetsHistory() {
        val o = orch()
        o.onFrame(signals, screenTrip, mesh(face(), 0f), padScore = 0.5f, trackId = 1, nowMs = now)
        o.onFrame(signals, screenTrip, mesh(face(), 0f), padScore = 0.5f, trackId = 2, nowMs = now)
        assertNull(o.confirmedPixelVeto())
        assertEquals(1, o.meshFrameCount)
        assertEquals(1, o.padFrameCount)
    }

    @Test
    fun staleMeshOutsideWindow_cannotCreateParallax() {
        val o = orch()
        val base = face()
        o.onFrame(signals, clean, mesh(base, 0f), padScore = 0.9f, nowMs = now)
        now += 5_000   // > meshWindowMs (3000)
        o.onFrame(signals, clean, mesh(rotY(base, 15.0), 15f), padScore = 0.9f, nowMs = now)
        val v = o.evaluate(signals, now)
        assertTrue(v.evidence.depthInconclusive)
        assertEquals(LivenessRisk.MEDIUM, v.risk)
    }

    @Test
    fun reset_clearsEverything() {
        val o = orch()
        o.onFrame(signals, screenTrip, mesh(face(), 0f), padScore = 0.5f, nowMs = now)
        o.markInjectionSuspected()
        o.reset()
        assertEquals(0, o.meshFrameCount)
        assertEquals(0, o.padFrameCount)
        assertNull(o.confirmedPixelVeto())
        assertEquals(LivenessRisk.MEDIUM, o.evaluate(signals, now).risk)
    }

    @Test
    fun injection_isHigh() {
        val o = orch()
        o.markInjectionSuspected()
        assertEquals(LivenessRisk.HIGH, o.evaluate(signals, now).risk)
    }
}
