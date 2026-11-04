# 22 — Backlight "Screen replay detected" false rejects + laptop-photo acceptance: root cause, measurements, fix (as built)

Status: **IMPLEMENTED (Kotlin) + JVM unit tests green. Device validation (§7) still required before merge to `dev`/`main`.**
Base: `origin/dev` @ `d21c17c` (contains `f85e7a8`). Branch: `genspark_ai_developer_backlight_spoof`.
Policy id: **`liveness_v5_doc22`** (`LivenessPolicy.id` and `BiometricModelProfile.livenessPolicyId`).

---

## 0. Progress log (read first if resuming)

| # | Item | State | Commit |
|---|---|---|---|
| 0.1 | Code audit of the dev liveness path | DONE (§1) | `d21c17c` |
| 0.2 | exp7 (backlight pixel-PAD FP, MiniFASNet scores) / exp8 (3D parallax real vs planar) | DONE (§2.1, §2.2) | `552bedb` |
| 0.3 | exp10: validate the policy (inner-face pixel PAD + MiniFASNet median-3 + mandatory 3D) | DONE (§2.3); numbers locked | — |
| 0.4 | WP-C: single-source orchestrator history, 2-of-3 veto, MEDIUM → `ACTIVE_CHECK_REQUIRED`, policy id | DONE | `bdbc05f` |
| 0.5 | WP-B: `MiniFasNetEngine` (silentface.tflite) + pure `PadCnnMath` | DONE | `a1cb019` |
| 0.6 | WP-A/B/C wiring: inner-crop pixel PAD, wide-crop MiniFASNet, continuous preview feeding, guidance UI | DONE | `4394356` |
| 0.7 | Unit tests (RiskFusion, ParallaxDepthTest, LivenessOrchestrator, PadCnnMath) | DONE: 74/74 pass on JVM (§6) | `f6e7efc` |
| 0.8 | This document finalised | DONE | (this commit) |
| 0.9 | Android build (`./gradlew assembleDebug testDebugUnitTest`) | **TODO.** No Android SDK in the sandbox; Android-dependent files were reviewed, not compiled | — |
| 0.10 | Device validation protocol (§7) | **TODO** | — |
| 0.11 | Open items (§8): flash liveness not wired, per-frame latency budget, head-turn magnitude | **TODO** | — |

### Resume notes (no Android SDK in the sandbox)
- JVM toolchain in `scratch/tk/` (git-ignored). Re-create with:
  `mkdir -p scratch/tk && cd scratch/tk && curl -sLO https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip && unzip -q kotlin-compiler-2.0.21.zip && curl -sL -o junit.jar https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar && curl -sL -o hamcrest.jar https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar`
- Exact test command: §6.2.
- **Experiments are finished. Do not re-run Python or download datasets for this doc.** The exp7/exp8/exp10 numbers below are the locked inputs.

---

## 1. Audit findings on dev (`f85e7a8`), all fixed

| # | Defect | Effect | Fixed by |
|---|---|---|---|
| 1 | `captureClick` → `livenessOrchestrator.onFrame(..., padScore = null)`; no PAD model shipped | PAD CNN never contributed | WP-B: `silentface.tflite` + `MiniFasNetEngine`; score flows via `PresentationAttackSignals.padScore` |
| 2 | `RiskFusion` MEDIUM was mapped to `LivenessResult.Passed`; `startChallenge()` never called | Anything not conclusively flat **passed** | WP-C: MEDIUM → `Rejected(ACTIVE_CHECK_REQUIRED)` + guidance; challenge auto-started |
| 3 | `onFrame` called only on capture; auto-capture after 2–4 frames | `meshHistory` had 1–2 frames, Δyaw < 4°, so depth was always inconclusive. **Laptop photo bypass** | WP-C: preview callback feeds `onFrame` on every analysed frame |
| 4 | Pixel PAD on the 2.7× `wideCrop` (mostly background) | White wall / window behind gave `specular > 0.25` → "Screen glare". **Backlight false reject** | WP-A: pixel stats on the INNER 1.0× aligned 112×112 crop |
| 5 | `evaluate` re-appended the current pixel trip when the list had < 3 entries | One tripped frame counted twice → instant "2-of-3" veto | WP-C: only `onFrame` mutates history; `evaluate` is read-only |

---

## 2. Measurements (locked inputs)

