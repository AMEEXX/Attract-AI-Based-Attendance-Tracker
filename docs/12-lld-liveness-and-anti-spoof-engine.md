# LLD-12 — Liveness / Anti-Spoof Engine

**Status:** Draft — ready for review and freeze after Gate B  
**Requirements source:** SDD §§27–28, 70–73  
**Depends on:** LLD-08, LLD-09, Decision D-004

## Goal

Verify that the presented face is live before recognition/enrollment. Passive RGB liveness is attempted first; uncertain cases use an active challenge. This is a risk-reduction control, not an assertion of hardware depth-sensing security.

## Contract and flow

```kotlin
sealed interface LivenessResult {
  data class Passed(val configVersion: String) : LivenessResult
  data class NeedsActiveChallenge(val reason: SafeLivenessReason) : LivenessResult
  data class Failed(val reason: SafeLivenessReason) : LivenessResult
  data class Unavailable(val error: LivenessError) : LivenessResult
}
```

Passive adapter evaluates quality-approved frames against calibrated liveness gates:
- **Eye Openness Gate**: Both eyes must have open probability $\ge 0.25$.
- **Blur & Detail Gate**: Frame blur variance must be $\ge 15.0$ to reject static flat photos.
- **Ambient Lighting Gate**: Brightness must be $\ge 45.0$ for valid liveness evaluation.
- **Cross-Frame Motion Variance Gate**: Cross-frame eye openness variance $< 0.0001$ AND head yaw variance $< 0.001$ rejects static photo presentation attacks as `INSUFFICIENT_VARIANCE`.

### Presentation-Attack Detection (per-frame, applied to EVERY captured frame)

Each frame offered for capture (enrollment or recognition) passes these checks BEFORE acceptance. A failing frame is rejected with student-safe guidance and the capture must be retaken:

1. **Multi-Face Gate** (from LLD-09): `faceCount > 1` → reject (“Only one person at a time”). Catches group-photo and video-of-others attacks.
2. **Screen-Replay / Phone-Screen Detection** — detects a face displayed on another device's screen:
   - Moiré/interference pattern check: high-frequency spectral energy in the face crop (FFT or high-pass energy ratio) above calibrated threshold → `SCREEN_REPLAY_SUSPECTED`.
   - Specular highlight check: presence of saturated near-white blobs with sharp edges inside the face box (screen glare/reflection signature) → `SCREEN_REPLAY_SUSPECTED`.
   - Screen-color cast check: dominant hue distribution of the crop skewed toward screen-emission signatures (cool/blue-shifted skin tones, banding) → flagged as soft signal feeding the score.
3. **Printed-Photo Detection** — detects a physical photo held before the camera:
   - Uniform low-texture background ring around the face box (photo paper border) → soft signal.
   - Abnormally flat local contrast (low Laplacian variance in skin regions combined with hard print edges at the crop boundary) → `PRINT_PHOTO_SUSPECTED`.
4. **Blur Gate** (LLD-09): `blurVariance < config.minBlurVariance` → “Image blurry” — also catches re-photographed/re-displayed imagery that lost detail.
5. All thresholds are calibration parameters versioned with `FaceQualityConfig`; reasons are collapsed to the safe message “Verification failed. Please try again.” — never exposing attack classification to students.

Active fallback randomly selects one short challenge supported by device/test evidence: blink, turn left, or turn right. It creates a nonce, start/deadline, required landmark/pose sequence, and attempt ID. The UI shows one large instruction; observation is evaluated only for that nonce/attempt. Do not persist challenge frames, nonce history, or detailed spoof reasons. One retry is allowed; repeated failure goes to teacher assistance.

## Model and failure policy

The initial candidate remains Silent-Face-Anti-Spoofing only if Android/TFLite export, license, latency, and Gate B attack evidence pass. The adapter interface permits replacement without changing session/enrollment logic. If passive is unavailable or fails Gate B, active challenge is the MVP control; it does not silently pass liveness.

Failure message is “Verification failed. Please try again.” No student sees confidence, model name, or attack classification. Unavailable model/camera input yields teacher assistance, not PRESENT.

## Build and tests

1. Build fake passive/active engines and state tests.
2. Implement sequence buffering with strict size/time cap and zero persistence.
3. Integrate selected model only after compatibility/licensing review.
4. Execute Gate B on print, phone, tablet, and practical replay attacks.

| Scenario | Expected result |
|---|---|
| passive confident live | Passed |
| passive ambiguous | one active challenge |
| passive reject | retry/assist, never recognition |
| active wrong/expired/stale nonce | failed |
| model missing/corrupt | Unavailable and teacher path |
| print/phone/tablet/replay corpus | results reported as APCER/BPCER evidence |
| repeated attempts | bounded retries, buffers cleared |
| rotated/low-light quality-rejected input | liveness not invoked |

## Definition of done

All liveness outcomes are bounded and fail closed. Gate B records attack, latency, and usability evidence before any production threshold/model is frozen.
