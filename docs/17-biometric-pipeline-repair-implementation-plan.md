# Biometric Pipeline Repair: Source Audit and Implementation Handoff

**Date:** 2026-10-02
**Audited baseline:** `35f6451d19431ec2f56940b03a922e6cc24ac6ee` (`origin/main`)
**Status:** Proposed implementation plan; application fixes are NOT implemented by this document.
**Scope:** Native Android, offline, class-scoped recognition, progressive enrollment, attendance persistence, and pinned-session safety.
**Audience:** An implementing developer/model. Execute the numbered work packages in dependency order; do not substitute threshold changes for pipeline repairs.

## 1. Executive diagnosis

The repository now contains real application code despite README.md still claiming implementation has not started. The app already uses **YOLO for detection and ArcFace MobileFaceNet for recognition**. Replacing YOLO with another recognizer is not the primary fix.

The reported loop can arise from several independently confirmed defects:

1. Unknown/ambiguous recognition opens a roster of everyone not yet PRESENT, including enrolled students. Selecting an enrolled identity immediately writes manual attendance without teacher authentication.
2. Inline enrollment does not perform duplicate-identity protection. A recognition false rejection can therefore let the same person enroll under another unused name.
3. The v2-to-v3 migration deactivates old templates but leaves their students ENROLLED. The later sweep sees only active templates, so it cannot discover these already-orphaned students. They are permanently invisible to recognition but routed to manual fallback instead of repair.
4. Retry feedback exposes READY before clearing the terminal adaptive engine. A quick second tap can reuse a finished engine; the old delayed reset can also erase a newer interaction.
5. Multi-frame scoring overwrites multiple templates for a student instead of retaining their maximum. Frame decisions and fusion then operate on different rankings.
6. Enrollment accepts incomplete embedding extraction, has no repository-level same-person/quality/approval contract, and reports completion through a success-only callback.
7. Standalone enrollment shares the live-recognition SUBMIT handler and unconditionally creates a face-attendance session. Enrollment success and UI present counters also disagree.

These are source-level defects, not proof of which exact sequence occurred on the user's phone. Camera capture, score distributions, pose accuracy, and spoof resistance still need physical-device reproduction. No accuracy percentage is promised.

## 2. Evidence and audit limits

All source paths below are relative to the repository. For brevity:

- **UI** = `app/src/main/java/com/attract/attendance/feature/attendance/AttendanceScreen.kt`
- **CAM** = `app/src/main/java/com/attract/attendance/feature/attendance/CameraPreview.kt`
- **REPO** = `app/src/main/java/com/attract/attendance/data/repository/AttractRepository.kt`
- **FACE** = `app/src/main/java/com/attract/attendance/domain/face/`
- **DB** = `app/src/main/java/com/attract/attendance/data/local/`

Line references refer to the audited commit, not future edited versions.

### 2.1 What was actually verified

- Read SDD sections 12–25 and 31–35, LLD-10/11/16, architecture decisions D-001–D-006, the prior device/pose repair plan, and relevant production/test sources.
- Loaded all three committed TFLite assets with host LiteRT 2.2.0 and inspected actual tensors. This proves host initialization and tensor contracts, NOT Android JNI compatibility or classroom recognition quality.
- Ran one blank-frame detector inference: no detections above 0.5; box coordinates were approximately normalized as assumed by the parser. No evidence was found for the generic claim that this asset necessarily emits pixel-space boxes.
- Reproduced the migration/status defect in an in-memory SQLite example using the literal migration UPDATE and active-only selection.
- Reproduced `associate` last-write scoring versus grouped maximum with numeric examples.
- Traced retry transitions and pose-formula bounds from source. These are deterministic source analyses, not executed Compose/device tests.
- No student photos or phone database were accessed. No real-photo dataset is present in this checkout. Dataset preparation scripts and historical reports are not new execution evidence.
- Android tests/build were **not run**: this sandbox has no configured Android SDK or adb. Build/instrumentation commands in section 11 are implementation validation requirements, not claimed results.

### 2.2 Actual asset contracts

| Asset | Input | Output | SHA-256 |
|---|---|---|---|
| `arcface_mobilefacenet.tflite` | float32 `[1,112,112,3]` | float32 `[1,512]` | `dfac9cfe6517a9c4c3969b6ff0c2a0ac112cdf67a287d8218b60636810f0b576` |
| `yolov8n_face.tflite` | float32 `[1,3,640,640]` | float32 `[1,20,8400]` | `85a19457127249bb7f2a0875ff344b9dc6021a2a371e14c77d4c0e5f22f7ed54` |
| `mobilefacenet.tflite` (legacy, not active engine) | float32 `[1,112,112,3]` | float32 `[1,192]` | `be4bc7cfc53f7bc336d0f28b1ab92535f618c913a422b683210750f6b5354854` |

Tensor shape does not establish training provenance, expected color/normalization, licensing, or accuracy. Verify those against the actual upstream model/export and golden-image tests. `scripts/download_models.py` records release URLs but does not enforce a pinned manifest/hash/license contract.

## 3. Confirmed defect register

