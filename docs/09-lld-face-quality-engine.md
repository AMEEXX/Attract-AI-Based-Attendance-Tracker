# LLD-09 — Face Quality Engine

**Status:** Draft — ready for review and freeze
**Requirements source:** SDD §§15–16, 22, 70–72
**Depends on:** LLD-08, Decision D-004

## Goal

Reject unusable observations before liveness or embedding inference and choose diverse high-quality enrollment frames. Quality guidance is helpful, short, and never exposes biometric scores.

## Contract

```kotlin
sealed interface QualityResult {
  data class Accepted(val score: Float, val poseBucket: PoseBucket) : QualityResult
  data class Rejected(val reason: QualityReason) : QualityResult
}
enum class QualityReason { NO_FACE, MULTIPLE_FACES, TOO_SMALL, OFF_CENTER, POSE, BLUR, DARK, OVEREXPOSED, EYES_UNCLEAR }
```

Hard gates: exactly one face (`faceCount == 1`), minimum face size (`faceRatio >= 0.08`), in-frame bounds (`centerX`, `centerY` within 0.25..0.75 margin), valid crop, and maximum extreme pose (`|pitch| <= 20°`; yaw is gated **per capture step**, see below). Soft signals: blur (`blurVariance >= 20.0`), luminance/histogram (`brightness` within 50.0..220.0), pose, eye-open probability when detector supplies it, and framing. Null/unsupported eye probabilities are “unavailable,” not fake closed eyes; the configured policy decides whether other signals suffice.

## Step-Aware Pose Gating (Straight → Left → Right)

The global `|yaw| <= 20°` gate applies **only to the STRAIGHT step**. Because enrollment requires genuine profile turns, the POSE hard gate must be evaluated against an explicit per-step expected-yaw window supplied by the caller:

| Capture step | Required ML Kit `headEulerAngleY` window | Reject reason if outside window |
|---|---|---|
| 1 — Straight | `-15° .. +15°` | `POSE_NOT_STRAIGHT` |
| 2 — Left profile | `-45° .. -20°` (negative yaw = turned left) | `POSE_NOT_LEFT` |
| 3 — Right profile | `+20° .. +45°` | `POSE_NOT_RIGHT` |

Rules:
1. `evaluate(signals, config)` gains an optional `expectedPose: ExpectedPose` parameter (`STRAIGHT | LEFT | RIGHT`). When absent, behavior falls back to the legacy straight-only window (backwards compatible).
2. All other hard gates (single face, size, framing, blur, brightness, eyes, `|pitch| <= 20°`) apply identically to every step.
3. A frame captured for step N is validated against step N's window BEFORE the session advances; a passing frame advances the step, a failing frame re-prompts with step-specific guidance (“Turn your head slightly LEFT” / “Turn back to center”). The session NEVER advances on a pose-mismatched frame.
4. Yaw sign conventions vary across devices/cameras; the adapter layer (LLD-08) normalizes `headEulerAngleY` sign relative to the mirrored preview so LEFT is consistently negative on all devices. Calibration values are versioned in `FaceQualityConfig`.
5. Duplicate-frame protection: consecutive accepted frames must differ in yaw by at least the step-window separation (>= 15°), preventing three identical frontal frames from satisfying the flow.

Configuration is versioned: hard limits, score weights, pose bins, and reject reasons. Values match calibrated production configuration (`FaceQualityConfig.calibrationDefaults()`). The engine returns reason category and score only to face/session internals; UI receives mapped guidance such as “Move closer” or “Move to better lighting.”

## Per-Frame Quality Gating on CLICK
1. On each student CLICK, the Quality Engine immediately evaluates brightness luminance, face presence, blur, and pose direction.
2. If luminance < minThreshold (dark) or no face is detected: Return `QualityResult.Rejected(QualityReason.DARK)`. The UI displays a guidance warning ("Low lighting detected. Move to bright light") and DOES NOT advance the pose step.
3. The button only unlocks to SUBMIT after all 3 pose steps (Straight, Left, Right) receive `QualityResult.Accepted` against their respective per-step pose windows. A frame is only accepted for the step currently prompted; mismatched poses are rejected with step-specific guidance and do not advance the flow.

For 3–5 templates, retain candidates from distinct pose/quality buckets rather than five near-identical consecutive frames. Reject duplicates based on tracking time and perceptual/crop similarity. Stop on the configured count or controlled timeout; timeout asks the student to try again and stores nothing.

## Build and tests

Implement pixel statistics and detection-signal adapters as pure functions. Validate crop bounds before OpenCV/bitmap work. Use fixture images/synthetic buffers with no retained production face data.

| Scenario                               | Expected result                    |
| -------------------------------------- | ---------------------------------- |
| no/multiple faces                      | named rejection                    |
| small/off-frame face                   | rejected before liveness/model     |
| blurred/dark/overexposed fixtures      | correct category                   |
| unsupported eye probability            | safe configured fallback, no crash |
| similar sequential enrollment frames   | only one retained                  |
| varied valid pose frames               | distinct buckets selected          |
| threshold configuration version change | result carries current version |
| frontal frame offered for LEFT step     | `POSE_NOT_LEFT`, step does not advance |
| valid left-profile frame for LEFT step  | accepted, advances to RIGHT step      |
| three identical frontal frames          | duplicate-yaw guard rejects           |
| yaw sign flipped on another device      | normalized adapter yields same bucket |
| malformed crop/NaN signal              | safe reject, no native crash       |

## Definition of done

The engine produces deterministic, testable quality outcomes, retains no image data, and sends only useful observations to expensive ML.