### 2.1 exp7: dev pixel PAD on the 2.7× wide crop (200 LFW identities per scene)
| scene | dev pixel PAD reject | reason | MiniFASNetV2 live p50 |
|---|---|---|---|
| genuine, normal background | 1/200 (0.5 %) | GLARE | 0.961 |
| **genuine, white sun-lit wall** | **200/200 (100 %)** | SCREEN_GLARE (wide-crop specular p50 = 0.49 > 0.25) | 0.983 |
| **genuine, window behind** | **200/200 (100 %)** | SCREEN_GLARE | 0.978 |
| **genuine, hard window backlight** | **182/182 (100 %)** | SCREEN_GLARE | 0.988 |
| **attack, photo on laptop, dark bg** | **0/198 (0 %)**, passes | — | **0.002** |
| attack, photo on phone, dark bezel | 0/199 (0 %) | — | 0.435 |
| attack, A4 print | 0/200 (0 %) | — | 0.941 |

The inner 1.0× face specular ratio p95 is 0.000–0.002 in every genuine scene, so the "glare" was 100 % background.

### 2.2 exp8: MediaPipe 3D parallax (ParallaxDepthTest port), real head rotation vs flat photo (Pointing'04, 15 subjects)
| pair | residual p5 / p50 / p95 | MediaPipe \|Δyaw\| p50 / max |
|---|---|---|
| real head pan 15° | 0.042 / 0.060 / 0.091 | 11.6 / 17.4 |
| real head pan 30° | 0.109 / 0.154 / 0.224 | 23.1 / 30.5 |
| real head tilt 15° | 0.015 / 0.046 / 0.073 | 2.1 / 9.1 |
| flat photo turned 5° | 0.004 / 0.006 / 0.010 | 1.1 / 2.2 |
| flat photo turned 15° | 0.009 / 0.013 / 0.021 | 2.9 / 5.0 |
| flat photo turned 25° | 0.016 / 0.021 / 0.031 | 4.9 / 7.6 |
| flat photo tilted 15° | 0.006 / 0.013 / 0.020 | 0.4 / 4.4 |
| flat photo static + hand jitter | 0.002 / 0.004 / 0.007 | 0.4 / 1.4 |

- AUC (real pan 15° vs all planar) = **0.998**.
- With residual ≥ **0.035** and pose delta ≥ **8°**: real 97 % pass, planar 0 % pass.
- A flat photo turned 25° only reports a MediaPipe yaw of about 5°, so the 8° trigger is effectively reachable only by a real head.

### 2.3 exp10: proposed policy validation (locked numbers)
- **MiniFASNetV2 3-frame median (`pad_median3`)**:
  - `< 0.10` catches **84.4 %** of laptop replays (`A:laptop_dark` p50 = 0.002).
  - Genuine window-backlit faces (`G:window_backlit`) fall below 0.10 in **8.7 %** of cases.
  - Therefore **a low PAD must escalate to an active check (MEDIUM), never hard-reject.**
- **Inner-face pixel PAD** (1.0× tight crop → 112×112 → analyser 96×96), 2-of-3 temporal veto with `hfeCompensated > 0.22 | specular > 0.25 | centerStd < 3.0`:
  - **0.0 % false veto** on `G:normal_bg`, `G:white_wall`, `G:window_backlit`, `G:window_backlit_hard`.
- **3D parallax**: `DEFAULT_MIN_POSE_DELTA_DEG = 8.0`, `DEFAULT_MIN_PARALLAX_RESIDUAL = 0.035`.

---

## 3. Policy (`LivenessPolicy`, `liveness_v5_doc22`): single source of thresholds

| field | value | source |
|---|---|---|
| `maxHighFrequencyEnergy` (on `hfeCompensated`) | 0.22 | Doc 21 WP-D, exp10 |
| `maxSpecularHighlightRatio` | 0.25 | exp10 (inner crop) |
| `printFlatTextureStdDev` | 3.0 | exp10 |
| `pixelVetoWindow` / `pixelVetoMinHits` | 3 / 2 | Doc 21 WP-D |
| `padSpoofReject` | **0.10** (escalate only) | exp10 |
| `padLiveAccept` | 0.80 (telemetry only) | — |
| `parallaxMinYawDeltaDeg` | **8.0** (applied to max(\|Δyaw\|, \|Δpitch\|)) | exp8 |
| `parallaxLiveResidual` | **0.035** | exp8 |
| `meshWindowMs` | 3000 | history older than this is ignored |
| `maxChallengeMs` | 4000 | challenge timeout = HIGH |