| ID / priority | Evidence | Defect and consequence | Required repair |
|---|---|---|---|
| R01 / P0 | UI:314–327, 1215–1255 | Candidate list is `students.filter { it.id !in presentIds }`, not NOT_ENROLLED. This violates SDD §14 and exposes enrolled identities. | Separate student enrollment list from authenticated teacher attendance list; eligibility/status checked again at commit. |
| R02 / P0 | UI:404–417, 346–394 | Selecting ENROLLED calls `markFallbackPresent` immediately. A comment calls the selector a teacher, but there is no authentication in this path. | Require a named, action-bound teacher authorization before assisted marking. Screen pinning is not teacher authorization. |
| R03 / P0 | UI:423–496; REPO:640–719 | Inline enrollment has no duplicate check. Standalone's check is UI-only, uses averaged embeddings and recognition margin, and blocks only Match. Repository callers bypass it entirely. | Shared duplicate checker before every enrollment/re-enrollment; Suspicious/Ambiguous/Unavailable block commit. |
| R04 / P0 | DB/AttractDatabase.kt:43–50; REPO:587–637 | Migration sets old templates inactive without changing student status. Sweep scans active rows and reconciles only students touched during that sweep. Already-orphaned ENROLLED students are missed. | Correct forward migration AND all-roster runtime reconciliation; preserve history and expose explicit repair status. |
| R05 / P0 | UI:554–568, 281–300; FACE/AdaptiveVerificationEngine.kt:103–107 | Unknown sets READY and unlocks capture, but waits 2 seconds before clearing the finished engine. Old timers have no attempt ID. | Fresh attempt before enabling capture; serialized events and cancellation/attempt tokens for every callback/timer. |
| R06 / P0 | FACE/AdaptiveVerificationEngine.kt:137–152; RecognitionDecisionEngine.kt:77–81 | `compatibleTemplates.associate { studentId to score }` keeps the LAST template score. Single-frame engine uses MAX. Fusion results depend on template row order. | One shared identity scorer with grouped maximum, finite checks and stable tie handling. |
| R07 / P0 | UI:439–449; FACE/TemplateCompatibility.kt:79–90 | `mapNotNull` swallows individual embedding exceptions; one surviving vector suffices. Repository accepts any nonempty current-dimension finite list, including zero vectors. | Require the approved complete sample set and evidence; inference failure cannot become a partial successful enrollment. |
| R08 / P1 | UI:220–234, 672–686, 784–889, 1105–1157; AttractApp.kt:226–247 | Standalone setup unconditionally ensures a FACE session; SUBMIT calls recognition, not enrollment. It may recognize another student or force repeated capture/selection before enrollment. | Separate standalone and live commands/screens; explicit target-bound enrollment commit without attendance side effects. |
| R09 / P1 | REPO:700–714; UI:457–483 | Repository already inserts ENROLLMENT attendance atomically. UI then calls manual marking, gets AlreadyPresent, but does not add target to `presentIds` in that branch. Count/roster/zero-present dialog can be wrong. | Return committed enrollment + attendance IDs once; derive present rows/count from Room, not a local set. |
| R10 / P1 | UI:457–486; AttractViewModel.kt:268–273, 382–391 | Success-only callback gives the screen no failure/busy result. Repository failure can leave UI PROCESSING indefinitely while an app-level message is shown. | Suspend typed result; explicit Busy/Failure/Cancelled handling and capture-lock release. |
| R11 / P1 | REPO:536–571, 587–637; FACE/TemplateCompatibility.kt:29–35 | Reads gate dimension/finite values, not model/profile equality or positive norm. Decode faults are skipped; an unreadable gallery is indistinguishable from a legitimate empty gallery. Sweep errors return an empty success-like report. | Versioned profile compatibility and typed gallery health; storage/crypto faults are not Unknown/first-day enrollment. |
| R12 / P1 | FACE/YoloFaceDetector.kt:303–334; FaceQualityConfig.kt:19–21 | Landmark distance ratio is stamped as yaw degrees. Its range is only -30..30, yet enrollment requests 20..45 degree windows. Real-turn accuracy/sign is unvalidated and pitch is also heuristic. | Calibrated pose provider or approved relative-pose protocol; never apply Euler-degree thresholds to arbitrary ratio units. |
| R13 / P1 | CAM:138–139; FACE/LivenessEngine.kt:29–30, 59–72; PresentationAttackDetector.kt:33 | Eye probabilities are fabricated as 1.0. Missing PAD passes. Tiny yaw/eye equality is treated as spoof proof. These checks are not validated liveness; genuine stillness can fail. | Missing signals remain unavailable; sequence-based PAD with real evidence and a safe authenticated fallback. |
| R14 / P1 | CAM:67–173; UI:244–248, 575–576, 997–1000 | Latest signals/crop have no frame ID/timestamp/track binding. Conversion failure keeps old values; callbacks may outlive disposal; capture can use stale imagery. Detector errors become faceCount=0. | Immutable timed frame bundle, fresh post-request acquisition, lifecycle generation and distinct camera/model errors. |
| R15 / P1 | UI:139–161, 571–572, 1083–1175, 1297–1325 | Model/session/pinning readiness does not gate capture. Exit unpins before finalization; cancellation of zero-present dialog leaves session unpinned. | One initialization barrier; authenticated end drains work, finalizes DB, clears biometrics, then unpins. |
| R16 / P1 | FACE/FaceAligner.kt:40–88 | Requires five landmarks but computes alignment only from two eyes; no finite/degenerate/reprojection checks. This is not a five-point best-fit alignment. | Validated five-point similarity transform and rejection of bad geometry; bump preprocessing profile. |
| R17 / P1 | FACE/EvidenceFusion.kt:221–239; docs/16-lld-adaptive-multiframe-verification.md:102–126 | Production defaults to merged-max rescue despite historical document warning of elevated FAR and pending calibration. Alternative averages are not automatically secure either. | Freeze rescue behind calibration; calibrate the whole sequential policy, not isolated pair scores. |
| R18 / P1 | REPO:659–715 | Generic enrollment silently replaces any student's templates and optionally writes to any active class session. No NOT_ENROLLED, approval, mode, archive or attendance-eligibility enforcement. Existing PRESENT insert can roll back re-enrollment on uniqueness conflict. | Separate first-enrollment/re-enrollment commands with explicit session/context and transaction guards. |

Additional hardening: `TemplateEnvelopeCodec.kt:37–42, 67–86` supports null-cipher plaintext writes and treats an encrypted row as plaintext if cipher is absent. Production currently injects a Keystore cipher in `AttractApplication.kt:26–34`, so this is not evidence that current production always writes plaintext. Make the production boundary non-null, retain plaintext only as an explicit legacy/test path, and never decode AEAD bytes as floats.

