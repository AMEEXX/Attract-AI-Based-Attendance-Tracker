# .check

> 19 nodes

## Key Concepts

- **.check()** (28 connections) — `app/src/main/java/com/attract/attendance/domain/face/LivenessEngine.kt`
- **LivenessEngineTest** (11 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.signals()** (8 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **WP1.1 Strip non-spoof gates out of `LivenessEngine`** (6 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP1 — Separate *quality* from *liveness* (quick win, ships first)** (6 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **Issue C — Enforce Straight → Left → Right pose sequence with full per-frame anti-spoof validation** (5 connections) — `docs/00-fix-plan-device-parity-pose-antispoof.md`
- **.closedEyes_rejected()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.darkAndBlurryFrame_passedByLiveness()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.lowLight_passedByLiveness_qualityEngineOwnsIt()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.naturalMovementCrossFrame_passed()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.staticCrossFramePresentationAttack_rejected()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.staticLowQualityBlur_passedByLiveness_qualityEngineOwnsIt()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **Tests** (4 connections) — `docs/00-fix-plan-device-parity-pose-antispoof.md`
- **WP1.5 Tests** (3 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP1.3 Run pixel statistics on the right crop** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **Files** (1 connections) — `docs/00-fix-plan-device-parity-pose-antispoof.md`
- **Fix design** (1 connections) — `docs/00-fix-plan-device-parity-pose-antispoof.md`
- **Root cause** (1 connections) — `docs/00-fix-plan-device-parity-pose-antispoof.md`
- **WP1.4 UI wording** (1 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`

## Relationships

- [.analyze](analyze.md) (6 shared connections)
- [assertTrue](assertTrue.md) (6 shared connections)
- [LivenessOrchestrator](LivenessOrchestrator.md) (3 shared connections)
- [FaceQualitySignals](FaceQualitySignals.md) (3 shared connections)
- [.calibrationDefaults](calibrationDefaults.md) (2 shared connections)
- [LivenessResult](LivenessResult.md) (2 shared connections)
- [StudentTemplatePair](StudentTemplatePair.md) (2 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (2 shared connections)
- [20 — "Face-ID-grade" Recognition, Anti-Spoofing & Low-Light Plan (RGB-only)](20_—__Face-ID-grade__Recognition,_Anti-Spoofing_&_Low-Light_Plan_RGB-only.md) (2 shared connections)
- [test](test.md) (1 shared connections)
- [FrameObservation](FrameObservation.md) (1 shared connections)
- [.runAdaptiveStrategyBenchmark](runAdaptiveStrategyBenchmark.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/LivenessEngine.kt`
- `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- `docs/00-fix-plan-device-parity-pose-antispoof.md`
- `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`

## Audit Trail

- EXTRACTED: 49 (69%)
- INFERRED: 22 (31%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*