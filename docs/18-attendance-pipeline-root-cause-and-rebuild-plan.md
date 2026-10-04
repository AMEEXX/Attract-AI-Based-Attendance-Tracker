# 18 — Attendance Pipeline: Measured Root Causes and Rebuild Plan

**Date:** 2026-10-04
**Baseline audited:** `origin/main` @ `e458c9f` (includes the "WP01–WP13 repair" commit `cf6c6ab`)
**Status:** Proposed. Supersedes doc 17 where they conflict (doc 17 was written before `cf6c6ab` and before any measurements).
**Evidence:** `tools/biometric_eval/` (scripts, raw JSON results). Every number in this document comes from running the **actual shipped TFLite models** with a line-by-line Python port of the Kotlin pipeline (`tools/biometric_eval/app_pipeline.py`) on public face datasets.

---

## 0. TL;DR

Your symptoms are reproducible offline, and their causes are measurable:

| Your symptom | Root cause (measured) | Fix |
|---|---|---|
| "When I turn left/right it never accepts" | The app's "yaw" is not in degrees. For a **real 45° turn** it reports **≈ 8–9**, and its maximum is ≈ 9 even at 90°. The LEFT/RIGHT gate needs **20–45**. **0 of 1,773 frames ever passed** LEFT or RIGHT (EXP-1). | Replace the yaw formula with a calibrated nose-offset estimator (MAE 9° vs 11°, correct sign 100%), and gate turns **relative to your own straight frame** (§3.1). |
| "Enrolled many times, still shows the enroll list" | Enrollment **can never be committed**: it needs all 3 poses, and pose 2 can never pass. Nothing is saved, so every later click sees an empty or partial gallery → NOT FOUND → enroll list. The simulated class: **0 / 260 enrollments succeeded, 2,639 wasted clicks**. | Fixing pose fixes this. Then add a hard invariant: the UI cannot say "enrolled" unless the DB row exists (WP-E, §5). |
| "A random guy also gets *enroll*, or gets matched as someone" | Thresholds are uncalibrated. `accept=0.25` passes **6.3% of impostor pairs**; in a 60-student gallery a stranger is matched to *someone* **36% of the time** (EXP-2, calibration sweep). | Calibrated `accept=0.50`, `margin=0.08`, plus a two-frame confirmation below 0.60. Strangers matched: **36% → 0.8%**. Wrong-identity marks: **0** (§3.3). |
| "Teacher gate is in the way" | The D-001 PIN dialog is in the student path. | Remove it, per your decision (§2). |
| "The model fine-tuning isn't working" | **There is no fine-tuning, and none is needed.** ArcFace is a pretrained embedder. Enrollment stores vectors; it does not train anything. The model itself is healthy (EER 1.5–2.1% on LFW). The pipeline around it is broken. | Do not retrain. Fix the pose estimator, thresholds and flow (§1.4). |

**Result after the proposed fixes,** on the same mock class (5 random 60-student classes, 3 days, 600 stranger probes):

| Metric | CURRENT (`main`) | PROPOSED |
|---|---|---|
| Day-1 enrollments completed | **0 / 260** | **245 / 260** (94%) |
| Enrollment clicks spent | 2,639 | 1,140 |
| Day-2/3 enrolled students correctly marked PRESENT | 0 (nobody is enrolled) | **523 / 530** (98.7%) |
| Wrong student marked present | 0 (only because nobody is enrolled) | **0** |
| Day-1 absentees routed to enrollment on Day 2 | 300 / 300 (everyone, because nobody enrolled) | 55 → **40** late-enrolled OK |
| Strangers (not on roster) marked present | 0 / 600 (gallery empty) | **5 / 600** (0.8%) |

These numbers come from mock data (public datasets, not your phone camera). They prove the logic and the thresholds. The on-phone calibration in §6 is still required before release.

---

## 1. What was measured and how

### 1.1 Mock data (downloaded, never committed)

| Dataset | Source (public mirror) | Content | Used for |
|---|---|---|---|
| **Pointing'04 head-pose** | HF `StevenLe456/head-pose` | 15 people × 2 sessions (different days) × 93 poses, labelled pan/tilt in degrees (−90…+90) | Left/right turn testing, enrollment, check-ins on a different day |
| **LFW** | HF `bitmind/lfw` (Kaggle `jessicali9530/lfw-dataset` is the same data) | 13,233 photos of 5,749 people | Threshold calibration, in-the-wild class members, 400 strangers |

`tools/biometric_eval/extract_datasets.py` downloads the data into `.datasets/`, which is git-ignored. Dataset images are **never** committed and **never** go into the APK.

### 1.2 Faithful port

`app_pipeline.py` reproduces the Kotlin pipeline exactly: `YoloFaceDetector` (640² stretch resize, RGB/255 NCHW, conf ≥ 0.5, **all five** landmark confidences ≥ 0.5, NMS 0.45, ratio yaw/pitch), `FaceAligner` (5-point Umeyama, MSE > 450 → eye fallback), `EmbeddingEngine` (RGB, (x−127.5)/128, L2), `FaceQualityEngine.calibrationDefaults()`, `EnrollmentBatchValidator`, `DuplicateCheckService` and `IdentityScorer`. It runs the shipped `.tflite` files with LiteRT.