### 3.1 Reproducible explanations, not guesses

**Migration:** start with ENROLLED student A and one active 192-D row. Execute `UPDATE face_templates SET active=0 WHERE embedding_dim != 512`. A remains ENROLLED. The active-only sweep reads no rows, so `possiblyOrphaned` is empty. A cannot recognize; selecting A takes manual fallback rather than enrollment repair.

**Scoring:** rows `(A,0.95), (A,0.10), (B,0.60)` produce fusion map `{A:0.10,B:0.60}` with `associate`, but correct grouped map `{A:0.95,B:0.60}`. Use a nonmatching/ambiguous first observation in regression tests so the fast path does not hide the fusion defect.

**Retry:** at t=0 terminal Unknown exposes READY while engine.finished=true. A tap at t=500 ms can call `submit` on that engine and throw. At t=2000 ms the previous coroutine calls reset without checking whether a new capture/enrollment has started.

**Pose:** `(0.5 - dLeft/(dLeft+dRight))*60` is a ratio mapping, not an Euler-angle estimator. Simple eye/nose fixtures yield about 7 or 11 degrees for substantial shifts. This establishes unit/contract mismatch; it does not measure actual phone head-pose errors.

## 4. Binding target behavior and design decisions

### 4.1 Product flow

1. Teacher starts one FACE session for a selected class; models, DB, gallery and pinning prerequisites resolve before student capture is enabled.
2. Student presses CHECK IN. Acquire a fresh live sequence, select a quality frontal observation, align and embed it, and search **all usable enrolled identities in the selected class**, including already-present students.
3. Confident identity plus required liveness: persist PRESENT through the idempotent attendance command; show name/roll only after commit. Repeated check-in gives ALREADY CHECKED IN.
4. Valid but inconclusive recognition: bounded retry. Ambiguous is not proof of being unenrolled. After the budget, show CREATE MY PROFILE and ASK TEACHER as different actions.
5. CREATE MY PROFILE shows only eligible NOT_ENROLLED identities. REENROLL_REQUIRED is a separately labelled teacher repair path, not silently treated as a never-enrolled identity.
6. Student chooses Amit; teacher confirms ownership per existing D-001. Collect/validate the complete enrollment sequence, run same-person and duplicate checks, encrypt, and atomically enroll + mark PRESENT with source ENROLLMENT.
7. Return to READY for the next person with a fresh interaction and refreshed gallery. On later days Amit recognizes without enrollment.
8. ASK TEACHER authenticates before showing enrolled identities and offering teacher-assisted attendance or re-enrollment. No student-visible name selection marks an enrolled identity automatically.

A failed match cannot mathematically prove that a person is unenrolled. Use wording such as "We couldn't verify you" rather than an authoritative NOT_ENROLLED biometric result. The database owns enrollment status; recognition owns confidence in a particular capture.

### 4.2 Enrollment recommendation: three angles, not full profiles

Keep multi-template enrollment. Recommended initial policy is **3–5 accepted observations** from a short guided live sequence: at least one good frontal sample, one slight left turn and one slight right turn, with up to two additional distinct high-quality samples. For MVP, implement exactly three required accepted samples first; enable additional samples only after evaluation.

- Slight turns are more useful and easier to align than near-90-degree profiles with hidden eyes.
- Begin experiments around modest actual turns (roughly 10–20 degrees), but do not ship these numbers without measuring the chosen pose provider. They are not replacements for the current ratio formula's thresholds.
- One click may start the guided sequence. Do not require three arbitrary manual screenshots if bounded automatic best-frame selection can deliver better frames.
- Reject low quality for the current slot, never increment completion on rejection. Avoid near-identical frames using timestamp/track/image-content diversity, not yaw difference alone.
- Enforce person continuity with tracking and calibrated pairwise embedding agreement to the frontal anchor. A person swap clears the entire attempt. Do not use the roster recognition threshold as this consistency threshold.
- Store individual normalized templates plus pose/quality/profile metadata. A derived centroid may be evaluated, but cannot replace all samples by default.
- Generate templates with a pretrained recognition model; there is no per-student YOLO training/retraining step.
- Do not automatically append samples after routine recognition: SDD §19 prohibits MVP self-learning/template poisoning.

Selecting a never-enrolled name cannot prove identity. Preserve D-001 teacher confirmation. If unattended first enrollment is essential, obtain an explicit product/security decision introducing another trusted identity proof; do not silently remove approval.

### 4.3 Models and preprocessing

Keep the committed detector and 512-D recognizer initially; fix software defects before comparing replacements. YOLO locates faces/landmarks. ArcFace embeddings establish similarity. Neither YOLO confidence nor a filename proves identity accuracy.

Implement one injected `BiometricModelProfile` containing:

```text
profileId, detectorAssetSha256, detectorInputLayout/dtype/size,
detectorResizePolicy/outputDecoderVersion,
recognizerAssetSha256, recognizerInputLayout/dtype/size,
RGBorBGR, normalizationMean/scale, alignmentVersion/referencePoints,
embeddingDim, normalizationPolicy, qualityConfigId,
livenessPolicyId, matchingPolicyId, calibrationId
```

Validate input/output tensors on initialization, before marking the model ready. Verify RGB order and `(pixel-127.5)/128` against the exact artifact. Test stretch versus letterbox with the export's reference implementation; do not impose letterboxing while keeping the old coordinate decoder. Verify Android 2.16.1 runtime separately from host LiteRT.

Replace two-eye alignment with a tested five-point similarity fit (rotation/scale/translation; not an unrestricted affine warp). Reject nonfinite points, tiny eye distance, impossible ordering, excessive residual, and insufficient source coverage. Keep preview mirroring separate from inference coordinates. Version any alignment/resize change even if embedding dimension remains 512.

For pose, evaluate a reliable on-device landmark/head-pose provider or a calibrated relative-pose protocol; if this changes D-006's no-ML-Kit decision, record approval and dependency/license implications first. Do not claim five-point heuristic ratios are calibrated physical angles. Evaluate alternate detectors/recognizers only against the same held-out camera dataset and device latency budget.

