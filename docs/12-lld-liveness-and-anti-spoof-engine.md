# LLD-12 — Liveness & Anti-Spoof Engine

**Status:** Implemented & Verified (Updated 2026-11-02 via Doc 20 & Doc 21)
**Requirements source:** SDD §§27–28, 70–73, Doc 20 (WP1, WP6, WP7), Doc 21 (WP-D)
**Depends on:** LLD-08, LLD-09, Decision D-004

## Goal

Verify that the presented face is a live, physically present human being rather than a presentation attack (printed photo, digital screen replay, video loop, or synthetic feed). Protection is achieved by stacking multiple independent signals so an attacker must defeat all layers simultaneously.

## Multi-Layered Anti-Spoof Architecture (Doc 20 & Doc 21)

```text
Camera Stream (1280×720 YUV)
   │
   ├── Un-averaged Wide Crop (160×160)
   │     ↓
   │   PresentationAttackAnalyzer (Doc 21 WP-D)
   │     ├─ Noise-compensated moiré energy: hfeCompensated = max(0, hfe*255 - 3.6*noiseSigma) / 255
   │     ├─ Specular highlight reflection ratio: maxSpecularHighlightRatio <= 0.25
   │     └─ Central flat texture std dev: centerTextureStdDev >= 3.0
   │     ↓
   │   PresentationAttackDetector (2-of-3 temporal confirmation: never rejects on 1 frame alone)
   │
   ├── Canonical Aligned Crop (112×112)
   │     ↓
   │   PadCnnEngine (MiniFASNet x2 TFLite) → padScore (live >= 0.80, spoof < 0.25)
   │
   ├── MediaPipe FaceMeshEngine (478 landmarks & blendshapes)
   │     ├─ ParallaxDepthTest: homography residual over yaw movement (distinguishes 3D face from 2D plane)
   │     └─ BlinkDetector: natural eyeBlink blendshape transitions (open -> closed -> open)
   │
   ├── Photometric Screen Flash (optional for dim / medium risk)
   │     ↓
   │   FlashLivenessTest: Pearson correlation of screen pulses + differential nose/cheek shading
   │
   ▼
LivenessOrchestrator & RiskFusion
   ├─ LOW    → Passed
   ├─ MEDIUM → Trigger targeted micro-challenge (smile, turn head, blink)
   └─ HIGH   → Rejected (student-safe message; fails closed)
```

## Contract

```kotlin
enum class LivenessRisk { LOW, MEDIUM, HIGH }

enum class LivenessReason {
    PASSED,
    EYES_CLOSED_OR_STATIC,
    PASSIVE_SPOOF_SUSPECTED,
    SCREEN_REPLAY_SUSPECTED,
    PRINT_PHOTO_SUSPECTED,
    INSUFFICIENT_VARIANCE,
}

sealed interface LivenessResult {
    data object Passed : LivenessResult
    data class Rejected(val reason: LivenessReason, val message: String) : LivenessResult
}
```

## Doc 21 WP-D: Noise-Compensated Pixel Gates

In dim rooms, camera sensors raise ISO and ISP sharpening amplifies high-frequency noise. Raw high-frequency energy previously exceeded 0.18 on 100% of genuine faces, causing false "Screen detected" rejects.

Doc 21 WP-D fixes this:

1. **Noise Compensation:**
   Computes Immerkær noise standard deviation $\sigma_{\text{noise}}$ on the 96×96 gray downsample and compensates high-frequency energy:
   $$\text{hfeCompensated} = \frac{\max(0, \text{hfe} \times 255 - 3.6 \times \sigma_{\text{noise}})}{255}$$
2. **Calibrated Thresholds (`LivenessPolicy` v4):**
   - `maxHighFrequencyEnergy`: `0.22f` (applied strictly to `hfeCompensated`).
   - `maxSpecularHighlightRatio`: `0.25f` (accommodates oily skin/forehead glare while blocking screens).
   - `printFlatTextureStdDev`: `3.0f` (unconditional flat-texture detection without blur coupling).
3. **Quality/Spoof Separation:**
   Deleted legacy `blurVariance < 15` and `brightness < 45` checks from `LivenessEngine`. Dim light and blur are image quality concerns managed by `FaceQualityEngine`, NEVER presentation attacks.
4. **PAD Crop Conditioning:**
   Pixel PAD runs strictly on the **un-levelled, un-averaged** newest crop. Multi-frame averaging or histogram equalization must never be applied to PAD inputs, as averaging suppresses screen moiré patterns.
5. **2-of-3 Temporal Confirmation:**
   A single-frame pixel PAD trip must NEVER reject on its own. `LivenessOrchestrator` maintains a ring buffer of the last 3 frame verdicts and requires the same rejection reason on $\ge 2$ of the 3 frames before issuing a veto.

## Multi-Factor Risk Fusion (`RiskFusion`)

`RiskFusion.decide(evidence, policy)` combines independent signals:

- **Rule 1 (Injection):** Camera timing violation or synthetic zero-jitter $\to$ `HIGH`.
- **Rule 2 (Pixel Veto):** Confirmed $\ge$ 2-of-3 legacy pixel attack $\to$ `HIGH`.
- **Rule 3 (CNN PAD):** MiniFASNet score $< 0.25$ (or $< 0.12$ in very dim light) $\to$ `HIGH`.
- **Rule 4 (Photometric):** Flash reflection correlation $< 0.35$ $\to$ `HIGH`.
- **Rule 5 (Challenge):** Timed-out or failed micro-challenge $\to$ `HIGH`.
- **Rule 6 (Planar 2D):** Homography residual $< 0.020$ with angular movement (conclusive flat plane) $\to$ `HIGH`.
- **Rule 7 (Live Corroborated):** Strong CNN PAD ($\ge 0.80$) corroborated by 3D depth, natural blink, or flash reflection $\to$ `LOW`.
- **Rule 8 (Challenge Passed):** Completed micro-challenge with supporting cues $\to$ `LOW`.
- **Rule 9 (Dark Room Path):** Verified flash correlation $\ge 0.75$ and depth $\ge 0.50$ in total darkness $\to$ `LOW`.
- **Rule 10 (Inconclusive):** Otherwise $\to$ `MEDIUM` (requests natural active verification).

## Tests

| Test Suite | Coverage Target |
|---|---|
| `PresentationAttackDetectorTest` (8 tests) | Tests noise-compensated HFE passing genuine dim faces, screen replay detection, specular glare rejection, flat print photo rejection, non-finite signal safety, and legacy integration. |
| `RiskFusionTest` (10 tests) | Validates all 10 risk fusion rules (injection, pixel veto, CNN threshold, planar homography, challenge outcomes, dark room flash path). |
| `ParallaxDepthTestTest` (4 tests) | Proves 3D human faces yield high residual depth scores while flat planar targets yield low scores. |
| `BlinkDetectorTest` (5 tests) | Validates full open $\to$ closed $\to$ open blink cycle; rejects permanently closed eyes and insufficient frames. |
| `FlashLivenessTestTest` (6 tests) | Validates Pearson correlation and differential facial shading against screen replay attacks. |

## Definition of Done

Liveness defense stacks learned CNN texture, 3D motion parallax, and photometric cues; genuine users in dim rooms are never falsely flagged as screens; all decisions fail closed safely with student-safe guidance.
