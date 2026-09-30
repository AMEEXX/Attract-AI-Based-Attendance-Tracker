# LLD-16 — Adaptive 1→2→3 Frame Biometric Verification

**Status:** Implemented (architecture + TEST-ONLY benchmark). Production fusion switch PENDING calibration.
**Requirements source:** Adaptive verification directive (2026-08); SDD §§21–26, 29–31
**Depends on:** LLD-08–12, Decision D-004
**Supersedes (partially):** the mandatory STRAIGHT→LEFT→RIGHT capture philosophy of LLD-09's session flow

## Goal

Recognize a student from ONE excellent frontal observation when the existing production
decision logic is already confident; request additional natural observations ONLY when
evidence is insufficient; never allow extra frames to weaken a safe decision or to
manufacture an acceptance that single-frame semantics would reject for safety reasons.

## Production components (all additive)

| Component | File | Role |
|---|---|---|
| `FrameObservation` | `domain/face/FrameObservation.kt` | One capture: signals + quality verdict + liveness verdict + embedding. Carries `isBiometricallyUsable` gate and `looksLikeReplayOf` static-replay detector. |
| `EvidenceFusionStrategy` / `FusedOutcome` | `domain/face/EvidenceFusion.kt` | Pluggable evidence fusion. Strategies A–G implemented as pure functions for the benchmark. |
| `AdaptiveVerificationEngine` | `domain/face/AdaptiveVerificationEngine.kt` | The adaptive state machine: READY → FRAME_1 → (quality+liveness+embedding) → RECOGNITION_ATTEMPT_1 → [FRAME_2 → fuse] → [FRAME_3 → final fusion] → ACCEPT/AMBIGUOUS/UNKNOWN/ERROR. |

### State machine contract

```kotlin
engine.submit(observation): Step
  Step.NeedMoreFrames(framesSubmitted, reason)  // keep capturing (UI prompt hint)
  Step.Final(outcome)                            // terminal; engine refuses further submissions

Outcome = Match(studentId, confidence, framesUsed)
        | Ambiguous | Unknown | Error(message /* recoverable */)
```

Flow rules:

1. **Frame 1 fast path:** first *usable* observation whose production decision is a confident
   `Match` terminates the transaction immediately (`framesUsed == 1`). No left/right captures.
2. **Early rescue:** from the second usable frame onward, current evidence is fused; a fused
   `Match` ends the transaction without spending the remaining budget.
3. **Budget:** maximum 3 observations (configurable 1..3). Budget exhaustion forces terminal
   fusion over whatever usable evidence exists.
4. **Unusable frames** (quality/liveness rejection, missing crop/embedding) consume budget,
   contribute NO biometric evidence, and generate a retake prompt.
5. **Malformed embeddings** (wrong dimension / NaN) are a recoverable `Error`, per LLD-11
   Unavailable semantics.
6. **Static replays** (identical eye probabilities ±0.0001 AND identical yaw ±0.001) are never
   independent evidence; they are counted in diagnostics.

### Safety invariants (S1–S5)

* **S1** A confident single-frame Match is FINAL. Later bad frames cannot weaken it.
* **S2** Confident Matches to DIFFERENT identities across frames ⇒ `Ambiguous`. Never arbitrary acceptance.
* **S3** Unusable frames carry no biometric evidence.
* **S4** Replays count once.
* **S5** Pose is supporting evidence only: supporting frames reject EXTREME yaw/pitch using
  existing thresholds (`isAcceptableSupportPose`), but no specific pose is required.

## Thresholds — UNCHANGED

accept=0.45, ambiguity margin=0.10, `FaceQualityConfig.calibrationDefaults()`,
liveness gates, pose windows — all untouched. The adaptive layer reuses
`RecognitionDecisionEngine.evaluate` verbatim for every per-frame decision.

## UI integration (AttendanceScreen)

* Frame 1 prompt: straight look. Supporting prompts: "one more natural look".
* Frame-1 quality uses the STRAIGHT window; frames 2–3 bin by MEASURED yaw (production
  windows) so genuine profiles validate without forcing profiles.
* `NeedMoreFrames.reason` drives status messages; `Final(Match)` reuses the existing
  success/AlreadyCheckedIn flow; `Final(Ambiguous|Unknown)` reuses the existing 2-attempt
  retry → teacher-assistance/enrollment flow; `Final(Error)` shows the standard
  "temporarily unavailable" message.