### 4.4 Recognition and fusion

Shared scoring contract:

```text
score(query, student) = max(cosine(query, valid template) for that student)
rank by descending score, then stable student ID
B = top score; S = next DISTINCT student score
accept only if quality + liveness + profile + B threshold + margin all pass
exact/near ties => Ambiguous, never choose by insertion order
```

For a single candidate, the absolute threshold and liveness still apply; define absent runner-up explicitly without manufacturing infinite confidence. Reject NaN/Inf/wrong profile/wrong dimension/near-zero norm as malformed/unavailable evidence, not an ordinary Unknown.

A high-confidence first quality observation can terminate after the required sequence-level liveness evidence is available. The user sees one click, not necessarily one raw frame. Uncertain captures may request up to two additional observations from the same person.

Use one complete candidate-score map per usable frame. Initial safe release may disable uncalibrated fusion rescue and request another independently evaluated observation. Because multiple acceptance opportunities still increase attempt-level false acceptance, calibrate this sequential policy too. Candidate strategy for later evaluation: quality-weighted average identity scores with same-person continuity and a separate rescue threshold/margin. Do not switch to average fusion solely because historical LFW numbers look better.

Wrong-identity acceptance, ordinary Unknown, Ambiguous, quality retry, empty healthy gallery, incompatible gallery and model/crypto/camera failure must remain separate outcomes.

## 5. State and service contracts

### 5.1 One state owner

Move workflow out of Composable-local functions into an attendance ViewModel/coordinator. `SessionCoordinator.kt` has attempt IDs but is NOT wired into this production screen and does not yet implement full enrollment/authorization/camera lifecycle. Extend it or replace it with one authoritative coordinator; do not run two state machines.

```text
Initializing -> Ready -> Acquiring -> Validating -> Recognizing
  Match -> Persisting -> Success/AlreadyPresent -> Ready(new interaction)
  QualityRetry -> Ready(new capture, same interaction)
  NeedMoreEvidence -> Acquiring(same bounded attempt)
  Unknown -> RetryFeedback -> Ready(new attempt)
  ExhaustedUnknown -> UnknownOptions
  Ambiguous -> Retry/TeacherAssistance (never auto-enroll)
  Error -> RecoverableError (never name-selection fallback)

UnknownOptions -> EnrollmentSelection -> AwaitingTeacherApproval
 -> EnrollmentCapture(slot 0..2) -> EnrollmentValidation
 -> DuplicateCheck -> EnrollmentCommit -> Success -> Ready

TeacherAssistance -> Authenticate -> NamedAssistedAction -> Persisting
Ready/other permitted state -> Ending -> Authenticate -> Drain/CancelWork
 -> FinalizeDatabase -> ClearBiometrics -> Unpin -> Ended
```

Use explicit `SessionContext(sessionId,classId,mode)` and `InteractionContext(interactionId,attemptId,galleryVersion,lifecycleGeneration)`. Serialize events via one channel/mutex. Ignore stale callbacks before changing state AND before committing any write. Never expose READY until the new attempt is fully initialized. Cancel old feedback timers on next interaction, pause, navigation, end, or disposal. Distinguish retry budget from the next student's interaction; explicit cancel/next-person resets the former.

Retake during enrollment retains the approved target and recaptures the current sample; Cancel clears target/approval and returns to a new interaction. Current `resetToReady` loses the enrollment target. D-001 presently expires approval on first failed capture: implement that rule or approve a documented amendment allowing bounded quality retries under the same live-person track. Do not silently change token lifetime.

### 5.2 Typed boundaries (implement as Kotlin sealed results)

```text
FrameBundle:
  frameId, timestampNanos, lifecycleGeneration, trackId,
  lens/rotation/mirror metadata, detections, qualitySignals,
  owned aligned crop or explicit error

GalleryLoadResult:
  Ready(profileId, version, templates, healthSummary)
  EmptyHealthy
  NeedsRepair(affectedStudentIds, reason)
  Unavailable(camera/model/storage/crypto reason)

VerificationResult:
  Match(studentId, evidenceToken, score, margin, calibrationId)
  Unknown / Ambiguous / QualityRetry / NeedMoreEvidence / Unavailable

DuplicateResult:
  Clear / Suspicious(existingStudentId) / Unavailable

EnrollmentResult:
  Committed(studentId, templateIds, attendanceRecordId?, galleryVersion)
  AlreadyEnrolled / DuplicateSuspected / ApprovalExpired / Ineligible
  InsufficientSamples / CaptureRejected / Busy / Failed / Cancelled
```

A `ValidatedEnrollmentBatch` is produced only by the domain validator and contains the complete sample set, profile/evidence provenance, target/context and approval reference. Do not let UI construct it from arbitrary ByteArrays and asserted booleans. Restrict constructors/internal APIs accordingly; repository rechecks DB-dependent invariants.

Teacher authorization is an in-memory, short-lived, single-action grant bound to session, class, selected student, interaction and expiry. Clear it on process death/person change/cancellation/consumption. Enrollment approval and assisted marking are different actions. Authorization cannot be supplied by a student UI flag.

### 5.3 Capture freshness and ownership

Acquire the first suitable frame AFTER capture request or allow only a tightly bounded age under a measured policy. Use monotonic sensor/elapsed timestamps, not wall clock. Capture pixels and signals from one immutable bundle. Invalid conversion clears/replaces the bundle with an error, not yesterday's usable frame.

One analyzer in flight, KEEP_ONLY_LATEST, bounded inference dispatcher, thread-safe interpreters. Resource cleanup in finally. On disposal invalidate generation, stop analyzer and reject queued callbacks. Coordinate crop ownership so captured bitmaps are not recycled while embedding runs. Release nonselected full frames promptly; wipe embeddings and release sample buffers on every terminal/cancel/error/end path. Do not persist raw photos or debug crops in production.

