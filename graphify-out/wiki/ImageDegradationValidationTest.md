# ImageDegradationValidationTest

> 15 nodes

## Key Concepts

- **ImageDegradationValidationTest** (10 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- **.realImageDegradation_darkness_rejectedAsDarkOrLowLight()** (6 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- **WP3 — Low-light image enhancement and temporal denoising** (6 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **.computeBitmapBrightness()** (5 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- **.realImageDegradation_overexposure_rejectedAsOverexposed()** (5 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- **.realImageDegradation_excessiveBlur_rejectedAsBlur()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- **.realImageDegradation_offCenterDisplacement_rejectedAsOffCenter()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- **Bitmap** (3 connections)
- **WP3.1 Fast face-ROI luma from the Y plane** (3 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP3.2 `LowLightEnhancer` (pure Kotlin, operates on `IntArray` ARGB)** (3 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP3.5 Tests** (3 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **Context** (2 connections)
- **WP3.3 Temporal denoising of aligned crops (free registration)** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP3.4 Quality gate change** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **.setUp()** (1 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`

## Relationships

- [FaceQualitySignals](FaceQualitySignals.md) (6 shared connections)
- [.evaluate](evaluate.md) (4 shared connections)
- [assertTrue](assertTrue.md) (4 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (3 shared connections)
- [LowLightEnhancer](LowLightEnhancer.md) (2 shared connections)
- [.calibrationDefaults](calibrationDefaults.md) (1 shared connections)
- [.check](check.md) (1 shared connections)
- [YoloFaceDetector](YoloFaceDetector.md) (1 shared connections)
- [CameraPreview.kt](CameraPreview.kt.md) (1 shared connections)
- [TemporalCropBuffer](TemporalCropBuffer.md) (1 shared connections)
- [20 — "Face-ID-grade" Recognition, Anti-Spoofing & Low-Light Plan (RGB-only)](20_—__Face-ID-grade__Recognition,_Anti-Spoofing_&_Low-Light_Plan_RGB-only.md) (1 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`

## Audit Trail

- EXTRACTED: 26 (62%)
- INFERRED: 16 (38%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*