`depthScoreFor(residual, min) = (residual / (2·min)).coerceIn(0, 1)`. So residual = 0.035 gives exactly **0.50** (`DEPTH_LIVE`), and residual ≤ 0.014 gives ≤ 0.20 (`DEPTH_FLAT`).

---

## 4. Decision contract (`RiskFusion.decide`, as built)

Rules are evaluated in order:
1. `injectionSuspected` → **HIGH**
2. confirmed 2-of-3 pixel veto (inner crop) → **HIGH**
3. random challenge failed / timed out → **HIGH**
4. `flashScore < 0.35` → **HIGH**
5. conclusive flat parallax (`!inconclusive && depthScore < 0.20`) → **HIGH**
6. conclusive live parallax (`!inconclusive && depthScore ≥ 0.50`):
   - PAD median not suspect (null or ≥ 0.10) → **LOW**
   - PAD suspect + challenge passed → **LOW**
   - PAD suspect, no challenge yet → **MEDIUM** (the orchestrator starts one random challenge)
7. challenge passed without depth + PAD not suspect → **LOW**
8. everything else (including **every `depthInconclusive == true` case**) → **MEDIUM**

Mapping in `LivenessOrchestrator.evaluate` → `LivenessVerdict(risk, result, guidance, evidence, depth)`:
- **LOW** → `LivenessResult.Passed`
- **MEDIUM** → `Rejected(ACTIVE_CHECK_REQUIRED, guidance message)`. **Never `Passed`.** UI shows guidance, not a spoof message.
  - `TURN_HEAD`: no conclusive depth yet.
  - `CHALLENGE_BLINK / _TURN_LEFT / _TURN_RIGHT / _SMILE`: depth live but PAD suspect.
- **HIGH** → `Rejected(pixelVeto ?: PASSIVE_SPOOF_SUSPECTED)`. The UI resets the orchestrator and pauses auto-capture for 1.5 s.

**Consequence the product owner must know:** with this contract **every** live check-in needs a conclusive 3D head movement (≥ 8° yaw or pitch with a residual ≥ 0.035). A student who keeps perfectly still stays at "Turn your head slightly" indefinitely. This is intentional: it is the only RGB-only signal that rejected the laptop replay in exp8.

---

## 5. As-built implementation

### 5.1 Files
| file | change |
|---|---|
| `domain/face/LivenessPolicy.kt` | id `liveness_v5_doc22`, all Doc-22 thresholds (§3) |
| `domain/face/LivenessEngine.kt` | `LivenessReason.ACTIVE_CHECK_REQUIRED` (guidance, not a spoof verdict) |
| `domain/face/BiometricModelProfile.kt` | `livenessPolicyId = "liveness_v5_doc22"` |
| `domain/face/PresentationAttackSignals.kt` | `padScore: Float?` (MiniFASNet, wide crop); pixel fields = inner crop |
| `domain/face/FrameBundle.kt` | `patSignals: PresentationAttackSignals?` per frame |
| `domain/face/PadCnnMath.kt` (new) | pure NCHW-BGR-raw packing, softmax, `liveProbability` (index 1) |
| `domain/face/liveness/ParallaxDepthTest.kt` | `poseDelta = max(|Δyaw|, |Δpitch|)`; inconclusive results keep `poseDeltaDeg`; defaults 8° / 0.035; `depthScoreFor` |
| `domain/face/liveness/RiskFusion.kt` | Doc-22 contract (§4) |
| `domain/face/liveness/LivenessOrchestrator.kt` | rewritten: single-source timed history, 2-of-3 veto from policy, PAD median-3, track-change reset, window pruning, `LivenessVerdict` + `LivenessGuidance`, auto-challenge |
| `platform/face/MiniFasNetEngine.kt` (new) | loads `silentface.tflite` (validates `[1,3,80,80]` → `[1,3]`), resizes 2.7× crop to 80×80, packs via `PadCnnMath`, runs the Interpreter, returns live prob or null; zero per-frame allocation; never throws |
| `feature/attendance/PresentationAttackAnalyzer.kt` | `analyze(innerFaceCrop, padScore)` overload; KDoc forbids the 2.7× crop |
| `feature/attendance/CameraPreview.kt` | `MiniFasNetEngine.init`; per frame: `padScore = MiniFasNetEngine.score(wideFace)`, `patSignals = analyze(alignedFace, padScore)` → `FrameBundle.patSignals` |
| `feature/attendance/AttendanceScreen.kt` | preview callback = **only** feeder of `onFrame` (live attendance, CAPTURING/READY, single face; reset when face count ≠ 1); `captureClick` only evaluates; guidance UI for `ACTIVE_CHECK_REQUIRED`; HIGH → reset + 1.5 s cooldown; `[LIVENESS]` log line |
| `feature/attendance/AttendanceStrings.kt` | liveness guidance / rejection strings |