### 1.3 Experiments

| ID | Script | Question |
|---|---|---|
| EXP-1 | `exp1_pose_sweep.py` | What does the app's yaw report for real turns? Which pose slots can ever pass? |
| EXP-1b | `exp1b_quality_gates.py` | Pass-rate of the full quality gate on frontal frames; stretch vs letterbox |
| EXP-2/2b | `exp2_embedding_sanity.py`, `exp2b_center_face.py` | Is ArcFace healthy? RGB vs BGR? What do the shipped thresholds mean? |
| EXP-3 | `exp3_pose_estimators.py` | Which estimator from the same 5 landmarks tracks true yaw? (train on subjects 1–8, test on 9–15) |
| EXP-4 | `exp4_turn_templates.py` | Do LEFT/RIGHT templates actually help recognition? |
| CAL | `calibrate.py` | Thresholds from 200 LFW identities **disjoint** from the test class |
| SIM | `simulate_classroom.py`, `sweep_policy.py` | Full Day-1/2/3 classroom with absentees and strangers, CURRENT vs PROPOSED |

Reproduce everything:

```bash
pip install ai-edge-litert pyarrow opencv-python pillow numpy
python tools/biometric_eval/extract_datasets.py        # downloads ~1.5 GB into .datasets/
python tools/biometric_eval/build_feature_cache.py     # ~6 min CPU
python tools/biometric_eval/exp1_pose_sweep.py
python tools/biometric_eval/exp3_pose_estimators.py
python tools/biometric_eval/calibrate.py
python tools/biometric_eval/simulate_classroom.py
```

### 1.4 "Fine-tuning" — what the model actually does

Face ID, Android face unlock and this app all work the same basic way. A **pretrained** network turns a face into a vector (512 numbers here). *Enrollment* stores a few such vectors. *Verification* compares a new vector to the stored ones with cosine similarity. No per-student training happens anywhere, and it should not. Retraining on 60 classmates would overfit and would need a GPU and labelled data the app doesn't have.

EXP-2b shows the shipped recognizer is fine when given correct input:

| Input order | EER | TAR @ FAR 0.1% |
|---|---|---|
| RGB (shipped) | 2.11% | 92.0% |
| **BGR** | **1.55%** | **96.7%** |

The weights work. The pipeline around them is the problem. BGR scores consistently better on every metric, which strongly suggests the export expects BGR (common for InsightFace-derived models), so the plan switches to BGR (§3.2).

---

## 2. Product decision recorded: no teacher gate at enrollment

You decided that first-time enrollment is **self-service**. The student picks their own name, and the photo is bound to that name for the semester. This **replaces D-001** (teacher PIN at enrollment). Record it as **D-007** in `00-architecture-decisions.md` (done in this PR).

What stays:

- **Duplicate-face protection stays.** It costs the honest student nothing and stops the same face being enrolled under two names. Calibrated at 0.50, it catches 100% of same-person re-enrollments and falsely blocks a new student in only ~2.4% of 60-person classes (CAL).
- **Names are single-use.** Once a name is enrolled it disappears from the list, so nobody can "claim" an enrolled name.
- **The teacher PIN stays for exiting the session only.** The "Ask teacher" / assisted-check-in and profile-repair paths move to the teacher's class screen, outside the student flow.

The accepted risk: a student could deliberately pick a classmate's not-yet-enrolled name. You stated this will not happen. It is visible later, because the real owner can't enroll and their name is already taken. The teacher can fix it with "Reset face profile" on the student page (WP-A adds the reset; per-student reset belongs on StudentDetailScreen).

---

## 3. Root causes in detail

### 3.1 RC-1 (P0): the LEFT/RIGHT pose gate can never pass — *this is why enrollment never finishes*

`YoloFaceDetector.estimateYawFromLandmarks` computes `(0.5 − dL/(dL+dR)) × 60`. This is a bounded ratio, not an angle. As the head turns, the far eye moves *behind* the nose and the ratio saturates, then falls back. EXP-1 (1,773 frames, 15 people, |tilt| ≤ 15°):

| True pan | −90 | −60 | −45 | −30 | −15 | 0 | +15 | +30 | +45 | +60 | +90 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| App "yaw" (mean) | −4.0 | −7.7 | **−8.9** | −7.8 | −4.3 | 0.2 | 4.9 | 7.5 | **8.0** | 6.9 | 4.6 |
| Passes STRAIGHT (|y| ≤ 15) | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% | 100% |
| Passes LEFT (−45…−20) | **0** | **0** | **0** | **0** | **0** | 0 | 0 | 0 | 0 | 0 | 0 |
| Passes RIGHT (20…45) | 0 | 0 | 0 | 0 | 0 | 0 | **0** | **0** | **0** | **0** | **0** |

Three consequences:

1. **LEFT and RIGHT are impossible.** The largest value the formula ever produces is ≈ 9, but the gate starts at 20. This matches what you see exactly.
2. **STRAIGHT is meaningless.** A 90° profile counts as "straight", so the straight gate accepts anything.
3. `EnrollmentBatchValidator` adds a second impossible requirement (LEFT ≤ −15, RIGHT ≥ 15, ≥ 15 separation). Even with relaxed UI gates, the commit would still be rejected.

