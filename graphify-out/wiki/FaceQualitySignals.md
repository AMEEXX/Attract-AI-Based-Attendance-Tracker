# FaceQualitySignals

> 23 nodes

## Key Concepts

- **FaceQualitySignals** (44 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualitySignals.kt`
- **FaceQualityConfig** (18 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityConfig.kt`
- **FaceQualityEngine** (18 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **BiometricModelProfile** (12 connections) — `app/src/main/java/com/attract/attendance/domain/face/BiometricModelProfile.kt`
- **ExpectedPose** (12 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- **WP-A — Stop the raw-frame Laplacian from deciding anything (fixes "Too blurry")** (8 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **WP-B — Correct pose estimation (P0, 1 day) — *fixes the left/right symptom*** (7 connections) — `docs/18-attendance-pipeline-root-cause-and-rebuild-plan.md`
- **.score()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.isDistinctFromCaptured()** (5 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **WP4.4 MediaPipe Face Landmarker (478 points, blendshapes, true 3D head pose)** (5 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **.poseBucket()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.averageEyeOpenProbability()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.isInFrame()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.yawInWindow()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.logFailure()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/ConsolidatedFaceVerificationPipelineTest.kt`
- **.yawDiversityGuard_rejectsNearIdenticalCaptures()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.eyesClear()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **BiometricModelProfile.kt** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/BiometricModelProfile.kt`
- **ANY** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- **DOWN_HOLD** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- **LEFT** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- **RIGHT** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- **STRAIGHT** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`

## Relationships

- [.evaluate](evaluate.md) (12 shared connections)
- [LivenessOrchestrator](LivenessOrchestrator.md) (6 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (6 shared connections)
- [ImageDegradationValidationTest](ImageDegradationValidationTest.md) (6 shared connections)
- [.calibrationDefaults](calibrationDefaults.md) (4 shared connections)
- [PoseEstimatorTest](PoseEstimatorTest.md) (4 shared connections)
- [AttendanceScreen.kt](AttendanceScreen.kt.md) (3 shared connections)
- [.isAcceptableSupportPose](isAcceptableSupportPose.md) (3 shared connections)
- [.check](check.md) (3 shared connections)
- [QualityResult](QualityResult.md) (3 shared connections)
- [CameraPreview.kt](CameraPreview.kt.md) (3 shared connections)
- [AdaptiveVerificationEngineTest](AdaptiveVerificationEngineTest.md) (3 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/BiometricModelProfile.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityConfig.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceQualitySignals.kt`
- `app/src/test/java/com/attract/attendance/domain/face/ConsolidatedFaceVerificationPipelineTest.kt`
- `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- `docs/18-attendance-pipeline-root-cause-and-rebuild-plan.md`
- `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`

## Audit Trail

- EXTRACTED: 75 (60%)
- INFERRED: 50 (40%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*