* Legacy `submitRecognition()` (combine-all-embeddings) retained for compatibility but no
  longer on the happy path.

## Evidence fusion strategies (benchmark-only until calibrated)

A best-frame/max-similarity · B average identity score · C quality-weighted average ·
E majority identity · F all-frame consistency · G cross-frame embedding agreement ·
PROD conservativeBestFrame (current production default: any confident frame-level Match
stands unless conflicting; otherwise merged-max decision).

## Empirical results (LFW subset: 100 identities, 1000 images, seed 20260825)

Enrollment = up to 3 frontal observations (mean embedding). Verification queries EXCLUDE
enrollment frames. Thresholds unchanged. Full CSV: `adaptive_strategy_results.csv`.

```text
Strategy                     FAR%    FRR%   Acc@1  Need2  Need3  AvgFr  TA
STRATEGY_A_ONE_FRAME         5.96   22.11   77.9    0.0    0.0   1.00   74
STRATEGY_B_ADAPTIVE_1_TO_2   8.98   13.68   77.9    8.4    0.0   1.91   82
STRATEGY_C_ADAPTIVE_1_TO_3  11.73   12.63   77.9    8.4    1.1   2.68   83
C + 1-obs enrollment         8.87   29.00   53.0   12.0    6.0   2.83   71
FUSION_A_BEST_FRAME         11.73   16.84   77.9    4.2    1.1   2.68   79
FUSION_B_AVG_IDENTITY        6.76   11.58   77.9   10.5    0.0   2.68   84
FUSION_C_WEIGHTED_AVG        6.84   11.58   77.9   10.5    0.0   2.68   84
FUSION_E_MAJORITY           11.73   15.79   77.9    3.2    3.2   2.68   80
FUSION_F_ALL_CONSISTENT     11.73   12.63   77.9    8.4    1.1   2.68   83
FUSION_G_EMBED_AGREEMENT    10.09   10.53   77.9   11.6    0.0   2.68   85
```

### Findings

1. **Adaptive frames genuinely rescue genuine students:** FRR falls 22.11% → 13.68% (2 frames)
   → 12.63% (3 frames) under the production default fusion.
2. **Max-style fusion leaks on the impostor side:** FAR rises 5.96% → 11.73% because merged
   MAX similarity lets borderline impostors accumulate evidence toward the threshold across
   frames. This VIOLATES the spirit of "additional frames must never reduce security" and is
   why PROD_CONSERVATIVE must NOT ship to 3 frames as-is.
3. **Average-score fusion dominates max fusion on both axes:** B_AVG_IDENTITY achieves
   FAR 6.76% / FRR 11.58% — FAR within ~0.8pp of the one-frame baseline while nearly halving
   FRR. G_EMBED_AGREEMENT has the lowest FRR (10.53%) but materially higher FAR (10.09%).
4. **Frame-1 instant-accept security is IDENTICAL to today** in every strategy (Acc@1 = 77.9%
   at the same threshold): fast-path accepts never regress.
5. **Enrollment strength matters more than a third frame:** 1-obs enrollment costs +16pp FRR;
   3-obs enrollment is the biggest single robustness lever measured.
6. Only ~5 valid S→L→R triplets were constructible from 95 profile-pose frames — confirming
   that mandatory profile poses were starving the flow (LLD-09 finding, now resolved).

### Pending decisions (require explicit calibration approval)

* D-16a: switch production fusion from `conservativeBestFrame` to `averageIdentityScore`
  (empirically dominant; requires camera-path re-validation before shipping).
* D-16b: consider requiring agreement margin for rescues (frame ≥2) if classroom-scale
  galleries shift distributions.
* D-16c: re-run this benchmark against live CameraX captures before freezing.

## Tests

* JVM: `AdaptiveVerificationEngineTest` — matrix items 1–17 incl. safety invariants S1–S5.
* Instrumented: `AdaptiveStrategyBenchmarkTest` — strategy comparison on-device.
* Existing suites retained as regression coverage (SessionCoordinator, RecordPresentCommand,
  FaceQualityEngine, LivenessEngine, RecognitionDecisionEngine, E2E androidTest).
