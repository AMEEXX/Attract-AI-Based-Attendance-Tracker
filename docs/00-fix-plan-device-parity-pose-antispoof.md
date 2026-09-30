# Fix Plan — Device Parity, 3-Pose Capture Enforcement, Anti-Spoof Validation

**Status:** Proposed — awaiting approval before implementation
**Scope:** Fixes three field-reported issues and closes SDD/LLD documentation gaps
**Docs updated by this plan:** LLD-09, LLD-10, LLD-11/12, LLD-13 (see "Doc Gap Closure" below)

---

## Issue A — Screen pinning reports failure on physical device, works on emulator

### Root cause
`LockTaskControllerImpl.start()` (app/src/main/java/com/attract/attendance/lockdown/platform/LockTaskControllerImpl.kt:19-32) calls `startLockTask()` and then **synchronously** reads `ActivityManager.lockTaskModeState`. On emulators the state flips immediately; on physical devices the transition is asynchronous, and on consumer (non-Device-Owner) devices Android first shows the system pinning-confirmation dialog. The immediate read returns `LOCK_TASK_MODE_NONE` → `LockTaskResult.Error` → UI shows pinning unavailable even though pinning is enabled and (moments later) active.

### Fix design
1. In `LockTaskControllerImpl.start()`: after `startLockTask()`, poll `currentLockState()` on a delay loop (250 ms interval, ~5 s timeout). Return `Started` as soon as state is `Locked`.
2. Only return `Error` when the timeout expires with state still unlocked AND no SecurityException occurred. Keep existing SecurityException handling unchanged.
3. Optional UX polish: expose an intermediate "awaiting confirmation" state so the badge can show a pending hint while polling.

### Files
- `lockdown/platform/LockTaskControllerImpl.kt` — polling start()
- `feature/attendance/AttendanceScreen.kt:154-184` — no logic change needed; already handles Started/Error correctly once start() is truthful

### Tests
- Unit (`LockTaskControllerTest`): fake state that transitions after N polls → `Started`; never transitions → `Error`; SecurityException → `Error` (unchanged).
- Manual matrix: emulator API 33/34 + one physical device with screen pinning enabled; verify badge appears on both within ~5 s.

### Acceptance criteria
With screen pinning ON, physical device shows pinned badge within 5 s of session start, matching emulator behavior.

---

## Issue B — Face verification not working on physical device

### Root cause hypotheses (to confirm with diagnostics first)
1. **No pose diversity in templates**: because the quality gate rejects real left/right turns (`|yaw| > 20°` → POSE), enrollment stores three near-identical frontal embeddings; matching degrades on noisy real-camera input.
2. **Silent failures**: `submitRecognition()` wraps everything in `runCatching` (AttendanceScreen.kt:290-389); TFLite/crop errors surface only via Logcat ("Face verification temporarily unavailable"), easy to miss.
3. **Crop/rotation correctness at real sensor resolutions**: CameraPreview rotation/crop math must be verified on a device whose sensor size/orientation differs from the emulator's.
4. **Threshold calibration**: cosine accept threshold 0.45 may behave differently with genuine camera noise vs emulator frames — needs measurement, not guessing.

### Fix design
1. **Diagnostics first**: capture adb logs from a physical device during a failing verification; add temporary verbose logging of per-frame signals (yaw/pitch/blur/brightness/faceRatio), embedding norms, and top cosine scores. Confirm which hypothesis applies.
2. Verify crop/rotation: unit-test `cropFaceFromBitmap` + `toBitmap` against rotated fixtures of varying sizes; fix any orientation mismatch found.
3. After Issue C lands (real pose-diverse templates), re-measure genuine-imposter/genuine-genuine score distributions on-device; recalibrate `acceptThreshold` / `ambiguousMargin` if needed.
4. Keep fail-closed behavior (never PRESENT on error) but improve error surfacing: distinct UI message for model-unavailable vs processing-error, both logged with cause.

### Files
- `feature/attendance/CameraPreview.kt` (crop/rotation), `domain/face/EmbeddingEngine.kt`, `RecognitionDecisionEngine.kt`, `AttendanceScreen.kt`

