package com.attract.attendance.feature.attendance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.attract.attendance.domain.face.FaceAligner
import com.attract.attendance.domain.face.FaceQualitySignals
import com.attract.attendance.domain.face.YoloFaceDetector
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Callback delivers:
 *   - [FaceQualitySignals] — live quality/pose signals for every analysis frame
 *   - [Bitmap]? — face-cropped bitmap (tight crop with margin) when exactly one face
 *     is detected, or null when no face / multiple faces detected.
 *
 * Per LLD-08: the cropped pixel buffer is short-lived and must NOT be persisted
 * to gallery or stored outside the active attempt scope.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
    onFrameAnalyzed: ((FaceQualitySignals, Bitmap?) -> Unit)? = null,
    onProviderInitialized: ((ProcessCameraProvider) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, cameraSelector) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()

        var lastAnalyzedTimestampMs = 0L

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            val now = System.currentTimeMillis()
            if (now - lastAnalyzedTimestampMs < 150L) {
                imageProxy.close()
                return@setAnalyzer
            }
            lastAnalyzedTimestampMs = now

            val frameBitmap = toBitmap(imageProxy)
            if (frameBitmap == null) {
                imageProxy.close()
                return@setAnalyzer
            }

            try {
                val detections = YoloFaceDetector.detect(context, frameBitmap)
                val faceCount = detections.size

                if (faceCount == 0) {
                    val emptySignals = FaceQualitySignals(
                        faceCount = 0,
                        yawDegrees = 0f,
                        pitchDegrees = 0f,
                        rollDegrees = 0f,
                        leftEyeOpenProbability = null,
                        rightEyeOpenProbability = null,
                        faceRatio = 0f,
                        centerX = 0.5f,
                        centerY = 0.5f,
                        blurVariance = 0f,
                        brightness = 0f
                    )
                    mainExecutor.execute {
                        onFrameAnalyzed?.invoke(emptySignals, null)
                    }
                    return@setAnalyzer
                }

                // Multiple faces: signal but pass null crop — quality engine will reject
                if (faceCount > 1) {
                    val multipleSignals = FaceQualitySignals(
                        faceCount = faceCount,
                        yawDegrees = 0f,
                        pitchDegrees = 0f,
                        rollDegrees = 0f,
                        leftEyeOpenProbability = null,
                        rightEyeOpenProbability = null,
                        faceRatio = 0f,
                        centerX = 0.5f,
                        centerY = 0.5f,
                        blurVariance = 0f,
                        brightness = 0f
                    )
                    mainExecutor.execute {
                        onFrameAnalyzed?.invoke(multipleSignals, null)
                    }
                    return@setAnalyzer
                }

                val primaryFace = detections[0]
                val box = primaryFace.boundingBox

                // Quality signals computed on full frame with bounding-box region
                val brightness = computeBitmapBrightness(frameBitmap, box)
                val blurVariance = computeBitmapLaplacianVariance(frameBitmap, box)

                val liveSignals = FaceQualitySignals(
                    faceCount = 1,
                    yawDegrees = primaryFace.estimatedYaw,
                    pitchDegrees = primaryFace.estimatedPitch,
                    rollDegrees = primaryFace.estimatedRoll,
                    leftEyeOpenProbability = 1.0f, // Unavailable from YOLO, treated as open per LLD-09
                    rightEyeOpenProbability = 1.0f,
                    faceRatio = primaryFace.faceRatio,
                    centerX = primaryFace.centerX,
                    centerY = primaryFace.centerY,
                    blurVariance = blurVariance,
                    brightness = brightness
                )

                // FaceAligner produces 112x112 canonical ArcFace aligned face bitmap
                val alignedFace = FaceAligner.align(frameBitmap, primaryFace.landmarks)

                mainExecutor.execute {
                    onFrameAnalyzed?.invoke(liveSignals, alignedFace)
                }
            } catch (e: Exception) {
                android.util.Log.e("CameraPreview", "YOLO frame analysis error: ${e.message}", e)
                val errorSignals = FaceQualitySignals(
                    faceCount = 0,
                    yawDegrees = 0f,
                    pitchDegrees = 0f,
                    rollDegrees = 0f,
                    leftEyeOpenProbability = 0f,
                    rightEyeOpenProbability = 0f,
                    faceRatio = 0f,
                    centerX = 0.5f,
                    centerY = 0.5f,
                    blurVariance = 0f,
                    brightness = 0f
                )
                mainExecutor.execute {
                    onFrameAnalyzed?.invoke(errorSignals, null)
                }
            } finally {
                imageProxy.close()
            }
        }

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                onProviderInitialized?.invoke(cameraProvider)

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                cameraProvider.unbindAll()
                try {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: IllegalArgumentException) {
                    // Preferred camera unavailable (emulator/edge device) — fall back to the other lens.
                    android.util.Log.w("CameraPreview", "Preferred camera unavailable, trying fallback", e)
                    val fallback = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                        CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, fallback, preview, imageAnalysis
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, mainExecutor)

        onDispose {
            try {
                analysisExecutor.shutdown()
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (_: Exception) {}
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

private fun getLuminance(bitmap: Bitmap, x: Int, y: Int): Float {
    val pixel = bitmap.getPixel(x, y)
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    return 0.299f * r + 0.587f * g + 0.114f * b
}

private fun computeBitmapBrightness(bitmap: Bitmap, box: Rect): Float {
    val left = box.left.coerceIn(0, bitmap.width - 1)
    val right = box.right.coerceIn(left + 1, bitmap.width)
    val top = box.top.coerceIn(0, bitmap.height - 1)
    val bottom = box.bottom.coerceIn(top + 1, bitmap.height)

    var sum = 0L
    var count = 0

    val step = ((bottom - top) / 40).coerceAtLeast(1)
    var y = top
    while (y < bottom) {
        var x = left
        while (x < right) {
            val luminance = getLuminance(bitmap, x, y)
            sum += luminance.toLong()
            count++
            x += step
        }
        y += step
    }

    return if (count > 0) sum.toFloat() / count else 0f
}

private fun computeBitmapLaplacianVariance(bitmap: Bitmap, box: Rect): Float {
    val left = (box.left + 1).coerceIn(1, bitmap.width - 2)
    val right = (box.right - 1).coerceIn(left + 1, bitmap.width - 1)
    val top = (box.top + 1).coerceIn(1, bitmap.height - 2)
    val bottom = (box.bottom - 1).coerceIn(top + 1, bitmap.height - 1)

    val step = ((bottom - top) / 30).coerceAtLeast(1)
    val responses = mutableListOf<Float>()

    var y = top
    while (y < bottom) {
        var x = left
        while (x < right) {
            val c = getLuminance(bitmap, x, y)
            val up = getLuminance(bitmap, x, y - 1)
            val down = getLuminance(bitmap, x, y + 1)
            val leftPx = getLuminance(bitmap, x - 1, y)
            val rightPx = getLuminance(bitmap, x + 1, y)

            val lap = up + down + leftPx + rightPx - 4 * c
            responses.add(lap)
            x += step
        }
        y += step
    }

    if (responses.isEmpty()) return 0f
    val mean = responses.average().toFloat()
    var varSum = 0.0
    for (r in responses) {
        val diff = r - mean
        varSum += diff * diff
    }
    return (varSum / responses.size).toFloat()
}

/**
 * Crops the face bounding box from the full-frame bitmap with a margin.
 *
 * Per LLD-08: rotation, mirror transform, crop, and color-space conversion are
 * central camera-layer utilities. The cropped buffer is short-lived; callers must
 * not persist it outside the current attempt scope.
 *
 * @param bitmap Full rotated frame bitmap from the camera
 * @param box Face bounding box from ML Kit (in rotated frame coordinates)
 * @param marginFraction Fractional margin added on each side (default 20%)
 * @return Face-cropped bitmap safe for TFLite input, or null if the box is invalid
 */
private fun cropFaceFromBitmap(bitmap: Bitmap, box: Rect, marginFraction: Float = 0.20f): Bitmap? {
    return try {
        val marginX = (box.width() * marginFraction).toInt()
        val marginY = (box.height() * marginFraction).toInt()
        val left = (box.left - marginX).coerceAtLeast(0)
        val top = (box.top - marginY).coerceAtLeast(0)
        val right = (box.right + marginX).coerceAtMost(bitmap.width)
        val bottom = (box.bottom + marginY).coerceAtMost(bitmap.height)
        val width = (right - left).coerceAtLeast(1)
        val height = (bottom - top).coerceAtLeast(1)
        if (width < 20 || height < 20) return null // too small to be useful
        Bitmap.createBitmap(bitmap, left, top, width, height)
    } catch (_: Exception) {
        null
    }
}


@OptIn(ExperimentalGetImage::class)
private fun toBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        android.util.Log.e("CameraPreview", "toBitmap conversion failed: ${e.message}", e)
        null
    }
}

