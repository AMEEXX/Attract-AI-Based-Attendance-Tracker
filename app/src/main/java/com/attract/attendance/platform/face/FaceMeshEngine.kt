package com.attract.attendance.platform.face

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.attract.attendance.domain.face.FaceMeshResult
import com.attract.attendance.domain.face.HeadPose
import com.attract.attendance.domain.face.Point3
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * MediaPipe Face Landmarker platform engine (Doc 20 WP4.4).
 * Platform adapter located in platform.face to maintain pure JVM testability in domain.face.
 *
 * Provides:
 * - 478 3D facial landmarks (Point3 with normalized x,y and relative depth z)
 * - 4x4 facial transformation matrix -> Euler angles (yaw, pitch, roll) via HeadPose
 * - Calibrated facial blendshapes: eyeBlinkLeft, eyeBlinkRight, jawOpen, mouthSmile
 *
 * Designed with safe fallback: if face_landmarker.task is absent or fails to load,
 * methods return null gracefully so callers fallback to YOLO geometry.
 */
object FaceMeshEngine {

    private const val TAG = "FaceMeshEngine"
    private const val MODEL_ASSET = "face_landmarker.task"

    private var faceLandmarker: FaceLandmarker? = null
    private var isInitialized = false
    var lastError: String? = null
        private set

    val isAvailable: Boolean
        get() = isInitialized && faceLandmarker != null

    @Synchronized
    fun init(context: Context): Boolean {
        if (isInitialized) return faceLandmarker != null

        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            isInitialized = true
            lastError = null
            Log.i(TAG, "MediaPipe FaceLandmarker successfully initialized")
            true
        } catch (t: Throwable) {
            lastError = t.message ?: "Failed to initialize FaceLandmarker"
            Log.w(TAG, "MediaPipe FaceLandmarker not available: $lastError")
            isInitialized = true
            faceLandmarker = null
            false
        }
    }

    @Synchronized
    fun detect(bitmap: Bitmap): FaceMeshResult? {
        val landmarker = faceLandmarker ?: return null

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: FaceLandmarkerResult = landmarker.detect(mpImage)

            val landmarksList = result.faceLandmarks()
            if (landmarksList.isEmpty() || landmarksList[0].isEmpty()) {
                return null
            }

            val landmarks = landmarksList[0].map { landmark ->
                Point3(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z()
                )
            }

            var yaw = 0f
            var pitch = 0f
            var roll = 0f

            val matrices = result.facialTransformationMatrixes()
            if (matrices.isPresent && matrices.get().isNotEmpty()) {
                val matrix = matrices.get()[0]
                val (y, p, r) = HeadPose.eulerFromMatrix(matrix)
                yaw = y
                pitch = p
                roll = r
            }

            var blinkLeft = 0f
            var blinkRight = 0f
            var jawOpen = 0f
            var mouthSmile = 0f

            val blendshapes = result.faceBlendshapes()
            if (blendshapes.isPresent && blendshapes.get().isNotEmpty()) {
                val categories = blendshapes.get()[0]
                for (cat in categories) {
                    when (cat.categoryName()) {
                        "eyeBlinkLeft" -> blinkLeft = cat.score()
                        "eyeBlinkRight" -> blinkRight = cat.score()
                        "jawOpen" -> jawOpen = cat.score()
                        "mouthSmileLeft", "mouthSmileRight" -> {
                            mouthSmile = maxOf(mouthSmile, cat.score())
                        }
                    }
                }
            }

            FaceMeshResult(
                landmarks = landmarks,
                yawDeg = yaw,
                pitchDeg = pitch,
                rollDeg = roll,
                eyeBlinkLeft = blinkLeft,
                eyeBlinkRight = blinkRight,
                jawOpen = jawOpen,
                mouthSmile = mouthSmile,
                faceCount = landmarksList.size
            )
        } catch (t: Throwable) {
            Log.e(TAG, "FaceLandmarker detect failed: ${t.message}", t)
            null
        }
    }

    @Synchronized
    fun close() {
        try {
            faceLandmarker?.close()
        } catch (ignored: Throwable) {
        } finally {
            faceLandmarker = null
            isInitialized = false
        }
    }
}
