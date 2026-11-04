# Doc 23 — "Turn your head slightly" loops forever: root cause and fix plan

| Field | Value |
|---|---|
| Branch analysed | `genspark_ai_developer_backlight_spoof` @ `3068d31` (Doc 22 as-built, policy `liveness_v5_doc22`) |
| Symptom | Live attendance always shows **"Turn your head slightly — Slowly look a little to the left, then to the right"**. It never moves on, whatever the student does, in any lighting. |
| Verdict | **This is a wiring and decision-logic failure, not a model-accuracy problem.** The math works on its own (exp8 AUC 0.998). The pipeline around it can never collect the evidence that the math needs. Retraining or swapping the CNN will **not** fix this. |
| Status | Plan only. No production code changed in this doc. |

---

## 1. Where the message comes from (exact path)

```
CameraPreview analyzer (every >=150 ms)
  -> YOLO 640 -> FaceTracker(trackId) -> FaceAligner -> MediaPipe FaceLandmarker (IMAGE mode, full frame)
  -> MiniFASNet (2.7x crop) -> PresentationAttackAnalyzer (1.0x crop) -> FrameBundle
AttendanceScreen.onFrameBundleAnalyzed (L~1718)
  -> livenessOrchestrator.onFrame(signals, patSignals, mesh, trackId)      // history
  -> processAutoCaptureFrame(bundle) -> captureClick()                     // when quality OK & trackFrameCount in 2..4
captureClick (L~1079)
  -> livenessOrchestrator.evaluate(signals)
       ParallaxDepthTest.evaluate(window<=3 s)  -> depthInconclusive?
       RiskFusion.decide -> Rule 9 "everything else" => MEDIUM
       guidanceFor(): !depthLive => TURN_HEAD
  -> Rejected(ACTIVE_CHECK_REQUIRED) and currentChallenge == null
  -> statusMessage = LIVENESS_TURN_HEAD_TITLE / SUBTITLE ; return   (no capture; loops on next frame)
```

**You only get LOW (accept) through `depthLive`:** at least 2 mesh frames, of the same trackId, inside the last 3 s, whose MediaPipe pose differs by **>= 8°** and whose 4-anchor homography residual is **>= 0.035 IOD**. The other way to LOW is a passed challenge, and that needs `depthLive` first. Any failure along this chain gives the same TURN_HEAD message.

---

## 2. Root causes (ranked by likelihood × impact)

