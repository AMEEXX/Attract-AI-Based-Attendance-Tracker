# LivenessOrchestrator

> 22 nodes

## Key Concepts

- **LivenessOrchestrator** (15 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- **LivenessPolicy** (12 connections) — `app/src/main/java/com/attract/attendance/domain/face/LivenessPolicy.kt`
- **LivenessOrchestrator.kt** (8 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- **1. Ground rules for the implementer** (8 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **PresentationAttackDetector** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackDetector.kt`
- **3. Fix plan (apply on top of the doc-20 code once `39a221b` is pushed)** (7 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **BiometricFeatureFlags** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/BiometricFeatureFlags.kt`
- **Doc 21 WP-D: Noise-Compensated Pixel Gates** (5 connections) — `docs/12-lld-liveness-and-anti-spoof-engine.md`
- **0. Progress log (read first if resuming)** (5 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **WP-D — PAD pixel gates: stop false "Screen detected" (and stop calling quality problems spoofs)** (5 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **WP1.2 Introduce `LivenessPolicy` and recalibrate the pixel gates** (4 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **Thresholds** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackDetector.kt`
- **.startChallenge()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- **.markInjectionSuspected()** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- **.reset()** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- **.setFlashScore()** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- **BiometricFeatureFlags.kt** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/BiometricFeatureFlags.kt`
- **LivenessPolicy.kt** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/LivenessPolicy.kt`
- **PresentationAttackDetector.kt** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackDetector.kt`
- **WP-C — Multi-frame fused crop for the embedding (the low-light accuracy win: +7…+26 points)** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **WP-E — Camera (verify doc-20 WP2 in `39a221b`; these are the settings that matter for backlight)** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **WP-F — UX: the student should not have to "find the light"** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`

## Relationships

- [FaceQualitySignals](FaceQualitySignals.md) (6 shared connections)
- [.analyze](analyze.md) (5 shared connections)
- [20 — "Face-ID-grade" Recognition, Anti-Spoofing & Low-Light Plan (RGB-only)](20_—__Face-ID-grade__Recognition,_Anti-Spoofing_&_Low-Light_Plan_RGB-only.md) (4 shared connections)
- [.decide](decide.md) (4 shared connections)
- [.check](check.md) (3 shared connections)
- [LivenessResult](LivenessResult.md) (3 shared connections)
- [ChallengeController](ChallengeController.md) (2 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (2 shared connections)
- [21 — Why recognition got WORSE after doc 20, and the measured fix (low light, backlight, "Too blurry", "Screen detected")](21_—_Why_recognition_got_WORSE_after_doc_20,_and_the_measured_fix_low_light,_backlight,__Too_blurry_,__Screen_detected_.md) (2 shared connections)
- [AttendanceScreen](AttendanceScreen.md) (1 shared connections)
- [Change-control record](Change-control_record.md) (1 shared connections)
- [test](test.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/BiometricFeatureFlags.kt`
- `app/src/main/java/com/attract/attendance/domain/face/LivenessPolicy.kt`
- `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackDetector.kt`
- `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- `docs/12-lld-liveness-and-anti-spoof-engine.md`
- `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`

## Audit Trail

- EXTRACTED: 39 (57%)
- INFERRED: 29 (43%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*