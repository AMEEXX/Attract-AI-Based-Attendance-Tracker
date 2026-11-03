# FaceQualitySignals

> God node · 44 connections · `app/src/main/java/com/attract/attendance/domain/face/FaceQualitySignals.kt`

**Community:** [FaceQualitySignals](FaceQualitySignals.md)

## Connections by Relation

### calls
- .signals() `INFERRED`
- .runAdaptiveStrategyBenchmark() `INFERRED`
- .runRealPipeline() `EXTRACTED`
- .decodeAndProcess() `EXTRACTED`
- .runPipeline() `INFERRED`
- .`R05 reproduction - finished engine throws IllegalStateException if called before reset`() `INFERRED`
- .`adaptive engine with stale-dimension templates fails closed without crashing`() `INFERRED`
- .signals() `INFERRED`
- .terminalEngineCannotBeReused_andMustBeClearedBeforeNewAttempt() `EXTRACTED`
- .realImageDegradation_darkness_rejectedAsDarkOrLowLight() `INFERRED`
- .makeInvalidObservation() `EXTRACTED`
- .realImageDegradation_overexposure_rejectedAsOverexposed() `INFERRED`
- .testLeftTurnProducesCalibratedNegativeYaw() `INFERRED`
- .testRightTurnProducesCalibratedPositiveYaw() `INFERRED`
- .livenessCheck_integratesPresentationAttackGates() `INFERRED`
- .mockBundle() `EXTRACTED`
- .realImageDegradation_excessiveBlur_rejectedAsBlur() `INFERRED`
- .realImageDegradation_offCenterDisplacement_rejectedAsOffCenter() `INFERRED`
- .testRelativeGatingWithAnchorStraightYaw() `INFERRED`

### contains
- FaceQualitySignals.kt `EXTRACTED`

### imports
- [AttendanceScreen.kt](AttendanceScreen.kt.md) `EXTRACTED`
- [CameraPreview.kt](CameraPreview.kt.md) `EXTRACTED`
- LivenessOrchestrator.kt `EXTRACTED`
- AutoCaptureControllerTest.kt `EXTRACTED`

### references
- .evaluate() `EXTRACTED`
- CameraPreview() `EXTRACTED`
- .check() `EXTRACTED`
- .observation() `EXTRACTED`
- .evaluate() `EXTRACTED`
- WP-A — Stop the raw-frame Laplacian from deciding anything (fixes "Too blurry") `INFERRED`
- WP-B — Correct pose estimation (P0, 1 day) — *fixes the left/right symptom* `INFERRED`
- .isAcceptableSupportPose() `EXTRACTED`
- .score() `EXTRACTED`
- .acceptedQuality() `EXTRACTED`
- .passedLiveness() `EXTRACTED`
- ImageRecord `EXTRACTED`
- .onFrame() `EXTRACTED`
- WP4.4 MediaPipe Face Landmarker (478 points, blendshapes, true 3D head pose) `INFERRED`
- .poseBucket() `EXTRACTED`
- .logFailure() `EXTRACTED`
- .averageEyeOpenProbability() `EXTRACTED`
- .isInFrame() `EXTRACTED`
- WP3.1 Fast face-ROI luma from the Y plane `INFERRED`
- .eyesClear() `EXTRACTED`

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*