### 5.2 Per-frame data flow (live attendance)
```
CameraPreview analyser (≈150 ms throttle)
  YOLO → aligned 112×112 (alignedFace) + 2.7× wideFace (160×160) + FaceMesh (478 pts, yaw/pitch, blendshapes)
  padScore   = MiniFasNetEngine.score(wideFace)                 // 2.7× crop → 80×80, BGR raw, softmax[1]
  patSignals = PresentationAttackAnalyzer.analyze(alignedFace, padScore)   // pixel stats on INNER crop
  FrameBundle(meshResult, patSignals, trackId, ...)
AttendanceScreen.onFrameBundleAnalyzed
  livenessOrchestrator.onFrame(signals, patSignals, mesh, trackId)   // single source of history
  processAutoCaptureFrame → captureClick → livenessOrchestrator.evaluate(signals)  // read-only
     LOW    → embedding + recognition (unchanged Doc 21 path)
     MEDIUM → "Turn your head slightly" / challenge text, keep previewing
     HIGH   → spoof message, reset, 1.5 s cooldown
```
Enrollment flows are unchanged (single-frame `LivenessEngine.check`).

### 5.3 Invariants (each covered by a unit test)
- `evaluate()` never mutates history: one tripped frame evaluated N times is never a veto.
- Pixel veto needs the same reason on ≥ 2 of the last 3 frames that *had* pixel signals.
- A track-id change, face count ≠ 1, or `reset()` clears all history. Frames older than 3 s cannot create parallax.
- An inconclusive depth result is MEDIUM for every PAD / blink / flash combination.
- A low PAD alone is MEDIUM, never HIGH. PAD exactly 0.10 is not suspect.
- A missing model (null PAD) degrades to "3D depth decides" and never blocks.

---

## 6. Tests

### 6.1 Coverage
| test class | cases | what it proves |
|---|---|---|
| `RiskFusionTest` (rewritten) | 21 | locked policy values; HIGH only on conclusive attacks; PAD escalation-only (0.10 boundary); LOW requires conclusive depth or a passed challenge; inconclusive → MEDIUM |
| `ParallaxDepthTestTest` (updated) | 4 | legacy cases; the true-3D case now uses the exp8 15° pan |
| `ParallaxDepthTestDoc22Test` (new) | 9 | 8° / 0.035 defaults; `depthScoreFor` mapping; max(\|Δyaw\|, \|Δpitch\|); pitch-only nod is live; flat photo turned → flat; inconclusive → MEDIUM for all PAD/blink combinations; short landmark list → inconclusive |
| `LivenessOrchestratorTest` (new) | 17 | single-source history; 2-of-3 window and sliding; PAD median-3; `patSignals.padScore` default; NaN ignored; track change / stale window / reset; guidance + auto-challenge; flat photo → HIGH; real turn + clean PAD → LOW |
| `PadCnnMathTest` (new) | 7 | NCHW plane order B,G,R; raw 0..255; ByteBuffer parity; softmax stability; index-1 live; bad output → null |
| existing `PresentationAttackDetectorTest`, `LivenessEngineTest`, `PadCropTest` | 16 | unchanged behaviour still passes |

**Result: 74 / 74 pass** (kotlinc 2.0.21, JUnit 4.13.2, JVM).

Note from testing: the synthetic canonical face needs about 11° of yaw before its residual clears 0.035 (10° gave 0.032). The exp8 protocol (real 15° pan, p5 = 0.042) is therefore what the tests use, and it is why the UI must ask for a clearly visible turn (§8.3).