### Tests
- Instrumented test on physical device: enroll → end session → re-enter → recognize → PRESENT (existing `FaceSessionE2EAndroidTest` extended to run on device).
- Threshold recalibration documented in `FaceQualityConfig` version notes.

---

## Issue C — Enforce Straight → Left → Right pose sequence with full per-frame anti-spoof validation

### Root cause
Prompts say LEFT/RIGHT but nothing enforces pose: `captureClick()` (AttendanceScreen.kt:221-283) validates every frame identically, and the global `|yaw| ≤ 20°` POSE gate actively *rejects* genuine profiles. Result: three straight faces are accepted regardless of prompt.

### Fix design
1. Introduce `ExpectedPose { STRAIGHT, LEFT, RIGHT }` and extend `FaceQualityEngine.evaluate(signals, config, expectedPose)` with per-step yaw windows:
   - STRAIGHT: `-15°..+15°` yaw; pitch `≤ 20°`
   - LEFT: `-45°..-20°` yaw
   - RIGHT: `+20°..+45°` yaw
   - New reasons: `POSE_NOT_STRAIGHT`, `POSE_NOT_LEFT`, `POSE_NOT_RIGHT` with step-specific guidance messages ("Turn your head slightly LEFT", "Turn back to center").
   - Legacy 2-arg overload keeps straight-window behavior for recognition-time checks (backwards compatible).
2. Yaw sign normalization in CameraPreview adapter so LEFT is consistently negative across mirrored/sensor-variant devices.
3. Track current step explicitly in AttendanceScreen (`captureStep` state) instead of deriving prompts from frame counts; pass it to `evaluate()` and advance ONLY on acceptance.
4. Per-frame validation pipeline on every CLICK, in order: face presence → single-face → step-aware pose → blur → brightness → eyes → LivenessEngine (incl. new presentation-attack gates).
5. Duplicate-frame guard: accepted consecutive frames must differ in yaw ≥ 15°, blocking "3 identical frontal frames" workarounds.
6. Presentation-attack detection (per LLD-12 update): add moiré/high-frequency energy check, specular-highlight blob check, print-photo flat-texture check to the liveness layer operating on the cropped bitmap; each returns a student-safe reject message and forces retake. Frame is accepted ONLY when ALL validations pass.
7. On rejection: do NOT advance step, clear nothing else, show specific guidance, allow immediate retap.

### Files
- `domain/face/FaceQualitySignals.kt`, `FaceQualityConfig.kt` (step windows, PAT thresholds), `FaceQualityEngine.kt`, `FaceQualityResult.kt` (new reasons)
- `domain/face/LivenessEngine.kt` + new `PresentationAttackDetector.kt` (pure functions over Bitmap)
- `feature/attendance/CameraPreview.kt` (yaw normalization), `feature/attendance/AttendanceScreen.kt` (step state machine)

### Tests
- `FaceQualityEngineTest`: per-step windows (accept/reject matrices for all 3 steps × yaw values), duplicate-yaw guard.
- `LivenessEngineTest` + new `PresentationAttackDetectorTest`: synthetic moiré/highlight/flat-texture fixtures rejected; clean live-like fixtures pass.
- Functional pipeline test: full Straight→Left→Right flow rejects wrong-pose submissions at each step and completes only with correct sequence.

---

## Doc Gap Closure (already applied to docs/)
| Gap | Where fixed |
|---|---|
| Step-aware pose windows contradicting global yaw gate | LLD-09 §"Step-Aware Pose Gating" |
| Ordered 3-pose capture with per-step validation & retake rule | LLD-10 step 1 |
| Screen-replay / phone-screen / print-photo detection algorithms | LLD-12 §"Presentation-Attack Detection" |
| Pinning async state transition + user-confirm dialog + OEM variance | LLD-13 §"Asynchronous lock-state confirmation" |

## Implementation order
1. **Phase 1 (Issue A)** — small, isolated; ship first to unblock physical-device testing.
2. **Phase 2 (Issue B diagnostics)** — instrument + verify crop/rotation on device; no behavior change yet.
3. **Phase 3 (Issue C)** — step-aware pose engine + presentation-attack detector + step state machine; largest change, gated by tests.
4. **Phase 4** — on-device threshold recalibration using real pose-diverse templates; update config versions.
