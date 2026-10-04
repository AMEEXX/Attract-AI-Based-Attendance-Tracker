package com.attract.attendance.domain.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * YOLOv8n-face TFLite detector.
 *
 * Provides:
 *   - Face bounding box detection
 *   - 5-point facial landmark extraction (left_eye, right_eye, nose, left_mouth, right_mouth)
 *   - Pose estimation (yaw, pitch, roll) from landmark geometry
 *   - Landmark confidence verification
 *
 * Thread-safety: @Synchronized on all interpreter calls (TFLite is NOT thread-safe).
 */
object YoloFaceDetector {

    private const val MODEL_FILE = "yolov8n_face.tflite"
    private const val INPUT_SIZE = 640
    private const val CONFIDENCE_THRESHOLD = 0.5f
    const val MIN_LANDMARK_CONFIDENCE = 0.3f
    private const val IOU_THRESHOLD = 0.45f

    const val K_YAW = -60.8f          // calibrated, tools/biometric_eval/exp3
    const val K_PITCH = 45.0f

    private var interpreter: Interpreter? = null
    private var isModelLoaded = false
    var lastInitError: String? = null
        private set

    data class DetectedFace(
        val boundingBox: Rect,
        val confidence: Float,
        /** 5 landmark points: [leftEye, rightEye, nose, leftMouth, rightMouth] in frame coordinates */
        val landmarks: List<PointF>,
        val landmarkConfidences: FloatArray,
        /** Estimated yaw from landmark geometry (degrees, negative=left) */
        val estimatedYaw: Float,
        /** Estimated pitch from landmark geometry (degrees, positive=down) */
        val estimatedPitch: Float,
        /** Estimated roll from landmark geometry (degrees) */
        val estimatedRoll: Float,
        /** Face area ratio relative to frame */
        val faceRatio: Float,
        /** Normalized center X (0.0–1.0) */
        val centerX: Float,
        /** Normalized center Y (0.0–1.0) */
        val centerY: Float,
    ) {
        val meanLandmarkConfidence: Float
            get() = if (landmarkConfidences.isNotEmpty()) landmarkConfidences.average().toFloat() else 0f
        val minLandmarkConfidence: Float
            get() = if (landmarkConfidences.isNotEmpty()) landmarkConfidences.minOrNull() ?: 0f else 0f
    }

    @Synchronized
    fun getOrInitInterpreter(context: Context): Interpreter? {
        if (isModelLoaded) return interpreter
        return try {
            val assetManager = context.assets
            val fd = assetManager.openFd(MODEL_FILE)
            val inputStream = FileInputStream(fd.fileDescriptor)
            val channel = inputStream.channel
            val buffer = channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
            val options = Interpreter.Options().setNumThreads(2)
            interpreter = Interpreter(buffer, options)
            isModelLoaded = true
            lastInitError = null
            interpreter
        } catch (e: Exception) {
            interpreter = null
            isModelLoaded = false
            lastInitError = "${e::class.simpleName}: ${e.message}"
            android.util.Log.e(
                "ATTRACT_FACE",
                "YOLO init FAILED for '$MODEL_FILE' — ${e::class.simpleName}: ${e.message}",
                e,
            )
            throw IllegalStateException("YOLO detector model '$MODEL_FILE' missing or failed to initialize.", e)
        }
    }

