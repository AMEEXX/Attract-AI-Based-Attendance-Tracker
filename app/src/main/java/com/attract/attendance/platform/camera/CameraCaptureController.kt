package com.attract.attendance.platform.camera

import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller for camera hardware operations (Doc 20 WP3.1):
 * - Auto-exposure / auto-focus lock and unlock
 * - Exposure compensation index adjustments
 * - Torch control
 */
class CameraCaptureController {

    private var camera: Camera? = null

    private val _torchState = MutableStateFlow(false)
    val torchState: StateFlow<Boolean> = _torchState.asStateFlow()

    private val _exposureCompensationIndex = MutableStateFlow(0)
    val exposureCompensationIndex: StateFlow<Int> = _exposureCompensationIndex.asStateFlow()

    fun attachCamera(camera: Camera) {
        this.camera = camera
    }

    fun detachCamera() {
        this.camera = null
    }

    fun setTorch(enabled: Boolean) {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(enabled)
            _torchState.value = enabled
        }
    }

    fun setExposureCompensation(index: Int) {
        val cam = camera ?: return
        val range = cam.cameraInfo.exposureState.exposureCompensationRange
        val clamped = index.coerceIn(range.lower, range.upper)
        if (cam.cameraInfo.exposureState.isExposureCompensationSupported) {
            cam.cameraControl.setExposureCompensationIndex(clamped)
            _exposureCompensationIndex.value = clamped
        }
    }

    fun lockExposureAndFocus(meteringPointX: Float = 0.5f, meteringPointY: Float = 0.5f) {
        val cam = camera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(meteringPointX, meteringPointY)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AF)
            .disableAutoCancel()
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    fun unlockExposureAndFocus() {
        val cam = camera ?: return
        cam.cameraControl.cancelFocusAndMetering()
    }
}