## 6. Duplicate protection, enrollment and database invariants

### 6.1 Duplicate algorithm

Before first enrollment or re-enrollment, load a healthy current class gallery including identities already PRESENT. Compare every proposed sample against every existing student's samples, preserving identity grouping. Do not require recognition's top1/top2 margin to flag a duplicate: two close identities are themselves suspicious.

Use an independently calibrated enrollment duplicate policy, generally more sensitive than automatic attendance acceptance. Return Suspicious on strong existing-identity evidence, conflicting evidence or uncertainty requiring review. EmptyHealthy permits first enrollment; an unreadable/stale gallery does not. Evaluate thresholds with false-duplicate and missed-duplicate metrics.

First enrollment may never exclude the selected ID as a shortcut. Re-enrollment may exclude only the teacher-authorized target's old templates while comparing all others. Serialise enrollment writes per class and recheck gallery revision inside the transaction; if it changed since duplicate checking, rerun checking before commit. This prevents concurrent enrollments slipping through an empty-gallery snapshot.

### 6.2 Commands and transaction order

**FirstEnrollAndCheckIn(sessionContext, target, validatedBatch, authorization):**

1. Validate batch count/diversity/continuity, finite unit vectors, profile and approval.
2. Duplicate-check healthy gallery. Encrypt templates before the transaction when possible, with failure producing no DB changes.
3. In Room transaction re-read session, target, enrollment state, archive/class/eligibility, approval binding and gallery revision. Require ACTIVE FACE and NOT_ENROLLED.
4. Insert templates, update enrollment/enrolledAt, and write PRESENT once with source ENROLLMENT using existing idempotent attendance rules.
5. If any step fails, throw/abort the transaction; do not return a failure after mutations and accidentally commit them. No templates/status survive a failed required attendance write.
6. Return committed IDs; refresh/invalidate gallery after commit before next capture. UI renders Room-derived state.

**StandaloneEnroll / ReEnroll:** no attendance session creation, no automatic attendance row. ReEnroll requires teacher authorization, excludes only its own templates for duplicate checking, inserts replacements and deactivates old templates atomically. Failed replacement leaves old usable templates active and preserves all attendance history. A teacher may mark attendance through a separate explicit authorized command.

**RecognizedCheckIn:** bind verified evidence to exact active session/attempt/gallery/profile and student; recheck eligibility/status. Persist immediately via RecordPresentCommand within transaction. Duplicate `(sessionId,studentId)` returns AlreadyPresent without overwriting source or check-in time.

**AssistedCheckIn:** requires teacher grant, uses TEACHER_ASSISTED (or the approved audited assisted source), never pretends to be AI recognition. Existing low-level manual helper must not remain a publicly reachable unauthenticated alternative.

### 6.3 Schema/compatibility work

Create a forward v3-to-v4 repair migration; also correct v2-to-v3 for devices not yet upgraded. Regenerate/export Room schemas and validate upgrade paths 1->2->3->4, 2->3->4 and 3->4 with real schema fixtures.

Proposed additions:

- enrollment state `REENROLL_REQUIRED` with reason/version (update converter/exhaustive UI branches), distinct from NOT_ENROLLED;
- template profile ID or existing modelVersion upgraded to full preprocessing-profile identity, embedding dimension, pose bucket and quality/config metadata;
- durable per-class gallery revision/profile metadata if needed for cache/commit versioning;
- minimal approval/calibration provenance where privacy policy permits; never store raw verification vectors/face frames.

Reconcile ALL active roster students, including those whose templates were already inactive before this launch. A structural migration can detect missing/current-metadata rows; crypto decode/current-vector health must be checked at runtime. An enrolled student must have the approved minimum usable sample set under the new policy; legacy single-template profiles need an explicit grandfather/re-enrollment decision, not accidental acceptance.

Runtime outcomes distinguish incompatible model, corrupt template and Keystore unavailable. Do not retire every template because a transient key/service failure was mistaken for incompatibility. Permanent key invalidation requires teacher-visible recovery and consented re-enrollment. Historical inactive rows/attendance are retained according to existing retention policy.

Never pad/truncate vectors, compare same-dimensional different models, reset the whole database, or use destructive downgrade as the rollout recovery plan. Re-enroll affected profiles without losing roster/history.

## 7. Calibration and liveness release gates

### 7.1 Existing claims cannot establish deployment accuracy

- Production defaults now use accept=0.25 / margin=0.05, but LLD-11's adaptive amendment and LLD-16 still say 0.45 / 0.10. Earlier LLD-10 mentions 0.75 and old dimensions. There is no single effective configuration.
- D-006 reports minimum genuine score 0.2489 while acceptance is 0.25: that sample fails the stated threshold. "Perfect separation" is not equivalent to every query being correctly accepted at the deployed policy.
- The older consolidated report cites 128-D and 3 identities / 12 photos while the active artifact is 512-D. It cannot validate the upgraded pipeline or camera/PAD behavior.
- Historical LFW rescue FAR figures around 6–12% in LLD-16 are not acceptable evidence of a safe classroom release, and must not be transplanted to the new model.
- RecognitionPathDiagnosticTest skips missing usable query/stranger samples and enrolls single samples with null cipher. FaceSessionE2EAndroidTest uses fake DAOs and injected recognition results. They are useful component tests, not the live enrollment UI acceptance test.
- TemplateMigrationAndroidTest builds a fresh current-schema database and exercises a sweep; it does not run the defective actual v2-to-v3 migration.

### 7.2 Evaluation protocol

Collect consented teacher-phone enrollment and later verification captures under the exact camera path. Use separate enrollment, calibration and held-out evaluation captures; include later days, not just adjacent video frames. Keep identities disjoint between calibration and evaluation where possible, with enrollment/gallery and genuine queries defined separately within each split. Include unknown people, similar-looking classmates, glasses, varied lighting, skin tones, phone orientation/lenses and classroom-scale gallery sizes.

