package com.attract.attendance.domain.face

/**
 * 3D facial point representation with normalized x,y in [0, 1] and relative depth z.
 */
data class Point3(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
)

/**
 * Face Mesh inference result incorporating 478 3D landmarks, transformation matrix pose,
 * and calibrated blendshapes (doc-20 WP4.4).
 */
data class FaceMeshResult(
    val landmarks: List<Point3>,
    val yawDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float,
    val eyeBlinkLeft: Float,
    val eyeBlinkRight: Float,
    val jawOpen: Float = 0f,
    val mouthSmile: Float = 0f,
    val faceCount: Int = 1,
)
