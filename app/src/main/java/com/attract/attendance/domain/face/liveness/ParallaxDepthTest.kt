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

    data class Result(
        val depthScore: Float?,
        val depthInconclusive: Boolean,
        val residual: Float = 0f,
    )

    fun evaluate(
        frames: List<FaceMeshResult>,
        minYawDeltaDeg: Float = 4.0f,
        parallaxLiveResidual: Float = 0.020f,
    ): Result {
        if (frames.size < 2) {
            return Result(depthScore = null, depthInconclusive = true)
        }

        // Search for a pair with sufficient angular delta
        var bestPair: Pair<FaceMeshResult, FaceMeshResult>? = null
        var maxDelta = 0f

        for (i in frames.indices) {
            for (j in i + 1 until frames.size) {
                val delta = abs(frames[i].yawDeg - frames[j].yawDeg)
                if (delta > maxDelta) {
                    maxDelta = delta
                    bestPair = Pair(frames[i], frames[j])
                }
            }
        }

        if (bestPair == null || maxDelta < minYawDeltaDeg) {
            return Result(depthScore = null, depthInconclusive = true)
        }

        val frameA = bestPair.first
        val frameB = bestPair.second

        if (frameA.landmarks.size < 478 || frameB.landmarks.size < 478) {
            return Result(depthScore = null, depthInconclusive = true)
        }

        // Inter-Ocular Distance (IOD) on destination frame (landmarks 33 and 263)
        val eyeL = frameB.landmarks[33]
        val eyeR = frameB.landmarks[263]
        val iod = sqrt(
            (eyeL.x - eyeR.x) * (eyeL.x - eyeR.x) + (eyeL.y - eyeR.y) * (eyeL.y - eyeR.y)
        ).toDouble()

        if (iod < 1e-4) {
            return Result(depthScore = null, depthInconclusive = true)
        }

        val srcAnchors = HOMOGRAPHY_ANCHORS.map { frameA.landmarks[it] }
        val dstAnchors = HOMOGRAPHY_ANCHORS.map { frameB.landmarks[it] }

        val h = Homography.solve4Points(srcAnchors, dstAnchors)
            ?: return Result(depthScore = null, depthInconclusive = true)

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

        // Depth score mapping: real faces yield residual > 0.020, flat attacks < 0.015
        val depthScore = ((medianResidual - 0.010f) / (parallaxLiveResidual * 2f - 0.010f))
            .coerceIn(0f, 1f)

        return Result(
            depthScore = depthScore,
            depthInconclusive = false,
            residual = medianResidual
        )
    }
}
