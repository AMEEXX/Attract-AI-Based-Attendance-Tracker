package com.attract.attendance.domain.face

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Head pose estimation and Euler angle extraction from 4x4 facial transformation matrices (doc-20 WP4.4).
 */
object HeadPose {

    const val MESH_YAW_SIGN = -1.0f  // Matches FaceQualityEngine: negative = LEFT
    const val MESH_PITCH_SIGN = 1.0f  // Matches FaceQualityEngine: negative = DOWN

    /**
     * Extracts (yaw, pitch, roll) in degrees from a 4x4 column-major transformation matrix.
     */
    fun eulerFromMatrix(m: FloatArray): Triple<Float, Float, Float> {
        if (m.size < 16) return Triple(0f, 0f, 0f)

        // Upper-left 3x3 rotation matrix in column-major order:
        // [0]  [4]  [8]  [12]
        // [1]  [5]  [9]  [13]
        // [2]  [6]  [10] [14]
        // [3]  [7]  [11] [15]
        val r00 = m[0]
        val r10 = m[1]
        val r20 = m[2]
        val r21 = m[6]
        val r22 = m[10]

        val pitch = atan2(r21.toDouble(), r22.toDouble())
        val yaw = atan2(-r20.toDouble(), sqrt((r00 * r00 + r10 * r10).toDouble()))
        val roll = atan2(r10.toDouble(), r00.toDouble())

        val yawDeg = Math.toDegrees(yaw).toFloat() * MESH_YAW_SIGN
        val pitchDeg = Math.toDegrees(pitch).toFloat() * MESH_PITCH_SIGN
        val rollDeg = Math.toDegrees(roll).toFloat()

        return Triple(yawDeg, pitchDeg, rollDeg)
    }

    /**
     * Maps head pose angles to canonical pose buckets for gallery storage (doc-20 WP5.3).
     */
    fun poseBucket(yawDeg: Float, pitchDeg: Float): String = when {
        yawDeg < -15f -> "LEFT"
        yawDeg > 15f -> "RIGHT"
        pitchDeg < -12f -> "DOWN"
        pitchDeg > 15f -> "UP"
        else -> "FRONTAL"
    }
}