**Fix: nose-offset yaw, gated relative to your own straight frame.**

```
eyeMid  = (leftEye + rightEye) / 2
ie      = |rightEye − leftEye|                  // inter-eye distance
yawDeg  = K_YAW × (nose.x − eyeMid.x) / ie      // K_YAW = −60.8 (fitted on subjects 1–8)
```

EXP-3 held-out results (subjects 9–15):

| Estimator | Corr. with truth | MAE | Sign correct when turned ≥ 30° | Mean output at true −30 / −15 / 0 / +15 / +30 |
|---|---|---|---|---|
| App ratio (current) | 0.93 (saturates) | 11.4° | 100% | −39 / −22 / 0 / 25 / 39 (after rescaling; raw ≈ ±8) |
| **Nose offset (proposed)** | **0.96** | **9.0°** | **100%** | −19 / −9.5 / −0.3 / 11 / 21 |
| solvePnP 5-pt generic head | 0.98 | 7.2° | 100% | −24 / −12 / 2 / 20 / 32 |

Nose offset is monotonic, has no saturation inside ±60°, costs 5 lines of Kotlin and needs no new dependency. solvePnP is a little more accurate but needs a camera-intrinsics guess. Keep it as a later upgrade.

**Gate rules (enrollment):**

| Slot | Rule | Why |
|---|---|---|
| STRAIGHT | `|yaw| ≤ 12°` and `|pitch| ≤ 15°` | Real frontal frames score −0.3 ± 3.3° |
| LEFT | `yaw − yawStraight ∈ [−40°, −10°]` | **Relative** to your own straight frame, so a tilted phone or asymmetric face doesn't matter. 10° is an easy, natural turn (true −15° → −9.5 ± 3.8). |
| RIGHT | `yaw − yawStraight ∈ [+10°, +40°]` | Mirror image. |
| Any slot | Reject if any landmark conf < 0.3 or if the eye fallback was used | Far side becomes unreliable past ~45° |

**Direction labels: use what the student sees.** The preview is mirrored on the front camera. "Turn LEFT" must mean the student's left, which is image-right in the raw frame. Compute yaw on the **unmirrored** analysis frame, then map: `studentLeft ⇔ yawRaw > 0` for the front camera. Add a unit test pinned with a fixture image, because a sign error here would silently recreate this bug.

**Measured effect:** with these gates, every Pointing'04 subject who produced a usable straight frame completed STRAIGHT → LEFT → RIGHT within 1–3 clicks per slot (SIM: 0 pose failures; the only failures were blurry or missing straight frames in the dataset).

### 3.2 RC-2 (P0): the recognizer gets RGB but scores better with BGR

EXP-2b: on the same 600 faces, BGR improves EER from 2.11% to 1.55% and TAR@FAR 0.1% from 92.0% to 96.7%. Impostor 99.9th percentile drops from 0.447 to 0.395.

Fix: in `EmbeddingEngine.extractEmbedding`, write `b, g, r` instead of `r, g, b`. Put the order in `BiometricModelProfile.recognizerColorOrder = "BGR"`. Bump `profileId`, because old RGB templates are not comparable with new BGR queries. This forces a one-time re-enrollment, which is fine since you are wiping the data anyway (WP-A, §5).

### 3.3 RC-3 (P0): thresholds are not calibrated → strangers match, real students land in limbo

Calibration on 200 LFW identities disjoint from the test class (60,000 impostor pairs):

| Quantity | Value |
|---|---|
| Genuine score 1% / 5% / 50% quantile | 0.285 / 0.436 / 0.667 |
| Impostor 50% / 99% / 99.9% / 99.99% | 0.010 / 0.324 / 0.427 / 0.505 |

What the shipped constants actually do:

| Constant | Shipped | Measured effect | Proposed |
|---|---|---|---|
| `acceptThreshold` | 0.25 | 6.3% of impostor pairs pass; in a 60-student gallery a stranger is matched to someone **36%** of the time (margin 0.05) | **0.50** |
| `ambiguousMargin` | 0.05 | Lets near-tied candidates through | **0.08** |
| Two-frame confirm | none | — | If 0.50 ≤ top < **0.60**, take one more frame. It must match the **same** student, otherwise "try again". |
| `DUPLICATE_DETECTION_THRESHOLD` | 0.22 | Flags 9.7% of *different* pairs; a new student in a 60-person class is falsely blocked **~100%** of the time once ~30 are enrolled | **0.50** (catches 100% same-person; 2.4% false-block at 60) |
| `SAME_PERSON_CONTINUITY` | 0.35 | Rejects ~1% of genuine straight-vs-turned pairs; with BGR, the genuine 1st percentile is 0.381 | **0.30** |

Sweep on the classroom simulator (5 seeds, 530 enrolled check-ins, 600 stranger probes):