Measure:

- correct identification rate, false rejection, wrong-identity acceptance among enrolled queries;
- unknown-to-enrolled false positive identification rate (FPIR) per FULL interaction;
- pairwise FMR/FNMR as supporting metrics, not a replacement for open-set identification;
- ambiguity, quality/PAD failures, rescue rates, frames/time to decision;
- duplicate false blocks/missed duplicates; same-person continuity errors;
- PAD attack acceptance and genuine rejection (APCER/BPCER with attack types and sample counts);
- p50/p95 latency, memory, temperature and battery on supported phones.

Sweep absolute/margin thresholds for fast path, rescue, duplicate and sample consistency separately, then select under an approved low false-accept target. Record raw counts, denominators, gallery sizes, confidence intervals, model/profile hashes and effective sequential policy. Do not choose thresholds from min/max of three participants.

Suggested release objectives, subject to owner approval: >=95% correct genuine identification within two usable captures in supported conditions; p95 verification <=3 seconds on supported phones; zero wrong-identity marks in scripted regressions; held-out unknown FPIR target <=0.1% per interaction. These are gates to validate, not current performance claims. About 3,000 genuinely independent zero-error unknown trials are needed for an approximately 0.1% one-sided 95% upper bound; correlated pairs/frames do not provide that sample size. Report inconclusive if evidence is too small.

### 7.3 PAD policy

Current brightness/texture/glare heuristics and fixed-open eyes are not validated presentation-attack detection. Keep them as quality/supporting signals only. Implement an approved on-device temporal PAD model/protocol, or randomized challenge with actual observed movement plus evaluated passive PAD; a blink/head turn alone does not prove resistance to video replay.

Missing PAD evidence => Unavailable/teacher path, not Passed. Make PAD latency and lighting limits explicit. One-click recognition may collect a brief live sequence before identity acceptance; identity model confidence cannot bypass liveness. Test printed photos, phone/tablet replay, prerecorded moving video and genuine still users. Do not market unsupported spoof protection.

## 8. Ordered implementation work packages

Every package must ship with failing regression tests first where practical, a focused commit, and a clear test report. Do not declare a package complete solely because the UI displays success.

### WP01 — Freeze requirements and add failure reproductions

**Depends:** none. **Files:** SDD/LLD-10/11/12/16, architecture decisions; existing unit/android acceptance test directories.

- Agree class scope, teacher identity confirmation, separate repair status, modest-pose enrollment, one-click live sequence and release risk targets.
- Add R01/R03/R04/R05/R06/R08/R09 regressions; preserve current baseline artifacts for comparison.
- Establish test fixture manifest, consent/license, identities and capture splits. Stop release on absent required evaluation data.
- **Done:** documented approved contracts and tests that demonstrably expose the current defects; no threshold relaxation.

### WP02 — Shared profile, vector validation and identity scorer

**Depends:** WP01. **Files:** FACE/EmbeddingEngine.kt, TemplateCompatibility.kt, TemplateMatcher.kt, RecognitionDecisionEngine.kt, AdaptiveVerificationEngine.kt, EvidenceFusion.kt, scripts/download_models.py.

- Add pinned profile/asset manifest; validate tensors; positive finite near-unit norms and exact dimensions/profile.
- Replace duplicate scoring implementations with grouped-max scorer and stable tie decisions. Reuse in fusion/duplicate checks.
- Distinguish malformed/unavailable/no-template/unknown outcomes. Remove hardcoded per-screen threshold construction.
- **Done:** row permutation and duplicate-template count do not change rankings; profile mismatch and zero vector cannot mark attendance.

### WP03 — Forward migration and gallery health

**Depends:** WP02. **Files:** DB/AttractDatabase.kt, Entities.kt, Daos.kt, Converters.kt, exported app/schemas; REPO; core/model/AttendanceModels.kt.

- Implement v4 schema and all-roster reconciliation, including already-inactive orphan templates.
- Typed gallery health, profile equality and crypto-error classification; no success-like empty report on failure.
- **Done:** a 192-D ENROLLED v2 install upgrades with history intact and explicit repair path; valid 512-D profiles remain usable; same-size wrong-profile rows do not match.

### WP04 — Production coordinator and deterministic retries

**Depends:** WP02, WP03. **Files:** domain/session/SessionCoordinator.kt, SessionState.kt, SessionEvent.kt; new feature/attendance/AttendanceViewModel.kt if needed; UI and feature/app/AttractApp.kt.

- One serialized state owner, attempt/interaction/lifecycle tokens, cancellable feedback timers and explicit readiness barrier.
- Clear terminal engine before retry enablement; no stale writes or retake losing enrollment target.
- **Done:** simulated taps at 0/100/500/2000 ms cannot reuse terminal engine or clear another attempt; lifecycle recreation invalidates pending captures/grants.

### WP05 — Student roster and authenticated teacher actions

**Depends:** WP03, WP04. **Files:** UI/ViewModel, REPO/DAOs, lockdown authentication/domain services.

- Student list only eligible NOT_ENROLLED. Separate teacher repair/assisted list behind auth.
- Consume action-bound grants at command boundary. Remove unauthenticated selected-name attendance route.
- **Done:** selecting any enrolled ID in student mode cannot change attendance/templates; unauthorized/reused/expired grants fail with zero writes.

### WP06 — Fresh camera frames and error propagation

**Depends:** WP02, WP04. **Files:** CAM; FACE/YoloFaceDetector.kt; FrameObservation.kt; attendance ViewModel.

- Immutable timestamped frame bundle; no stale capture after conversion failure; stop/disposal generation checks and memory ownership.
- Prewarm detector and recognizer, classify model/camera errors separately, reject extra faces before landmark-quality filtering can hide bystanders. Face count and embedding suitability are distinct concepts.
- Test non-square sensors, front/back, 0/90/180/270 rotations, preview mirroring and slow-frame backpressure.
- **Done:** inference always uses the capture's corresponding signals/crop; every ImageProxy closes; model fault never appears as ordinary no-face/unknown.