### 6.2 Exact command
```bash
cd /home/user/webapp
D=app/src/main/java/com/attract/attendance/domain/face; T=app/src/test/java/com/attract/attendance/domain/face
SRC="$D/FaceMeshResult.kt $D/FaceQualitySignals.kt $D/LivenessEngine.kt $D/LivenessPolicy.kt $D/PresentationAttackDetector.kt $D/PresentationAttackSignals.kt $D/BiometricModelProfile.kt $D/BiometricFeatureFlags.kt $D/PadCrop.kt $D/FaceTracker.kt $D/HeadPose.kt $D/PadCnnMath.kt $D/liveness/*.kt"
TESTS="$T/liveness/RiskFusionTest.kt $T/liveness/ParallaxDepthTestTest.kt $T/liveness/LivenessOrchestratorTest.kt $T/PadCnnMathTest.kt $T/PresentationAttackDetectorTest.kt $T/LivenessEngineTest.kt $T/PadCropTest.kt"
scratch/tk/kotlinc/bin/kotlinc $SRC $TESTS -cp scratch/tk/junit.jar:scratch/tk/hamcrest.jar -d scratch/out_test
java -cp scratch/out_test:scratch/tk/kotlinc/lib/kotlin-stdlib.jar:scratch/tk/junit.jar:scratch/tk/hamcrest.jar org.junit.runner.JUnitCore \
  com.attract.attendance.domain.face.liveness.RiskFusionTest com.attract.attendance.domain.face.liveness.ParallaxDepthTestTest \
  com.attract.attendance.domain.face.liveness.ParallaxDepthTestDoc22Test com.attract.attendance.domain.face.liveness.LivenessOrchestratorTest \
  com.attract.attendance.domain.face.PadCnnMathTest com.attract.attendance.domain.face.PresentationAttackDetectorTest \
  com.attract.attendance.domain.face.LivenessEngineTest com.attract.attendance.domain.face.PadCropTest
```
On a machine with the Android SDK, run instead: `./gradlew :app:testDebugUnitTest :app:assembleDebug`. This is required (0.9) because `MiniFasNetEngine`, `CameraPreview`, `AttendanceScreen` and `PresentationAttackAnalyzer` were not compiled in the sandbox.

---

## 7. Device validation protocol (must pass before merge)

Enable `adb logcat -s AttendanceScreen MiniFasNetEngine FaceMeshEngine CameraPreview`. Every capture prints:
`[LIVENESS] risk=… guidance=… pad=… depth=… inconclusive=… poseDelta=… residual=… pixelVeto=… frames=…`

### 7.1 Genuine students (10 students × 5 attempts per scene)
Scenes: normal classroom, white sun-lit wall behind, window behind (daytime), hard backlight, dim evening.

Pass criteria:
- **0 spoof rejections** (`risk=HIGH`) across all genuine attempts.
- ≥ 95 % marked within 2 head-turn prompts (≤ 4 s) in normal and dim light; ≥ 90 % with the window behind.
- Report the distribution of `poseDelta` / `residual` at LOW to confirm that real turns clear 0.035.

### 7.2 Attacks (20 attempts each, normal and dim light)
Attacks: photo on a laptop (dark background), photo on a phone, A4 colour print, and a video on a laptop of the enrolled student turning their head.

Pass criteria:
- 0 marked present for laptop, phone and print.
- For video replay, record how often `pad < 0.10` forced a challenge, and how often the challenge was passed.

### 7.3 Performance
- Median analyser frame time with YOLO + FaceMesh + MiniFASNet + pixel PAD on a mid-range phone must stay ≤ 150 ms (the analyser throttle).
- Median time from face-in-frame to marked ≤ 3 s including the head turn.

---

## 8. Open items / known limits (honest)
1. **Flash liveness is not wired.** `FlashLivenessTest` and `setFlashScore()` exist but nothing calls them. Rule 4 is therefore dormant, and very dark rooms rely on the screen flash only as illumination. Next WP: run the photometric test during `ScreenFlashOverlay` colour changes and feed `setFlashScore`.
2. **Per-frame cost.** MiniFASNet (80×80) plus the pixel analyser now run on every analysed frame, in addition to FaceMesh. If §7.3 fails, run MiniFASNet every 2nd frame (the median-3 window still fills within about 1 s). Do not move the pixel analyser back to capture-only: the 2-of-3 veto needs per-frame history.
3. **Head-turn magnitude.** exp8 shows a real 15° pan clears 0.035 (p5 0.042); a 10° turn may not. The guidance text asks for "a little to the left, then to the right". If §7.1 shows prompts repeating, add a live yaw progress indicator (the target is a ≥ 12–15° swing) rather than lowering the residual threshold, which would let planar attacks through (planar p95 0.031).
4. **Video replay of a real head turn** produces live depth. It is caught only when the PAD median is < 0.10 (84.4 % of laptop replays in exp10) and the random challenge then fails. A replay that passes PAD and matches the challenge is out of scope for RGB-only liveness, as with doc 20 §0.2.
5. **Enrollment** still uses single-frame `LivenessEngine.check` (unchanged). Moving enrollment onto the orchestrator is a separate decision.