| accept / margin / confirm | Correct PRESENT | Wrong student | Enrolled but not recognised | Strangers marked |
|---|---|---|---|---|
| 0.25 / 0.05 / – (≈ shipped) | — | — | — | 36% (CAL estimate) |
| 0.43 / 0.05 / – | 523 | **10** | 1 | 39 |
| 0.47 / 0.08 / – | 525 | 5 | 3 | 14 |
| 0.50 / 0.08 / – | 524 | 5 | 6 | 5 |
| **0.50 / 0.08 / 0.60** | **523** | **0** | **7** | **5** |
| 0.53 / 0.08 / – | 520 | 0 | 10 | 1 |

All 5 wrong-student cases at 0.50 without confirmation were the **same pair** (Pointing'04 subjects 9 and 10, similarity 0.37–0.54). Requiring a second agreeing frame below 0.60 removed every one, at a cost of one extra click for borderline faces. This is the closest analogue to Face ID's "attention + consensus" behaviour a single RGB camera can offer.

### 3.4 RC-4 (P0): the student flow has dead ends that look like "not enrolled"

From reading `AttendanceScreen.kt` on `main`:

1. **Teacher PIN in the student path** (`showTeacherConfirmDialog`) — removed by decision (§2).
2. **The grant binds `interactionId = currentInteractionToken`**, but `onAdaptiveNonMatch` increments the token *before* the roster opens. This is safe only by accident. It goes away with the grant.
3. **`firstEnrollAndCheckIn` fails the whole enrollment when the gallery is `NeedsRepair`.** One stale or corrupt row anywhere in the class blocks every new student: "Class gallery requires repair before new enrollments". Fix: duplicate-check against the usable templates, and log or report the bad rows; don't block.
4. **Session id 0.** If `ensureFaceSession` has not finished, `activeSessionId ?: 0L` is passed, and the transaction fails with "Session not found". The capture button must be disabled until the session id resolves.
5. **The roster list comes from `students` (a ViewModel snapshot).** After a commit, the list refreshes only when Room re-emits. Because the screen also keeps its own `presentIds`, an enrolled student can still appear for a moment and show "already enrolled". Fix: derive both lists from one Room `Flow` (WP-E, §5).
6. **The NOT FOUND path needs two full failed attempts** (`recognitionAttemptCount < 1`) before the enroll list opens. With an empty gallery, `handleNoEnrolledStudents` opens it at once (correct). With a partial gallery on Day 1, every new student waits through a retry. Per SDD §13, use **one** attempt while the class has unenrolled students and a clean NOT FOUND (top < 0.35), and two otherwise.
7. **The yaw-distinctness guard** (`isDistinctFromCaptured`, 15°) runs in standalone mode on the broken yaw. Replace it with the relative gate in §3.1.

### 3.5 RC-5 (P1): quality gates tuned for nothing in particular

- `minBlurVariance = 120` rejects **50%** of perfectly sharp frontal webcam frames in Pointing'04 (median 124). This value depends on the camera and resolution. Use **60** for 640-px analysis frames, then calibrate on the phone.
- `maxOffCenterFraction = 0.35` means the face centre must lie in the middle 30% of the frame. On a 280-dp square preview with FILL_CENTER, the analysis frame is wider than what the user sees, so a face that looks centred can fail. Use **0.25** (middle 50%) and draw the oval guide to match.
- YOLO landmark gate: `all five landmark conf ≥ 0.5` drops the whole face. At ±45–60° the far-eye confidence falls (mean min conf 0.86–0.87 here, much lower on phones in dim light). Use **min ≥ 0.3** for detection, and require ≥ 0.5 only for the slot being captured.

### 3.6 RC-6 (P1): liveness is a placeholder

`LivenessEngine` and `PresentationAttackDetector` are brightness/blur/texture heuristics. Eye probabilities are always `null`. These checks don't stop a photo on a phone screen. With the teacher gate removed, the realistic attack is a friend holding up a student's photo for check-in, not for enrollment. The cheapest effective defence is the **guided turn itself**: a printed photo cannot produce a consistent relative yaw change with stable identity. Reuse the enrollment turn logic as an optional *random-direction* challenge at check-in (WP-G, §5). It costs one extra glance and works without a PAD model.

---

## 4. How Face ID and Android face unlock do it, and what we copy

| Aspect | Apple Face ID | Android face unlock | Attract (proposed) |
|---|---|---|---|
| Sensor | IR dot projector + IR camera (3-D depth) | Class 3: IR/depth (Pixel 4) or certified 2-D; most phones are Class 1/2, 2-D RGB | 2-D RGB front camera |
| Enrollment capture | Continuous video while the user "moves head in a circle", **two passes**; frames picked automatically across **a variety of poses** (Apple Platform Security guide) | Continuous video with an on-screen ring that fills as poses are covered; no "tap per pose" | **Continuous auto-capture:** the student holds still, then turns slightly left, then right. The app grabs the best frame for each slot automatically. **No manual CLICK per pose.** |
| Pose requirement | Moderate rotations, not profiles | Moderate | ±10–40° **relative** to the user's own straight frame |
| What's stored | Mathematical representation (embedding), never photos | Embedding in TEE | 3 embeddings, AES-GCM (Keystore), no photos |
| Matching | Neural network in the Secure Enclave, FAR ≈ 1/1,000,000 (depth) | Class 3 needs FAR ≤ 1/50,000, SAR ≤ 7% | Calibrated cosine, accept 0.50 + margin 0.08 + 2-frame confirm |
| Attention / liveness | "Require attention" (eyes open, looking) plus depth | Class 3 needs spoof testing | Guided-turn challenge (optional), blur/exposure checks |
| Adapting over time | "Augments its stored representation" after high-confidence matches | Similar | **Post-MVP only:** append a template only when score ≥ 0.70 *and* the two-frame confirm agrees (SDD §19 keeps this off for MVP) |

What makes Face ID feel effortless is **automatic frame selection during continuous motion**, not a stricter tap-per-pose gate. The current app asks the user to tap CLICK at an exact angle that the code (as RC-1 shows) can never measure. The proposed flow runs the gate on every analysed frame, about 6 per second at 150 ms, and auto-captures the first frame that satisfies the slot.

---

## 5. Implementation plan

Work packages are ordered by dependency. Each WP lists files, exact changes, tests, and a "done when" check. **WP-A … WP-D alone fix all three of your reported symptoms.** WP-E … WP-H harden the result.

### WP-A — Clean slate: remove all existing data (P0, ½ day)

You asked for "remove all the data". There are three kinds:

1. **Phone data (enrollments, templates, attendance).** Add `AttractRepository.resetBiometricData(classId: Long?)`. In one transaction it does `DELETE FROM face_templates [WHERE class]`, sets every student to `NOT_ENROLLED`, `enrolledAt = null`, and deletes ACTIVE face sessions and their records. Wire it to Settings → "Reset all face data" (teacher PIN + type `RESET`). For dev phones, `adb shell pm clear com.attract.attendance` does the same.
2. **Profile bump.** Changing `profileId` → `arcface512_bgr_noseyaw_v5` makes every old template incompatible. Add `MIGRATION_4_5`, which deletes `face_templates` and sets students to `NOT_ENROLLED`. Under D-007 this is intentional: old templates were made with a broken pipeline and must not be reused. Attendance history is **kept**.
3. **Repository clutter.** Delete `attract_large_face_dataset_pack/`, `attract_large_face_dataset_pack.zip`, `attract_real_face_test_data_bootstrap.zip`, `zip_contents_preview/`, the root `student_roster.csv` (duplicate of `sample-data/`), the unused `app/src/main/assets/mobilefacenet.tflite` (5 MB, 192-D, referenced by nothing), and the stale `scripts/check_p2_right.py`. All mock data now comes from `tools/biometric_eval/extract_datasets.py` into git-ignored `.datasets/` (done in this PR).

*Done when:* a fresh install or reset shows every student NOT_ENROLLED, 0 templates, and history intact; the APK is ~5 MB smaller.

### WP-B — Correct pose estimation (P0, 1 day) — *fixes the left/right symptom*

Files: `domain/face/YoloFaceDetector.kt`, `FaceQualityConfig.kt`, `FaceQualityEngine.kt`, `FaceQualitySignals.kt`, `EnrollmentBatch.kt`.

1. Replace `estimateYawFromLandmarks` with:
   ```kotlin
   private const val K_YAW = -60.8f          // calibrated, tools/biometric_eval/exp3
   fun estimateYaw(lm: List<PointF>): Float {
       val mx = (lm[0].x + lm[1].x) / 2f
       val ie = hypot(lm[1].x - lm[0].x, lm[1].y - lm[0].y).coerceAtLeast(1f)
       return K_YAW * (lm[2].x - mx) / ie
   }
   ```
   Pitch: use the same idea vertically. `nose_v = (nose.y − eyeMid.y) / ie`, and pitch = `K_PITCH × (nose_v − 0.55)`. Calibrate `K_PITCH` with `exp3` on tilt labels (add it to the script).
2. Rotate landmarks by `−roll` around eyeMid **before** computing yaw/pitch, so in-plane tilt doesn't leak into yaw.
3. Add `landmarkMinConf` to `FaceQualitySignals`. Lower the detector's landmark filter to 0.3.
4. `FaceQualityEngine`: new signature `evaluate(signals, cfg, slot, anchorYaw: Float?)`. STRAIGHT is `|yaw| ≤ 12`. LEFT/RIGHT use the relative window `[10, 40]` with the student-perspective sign mapping (§3.1). The pose score is distance to the slot target, not to 0.
5. `EnrollmentBatchValidator`: replace the absolute yaw checks with the same relative rule. Diversity becomes `|Δyaw| ≥ 8` **or** embedding cosine < 0.97 (not both required). Continuity threshold → 0.30.
6. Delete `FaceQualityEngine.isDistinctFromCaptured` and `MIN_CAPTURE_YAW_SEPARATION_DEGREES`.

Tests (`FaceQualityEngineTest`, new `PoseEstimatorTest`):
- Golden landmarks from Pointing'04 frames of pan −30/0/+30, stored as numbers, not images. Expect yaw −19 ± 6 / 0 ± 4 / +21 ± 6.
- Sign test for the front-camera mirror: a fixture where the student turns to *their* left must satisfy the LEFT slot.
- Relative gating: anchor = +5, frame = −8 → LEFT accepted (Δ = −13).

*Done when:* EXP-1 re-run with the new estimator shows ≥ 90% of frames at true ±15…±45 accepted for the matching slot, and 0% for the opposite slot.

### WP-C — Recognizer input + calibrated thresholds (P0, ½ day)

Files: `EmbeddingEngine.kt`, `BiometricModelProfile.kt`, `RecognitionDecisionEngine.kt`, `IdentityScorer.kt`, `EnrollmentBatch.kt` (`DuplicateCheckService`), `TemplateCompatibility.kt`.

1. BGR channel order in `extractEmbedding`. `recognizerColorOrder = "BGR"`. New `profileId`; `TemplateCompatibility.CURRENT_MODEL_ID` = `profileId` (one constant, not two as today).
2. `BiometricModelProfile`: `acceptThreshold = 0.50`, `ambiguousMargin = 0.08`, `confirmBelow = 0.60`, `duplicateThreshold = 0.50`, `continuityThreshold = 0.30`, `notFoundCeiling = 0.35`. **Every** engine reads these values. Remove the hard-coded `0.25f/0.05f` in `RecognitionDecisionEngine`'s defaults and in `AttendanceScreen.submitRecognition`.
3. `DuplicateCheckService`: use the max over all (candidate × template) pairs, return the best student (not the first hit), and drop the old 0.22 constant.
4. Delete the now-unused `combineEmbeddings` path (`submitRecognition`), which averages pose embeddings and was never calibrated.

Tests: update `RecognitionDecisionEngineTest` / `IdentityScorerTest` fixtures to the new thresholds. Add a regression test with the CAL quantiles: a synthetic impostor at 0.43 → NOT_FOUND, genuine 0.55 with a second agreeing frame → MATCH, genuine 0.55 with a disagreeing second frame → RETRY.

### WP-D — Self-service student flow (P0, 2 days) — *fixes "keeps showing enroll list"*

Files: `feature/attendance/AttendanceScreen.kt` (split), new `feature/attendance/AttendanceViewModel.kt`, new `feature/attendance/EnrollmentCaptureController.kt`, `AttractRepository.kt`, `domain/session/SessionContext.kt`.

**State machine (single owner: `AttendanceViewModel`):**

```
INITIALISING ──(models ok ∧ sessionId resolved ∧ camera bound)──► READY
READY ──tap CHECK IN──► SCANNING (auto best-frame, ≤ 3 s)
SCANNING ─► decide(frame):
   MATCH score ≥ 0.60                     ─► COMMIT_PRESENT ─► "✓ PRESENT: Amit (B123013)" ─2 s─► READY
   MATCH 0.50–0.60                        ─► CONFIRMING (grab 1 more frame) ─► same id ? COMMIT : RETRY
   already PRESENT this session           ─► "Already checked in: Amit" ─► READY
   AMBIGUOUS / QUALITY                    ─► RETRY (max 2) ─► then NOT_FOUND
   NOT_FOUND (top < 0.50) or gallery empty─► PICK_NAME
PICK_NAME  (only NOT_ENROLLED & not archived; search box; "I'm not on this list" → READY)
   tap name ─► confirm sheet "You are Amit Kumar · B123013? This face will be linked
                to this name for the semester."  [Yes, that's me] [Back]
   Yes ─► ENROLL_CAPTURE
ENROLL_CAPTURE (no CLICK per pose; auto-capture)
   slot STRAIGHT: "Look straight"          ─ first frame passing gate → captured ✓
   slot LEFT:     "Slowly turn to your LEFT"  ─ relative gate → captured ✓
   slot RIGHT:    "Now to your RIGHT"         ─ relative gate → captured ✓
   per-slot timeout 8 s → show hint (closer / more light / turn a bit more / a bit less)
   continuity check on each slot against STRAIGHT (≥ 0.30) else restart slot
ENROLL_COMMIT ─► repository.selfEnrollAndCheckIn(...)
   Committed            ─► "✓ ENROLLED & PRESENT: Amit"  ─► READY
   DuplicateSuspected   ─► "This face is already registered as <masked name>. Ask your teacher."  ─► READY
   NameTaken (race)     ─► "That name was just registered. Pick again." ─► PICK_NAME
   Failed               ─► explicit error, retry button (never an endless spinner)
```

Rules:
- **Recognition always runs first**, against **all** enrolled students in the class (including those already present). This produces "already checked in" instead of the enroll list.
- **The PICK_NAME list is `NOT_ENROLLED` only.** An enrolled student who gets NOT_FOUND (lighting etc.) cannot pick *their* name, because it's not listed. They get "Try again", then "Ask your teacher to mark you" (handled on the teacher's side after the session). This prevents the double-enrollment loop.
- **No teacher PIN** anywhere in this flow. `TeacherAuthorizationGrant` is removed from enrollment APIs.
- The CHECK IN button stays disabled until `INITIALISING` completes (fixes session id 0).
- One attempt before PICK_NAME when top < `notFoundCeiling` (0.35) — clearly a new face. Two attempts when 0.35–0.50 (could be an enrolled student in bad light).