### WP07 — Alignment and honest pose/quality signals

**Depends:** WP06. **Files:** FACE/FaceAligner.kt, YoloFaceDetector.kt, FaceQualityConfig.kt, FaceQualityEngine.kt, FaceQualitySignals.kt; CAM; model profile.

- Five-point similarity with golden parity tests and residual/geometry validation; calibrated pose provider/relative protocol.
- Unavailable eyes remain null/unavailable; fix quality scoring to use actual pose policy, not penalize every approved supporting turn as zero pose score.
- Version preprocessing, trigger targeted re-enrollment if representation changes; don't reuse 512-D templates solely because shape matches.
- **Done:** guided slight-left/right capture works on real supported phones and rejects degenerate/swapped landmarks.

### WP08 — Complete guided enrollment batch and PAD

**Depends:** WP05–WP07. **Files:** attendance ViewModel/UI; FACE/LivenessEngine.kt, PresentationAttackDetector.kt, FrameObservation.kt; PresentationAttackAnalyzer.kt; new enrollment validator/collector and approved PAD adapter as necessary.

- Collect exactly three required valid slots first, with bounded retakes, same-person track/embedding continuity and real PAD evidence.
- Remove mapNotNull partial success; sample extraction failure retains valid slots or cancels safely, never commits fewer samples.
- **Done:** person swaps, replay, missing PAD, identical frames and rejected slots never produce a valid batch; consented genuine enrollment can finish reliably.

### WP09 — Central duplicate service and atomic enrollment commands

**Depends:** WP02, WP03, WP05, WP08. **Files:** REPO, RecordPresentCommand.kt, FACE matcher services; new domain/enrollment command/duplicate service where justified.

- Duplicate checking at shared domain/repository boundary with class lock/gallery version validation.
- Separate first enrollment, standalone enrollment and replacement; transaction guards and rollback on any required write.
- Return typed committed IDs; recheck eligibility/session/auth/profile, disallow silent replacement.
- **Done:** same person cannot enroll as Amit then another classmate; duplicate-check outage blocks; concurrent duplicate submissions cannot both commit.

### WP10 — Wire UI result handling and DB-owned counts

**Depends:** WP04, WP09. **Files:** UI, AttractViewModel.kt:268–273/command handling, AttractApp.kt; Room attendance observation queries.

- Replace success callbacks with awaited typed results; stop indefinite PROCESSING on failed/busy/cancelled enrollment.
- Standalone SUBMIT invokes target enrollment directly, never recognition/session creation. Retake retains target.
- Enrollment success consumes one repository result; remove second manual attendance call. Observe committed present rows/count, including recovery and AlreadyPresent.
- **Done:** successful first enrollment shows ENROLLED & PRESENT and increments count once; standalone setup creates no attendance session/row.

### WP11 — Session end/recovery security

**Depends:** WP04, WP10. **Files:** UI/ViewModel, SessionRecoveryManager.kt, REPO finalization commands; lockdown/platform/LockTaskControllerImpl.kt.

- Stop accepting new capture, cancel/drain inference, authenticate and finalize exact session, clear cache/frames/grants, then unpin/navigate.
- Failed finalization or cancelled zero-present confirmation retains appropriate locked session state. Recover counts/gallery from DB, not remembered sets.
- **Done:** no delayed callback writes to ended/new session; app restart preserves committed attendance; failed end cannot expose teacher navigation.

### WP12 — Repair tests and run full pipeline calibration

**Depends:** WP02–WP11. **Files:** existing test/androidTest suites; scripts/test_models_inference.py and benchmark scripts; docs test reports/manual QA.

- Stop accepting skipped genuine/unknown trials as passing validation; test real UI plus production Room/Keystore, not only injected outcomes.
- Add actual Room migration fixtures; update stale dimensions/threshold labels.
- Implement held-out whole-interaction metrics and ablations: old/new alignment, 1 vs 3 vs 5 enrollment samples, single-frame vs bounded rescue.
- Freeze profile/config only after genuine/impostor/PAD/device acceptance gates; attach exact execution evidence.
- **Done:** every section 9 regression passes and calibrated physical-device criteria are met, or release is explicitly blocked.

### WP13 — Rollout and documentation reconciliation

**Depends:** WP12. **Files:** README, SDD, relevant LLDs, architecture decisions and validation reports.

- Update stale README and contradictory dimension/threshold/pose/fusion claims; one manifest is the executable authority.
- Pilot on supported devices/class sizes, monitor aggregate error categories, prepare teacher-led repair of incompatible profiles.
- Roll back via a schema-compatible build and teacher-assisted mode, not old APK destructive downgrade.
- **Done:** documented migration/support matrix, teacher instructions, profile hashes and go/no-go report; no claim that historical tests prove this release.

## 9. Mandatory acceptance and regression matrix