    @Synchronized
    fun isAvailable(context: Context): Boolean {
        return try {
            getOrInitInterpreter(context) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Detect faces in the given bitmap.
     *
     * Returns a list of DetectedFace ordered by confidence * meanLandmarkConfidence (highest first).
     * Returns empty list if no faces found above threshold.
     */
    @Synchronized
    fun detect(context: Context, bitmap: Bitmap): List<DetectedFace> {
        val activeInterpreter = getOrInitInterpreter(context)
            ?: throw IllegalStateException("YOLO detector not initialized")

        val frameWidth = bitmap.width
        val frameHeight = bitmap.height

        // 1. Preprocess: resize to 640x640, RGB normalized [0, 1] in NCHW format
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * INPUT_SIZE * INPUT_SIZE * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaledBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Red plane
        for (p in pixels) {
            inputBuffer.putFloat(((p shr 16) and 0xFF) / 255.0f)
        }
        // Green plane
        for (p in pixels) {
            inputBuffer.putFloat(((p shr 8) and 0xFF) / 255.0f)
        }
        // Blue plane
        for (p in pixels) {
            inputBuffer.putFloat((p and 0xFF) / 255.0f)
        }

        // 2. Run inference: output shape [1, 20, 8400]
        val outputMap = HashMap<Int, Any>()
        val rawOutput = Array(1) { Array(20) { FloatArray(8400) } }
        outputMap[0] = rawOutput

        try {
            activeInterpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)
        } catch (e: Exception) {
            if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            android.util.Log.e("ATTRACT_FACE", "YOLO inference failed: ${e.message}", e)
            throw IllegalStateException("YOLO inference failed: ${e.message}", e)
        }

        // 3. Post-process
        val detections = parseDetections(rawOutput[0], frameWidth, frameHeight)

        if (scaledBitmap !== bitmap) scaledBitmap.recycle()

        return detections
    }

    private data class Candidate(
        val boxNorm: FloatArray, // [x1, y1, x2, y2] in 0..1
        val score: Float,
        val landmarksNorm: List<PointF>, // in 0..1
        val landmarkConfs: FloatArray,
    )

    private fun parseDetections(
        rawOutput: Array<FloatArray>, // [20][8400]
        frameWidth: Int,
        frameHeight: Int,
    ): List<DetectedFace> {
        val candidates = mutableListOf<Candidate>()

        for (a in 0 until 8400) {
            val score = rawOutput[4][a]
            if (score < CONFIDENCE_THRESHOLD) continue

            // 5 landmarks verification
            var minLmConf = 1.0f
            val lmConfs = FloatArray(5)
            val lmsNorm = ArrayList<PointF>(5)
            for (l in 0 until 5) {
                val lx = rawOutput[5 + l * 3][a]
                val ly = rawOutput[6 + l * 3][a]
                val lconf = rawOutput[7 + l * 3][a]
                lmConfs[l] = lconf
                if (lconf < minLmConf) minLmConf = lconf
                lmsNorm.add(PointF(lx, ly))
            }

            // Quality filter: all 5 landmarks must meet minimum confidence
            if (minLmConf < MIN_LANDMARK_CONFIDENCE) continue

            val cx = rawOutput[0][a]
            val cy = rawOutput[1][a]
            val w = rawOutput[2][a]
            val h = rawOutput[3][a]

            val x1 = (cx - w / 2f).coerceIn(0f, 1f)
            val y1 = (cy - h / 2f).coerceIn(0f, 1f)
            val x2 = (cx + w / 2f).coerceIn(0f, 1f)
            val y2 = (cy + h / 2f).coerceIn(0f, 1f)

            candidates.add(
                Candidate(
                    boxNorm = floatArrayOf(x1, y1, x2, y2),
                    score = score,
                    landmarksNorm = lmsNorm,
                    landmarkConfs = lmConfs
                )
            )
        }

        if (candidates.isEmpty()) return emptyList()

        // Apply NMS
        val keep = nms(candidates, IOU_THRESHOLD)

        val results = mutableListOf<DetectedFace>()
        val frameArea = (frameWidth * frameHeight).toFloat().coerceAtLeast(1f)

        for (idx in keep) {
            val c = candidates[idx]
            val bx1 = (c.boxNorm[0] * frameWidth).toInt().coerceAtLeast(0)
            val by1 = (c.boxNorm[1] * frameHeight).toInt().coerceAtLeast(0)
            val bx2 = (c.boxNorm[2] * frameWidth).toInt().coerceAtMost(frameWidth)
            val by2 = (c.boxNorm[3] * frameHeight).toInt().coerceAtMost(frameHeight)
            val box = Rect(bx1, by1, bx2, by2)

            val landmarks = c.landmarksNorm.map {
                PointF(it.x * frameWidth, it.y * frameHeight)
            }

            val roll = estimateRoll(landmarks)
            val deRotated = rotateLandmarks(landmarks, -roll)
            val yaw = estimateYaw(deRotated)
            val pitch = estimatePitch(deRotated)

            val faceArea = (box.width() * box.height()).toFloat()
            val centerX = box.exactCenterX() / frameWidth.toFloat()
            val centerY = box.exactCenterY() / frameHeight.toFloat()

            results.add(
                DetectedFace(
                    boundingBox = box,
                    confidence = c.score,
                    landmarks = landmarks,
                    landmarkConfidences = c.landmarkConfs,
                    estimatedYaw = yaw,
                    estimatedPitch = pitch,
                    estimatedRoll = roll,
                    faceRatio = faceArea / frameArea,
                    centerX = centerX,
                    centerY = centerY,
                )
            )
        }

        // Sort by confidence * meanLandmarkConfidence descending
        return results.sortedByDescending { it.confidence * it.meanLandmarkConfidence }
    }

    private fun nms(candidates: List<Candidate>, iouThreshold: Float): List<Int> {
        val order = candidates.indices.sortedByDescending { candidates[it].score }.toMutableList()
        val keep = mutableListOf<Int>()

        while (order.isNotEmpty()) {
            val current = order.removeAt(0)
            keep.add(current)

            val boxA = candidates[current].boxNorm
            val areaA = (boxA[2] - boxA[0]) * (boxA[3] - boxA[1])

            val iterator = order.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                val boxB = candidates[next].boxNorm
                val areaB = (boxB[2] - boxB[0]) * (boxB[3] - boxB[1])

                val xx1 = max(boxA[0], boxB[0])
                val yy1 = max(boxA[1], boxB[1])
                val xx2 = min(boxA[2], boxB[2])
                val yy2 = min(boxA[3], boxB[3])

                val w = max(0f, xx2 - xx1)
                val h = max(0f, yy2 - yy1)
                val inter = w * h
                val union = areaA + areaB - inter

                val iou = if (union > 1e-6f) inter / union else 0f
                if (iou > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return keep
    }

    /**
     * Rotate 2D landmarks by [angleDegrees] around the eye midpoint.
     * Prevents in-plane roll tilt from leaking into yaw/pitch estimates (WP-B).
     */
    fun rotateLandmarks(landmarks: List<PointF>, angleDegrees: Float): List<PointF> {
        if (landmarks.size < 2 || abs(angleDegrees) < 0.05f) return landmarks
        val rad = Math.toRadians(angleDegrees.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        val eyeMidX = (landmarks[0].x + landmarks[1].x) / 2f
        val eyeMidY = (landmarks[0].y + landmarks[1].y) / 2f
        return landmarks.map { pt ->
            val dx = pt.x - eyeMidX
            val dy = pt.y - eyeMidY
            PointF(
                eyeMidX + (dx * cos - dy * sin),
                eyeMidY + (dx * sin + dy * cos)
            )
        }
    }

    /**
     * Calibrated nose-offset yaw calculation (WP-B / RC-1).
     * Negative = turned left, Positive = turned right.
     */
    fun calculateYaw(leftEyeX: Float, leftEyeY: Float, rightEyeX: Float, rightEyeY: Float, noseX: Float, noseY: Float): Float {
        val mx = (leftEyeX + rightEyeX) / 2f
        val ie = kotlin.math.hypot((rightEyeX - leftEyeX).toDouble(), (rightEyeY - leftEyeY).toDouble()).toFloat().coerceAtLeast(1f)
        return K_YAW * (noseX - mx) / ie
    }

    /**
     * Vertical nose-offset pitch calculation (WP-B).
     * Negative = looking up, Positive = looking down.
     */
    fun calculatePitch(leftEyeX: Float, leftEyeY: Float, rightEyeX: Float, rightEyeY: Float, noseX: Float, noseY: Float): Float {
        val my = (leftEyeY + rightEyeY) / 2f
        val ie = kotlin.math.hypot((rightEyeX - leftEyeX).toDouble(), (rightEyeY - leftEyeY).toDouble()).toFloat().coerceAtLeast(1f)
        val noseV = (noseY - my) / ie
        return K_PITCH * (noseV - 0.55f)
    }

    /**
     * Estimate roll (in-plane rotation) from eye coordinates.
     */
    fun calculateRoll(leftEyeX: Float, leftEyeY: Float, rightEyeX: Float, rightEyeY: Float): Float {
        val dx = rightEyeX - leftEyeX
        val dy = rightEyeY - leftEyeY
        return Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    fun estimateYaw(landmarks: List<PointF>): Float {
        if (landmarks.size < 3) return 0f
        return calculateYaw(landmarks[0].x, landmarks[0].y, landmarks[1].x, landmarks[1].y, landmarks[2].x, landmarks[2].y)
    }

    fun estimatePitch(landmarks: List<PointF>): Float {
        if (landmarks.size < 3) return 0f
        return calculatePitch(landmarks[0].x, landmarks[0].y, landmarks[1].x, landmarks[1].y, landmarks[2].x, landmarks[2].y)
    }

    fun estimateRoll(landmarks: List<PointF>): Float {
        if (landmarks.size < 2) return 0f
        return calculateRoll(landmarks[0].x, landmarks[0].y, landmarks[1].x, landmarks[1].y)
    }
}