**Repository — single command** `selfEnrollAndCheckIn(sessionId, classId, studentId, batch)`:
1. Validate the batch (3 samples, finite unit vectors, profile id, continuity).
2. Load usable class templates (skip corrupt rows, **don't** fail on NeedsRepair). Run the duplicate check (≥ 0.50 → `DuplicateSuspected(existingId)`).
3. `withTransaction`: re-read the student (must be NOT_ENROLLED, same class, not archived) **and** the session (ACTIVE, FACE, same class). Insert 3 templates, set ENROLLED, insert or upgrade the PRESENT record with source `ENROLLMENT`. Return the IDs.
4. Add a per-class `Mutex` around steps 2–3 so two phones/taps can't race.
- Delete `firstEnrollAndCheckIn`, `enrollStudentFace`, `markFallbackPresent` and the grant-based `standaloneEnrollStudentFace`, so only one write path exists. The teacher-side "Enroll face" button on the student detail page calls `selfEnroll` without the attendance part (`sessionId = null`).

*Done when:* the acceptance matrix rows S1–S8 (§7) pass on a device or emulator with the camera fed by the mock images.

### WP-E — Single source of truth for UI lists and counts (P0, ½ day)

- `AttendanceViewModel` exposes `StateFlow<SessionUi>` built from `repository.observeSessionStudentRows(classId, sessionId)`. Present count, the PICK_NAME list and "already checked in" all come from this one flow.
- Remove `presentIds` from the composable entirely. `onEndSession` reads from the DB.
- The gallery cache in the ViewModel is invalidated on every commit (version = `max(template.id)`), and the next scan waits for the refreshed gallery.

### WP-F — Quality gates retuned (P1, ½ day)

`FaceQualityConfig.calibrationDefaults()`: `minBlurVariance 120 → 60`, `maxOffCenterFraction 0.35 → 0.25`, `maxPoseDegrees (pitch) 20 → 15` with the new pitch estimator, `straightMaxYaw 15 → 12`, turn window `[10, 40]` relative. Add an on-screen oval matching the accepted centre region. Hints come from the **first** failing gate, at most one hint per second, so the text doesn't flicker.

### WP-G — Liveness via guided turn at check-in (P1, 1 day, optional switch)

`LivenessPolicy.TURN_CHALLENGE`: after a MATCH (before commit), ask for a random LEFT or RIGHT turn of ≥ 10° relative, within 3 s, with identity continuity ≥ 0.30 to the matched frame. A printed photo or a static screen fails. Default **on** for check-in, off for enrollment (enrollment already contains turns). Keep the existing blur/glare heuristics as quality hints only, never as spoof verdicts. They were never validated.

### WP-H — Camera/frame correctness (P1, 1 day)

- `FrameBundle` carries `lensFacing` and `isMirrored` from the real `CameraSelector`. Pose sign mapping uses them.
- Use `SystemClock.elapsedRealtimeNanos` timestamps. Capture only frames newer than the tap. Drop the dual `onFrameAnalyzed` + `onFrameBundleAnalyzed` callbacks; keep the bundle only.
- YOLO letterbox: EXP-1b shows letterbox vs stretch changes yaw by only ~1°, so this is **not** a root cause. Keep stretch, which matches how the model is decoded today.
- Analyse at most 1 frame in flight (already done). On dispose, increment a generation counter so late callbacks are ignored.

---

## 6. Mock data, on-device test harness, and calibration on the phone

### 6.1 Mock class (already built, `tools/biometric_eval/simulate_classroom.py`)

| Group | Count | Source | Role |
|---|---|---|---|
| Pose-labelled students | 15 | Pointing'04 (session 1 = enrollment day, session 2 = later days) | Real left/right turns, different-day check-ins |
| In-the-wild students | 45 | LFW people with ≥ 4 photos (disjoint from the calibration set) | Gallery diversity, lighting/age variation |
| **Day-1 absentees** | 8 random per class | from the 60 above | Must be routed to enrollment on Day 2 |
| **Strangers** | 120 per class | LFW people with 1 photo, not on the roster | Must never be marked present |
| Calibration-only | 200 | LFW, disjoint | Thresholds chosen here, never tested here |

The simulation runs 5 random classes (seeds 0–4). Raw per-event logs are in `results/classroom_simulation.json`.

### 6.2 Seeding a phone/emulator with the same mock class

Add a **debug-only** instrumentation test `MockClassroomE2ETest` (androidTest, not shipped):
1. Run `tools/biometric_eval/export_android_fixture.py` (to write). It produces `app/src/androidTest/assets/mock-class/` with **20 students × (3 enrollment frames + 2 check-in frames)** and 20 stranger frames, at 640×480 JPEG, about 4 MB. This path is already git-ignored by `.gitignore` → `app/src/androidTest/assets/test-data/`; put the fixture there.
2. Import a 20-name roster through `importRoster`.
3. Inject frames into `CameraPreview` via a `FrameSource` interface (production = CameraX; test = asset sequence). This is the only way to test the real pose gates and auto-capture, not a fake "recognised" event.
4. Script Day 1 (enroll 16, 4 absent) → `finalizeLiveSession` → Day 2 (20 check-ins + 20 strangers) → assert the DB rows.

### 6.3 Calibrating on your own phone (required before release)

Mock data proves the logic. Your phone's camera, lens and lighting shift the score distributions. Procedure, about 30 min with 10 volunteers:
1. Debug build → Settings → "Calibration capture" (debug-only screen). Each volunteer does an enrollment and then 5 check-ins at different spots in the room. The app stores **only scores** (top-1, top-2, slot yaw values), no images, in a CSV.
2. `python tools/biometric_eval/calibrate_device.py scores.csv` reports the genuine 1st percentile, the impostor 99.9th percentile, and recommends `accept/confirmBelow/dup`.
3. Accept the recommendation only if genuine p01 > impostor p999. Otherwise keep the conservative defaults (0.50/0.60).

---

## 7. Acceptance matrix (must pass before calling the pipeline "working")

| # | Scenario | Expected | Measured (mock, PROPOSED) |
|---|---|---|---|
| S1 | Clean DB, Day 1, student taps CHECK IN | NOT FOUND immediately → name list (NOT_ENROLLED only) | ✓ 260/260 routed |
| S2 | Picks own name → looks straight → turns left → turns right | Auto-captured, ENROLLED & PRESENT, count +1, ≤ 15 s | ✓ 245/260 (94%); failures = blurry/no-face source images |
| S3 | Turn LEFT ~15–30° | LEFT slot accepted | ✓ (was 0% on `main`) |
| S4 | Turn RIGHT ~15–30° | RIGHT slot accepted | ✓ (was 0% on `main`) |
| S5 | Same student taps again same day | "Already checked in" — never the name list | ✓ by design (recognition first, all enrolled) |
| S6 | Day 2, enrolled student | PRESENT, no enrollment | ✓ 523/530 (98.7%); 7 got "try again" |
| S7 | Day 2, Day-1 absentee | NOT FOUND → name list → enrolls | ✓ 40/55 late-enrolled; rest = unusable source images |
| S8 | Stranger not on roster | Never PRESENT | 5/600 (0.8%) — target ≤ 0.5% after phone calibration (§6.3) |
| S9 | Wrong student marked PRESENT | 0 | ✓ 0 / 530 |
| S10 | Enrolled student tries to enroll under another free name | Duplicate blocked | ✓ duplicate check catches 100% same-person at 0.50 |
| S11 | Two different people, one enrolled | New one not falsely blocked | 2.4% false-block per 60-class (CAL) |
| S12 | App killed mid-enrollment | Nothing half-written; student still NOT_ENROLLED | Unit test on transaction (WP-D) |
| S13 | Reset face data | All NOT_ENROLLED, 0 templates, history kept | WP-A test |
| S14 | Photo of an enrolled student held to camera | Rejected by the turn challenge | WP-G test (printed photo has no relative yaw) |

---

## 8. What NOT to do

- **Don't retrain or "fine-tune" ArcFace** on classmates. It's not needed (EER 1.5%) and not feasible on-device.
- **Don't lower `accept` back toward 0.25** to "make it recognise me". That is what makes random people match. Fix pose and BGR first; genuine scores rise (median 0.67 → see EXP-4: 0.85 with turn templates).
- **Don't widen the yaw window on the old formula.** It cannot exceed ~9 for any head turn, so no window will work.
- **Don't require profile views (≥ 45°).** Pose templates at ±15° raise top-1 accuracy from 97.8% to 100% (EXP-4). Going to ±30° adds nothing and makes enrollment harder.
- **Don't keep two write paths for enrollment.** One repository command, one transaction.

---

## 9. Effort and sequencing

| Order | WP | Effort | Unblocks |
|---|---|---|---|
| 1 | WP-A clean slate + migration | 0.5 d | everything |
| 2 | WP-B pose estimator | 1 d | left/right enrollment |
| 3 | WP-C BGR + thresholds | 0.5 d | correct match/no-match |
| 4 | WP-D self-service flow + single repo command | 2 d | Day-1/Day-2 behaviour |
| 5 | WP-E DB-derived UI state | 0.5 d | no stale "enroll" lists |
| 6 | WP-F quality retune | 0.5 d | fewer "blurry/off-centre" loops |
| 7 | WP-H camera correctness | 1 d | mirror/sign safety |
| 8 | WP-G turn-challenge liveness | 1 d | photo spoofing |
| 9 | §6.2 E2E harness + §6.3 phone calibration | 1.5 d | release gate |

**Total ≈ 8.5 developer-days.** WP-A … WP-D (4 days) already turn the app from "0 enrollments ever succeed" into the working Day-1/Day-2 flow shown in §0.

---

## 10. Limits of this analysis (honest)

- Android was **not built or run** here (no SDK/emulator in the sandbox). All measurements use the shipped `.tflite` files through LiteRT on CPU with a line-faithful Python port. Small numeric differences from Android `Bitmap` bilinear scaling are expected.
- Pointing'04 is 15 people at 384×288 from 2004. LFW is web photos. Neither is your classroom or your phone. The **logic** findings (RC-1 impossible gate, RC-3 threshold meanings, RC-4 flow bugs) are camera-independent. The **exact numbers** must be re-checked with §6.3.
- The BGR finding is empirical (better on every metric) and was not confirmed from the model's export code. The upstream repo (`sanjaysharmajw/flutter_face_liveness`) should be checked, and the golden-vector test in WP-C pins the choice either way.
- LFW strangers in the simulation skip the pose gate (web photos); real strangers at the phone also have to pass quality, so the 0.8% figure is pessimistic.
