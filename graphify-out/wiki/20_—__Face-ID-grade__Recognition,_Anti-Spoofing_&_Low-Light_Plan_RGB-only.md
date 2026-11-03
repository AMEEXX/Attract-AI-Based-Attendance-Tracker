# 20 — "Face-ID-grade" Recognition, Anti-Spoofing & Low-Light Plan (RGB-only)

> 31 nodes

## Key Concepts

- **20 — "Face-ID-grade" Recognition, Anti-Spoofing & Low-Light Plan (RGB-only)** (17 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP6 — Learned passive anti-spoofing (PAD CNN)** (7 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **AttemptTrace** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/AttemptTrace.kt`
- **WP9 — Evaluation and calibration (do not ship thresholds without this)** (6 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **0. Read this first** (5 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP0 — Flags, telemetry and data collection (foundation)** (4 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **.attemptTrace_toString_containsTelemetryWithoutEmbeddingsOrFloatArrays()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/AttemptTraceTest.kt`
- **0.1 What the user sees today, and why (from the code)** (3 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP0.1 `BiometricFeatureFlags`** (3 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP8 — "Just look at it" UX (auto-capture, no hunting for an angle)** (3 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP9.1 Offline scripts (extend `tools/biometric_eval/`)** (3 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **AttemptTraceTest** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/AttemptTraceTest.kt`
- **.toString()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/AttemptTrace.kt`
- **WP0.2 Per-attempt telemetry record (debug + Crashlytics breadcrumbs)** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP0.3 Debug-only data-collection mode (needed for calibration)** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP10 — (Optional, after WP1–9 ship) Stronger recognizer** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP6.4 Engine** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP6.6 Shadow mode first** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP9.4 Device protocol (attacks)** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **AttemptTrace.kt** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/AttemptTrace.kt`
- **0.2 Honest limits (put this in the release notes)** (1 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **0.3 Final pipeline (target architecture)** (1 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **0.4 Acceptance targets (measure with WP9; release only if all pass)** (1 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **12. Rollout order and release gates** (1 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP6.1 Model choice** (1 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- *... and 6 more nodes in this community*

## Relationships

- [LivenessOrchestrator](LivenessOrchestrator.md) (4 shared connections)
- [.check](check.md) (2 shared connections)
- [AttendanceScreen.kt](AttendanceScreen.kt.md) (1 shared connections)
- [test](test.md) (1 shared connections)
- [assertTrue](assertTrue.md) (1 shared connections)
- [00-lld-plan.md](00-lld-plan.md.md) (1 shared connections)
- [Pipeline Details](Pipeline_Details.md) (1 shared connections)
- [ImageDegradationValidationTest](ImageDegradationValidationTest.md) (1 shared connections)
- [WP4 — Detection, 3D landmarks and face tracking](WP4_—_Detection,_3D_landmarks_and_face_tracking.md) (1 shared connections)
- [.isAcceptableSupportPose](isAcceptableSupportPose.md) (1 shared connections)
- [Change-control record](Change-control_record.md) (1 shared connections)
- [AuthResult](AuthResult.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/AttemptTrace.kt`
- `app/src/test/java/com/attract/attendance/domain/face/AttemptTraceTest.kt`
- `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`

## Audit Trail

- EXTRACTED: 40 (71%)
- INFERRED: 16 (29%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*