### RC-1 (P0): MediaPipe mesh is `null` → depth can never be conclusive (fails silently)
* `FaceMeshEngine.init()` and `detect()` catch **every Throwable**. On failure they just log a warning and return `null`/`false`.
* With `mesh == null`, `onFrame` appends nothing to `meshHistory`. `frames.size < 2` gives `depthInconclusive`, which gives TURN_HEAD **forever**. This matches "always, in all situations, never moves forward".
* Most likely trigger: **release / R8 build.** `isMinifyEnabled = true`, and `proguard-rules.pro` keeps `com.google.mediapipe.tasks.vision.**` and `framework.**`. It does **not** keep `com.google.protobuf.**` (MediaPipe's graph config uses protobuf-lite reflection), and it does not keep `com.google.mediapipe.proto.**` / `calculator.**`. The repo also auto-copies the release APK (`copyReleaseApkToWorkspace`), so the build being tested is very likely the minified one.
* Secondary triggers: `face_landmarker.task` not found or OOM at init. `detect()` throwing on non-ARGB_8888 bitmaps. In very dark or backlit frames, MediaPipe's own face detector finds no face on the **full** 720×1280 frame even though YOLO did.
* There is **no fallback**. When the mesh is unavailable, the liveness gate becomes impossible to pass instead of degrading.

### RC-2 (P0): Evidence history is wiped all the time (track churn and resets)
`onFrame()` calls `reset()` whenever `trackId` changes. `AttendanceScreen` also resets on **any** frame with `faceCount != 1`.
* `FaceTracker` starts a **new track** if the gap between analysed frames is **> 400 ms** or IoU < 0.30. Each analysed frame runs YOLO-640 + a full-frame MediaPipe + MiniFASNet + bitmap rotation/crops/fused crop on **one** thread. On mid-range phones that is 300–700 ms per frame, so the 400 ms gap is regularly exceeded. Every such frame gets a new trackId, the history is cleared, and `meshHistory.size` stays at **1**.
* A single YOLO miss (backlight, motion blur **during the very head turn we asked for**) or a single false second detection gives `faceCount != 1`, which gives a full reset.
* A HIGH verdict also does `reset()` plus a 1.5 s cooldown.
* Result: the two frames 8° apart almost never share a history.

### RC-3 (P0): Liveness is not latched; it is recomputed from a 3 s sliding window
* Even if a student does the turn and `depthLive` briefly becomes true, `evaluate()` runs only when `captureClick()` fires. That needs quality-accepted frames **and** `trackFrameCount in 2..4`, and the `TemporalCropBuffer` clears on IoU < 0.5 or a gap > 600 ms, which happens exactly during a head turn.
* By the time the student is still again and a frame passes quality, the turn frames are **older than 3 s** (`meshWindowMs`) and get pruned. The verdict is MEDIUM again and the student is asked to turn **again**: an endless loop.
* The adaptive multi-frame flow (LLD-16) calls `captureClick()` again for supporting frames, and `submitRecognition()` re-evaluates too. Every later call needs a fresh turn inside 3 s.
* Proof of liveness is an **event**: "this track demonstrated 3D at time t". It must be latched for the life of the track, not re-derived from a window that slides past it.

### RC-4 (P1): The 8° threshold is in MediaPipe-yaw units, which under-read real rotation
* exp8 (`results/exp8_parallax_3d.json`): a real **15°** pan gives measured `dyaw` p5 **6.9°**, p50 **11.6°**. MediaPipe's transformation-matrix yaw under-reads by about 25–50 %.
* "Look a little to the left" from a seated student is typically a 8–15° physical turn, which measures as **5–10°**, so it is below or at the gate. Half of the honest attempts fail the pose-delta gate even when the history survives.
* The 8° was chosen against **planar** yaw (planar max 7.6°). But planar attacks barely move MediaPipe yaw at all (planar_yaw25 → measured p50 4.9°). The **residual**, not the yaw gate, is what separates the classes (planar residual p95 ≤ 0.031 at 25° of sheet rotation; static jitter p95 0.0074).

### RC-5 (P1): Math issues in `ParallaxDepthTest`
1. **Anisotropic coordinates.** Residuals are computed in MediaPipe **normalised** x,y ([0,1] of a 720×1280 portrait frame), then divided by an IOD that is also normalised. A homography fit is invariant to per-axis scaling. The **Euclidean residual is not**: a vertical error of 1 px weighs 720/1280 = 0.56× a horizontal one. exp8 computed everything in **pixels**, so the device and the experiment do not measure the same quantity. Pitch (nod) parallax is under-measured by about 44 %, and the 0.035 threshold does not transfer. Fix: multiply x by `imageWidth` and y by `imageHeight` before any fit. `FaceMeshResult` must carry the image size.
2. **Exact 4-point homography on 10/152/234/454.** 234/454 are silhouette (cheek-contour) points. During yaw they slide along the occluding contour and are the noisiest landmarks MediaPipe outputs. An exact 4-point solve has zero redundancy, so their noise goes straight into H. exp8 shows `lsq` (least squares over all 33 stable points) has tighter real/planar separation (pan15 lsq p5 0.037 vs planar p95 0.026). Use a normalised-DLT least-squares fit over the stable set, with one Huber/RANSAC pass.
3. **Only the single max-yaw pair is tested.** One noisy pair decides everything. Use the median residual over the **top-k pose-separated pairs** (k = 3).
4. `poseDelta = max(|Δyaw|, |Δpitch|)` ignores combined motion. Use `sqrt(Δyaw² + Δpitch²)`.

### RC-6 (P1): The challenge path cannot rescue the user
* A challenge starts only when `depthLive` is **already true** and PAD < 0.10. Otherwise the UI never leaves TURN_HEAD.
* `TURN_SLIGHT_LEFT/RIGHT` needs **10° measured** (≈ 13–20° real) within 4 s, and the timer starts **before** the instruction has rendered. The yaw-sign convention (`MESH_YAW_SIGN = -1`, non-mirrored front-camera bitmap) has never been checked on a device. If it is inverted, a student who obeys "turn left" never passes. A timeout gives `challengePassed=false`, which gives **HIGH**, then reset, then back to TURN_HEAD.
* One failed challenge is treated as a spoof (HIGH) with no retry.

### RC-7 (P2): No escape hatch, no telemetry surfaced
* There is no maximum time in MEDIUM. The student can be stuck indefinitely; there is no escalation to the explicit challenge UI or to teacher assist (the teacher-PIN flow already exists).
* The `[LIVENESS]` log line exists, but nothing tells the user or the tester **which** gate is failing (mesh missing / frames=1 / poseDelta / residual).

### What is NOT the cause
* **Not lack of training / not the PAD CNN.** MiniFASNet only escalates. It cannot produce TURN_HEAD (a low PAD with depthLive produces a *challenge*, a different message).
* **Not the homography solver itself.** `Homography.solve4Points` is a correct DLT with partial pivoting.
* **Not the backlight/glare pixel gates.** Those give HIGH with spoof titles, not TURN_HEAD.

---

## 3. 60-second device triage (confirm before coding)
```
adb logcat -c && adb logcat | grep -E "FaceMeshEngine|\[LIVENESS\]"
```
| What you see | Root cause |
|---|---|
| `MediaPipe FaceLandmarker not available` or `FaceLandmarker detect failed` | **RC-1** (check release vs debug: if debug works and release does not, it is R8) |
| `[LIVENESS] ... frames=0` or `frames=1` repeatedly | **RC-1 / RC-2** (mesh null or history reset each frame) |
| `frames>=2`, `inconclusive=true`, `poseDelta` < 8 while you clearly turned | **RC-4** (yaw under-read) / RC-3 (turn frames pruned) |
| `inconclusive=false`, `residual` < 0.035 for a real face | **RC-5** (anisotropic/4-point math) |
| depth live, then `risk=HIGH` after a challenge | **RC-6** |

---

## 4. Implementation plan (work packages, in order)

> Rule for every WP: pure-Kotlin logic in `domain/`, unit-tested on the JVM, plus one telemetry field. Commit and push after each WP.

### WP-0 — Instrument (½ day, do first)
* Add a `LivenessDebug` data class to `LivenessVerdict`: `meshAvailable`, `meshFramesInWindow`, `trackResetsLast10s`, `lastResetReason` (TRACK_CHANGE / FACE_COUNT / HIGH / WINDOW), `bestPoseDelta`, `bestResidual`, `latchedAtMs`, `avgFrameIntervalMs`.
* In debug / `collect` builds, show a one-line overlay under the camera box (e.g. `mesh✓ f=7 Δ=6.2° r=0.041 resets=0`).
* Log `FaceMeshEngine.lastError` once at session start with `AppLog.w`.

### WP-1 — Make MediaPipe reliable, and never block when it is not (RC-1) — P0
1. `proguard-rules.pro`: add
   ```
   -keep class com.google.protobuf.** { *; }
   -keep class com.google.mediapipe.proto.** { *; }
   -keep class com.google.mediapipe.calculator.proto.** { *; }
   -keep class com.google.mediapipe.formats.proto.** { *; }
   -keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
   ```
   Verify with `assembleRelease` on a device: the log must show `FaceLandmarker successfully initialized` and `[LIVENESS] frames>=2`.
2. Run MediaPipe on a **face ROI** (YOLO box × 1.8, square, ≤ 384 px), not the full frame. Then map landmarks back to full-frame **pixels**. This is about 3–5× faster and finds faces in backlit frames where the full-frame detector misses.
3. Switch to `RunningMode.VIDEO` + `detectForVideo(bitmap, timestampMs)`. Temporal tracking gives lower landmark jitter, which lowers the planar residual noise floor and speeds things up.
4. **Fallback parallax from YOLO's 5 landmarks** (`domain/face/liveness/FivePointParallax.kt`). Fit an affine/homography to eyes + mouth corners (4 points, nearly coplanar on a real face). The residual of the **nose tip** (which sits about 2–3 cm in front of that plane) normalised by IOD is the classic off-plane parallax. A flat photo gives about 0. This is used only when `mesh == null`. Same pose-delta gate, using the YOLO-ratio yaw.
5. `FaceMeshEngine.isAvailable == false` after init → `AppLog.e` plus one Crashlytics non-fatal (PII-free).

### WP-2 — Stop wiping evidence (RC-2) — P0
1. `FaceTracker`: make the gap timeout adaptive, `max(1000 ms, 3 × median frame interval)`. Lower IoU to 0.20, plus a centre-distance fallback (centre moved < 0.5 × box width counts as the same track). A head turn moves the box but not the person.
2. Orchestrator reset policy (`LivenessOrchestrator.onFrame`):
   * `faceCount == 0` for **< 800 ms** keeps the history (a "dropout"). Reset only after ≥ 800 ms with no face, or ≥ 3 consecutive frames with ≥ 2 faces.
   * A trackId change resets only if the new box does **not** overlap the last known box (IoU < 0.1). Optional identity guard: cosine between the latest embeddings < continuity threshold → reset.
3. Move the `faceCount` reset decision **out of** `AttendanceScreen` into the orchestrator: `onFrame(..., faceCount)`. A single source again (Doc 22 principle).
4. Raise `MAX_MESH_FRAMES` to 45 and keep `meshWindowMs` at 3000 **for collection only** (see WP-3).

### WP-3 — Latch liveness per track (RC-3) — P0
* New state in `LivenessOrchestrator`: `latch: LivenessLatch? = (risk = LOW, provenAtMs, trackId, method = PARALLAX_MESH | PARALLAX_5PT | CHALLENGE | PASSIVE_STRONG)`.
* `onFrame()` (not `evaluate()`) runs the depth test **incrementally** on every new mesh frame, against the frames in the window. The first time it is conclusive-live (and PAD is not suspect, or a challenge has passed), it **sets the latch**.
* `evaluate()` returns LOW while `latch != null && latch.trackId == currentTrack && now - provenAtMs <= latchTtlMs (default 20 s)`, unless a **HIGH** signal appears afterwards (pixel veto confirmed, planar depth, injection). That revokes the latch.
* `reset()` clears the latch. `submitRecognition()` reads the latch and does not demand a fresh turn.
* The evaluate-during-capture problem goes away: the turn can happen while quality is rejecting (blur during the turn), and the capture happens a second later on a still, sharp frame.

### WP-4 — Fix the parallax math (RC-4, RC-5) — P1
`ParallaxDepthTest` v2 (keep v1 for the A/B tests):
1. Inputs in **pixels**: `FaceMeshResult` gets `imageWidth`, `imageHeight` (or `FaceMeshEngine` returns pixel coordinates).
2. **LSQ homography**: normalised DLT (Hartley normalisation: centroid to 0, mean distance √2) over the 33 `STABLE_INDICES`, solved by 9×9 normal equations / Jacobi SVD (pure Kotlin, about 60 lines), then one IRLS/Huber reweighting pass. Drop 234/454/127/356/93/323 (contour points) from the **fit** set and keep them in the **residual** set (they carry the most parallax).
3. Pose delta = `hypot(Δyaw, Δpitch)`. Evaluate the **top-3 pose-separated pairs** and take the median residual.
4. Thresholds (re-derived from exp8; replay exp8 with the v2 port to confirm before merging):
   * pose gate: **`minPoseDeltaDeg = 5°` measured** (≈ 7–9° real). Planar pairs rarely exceed 5° measured, and when they do the residual still rejects them.
   * live: `lsqResidual >= 0.030` IOD. planar (HIGH): `poseDelta >= 6° && lsqResidual < 0.012`. In between: inconclusive (keep collecting).
   * `depthScoreFor` stays a monotonic map, re-anchored so 0.030 → 0.50.
5. New tool `tools/biometric_eval/exp11_parallax_v2.py`: an exact Python port of v2, run on the exp8 groups. It must reach ≥ 97 % real-pass at a 5° measured turn and 0 % planar-pass on all planar groups, including static jitter. Commit the JSON result. (Needs `.datasets/pointing04` + mediapipe; run it where they already exist. Do not install them in the CI sandbox.)

### WP-5 — Guided, closed-loop head-turn UX (RC-4/RC-6/RC-7) — P1
* Replace the static text with a **live progress indicator**: an arc or bar that fills as `bestPoseDelta / minPoseDelta` grows, with an arrow toward the side not yet visited. The student sees that the turn is being registered and how far to go.
* Shorter copy: "Turn your head a little ← →". Once the latch is set: a ✓ tick and "Hold still…", then capture.
* Show the TURN_HEAD prompt only after **1.0 s** of collected mesh frames without a latch. Many students move naturally enough to latch passively (see WP-6).

### WP-6 — Passive-first decision (makes it "Face-ID-like" without a forced turn) — P1
Add a **PASSIVE_STRONG** latch for students who look straight and stay still. It requires **all** of:
* MiniFASNet median-of-5 ≥ `padLiveAccept` (0.80),
* a natural blink seen (`BlinkDetector`) within the window,
* micro-parallax not planar: over ≥ 1.5 s, at least 2° of natural sway with `lsqResidual` above 0.6 × the live threshold, or blink-induced non-rigid motion,
* no pixel veto in the last 5 frames.
A photo cannot blink. A laptop replay is caught by PAD (84 % < 0.10 in exp10) and must also beat the parallax floor. Anything weaker falls through to the WP-5 guided turn.

### WP-7 — Challenge path fixes (RC-6) — P1
* Calibrate and test the yaw sign on a device. Add a unit test that pins `HeadPose` signs to the mirrored preview the user sees: the student's "left" must equal the UI arrow.
* Turn challenge threshold **7° measured**. The timer starts when the instruction first renders (`ChallengeController.armAt(nowMs)`). Timeout 6 s.
* Failure policy: 1st failure → MEDIUM + a new random challenge; 2nd failure → HIGH. Never HIGH on the very first timeout.
* Also allow a challenge when depth is **inconclusive for > 4 s** (not only when it is live). BLINK/SMILE challenges plus PAD ≥ 0.5 can then latch LOW (Rule 7). This gives users without a working mesh a way forward.

### WP-8 — Escape hatch (RC-7) — P1
* After **12 s** in MEDIUM on one track: show "Having trouble? Ask your teacher". This routes into the existing teacher-PIN assisted attendance flow (`TeacherAuthorizationGrant`), and the record is flagged `livenessMethod = TEACHER_ASSIST` in `AttemptTrace`.
* No infinite loops: every MEDIUM state has a timeout that either escalates or hands off.

### WP-9 — Performance budget (supports RC-2) — P2
* Target ≤ 120 ms per analysed frame on a mid-range device (Snapdragon 6-series):
  * MediaPipe on the ROI in VIDEO mode (WP-1).
  * MiniFASNet every 2nd frame (its median only needs 3–5 scores).
  * YOLO input 320 while a track is stable, 640 on (re)acquire.
  * Reuse bitmaps (no `createBitmap` per frame for wide/fused crops), and do the rotation via the `Matrix` passed into the crop instead of rotating the full frame.
* Log `avgFrameIntervalMs` (WP-0). Fail the device test if it is > 250 ms.

---

## 5. Tests to add (JVM, no device)
| Test | Asserts |
|---|---|
| `LivenessOrchestratorTest.trackChurn_doesNotWipeEvidence` | frames 600 ms apart with a ±1 trackId flip → still latches |
| `..._faceDropout_under800ms_keepsHistory` | one `faceCount=0` frame mid-turn → latch still reached |
| `..._latch_survivesWindowExpiry` | turn at t=0, evaluate at t=5 s → LOW |
| `..._latch_revokedByPixelVeto` | latch, then 2/3 veto → HIGH |
| `..._meshNull_fallsBackTo5Point` | mesh=null, YOLO 5-pt with nose parallax → LOW; flat 5-pt → not LOW |
| `ParallaxDepthTestV2Test.pixelSpace_isotropic` | same 3D motion in 720×1280 vs 1280×720 gives equal residual ±5 % |
| `..._lsq_robustToContourNoise` | ±2 px noise on 234/454 → planar stays < 0.012 |
| `..._synthetic3DHead_5deg_live / plane_25deg_flat` | projected synthetic rigid 3D mesh vs plane, pinhole camera |
| `ChallengeControllerTest.timerStartsAtArm`, `firstTimeout_isNotHigh` | |
| `RiskFusionTest.passiveStrong_requiresBlinkAndPad` | each missing factor → MEDIUM |

## 6. Device acceptance (must pass before merge)
| Scenario (20 trials each, 3 phones) | Target |
|---|---|
| Real student, normal light, looks straight, no instruction | ≥ 80 % LOW within 2.5 s (passive) |
| Real student, prompted turn | ≥ 98 % LOW within 4 s; **0 infinite loops** (WP-8 reached ≤ 1 %) |
| Real student, window backlight | ≥ 95 % LOW within 5 s |
| Printed photo held still / tilted / bent | 0 % LOW |
| Laptop / phone photo, held still and rotated ±25° | 0 % LOW |
| Laptop replay video of the enrolled student turning | ≤ 1 % LOW (PAD + challenge) |
| Release (R8) APK | identical results to debug |

## 7. Suggested order and effort
| Order | WP | Effort | Unblocks |
|---|---|---|---|
| 1 | WP-0 + WP-1.1 (R8 keep rules) | 0.5 d | probably fixes the "always" symptom by itself in release |
| 2 | WP-2 + WP-3 | 1.5 d | the loop disappears for real users |
| 3 | WP-4 + exp11 | 1.5 d | correct, device-consistent math |
| 4 | WP-5, WP-7, WP-8 | 1.5 d | UX, no dead ends |
| 5 | WP-6, WP-1.2–1.4, WP-9 | 2 d | passive pass, speed, mesh-free fallback |

## 8. Policy changes summary (`LivenessPolicy` → `liveness_v6_doc23`)
| Field | v5 | v6 |
|---|---|---|
| `parallaxMinYawDeltaDeg` | 8 (max of axes) | **5 (hypot), measured** |
| `parallaxLiveResidual` | 0.035 (4-pt, normalised coordinates) | **0.030 (LSQ, pixels)** |
| `parallaxFlatResidual` (new) | 0.4 × live | **0.012 with poseDelta ≥ 6°** |
| `latchTtlMs` (new) | — | 20 000 |
| `faceDropoutGraceMs` (new) | 0 | 800 |
| `trackerGapMs` | 400 | max(1000, 3 × frame interval) |
| `maxChallengeMs` | 4000 | 6000, armed at render |
| `challengeTurnDeg` | 10 | 7 |
| `maxMediumMsBeforeAssist` (new) | ∞ | 12 000 |
