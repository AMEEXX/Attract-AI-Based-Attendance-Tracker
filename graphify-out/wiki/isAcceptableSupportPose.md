# .isAcceptableSupportPose

> 7 nodes

## Key Concepts

- **.isAcceptableSupportPose()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/AdaptiveVerificationEngine.kt`
- **WP5 — Pose-tolerant quality and stronger recognition** (6 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP5.1 `FaceQualityConfig` version 4 (degree-true thresholds)** (5 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP5.4 Track-fused embedding and optional flip test-time augmentation** (4 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **Safety invariants (S1–S5)** (2 connections) — `docs/16-lld-adaptive-multiframe-verification.md`
- **WP5.2 Attendance no longer needs a straight first frame** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP5.5 Recalibrate thresholds for the new pose range** (1 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`

## Relationships

- [FaceQualitySignals](FaceQualitySignals.md) (3 shared connections)
- [AdaptiveVerificationEngine](AdaptiveVerificationEngine.md) (2 shared connections)
- [.evaluate](evaluate.md) (2 shared connections)
- [AdaptiveVerificationEngineTest](AdaptiveVerificationEngineTest.md) (1 shared connections)
- [FrameObservation](FrameObservation.md) (1 shared connections)
- [.calibrationDefaults](calibrationDefaults.md) (1 shared connections)
- [.scoreGroupedMax](scoreGroupedMax.md) (1 shared connections)
- [RecognitionDecisionEngine](RecognitionDecisionEngine.md) (1 shared connections)
- [20 — "Face-ID-grade" Recognition, Anti-Spoofing & Low-Light Plan (RGB-only)](20_—__Face-ID-grade__Recognition,_Anti-Spoofing_&_Low-Light_Plan_RGB-only.md) (1 shared connections)
- [WP5.3 Enrollment: add a "phone-low" template, and adaptive template updates](WP5.3_Enrollment-_add_a__phone-low__template,_and_adaptive_template_updates.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/AdaptiveVerificationEngine.kt`
- `docs/16-lld-adaptive-multiframe-verification.md`
- `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`

## Audit Trail

- EXTRACTED: 11 (55%)
- INFERRED: 9 (45%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*