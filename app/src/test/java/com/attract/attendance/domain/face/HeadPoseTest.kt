package com.attract.attendance.domain.face

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadPoseTest {

    @Test
    fun eulerFromMatrix_identityMatrix_returnsZeroDegrees() {
        val identity = FloatArray(16).apply {
            this[0] = 1f
            this[5] = 1f
            this[10] = 1f
            this[15] = 1f
        }
        val (yaw, pitch, roll) = HeadPose.eulerFromMatrix(identity)
        assertEquals(0f, yaw, 0.01f)
        assertEquals(0f, pitch, 0.01f)
        assertEquals(0f, roll, 0.01f)
    }

    @Test
    fun eulerFromMatrix_pureYawRotation_calculatesCorrectDegrees() {
        // 30 degree yaw rotation around Y-axis
        // cos(30) = 0.866, sin(30) = 0.5
        val rad = Math.toRadians(30.0)
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()

        // Column-major Y rotation:
        // cos  0  sin  0
        //  0   1   0   0
        // -sin 0  cos  0
        //  0   0   0   1
        val m = FloatArray(16).apply {
            this[0] = cos
            this[2] = -sin
            this[5] = 1f
            this[8] = sin
            this[10] = cos
            this[15] = 1f
        }

        val (yaw, pitch, roll) = HeadPose.eulerFromMatrix(m)
        // With MESH_YAW_SIGN = -1.0f and sin positive
        assertEquals(30f * HeadPose.MESH_YAW_SIGN, yaw, 0.5f)
        assertEquals(0f, pitch, 0.5f)
        assertEquals(0f, roll, 0.5f)
    }

    @Test
    fun poseBucket_classifiesCorrectly() {
        assertEquals("FRONTAL", HeadPose.poseBucket(0f, 0f))
        assertEquals("LEFT", HeadPose.poseBucket(-20f, 0f))
        assertEquals("RIGHT", HeadPose.poseBucket(25f, 0f))
        assertEquals("DOWN", HeadPose.poseBucket(0f, -20f))
        assertEquals("UP", HeadPose.poseBucket(0f, 20f))
    }
}
