package com.attract.attendance.domain.face.liveness

import com.attract.attendance.domain.face.FaceMeshResult
import com.attract.attendance.domain.face.Point3
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Passive 3D motion-parallax depth verification (doc-20 WP7.1).
 * Distinguishes true 3D facial topology from planar presentation attacks (photos, screen replays).
 */
object ParallaxDepthTest {

    // 33 stable facial landmark indices (rigid facial structure excluding eyes and lips)
    val STABLE_INDICES = listOf(
        1, 4, 6, 168, 197, 195, 5, 33, 133, 362, 263, 234, 454, 93, 323, 132,
        361, 58, 288, 172, 397, 136, 365, 152, 10, 109, 338, 67, 297, 21, 251, 127, 356
    )

    // Key anchor points spanning top, bottom, left, right for homography fitting
    val HOMOGRAPHY_ANCHORS = listOf(10, 152, 234, 454)

    /** Doc 22 WP-E defaults, measured in exp8 (real 15° pan p5 residual 0.042; planar p95 0.031, max yaw 7.6°). */
    const val DEFAULT_MIN_POSE_DELTA_DEG = 5.0f
    const val DEFAULT_MIN_PARALLAX_RESIDUAL = 0.030f

    data class Result(
        val depthScore: Float?,
        val depthInconclusive: Boolean,
        val residual: Float = 0f,
        val poseDeltaDeg: Float = 0f,
    )

    /** Doc 22 WP-E: angular separation of two frames = max(|Δyaw|, |Δpitch|). Nodding counts as much as turning. */
    fun poseDelta(a: FaceMeshResult, b: FaceMeshResult): Float =
        maxOf(abs(a.yawDeg - b.yawDeg), abs(a.pitchDeg - b.pitchDeg))

    /**
     * Maps a median parallax residual (normalised by IOD) to a 0..1 depth score.
     * residual == minParallaxResidual  -> exactly 0.50 (the LOW-risk "live" boundary used by RiskFusion)
     * residual <= 0.4 * minParallaxResidual -> <= 0.20 (conclusive flat plane => HIGH risk)
     */
    fun depthScoreFor(residual: Float, minParallaxResidual: Float = DEFAULT_MIN_PARALLAX_RESIDUAL): Float =
        (residual / (2f * minParallaxResidual)).coerceIn(0f, 1f)

    fun evaluate(
        frames: List<FaceMeshResult>,
        minYawDeltaDeg: Float = DEFAULT_MIN_POSE_DELTA_DEG,
        parallaxLiveResidual: Float = DEFAULT_MIN_PARALLAX_RESIDUAL,
    ): Result {
        val minPoseDeltaDeg = minYawDeltaDeg
        val minParallaxResidual = parallaxLiveResidual
        if (frames.size < 2) {
            return Result(depthScore = null, depthInconclusive = true)
        }

        // Search for the pair with the largest angular delta = max(|Δyaw|, |Δpitch|)
        var bestPair: Pair<FaceMeshResult, FaceMeshResult>? = null
        var maxDelta = 0f

        for (i in frames.indices) {
            for (j in i + 1 until frames.size) {
                val delta = poseDelta(frames[i], frames[j])
                if (delta > maxDelta) {
                    maxDelta = delta
                    bestPair = Pair(frames[i], frames[j])
                }
            }
        }

        if (bestPair == null || maxDelta < minPoseDeltaDeg) {
            return Result(depthScore = null, depthInconclusive = true, poseDeltaDeg = maxDelta)
        }

        val frameA = bestPair.first
        val frameB = bestPair.second

        if (frameA.landmarks.size < 478 || frameB.landmarks.size < 478) {
            return Result(depthScore = null, depthInconclusive = true, poseDeltaDeg = maxDelta)
        }

        // Inter-Ocular Distance (IOD) on destination frame (landmarks 33 and 263)
        val eyeL = frameB.landmarks[33]
        val eyeR = frameB.landmarks[263]
        val iod = sqrt(
            (eyeL.x - eyeR.x) * (eyeL.x - eyeR.x) + (eyeL.y - eyeR.y) * (eyeL.y - eyeR.y)
        ).toDouble()

        if (iod < 1e-4) {
            return Result(depthScore = null, depthInconclusive = true, poseDeltaDeg = maxDelta)
        }

        val srcAnchors = HOMOGRAPHY_ANCHORS.map { frameA.landmarks[it] }
        val dstAnchors = HOMOGRAPHY_ANCHORS.map { frameB.landmarks[it] }

        val h = Homography.solve4Points(srcAnchors, dstAnchors)
            ?: return Result(depthScore = null, depthInconclusive = true, poseDeltaDeg = maxDelta)

        // Compute residuals across all 33 stable points
        val residuals = mutableListOf<Float>()
        for (idx in STABLE_INDICES) {
            val ptA = frameA.landmarks[idx]
            val ptB = frameB.landmarks[idx]

            val (tx, ty) = h.transform(ptA.x.toDouble(), ptA.y.toDouble())
            val dx = tx - ptB.x.toDouble()
            val dy = ty - ptB.y.toDouble()
            val dist = sqrt(dx * dx + dy * dy)
            residuals.add((dist / iod).toFloat())
        }

        residuals.sort()
        val medianResidual = residuals[residuals.size / 2]

        return Result(
            depthScore = depthScoreFor(medianResidual, minParallaxResidual),
            depthInconclusive = false,
            residual = medianResidual,
            poseDeltaDeg = maxDelta,
        )
    }
}