| Test | Setup/action | Required result |
|---|---|---|
| A01 | Healthy empty gallery, new Amit capture/select/approve/complete enrollment | Exactly three valid templates, ENROLLED and one ENROLLMENT PRESENT row; count=1. |
| A02 | Recapture Amit ten times, varied valid images | Only Amit or safe retry; never another ID/enrollment; one attendance row this session. |
| A03 | Next day/new session, same class Amit | Recognition without enrollment; one new session PRESENT row. |
| A04 | Unenrolled student first attends day 2/3 | Listed independently of previous absence; enrollment and first attendance complete atomically. |
| A05 | Enrolled but unrecognized student | Not in student enrollment list; ASK TEACHER is required for assisted marking/repair. |
| A06 | Amit tries to enroll as another unused roll | Duplicate protection blocks with no template/status/attendance change. |
| A07 | Two close existing identities; duplicate detector uncertain | Suspicious/teacher review, not Clear because recognition margin is small. |
| A08 | Gallery empty due to crypto/model/storage failure | Unavailable/repair, not first-day enrollment. |
| A09 | Actual v2->v3->v4 upgrade with only 192-D rows | All affected roster statuses repaired; attendance/history preserved; explicit re-enrollment. |
| A10 | Already-v3 orphan, no active templates | Reconciliation finds orphan without needing an active stale row. |
| A11 | 512-D row from different model/alignment, zero/NaN/Inf vector | Cannot enter usable gallery or enrollment commit. |
| A12 | Frames/templates permuted; multiple templates per person | Identical scores/outcomes; strongest relevant template retained. |
| A13 | Second tap before retry feedback timer finishes | New valid attempt or disabled tap; no finished-engine exception/stale reset. |
| A14 | Delayed callback after cancel/end/new person | Ignored; no UI or DB mutation. |
| A15 | One of three embedding extractions fails | No incomplete enrollment; explicit retake/error; no indefinite spinner. |
| A16 | Same frontal frame reused/old timestamp/person swaps | Diversity/continuity failure; no profile committed. |
| A17 | DB insertion/crypto failure at each transaction step | Full rollback; no partial first enrollment; previous profile survives failed replacement. |
| A18 | Enrollment succeeds then UI recreation/recovery | DB count/list/source correct; no additional MANUAL write. |
| A19 | Standalone enrollment/re-enrollment | No ensureFaceSession or attendance row; explicit target retained; old profile replaced only on success. |
| A20 | Wrong class/archived/ineligible target/session ended | Command rejects; no biometric or attendance write. |
| A21 | Missing/reused/expired/wrong-action teacher grant | No enrollment/replacement/assisted attendance. |
| A22 | Print/screen/video replay; PAD unavailable | No automatic PRESENT; approved recovery path. Genuine stillness not automatically labelled spoof. |
| A23 | Non-square front/back camera and all rotations | Coordinates/landmarks/alignment correct; view mirror does not corrupt inference. |
| A24 | Model missing, wrong tensor contract, inference exception | Specific recoverable unavailable state; enrollment list not auto-opened. |
| A25 | Close/tied candidates/identity conflict across frames | Ambiguous; no arbitrary ID. |
| A26 | Concurrent enrollment for same face/two IDs | At most one commit; other checks revised gallery and fails safely. |
| A27 | Finalization fails or zero-present exit is cancelled | No premature unpin/teacher navigation; session remains recoverable. |
| A28 | Profile change while attempt is running | Stale evidence rejected; new attempt uses refreshed gallery/profile. |
| A29 | Unknown student not in roster | Cannot create arbitrary ID; teacher manages roster outside active student flow. |
| A30 | No unenrolled students left, unknown/ambiguous face | Teacher assistance only; no misleading profile list. |

Use unit tests for pure decisions/coordinator timing; Room/Keystore instrumentation for persistence/migration; Compose tests for real button routing/status/counts; physical-camera pilot for biometric/PAD/device behavior. A fake recognized student event is not a recognition test.

## 10. Diagnostics and deployment checklist

Add opt-in, teacher-authorized DEBUG diagnostics with trace/attempt ID, frame age, pipeline stage, profile/calibration IDs, gallery counts/health, quality rejection category, inference timings and commit outcome. Scores may be captured in an approved transient evaluation report, not unconditional release logs. Never log PINs, face pixels, embeddings or full roster rankings/names. Current production logging in UI/recognition/repository should be reviewed and gated.

For the user's failing phone, obtain build/version/model profile, Room schema version, class/session ID, roster enrollment/usable-template counts, migration status, and sanitized per-stage outcomes. Reproduce: enroll Amit, immediate recapture, end/restart, next-day session. This distinguishes software-state failure from remaining camera/biometric false rejects without collecting raw biometrics by default.

Before release:

- exact APK commit/profile/asset hashes recorded;
- all P0 defects resolved and section 9 regressions green;
- migrations verified against existing installs without resetting data;
- healthy gallery refresh visible immediately after enrollment;
- physical-device capture/PAD/calibration evidence meets approved risk targets;
- teacher can safely assist and repair incompatible profiles;
- raw buffers/cache cleared on cancel/pause/end and encryption enforced;
- UI shows success only after persistence, and timeout/failure always returns a defined state;
- compatible rollback/assisted-mode instructions available.

## 11. Validation commands and handoff instructions

Run with JDK 17-compatible Android toolchain, Android SDK/API 35 and a configured emulator/physical phone. The existing root build.gradle.kts redirects output to a Windows-style path; make build-output policy portable when setting up CI, without writing outside an authorized workspace.

```bash
# Host unit tests and compile both APKs
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest

# Instrumentation; required fixture provisioning must complete first
bash gradlew :app:connectedDebugAndroidTest

# Focused migration/acceptance suites (extend these, do not trust names alone)
bash gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.attract.attendance.data.local.TemplateMigrationAndroidTest
bash gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.attract.attendance.acceptance.RealUiFlowAcceptanceTest
```

Inspect scripts/prepare_face_test_data.py and dataset instructions before running benchmarks. The existing scripts/test_models_inference.py saves aligned debug images: keep that explicitly consented TEST-ONLY and separate from production privacy behavior. Correct its min/max "100% accuracy" claim; compute held-out identification/attempt metrics instead. No test should pass a release gate merely by skipping unavailable human images.

**Prompt for the implementing model:**

> Read this plan, docs/00-main-sdd.md, architecture decisions, and the cited source before editing. Implement WP01–WP13 in dependency order. Preserve offline/class-scoped behavior and attendance history. Do not lower thresholds, bypass quality/PAD/teacher approval, treat gallery errors as Unknown, or replace model assets to mask control-flow defects. Use one attempt-bound coordinator, one model profile/scorer, complete validated enrollment batches and typed transactional results. Reproduce each defect with tests, then fix it. Report exact modified files, commands, executed results, skipped tests, remaining device/calibration gates and migration effects. Do not claim production readiness until the acceptance matrix and held-out physical-camera evaluation pass.
