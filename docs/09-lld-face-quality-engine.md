# LLD-09 — Face Quality Engine

**Status:** Implemented & Verified (Updated 2026-11-02 via Doc 20 & Doc 21)
**Requirements source:** SDD §§15–16, 22, 70–72, Doc 20 (WP4, WP5), Doc 21 (WP-A, WP-B, WP-F)
**Depends on:** LLD-08, Decision D-004

## Goal

Deterministically reject unusable face observations before liveness or embedding inference and choose high-quality frames. Quality evaluation must be contrast-normalized, resolution-independent, and pure Kotlin (zero `android.*` framework dependencies) for instant JVM testability.

## Architecture & Crop Metrics (Doc 21 WP-B)

Full-frame raw Laplacian variance is fundamentally flawed because it measures contrast $\times$ resolution rather than focus blur: halving the light quarters the variance, and switching to 720p drastically reduces pixel-step gradients.

Doc 21 WP-B introduces `CropMetrics` operating directly on the canonical 112×112 ARGB pixel array (`IntArray`):

```text
luma(p) = 0.299 R + 0.587 G + 0.114 B                      // Rec.601 float
ROI     = rows 20..99, cols 16..95                        // 80×80 central facial ROI

faceLuma   = mean(luma over ROI)                          // raw crop central brightness

noiseSigma = sqrt(pi/2) * mean(|r|) / 6                   // Immerkær noise standard deviation
             where r = conv(Y_roi, [[1,-2,1],[-2,4,-2],[1,-2,1]])

levels(crop):                                             // robust auto-levels
   lo = P2(luma ROI), hi = P98(luma ROI)                  // 256-bin histogram percentiles
   if (hi - lo < 8) return crop
   gain = min(200 / (hi - lo), 4.0)
   c' = clamp((c - lo) * gain + 28, 0, 255)

cropSharpness:  computed on levels(crop)
   Y = luma ROI; Y = gaussianBlur(Y, sigma=0.6)           // 5-tap separable kernel
   L = 4-neighbour Laplacian(Y)
   cropSharpness = std(L) / (std(Y) + 1e-3)               // contrast-normalized
```

## Contract

```kotlin
sealed interface QualityResult {
    data class Accepted(val score: Float, val poseBucket: PoseBucket, val configVersion: Int = 4) : QualityResult
    data class Rejected(val reason: QualityReason) : QualityResult
}

enum class QualityReason {
    NO_FACE,
    MULTIPLE_FACES,
    TOO_SMALL,
    OFF_CENTER,
    POSE,
    POSE_NOT_STRAIGHT,
    POSE_NOT_LEFT,
    POSE_NOT_RIGHT,
    POSE_NOT_DOWN,
    BLUR,
    DARK,
    OVEREXPOSED,
    EYES_UNCLEAR,
}
```

## FaceQualityConfig v4 (`quality_v4`)

Version 4 thresholds calibrated from empirical benchmarks on LFW and device simulations:

| Parameter | Value | Purpose |
|---|---|---|
| `minCropSharpness` | `0.09f` | Contrast-normalized sharpness floor (passes 100% of sharp genuine faces; rejects heavy defocus) |
| `minFaceLuma` | `12f` | Minimum central face luma (permits dim scenes handled by multi-frame fusion / levels) |
| `maxFaceLuma` | `235f` | Overexposure ceiling |
| `minInterOcularPx` | `28f` | Minimum pupil-to-pupil distance in raw pixels (resolution-independent; replaces `faceRatio`) |
| `straightMaxYawDegrees` | `12.0f` | Frontal yaw gate for enrollment step 1 |
| `profileMinYawDegrees` | `18.0f` | Minimum turn angle for profile enrollment |
| `profileMaxYawDegrees` | `40.0f` | Maximum turn angle for profile enrollment |
| `maxAttendanceYawDegrees` | `30.0f` | Natural yaw tolerance for live attendance |
| `maxRollDegrees` | `15.0f` | Head tilt limit |
| `minPitchDegrees` / `maxPitchDegrees` | `-25.0f` / `+20.0f` | Natural chest-height phone hold pitch acceptance |

## Evaluation Order & Priority (Doc 21 WP-F)

When evaluating a frame, checks execute in strict priority order:

1. **NO_FACE**: `faceCount == 0` or non-finite signals.
2. **MULTIPLE_FACES**: `faceCount > 1`.
3. **TOO_SMALL**: `interOcularPx < minInterOcularPx (28px)` (or fallback `faceRatio < 0.08` if inter-ocular distance unavailable).
4. **OFF_CENTER**: `centerX` or `centerY` outside `0.20..0.80`.
5. **POSE**: Extreme roll ($> 15^\circ$) or extreme pitch ($< -25^\circ$ or $> +20^\circ$).
6. **POSE WINDOW**: Evaluated against `ExpectedPose` (`STRAIGHT`, `LEFT`, `RIGHT`, `ANY`). Live attendance uses `ExpectedPose.ANY`.
7. **BLUR**:
   - If `cropSharpness >= 0f`: `cropSharpness < minCropSharpness (0.09f)`.
   - Fallback (legacy callers/tests): `blurVariance < minBlurVariance`.
8. **LIGHTING**:
   - If `faceLuma >= 0f`: `faceLuma < minFaceLuma (12f)` $\to$ `DARK`; `faceLuma > maxFaceLuma (235f)` $\to$ `OVEREXPOSED`.
   - Fallback: `brightness < minBrightness (40f)`.
9. **EYES_UNCLEAR**: Eye openness evaluated only when eye probabilities are provided by detector/mesh (`requireEyes = false` by default for YOLO).

## UX Blur Hysteresis & Feedback

- **String Rename:** "Too blurry" is renamed to `"Hold the phone still for a moment"`.
- **Blur Hysteresis:** Because sensor noise in dim light can transiently affect single-frame sharpness, blur guidance is displayed only when `BLUR` persists for $\ge 3$ consecutive frames.
- **Dark Lighting:** When `DARK` is detected, screen flash illuminates automatically without disruptive error text.

## Tests

| Test Case | Verification Target |
|---|---|
| `CropMetricsTest` (8 tests) | Validates Rec.601 luma, 80×80 ROI bounds, noise sigma, auto-levels histogram stretch, and crop sharpness against mathematical expectations. |
| `FaceQualityEngineTest` (23 tests) | Tests small face ratio with sufficient inter-ocular distance accepted; low crop sharpness rejected as blur; dark sharp crop accepted; pose steps (straight, left, right); malformed non-finite signals; config versioning. |

## Definition of Done

Evaluation is 100% pure Kotlin; quality decisions are independent of camera stream resolution; full-frame Laplacian variance never decides rejection; student guidance is prioritized and debounced.
