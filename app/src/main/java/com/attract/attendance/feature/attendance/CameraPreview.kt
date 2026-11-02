package com.attract.attendance.feature.attendance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.attract.attendance.domain.face.CropMetrics
import com.attract.attendance.domain.face.FaceAligner
import com.attract.attendance.domain.face.FaceBox
import com.attract.attendance.domain.face.FaceQualitySignals
import com.attract.attendance.domain.face.FaceTracker
import com.attract.attendance.domain.face.FrameBundle
import com.attract.attendance.domain.face.PadCrop
import com.attract.attendance.domain.face.PoseSource
import com.attract.attendance.domain.face.YoloFaceDetector
import com.attract.attendance.platform.camera.CameraCaptureController
import com.attract.attendance.platform.face.FaceMeshEngine
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.core.FocusMeteringAction
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.attract.attendance.domain.face.CameraTuning
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@OptIn(ExperimentalCamera2Interop::class)
private fun <T> Camera2Interop.Extender<T>.lowLightFriendly(supportedFpsRange: IntRange?) {
    if (supportedFpsRange != null) {
        setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(supportedFpsRange.first, supportedFpsRange.last))
    }
    setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
    setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
    if (Build.VERSION.SDK_INT >= 35) {
        setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, 6)
    }
}

/**
 * Callback delivers:
 *   - [FaceQualitySignals] — live quality/pose signals for every analysis frame
 *   - [Bitmap]? — face-cropped bitmap (tight crop with margin) when exactly one face
 *     is detected, or null when no face / multiple faces detected.
 *   - [FrameBundle] — immutable timestamped bundle containing detections, signals, and crop
 *
 * Per LLD-08: the cropped pixel buffer is short-lived and must NOT be persisted
 * to gallery or stored outside the active attempt scope.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
    cameraCaptureController: CameraCaptureController? = null,
    onFrameAnalyzed: ((FaceQualitySignals, Bitmap?) -> Unit)? = null,
    onFrameBundleAnalyzed: ((FrameBundle) -> Unit)? = null,
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
        var activeCameraProvider: ProcessCameraProvider? = null
        try {
            FaceMeshEngine.init(context)
            val tracker = FaceTracker()
            val temporalCropBuffer = com.attract.attendance.domain.face.TemporalCropBuffer(maxCapacity = 4)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val analysisExecutor = Executors.newSingleThreadExecutor()

            var activeCamera: androidx.camera.core.Camera? = null
            var lastExposureAdjustTimeMs = 0L
            var lastAeMeteringTimeMs = 0L
            var lastAnalyzedTimestampMs = 0L

            val supportedFpsRange: IntRange? = try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                val targetFacing = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
                    CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    CameraCharacteristics.LENS_FACING_BACK
                }
                var ranges: List<IntRange> = emptyList()
                cameraManager?.cameraIdList?.forEach { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    if (chars.get(CameraCharacteristics.LENS_FACING) == targetFacing) {
                        val available = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                        if (available != null) {
                            ranges = available.map { it.lower..it.upper }
                            return@forEach
                        }
                    }
                }
                CameraTuning.chooseAeFpsRange(ranges)
            } catch (_: Throwable) {
                null
            }

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val imageAnalysisBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            Camera2Interop.Extender(imageAnalysisBuilder).lowLightFriendly(supportedFpsRange)
            val imageAnalysis = imageAnalysisBuilder.build()

            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                val now = System.currentTimeMillis()
                if (now - lastAnalyzedTimestampMs < 150L) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                lastAnalyzedTimestampMs = now

                val frameBitmap = toBitmap(imageProxy)
                val nowNanos = System.nanoTime()
                val frameId = System.currentTimeMillis()

                if (frameBitmap == null) {
                    val errorBundle = FrameBundle(
                        frameId = frameId,
                        timestampNanos = nowNanos,
                        error = "Frame bitmap conversion failed"
                    )
                    mainExecutor.execute {
                        onFrameBundleAnalyzed?.invoke(errorBundle)
                    }
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {
                    val detections = YoloFaceDetector.detect(context, frameBitmap)
                    val faceCount = detections.size

                    if (faceCount == 0) {
                        tracker.update(emptyList(), nowNanos)
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
                        val bundle = FrameBundle(
                            frameId = frameId,
                            timestampNanos = nowNanos,
                            trackId = 0,
                            detections = emptyList(),
                            qualitySignals = emptySignals,
                            alignedCrop = null
                        )
                        mainExecutor.execute {
                            onFrameBundleAnalyzed?.invoke(bundle)
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
                        val bundle = FrameBundle(
                            frameId = frameId,
                            timestampNanos = nowNanos,
                            detections = detections,
                            qualitySignals = multipleSignals,
                            alignedCrop = null
                        )
                        mainExecutor.execute {
                            onFrameBundleAnalyzed?.invoke(bundle)
                            onFrameAnalyzed?.invoke(multipleSignals, null)
                        }
                        return@setAnalyzer
                    }

                    val faceBoxes = detections.map { d ->
                        FaceBox(
                            left = d.boundingBox.left.toFloat(),
                            top = d.boundingBox.top.toFloat(),
                            right = d.boundingBox.right.toFloat(),
                            bottom = d.boundingBox.bottom.toFloat()
                        )
                    }
                    val activeTrack = tracker.update(faceBoxes, nowNanos)
                    val trackId = activeTrack?.id ?: 1

                    val primaryFace = detections[0]
                    val box = primaryFace.boundingBox

                    // Quality signals computed on full frame with bounding-box region
                    val lumaAndNoise = computeFaceLumaAndNoise(frameBitmap, box)
                    val brightness = lumaAndNoise.meanLuma
                    val noiseSigma = lumaAndNoise.noiseSigma
                    val blurVariance = computeBitmapLaplacianVariance(frameBitmap, box)

                    // FaceAligner produces 112x112 canonical ArcFace aligned face bitmap
                    val alignedFace = FaceAligner.align(frameBitmap, primaryFace.landmarks)

                    // Doc 21 WP-A / WP-B: compute crop-level metrics on the 112x112 aligned face crop
                    val cropPixels = IntArray(CropMetrics.CROP_SIZE * CropMetrics.CROP_SIZE)
                    alignedFace.getPixels(cropPixels, 0, CropMetrics.CROP_SIZE, 0, 0, CropMetrics.CROP_SIZE, CropMetrics.CROP_SIZE)
                    val cropFaceLuma = CropMetrics.computeFaceLuma(cropPixels)
                    val cropNoiseSigma = CropMetrics.computeNoiseSigma(cropPixels)
                    val leveledCrop = CropMetrics.applyLevels(cropPixels)
                    val cropSharpness = CropMetrics.computeCropSharpness(leveledCrop)

                    // Doc 21 WP-E (1): Face-metered AE re-issued every 1.5s on primary face center
                    if (now - lastAeMeteringTimeMs >= 1500L) {
                        lastAeMeteringTimeMs = now
                        activeCamera?.let { cam ->
                            try {
                                if (previewView.width > 0 && previewView.height > 0) {
                                    val isFrontCamera = (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                                    val u = (box.left + box.right) / 2f / frameBitmap.width.toFloat()
                                    val v = (box.top + box.bottom) / 2f / frameBitmap.height.toFloat()
                                    val previewX = if (isFrontCamera) (1f - u) * previewView.width else u * previewView.width
                                    val previewY = v * previewView.height
                                    val point = previewView.meteringPointFactory.createPoint(previewX, previewY)
                                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE)
                                        .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()
                                    cam.cameraControl.startFocusAndMetering(action)
                                }
                            } catch (_: Throwable) {}
                        }
                    }

                    // Doc 21 WP-E (1): EV compensation loop targeting cropFaceLuma 110±20
                    if (now - lastExposureAdjustTimeMs >= 500L) {
                        lastExposureAdjustTimeMs = now
                        activeCamera?.let { cam ->
                            try {
                                val exp = cam.cameraInfo.exposureState
                                if (exp.isExposureCompensationSupported) {
                                    val range = exp.exposureCompensationRange
                                    val currentIdx = exp.exposureCompensationIndex
                                    val nextIdx = CameraTuning.nextExposureIndex(cropFaceLuma, currentIdx, range.lower..range.upper)
                                    if (nextIdx != currentIdx) {
                                        cam.cameraControl.setExposureCompensationIndex(nextIdx)
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }

                    // Inter-ocular distance in pixels between eye landmarks
                    val leftEye = primaryFace.landmarks.getOrNull(0)
                    val rightEye = primaryFace.landmarks.getOrNull(1)
                    val interOcularPx = if (leftEye != null && rightEye != null) {
                        val dx = leftEye.x - rightEye.x
                        val dy = leftEye.y - rightEye.y
                        kotlin.math.sqrt(dx * dx + dy * dy)
                    } else -1f

                    // Run MediaPipe FaceMesh when available
                    val mesh = if (FaceMeshEngine.isAvailable) {
                        FaceMeshEngine.detect(frameBitmap)
                    } else null

                    val liveSignals = if (mesh != null) {
                        FaceQualitySignals(
                            faceCount = 1,
                            yawDegrees = mesh.yawDeg,
                            pitchDegrees = mesh.pitchDeg,
                            rollDegrees = mesh.rollDeg,
                            leftEyeOpenProbability = (1f - mesh.eyeBlinkLeft).coerceIn(0f, 1f),
                            rightEyeOpenProbability = (1f - mesh.eyeBlinkRight).coerceIn(0f, 1f),
                            faceRatio = primaryFace.faceRatio,
                            centerX = primaryFace.centerX,
                            centerY = primaryFace.centerY,
                            blurVariance = blurVariance,
                            brightness = brightness,
                            landmarkMinConf = primaryFace.minLandmarkConfidence,
                            noiseSigma = cropNoiseSigma,
                            poseSource = PoseSource.MESH,
                            cropSharpness = cropSharpness,
                            faceLuma = cropFaceLuma,
                            interOcularPx = interOcularPx,
                        )
                    } else {
                        FaceQualitySignals(
                            faceCount = 1,
                            yawDegrees = primaryFace.estimatedYaw,
                            pitchDegrees = primaryFace.estimatedPitch,
                            rollDegrees = primaryFace.estimatedRoll,
                            leftEyeOpenProbability = null, // R13: Leave null (unavailable), do not fabricate 1.0f
                            rightEyeOpenProbability = null,
                            faceRatio = primaryFace.faceRatio,
                            centerX = primaryFace.centerX,
                            centerY = primaryFace.centerY,
                            blurVariance = blurVariance,
                            brightness = brightness,
                            landmarkMinConf = primaryFace.minLandmarkConfidence,
                            noiseSigma = cropNoiseSigma,
                            poseSource = PoseSource.YOLO_RATIO,
                            cropSharpness = cropSharpness,
                            faceLuma = cropFaceLuma,
                            interOcularPx = interOcularPx,
                        )
                    }

                    // Wide crop (160x160) for presentation attack detection (WP1.3)
                    val wideCropRect = PadCrop.box(
                        srcW = frameBitmap.width,
                        srcH = frameBitmap.height,
                        faceBox = faceBoxes[0],
                        scale = 2.7f
                    )
                    val wideFace = if (wideCropRect.width > 0 && wideCropRect.height > 0) {
                        try {
                            val rawCrop = Bitmap.createBitmap(
                                frameBitmap,
                                wideCropRect.left,
                                wideCropRect.top,
                                wideCropRect.width,
                                wideCropRect.height
                            )
                            val scaled = Bitmap.createScaledBitmap(rawCrop, 160, 160, true)
                            if (rawCrop != scaled && rawCrop != frameBitmap) {
                                rawCrop.recycle()
                            }
                            scaled
                        } catch (_: Throwable) {
                            null
                        }
                    } else null

                    // Doc 21 WP-C: maintain 4-frame temporal buffer and fuse crops along identity track
                    temporalCropBuffer.add(cropPixels, nowNanos, faceBoxes[0])
                    val fusedPixels = temporalCropBuffer.getFusedCrop(
                        frameMeanLuma = brightness,
                        faceLuma = cropFaceLuma
                    )
                    val fusedFace = if (fusedPixels.isNotEmpty()) {
                        try {
                            val fb = Bitmap.createBitmap(CropMetrics.CROP_SIZE, CropMetrics.CROP_SIZE, Bitmap.Config.ARGB_8888)
                            fb.setPixels(fusedPixels, 0, CropMetrics.CROP_SIZE, 0, 0, CropMetrics.CROP_SIZE, CropMetrics.CROP_SIZE)
                            fb
                        } catch (_: Throwable) {
                            null
                        }
                    } else null

                    val bundle = FrameBundle(
                        frameId = frameId,
                        timestampNanos = nowNanos,
                        trackId = trackId,
                        detections = detections,
                        qualitySignals = liveSignals,
                        alignedCrop = alignedFace,
                        fusedCrop = fusedFace ?: alignedFace,
                        wideCrop = wideFace,
                        meshResult = mesh,
                        trackFrameCount = temporalCropBuffer.size()
                    )

                    mainExecutor.execute {
                        onFrameBundleAnalyzed?.invoke(bundle)
                        onFrameAnalyzed?.invoke(liveSignals, alignedFace)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CameraPreview", "YOLO frame analysis error: ${e.message}", e)
                    val errorBundle = FrameBundle(
                        frameId = frameId,
                        timestampNanos = nowNanos,
                        error = "YOLO detector error: ${e.message}"
                    )
                    mainExecutor.execute {
                        onFrameBundleAnalyzed?.invoke(errorBundle)
                    }
                } finally {
                    frameBitmap.recycle()
                    imageProxy.close()
                }
            }

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    activeCameraProvider = cameraProvider
                    onProviderInitialized?.invoke(cameraProvider)

                    val previewBuilder = Preview.Builder().setResolutionSelector(resolutionSelector)
                    Camera2Interop.Extender(previewBuilder).lowLightFriendly(supportedFpsRange)
                    val preview = previewBuilder.build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    cameraProvider.unbindAll()
                    try {
                        val boundCam = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        activeCamera = boundCam
                        cameraCaptureController?.attachCamera(boundCam)
                    } catch (e: Exception) {
                        // Preferred camera unavailable (emulator/edge device) — fall back to the other lens.
                        android.util.Log.w("CameraPreview", "Preferred camera unavailable, trying fallback: ${e.message}")
                        val fallback = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                            CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                        try {
                            val boundCam = cameraProvider.bindToLifecycle(
                                lifecycleOwner, fallback, preview, imageAnalysis
                            )
                            activeCamera = boundCam
                            cameraCaptureController?.attachCamera(boundCam)
                        } catch (e2: Exception) {
                            android.util.Log.e("CameraPreview", "Fallback camera bind also failed: ${e2.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CameraPreview", "Camera initialization listener failed: ${e.message}", e)
                }
            }, mainExecutor)

            onDispose {
                try {
                    cameraCaptureController?.detachCamera()
                    analysisExecutor.shutdown()
                    activeCameraProvider?.unbindAll()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            android.util.Log.e("CameraPreview", "Failed to start camera provider: ${e.message}", e)
            onDispose {}
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

data class LumaAndNoise(val meanLuma: Float, val noiseSigma: Float)

fun computeFaceLumaAndNoise(bitmap: Bitmap, box: Rect): LumaAndNoise {
    val left = (box.left + 1).coerceIn(1, bitmap.width - 2)
    val right = (box.right - 1).coerceIn(left + 1, bitmap.width - 1)
    val top = (box.top + 1).coerceIn(1, bitmap.height - 2)
    val bottom = (box.bottom - 1).coerceIn(top + 1, bitmap.height - 1)

    var sum = 0L
    var count = 0
    val residuals = ArrayList<Float>(1024)

    val step = maxOf(1, minOf((right - left) / 32, (bottom - top) / 32))
    var y = top
    while (y < bottom) {
        var x = left
        while (x < right) {
            val c = getLuminance(bitmap, x, y)
            sum += c.toLong()
            val up = getLuminance(bitmap, x, y - 1)
            val down = getLuminance(bitmap, x, y + 1)
            val leftPx = getLuminance(bitmap, x - 1, y)
            val rightPx = getLuminance(bitmap, x + 1, y)
            val lap = kotlin.math.abs(up + down + leftPx + rightPx - 4 * c)
            residuals.add(lap)
            count++
            x += step
        }
        y += step
    }

    val meanLuma = if (count > 0) sum.toFloat() / count else 0f
    val noiseSigma = if (residuals.isNotEmpty()) {
        residuals.sort()
        val median = residuals[residuals.size / 2]
        median * 1.4826f
    } else 0f

    return LumaAndNoise(meanLuma, noiseSigma)
}

private fun computeBitmapLaplacianVariance(bitmap: Bitmap, box: Rect): Float {
    val left = (box.left + 1).coerceIn(1, bitmap.width - 2)
    val right = (box.right - 1).coerceIn(left + 1, bitmap.width - 1)
    val top = (box.top + 1).coerceIn(1, bitmap.height - 2)
    val bottom = (box.bottom - 1).coerceIn(top + 1, bitmap.height - 1)

    val step = ((bottom - top) / 30).coerceAtLeast(1)
    var count = 0
    var mean = 0.0
    var m2 = 0.0

    var y = top
    while (y < bottom) {
        var x = left
        while (x < right) {
            val c = getLuminance(bitmap, x, y)
            val up = getLuminance(bitmap, x, y - 1)
            val down = getLuminance(bitmap, x, y + 1)
            val leftPx = getLuminance(bitmap, x - 1, y)
            val rightPx = getLuminance(bitmap, x + 1, y)

            val lap = (up + down + leftPx + rightPx - 4 * c).toDouble()
            count++
            val delta = lap - mean
            mean += delta / count
            val delta2 = lap - mean
            m2 += delta * delta2
            x += step
        }
        y += step
    }

    return if (count > 0) (m2 / count).toFloat() else 0f
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
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
            }
            rotated
        } else {
            bitmap
        }
    } catch (e: Exception) {
        android.util.Log.e("CameraPreview", "toBitmap conversion failed: ${e.message}", e)
        null
    }
}

