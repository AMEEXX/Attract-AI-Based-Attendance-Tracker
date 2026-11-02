# 20 — "Face-ID-grade" Recognition, Anti-Spoofing & Low-Light Plan (RGB-only)

**Date:** 2026-11-02
**Baseline:** `origin/main` (profile `arcface512_bgr_noseyaw_v5`, calibration `calib_20261004_bgr_512d`)
**Status:** Approved for implementation. Builds on doc 18 (do not undo any doc-18 fix).
**Audience:** Any engineer or small code model. Every work package (WP) lists the exact files, the code to write, the tests to add and a "Done when" gate. Do the WPs **in order**. Do not skip the tests.

---

## 0. Read this first

### 0.1 What the user sees today, and why (from the code)

| Symptom | Exact cause in code | Fixed in |
|---|---|---|
| "Liveness check failed" at some angles or in dim rooms | `LivenessEngine.check()` rejects when `blurVariance < 15` or `brightness < 45` and labels it `PASSIVE_SPOOF_SUSPECTED`. These are **quality** problems, not spoof evidence. Dim light → low Laplacian variance → "spoof". | WP1 |
| Glare or a bright forehead triggers "Screen glare detected" | `PresentationAttackDetector` rejects when `specularHighlightRatio > 0.10`. It runs on the **112×112 aligned crop** (`AttendanceScreen.kt` passes `bundle.alignedCrop`), where a lit forehead easily exceeds 10 % of pixels above 235. The thresholds were never calibrated. | WP1, WP6 |
| Must hold the phone "just right" | Frame 1 needs `abs(pitch) ≤ 15` (`maxPoseDegrees`) and `abs(yaw) ≤ 12` (`straightMaxYawDegrees`). Pitch comes from `K_PITCH = 45 × ratio`, which is **not calibrated** (doc 18 calibrated yaw only). A phone held at chest height gives a real pitch of 15–25°, so the frame is rejected. | WP4, WP5 |
| Works only in good light | (a) No camera exposure control: CameraX meters the whole scene, not the face. (b) The YOLO input is the 640×480 frame **stretched** to 640×640. (c) Single-frame decision, so sensor noise goes straight into the embedding. (d) No light source (no screen flash). (e) A hard `minBrightness = 40` gate. | WP2, WP3 |
| Anti-spoofing is weak | Only 3 hand-tuned pixel statistics. No learned presentation-attack-detection (PAD) model, no blink, no 3D/parallax cue, no challenge. A good print or phone replay with mild blur passes. | WP6, WP7 |

### 0.2 Honest limits (put this in the release notes)

Face ID uses a **dot projector + IR camera** (true depth) and an **IR flood illuminator** (works in total darkness). An RGB phone camera cannot measure depth directly. We get **as close as RGB allows** by stacking independent signals, so that an attacker would have to defeat all of them at once:

1. A learned passive PAD CNN (texture, moiré, print artefacts).
2. **Motion-parallax 3D test.** A flat photo or screen moves like a plane (it fits a homography). A real face does not.
3. **Screen-flash photometric test.** We light the face with random colours from the screen. A real 3D face reflects them diffusely, with nose/cheek shading. A screen or print does not.
4. Blink and randomised micro-challenge, verified with MediaPipe 478-point landmarks.
5. Device/camera-injection integrity (Play Integrity, virtual-camera checks).

No RGB system is "unbeatable". A high-grade silicone 3D mask or a camera-injection attack on a rooted device are out of scope. Measure against the targets in §0.4. **Never claim 100 %.**

### 0.3 Final pipeline (target architecture)

```
CameraX (1280x720 YUV, face-metered AE, low-light boost, 10–30 fps)
   │
   ▼
FrameAnalyzer (every frame, ~66 ms budget)
   ├─ LowLightEnhancer (only if face luma < 70)  ───────────► WP3
   ├─ YoloFaceDetector (letterbox, tracking)      ───────────► WP4
   ├─ FaceMeshEngine (MediaPipe FaceLandmarker: 478 pts, blendshapes, 4x4 pose) ► WP4
   ├─ FaceQualityEngine v4 (degree pose, SNR-based light)    ► WP5
   └─ TrackBuffer (last 8 frames of the SAME face track)
          │
          ▼  (auto-capture, no button needed)                ► WP8
   LivenessOrchestrator  ─────────────────────────────────────► WP6/WP7
     ├─ PadCnnEngine (MiniFASNet x2)          → padScore
     ├─ ParallaxDepthTest (homography residual) → depthScore
     ├─ BlinkDetector (blendshapes)             → blinkSeen
     ├─ FlashLivenessTest (only when dim or risk=MEDIUM) → flashScore
     └─ RiskFusion → LOW / MEDIUM (ask 1 challenge) / HIGH (reject)
          │ LOW
          ▼
   Recognition                                                ► WP5
     ├─ TemporalDenoise: average 3–4 aligned 112x112 crops
     ├─ ArcFace embedding (+ optional flip-TTA, gated)
     ├─ Track-fused embedding (quality-weighted mean of top-3)
     ├─ Pose-aware gallery (enrolled + auto-updated templates)
     └─ RecognitionDecisionEngine (existing thresholds, recalibrated)
```

### 0.4 Acceptance targets (measure with WP9; release only if all pass)

| Metric | Target | How measured |
|---|---|---|
| Genuine accept, normal light (≥150 lux), any natural phone hold | ≥ 99 % within 2 s, ≤ 1 retry | Device protocol §9.3 |
| Genuine accept, dim (10–30 lux, e.g. one tube light far away) | ≥ 95 % within 3 s | §9.3 |
| Genuine accept, very dim (1–10 lux) with screen flash | ≥ 85 % within 4 s | §9.3 |
| Pose tolerance | yaw ±35°, pitch −25°…+20° accepted | §9.3 |
| Wrong student marked present | 0 in ≥ 10,000 cross-identity trials | `tools/biometric_eval` |
| Stranger marked present | ≤ 0.5 % | `tools/biometric_eval` |
| APCER print (A4 colour, matte & glossy, cut-out eyes) | ≤ 1 % | §9.4 |
| APCER screen replay (phone, tablet, laptop; photo & video) | ≤ 2 % | §9.4 |
| BPCER (live user wrongly called spoof) | ≤ 2 % | §9.4 |
| Median time from face-in-frame to decision | ≤ 1.2 s (mid-range phone, e.g. SD 695 / Dimensity 7050) | Logcat timings |

---

## 1. Ground rules for the implementer

1. **Branch:** `genspark_ai_developer`. One commit per WP minimum. Conventional commits (`feat(liveness): ...`).
2. **Keep domain logic pure Kotlin** in `domain/face/` (JVM-testable, no `android.*` imports). Android adapters go in `feature/attendance/` or a new `platform/face/` package. This follows the existing pattern (`PresentationAttackAnalyzer` = adapter, `PresentationAttackDetector` = pure).
3. **Every threshold** goes in one place: the new `LivenessPolicy` and `FaceQualityConfig` v4 (WP1/WP5), referenced from `BiometricModelProfile`. No magic numbers in UI code.
4. **Feature flags** live in the new `BiometricFeatureFlags` (WP0). Every new subsystem can be turned off without a rebuild of the logic, so a regression can be bisected.
5. **Bump** `BiometricModelProfile.profileId` **only** when embeddings change (WP5.4 TTA, or WP10 model swap). Liveness/camera changes do **not** invalidate templates.
6. **Logging:** use the existing tags `ATTRACT_RECOGNITION`, `ATTRACT_FACE`, and add `ATTRACT_LIVENESS`. Never log embeddings or images.
7. **Privacy:** raw frames never leave the device and never go to disk, except in the debug-only data-collection build (WP0.3). That build is opt-in and stores data in app-private storage.
8. Run `./gradlew :app:testDebugUnitTest` after every WP. All existing tests must stay green. If a test encodes old behaviour that this plan intentionally changes (e.g. `LivenessEngineTest` expecting a brightness reject), **update the test and say why in the commit message**.

---

## WP0 — Flags, telemetry and data collection (foundation)

### WP0.1 `BiometricFeatureFlags`
Create `app/src/main/java/com/attract/attendance/domain/face/BiometricFeatureFlags.kt`:

```kotlin
package com.attract.attendance.domain.face

/** Single switchboard for the doc-20 subsystems. Defaults = production intent. */
data class BiometricFeatureFlags(
    val lowLightEnhancement: Boolean = true,      // WP3
    val temporalDenoise: Boolean = true,          // WP3.3
    val faceMeteredExposure: Boolean = true,      // WP2
    val screenFlash: Boolean = true,              // WP2.4 / WP7.3
    val faceMesh: Boolean = true,                 // WP4 (MediaPipe)
    val padCnn: Boolean = true,                   // WP6
    val parallaxDepth: Boolean = true,            // WP7.1
    val flashLiveness: Boolean = true,            // WP7.3
    val activeChallenge: Boolean = true,          // WP7.4
    val adaptiveTemplates: Boolean = true,        // WP5.3
    val flipTta: Boolean = false,                 // WP5.4 — OFF until calibrated
    val autoCapture: Boolean = true,              // WP8
) {
    companion object { val CURRENT = BiometricFeatureFlags() }
}
```

Add `val flags: BiometricFeatureFlags = BiometricFeatureFlags.CURRENT` to `BiometricModelProfile`.

### WP0.2 Per-attempt telemetry record (debug + Crashlytics breadcrumbs)
Create `domain/face/AttemptTrace.kt`. It is a data class that collects, for one recognition attempt: `faceLuma`, `enhancementApplied`, `yawDeg`, `pitchDeg`, `rollDeg`, `padScore`, `depthScore`, `blinkSeen`, `flashScore`, `risk`, `top1`, `top2`, `framesUsed`, `elapsedMs`, `outcome`. Emit `toString()` once per attempt to `ATTRACT_RECOGNITION`. This is how on-device calibration (WP9) gets its numbers. Add a unit test that `toString()` contains no float array.

### WP0.3 Debug-only data-collection mode (needed for calibration)
- Add `buildConfigField("boolean", "BIOMETRIC_COLLECT", "false")` to `app/build.gradle.kts` `defaultConfig`, and `true` only in a new `collect` build type (`initWith(getByName("debug"))`).
- When `BuildConfig.BIOMETRIC_COLLECT` is true, the Settings screen shows **"Biometric data collection"**. It records labelled sessions: label ∈ {LIVE, PRINT, PRINT_CUTOUT, PHONE_PHOTO, PHONE_VIDEO, TABLET_VIDEO, LAPTOP_VIDEO}, plus a lux reading from `Sensor.TYPE_LIGHT` if present.
- Per session, save to `filesDir/collect/<timestamp>_<label>/`: 8 consecutive 1280×720 JPEGs at quality 92, `meta.json` (label, lux, device model, AttemptTrace of every frame).
- A Settings button exports a zip via the share sheet. **Never** compiled into release: guard every call with `if (BuildConfig.BIOMETRIC_COLLECT)` so R8 strips it.

**Done when:** flags compile, `AttemptTrace` is logged for every attempt, and the `collect` build can save and export a session.

---

## WP1 — Separate *quality* from *liveness* (quick win, ships first)

This WP alone removes most of the false "Liveness check failed" messages.

### WP1.1 Strip non-spoof gates out of `LivenessEngine`
Edit `domain/face/LivenessEngine.kt`:
- **Delete** step 3 entirely (the `blurVariance < 15` and `brightness < 45` rejects). Darkness and blur are handled by `FaceQualityEngine` (`QualityReason.DARK` / `BLUR`), which already shows the correct "move to light / hold still" guidance.
- Keep the eye-openness gate. Keep the static-replay check.
- `LivenessEngine.check` becomes a thin compatibility wrapper that calls `PresentationAttackDetector.analyze` with the new policy (WP1.2). In WP7 it gets replaced by `LivenessOrchestrator`; keep the old signature until then so `AttendanceScreen.kt` compiles.

### WP1.2 Introduce `LivenessPolicy` and recalibrate the pixel gates
Create `domain/face/LivenessPolicy.kt`:

```kotlin
data class LivenessPolicy(
    val id: String = "liveness_v4",
    // Legacy pixel gates — now only veto STRONG signatures, measured on the WIDE crop (WP1.3)
    val maxHighFrequencyEnergy: Float = 0.30f,
    val maxSpecularHighlightRatio: Float = 0.25f,
    val printFlatTextureStdDev: Float = 4.0f,
    val printMaxBlurVariance: Float = 120f,
    // WP6 PAD CNN
    val padLiveAccept: Float = 0.80f,     // >= : LOW risk contribution
    val padSpoofReject: Float = 0.25f,    // <  : HIGH risk (hard reject)
    // WP7 3D / flash / blink
    val parallaxMinYawDeltaDeg: Float = 4f,
    val parallaxLiveResidual: Float = 0.020f,   // normalised by inter-ocular distance
    val flashMinCorrelation: Float = 0.55f,
    val flashMinShadingRatio: Float = 1.06f,
    val blinkClosedScore: Float = 0.55f,        // MediaPipe eyeBlink blendshape
    val blinkOpenScore: Float = 0.30f,
    val maxChallengeMs: Long = 4000L,
)
```

Change `PresentationAttackDetector.Thresholds` defaults to read from `LivenessPolicy()`. The **values above are starting points**. WP9 replaces them with measured values. Add `livenessPolicy: LivenessPolicy = LivenessPolicy()` to `BiometricModelProfile` and change `livenessPolicyId` to `"liveness_v4"`.

### WP1.3 Run pixel statistics on the right crop
In `AttendanceScreen.kt` (around line 1128), `PresentationAttackAnalyzer.analyze(frameBitmap)` currently gets the **aligned 112×112** crop. Change `FrameBundle` to also carry `wideCrop: Bitmap?`. That is the face box expanded ×2.7 around its centre, clamped to the frame, scaled to 160×160 (the same crop WP6 uses for the PAD CNN). Build it in `CameraPreview.kt` next to `FaceAligner.align(...)`, and recycle it in the same `finally` path as the other bitmaps. Pass `bundle.wideCrop` to the analyzer. A wide crop includes the screen bezel or paper edge, which is where replay and print artefacts live. A tight crop mostly shows skin, and that is what produced the false glare rejects.

### WP1.4 UI wording
In `AttendanceScreen.kt` show **"Liveness check failed"** only for reasons `SCREEN_REPLAY_SUSPECTED`, `PRINT_PHOTO_SUSPECTED`, `INSUFFICIENT_VARIANCE` and the new PAD reasons. Quality rejects keep their existing `AttendanceStrings.QUALITY_*` titles. In `submitRecognition()` (line ~1288), the liveness re-check on `lastQualitySignals` must call the same function as capture. Otherwise a frame that passed at capture can fail at submit.

### WP1.5 Tests
- `LivenessEngineTest`: replace "dark frame → PASSIVE_SPOOF_SUSPECTED" expectations with "dark frame → Passed by liveness (quality engine owns it)". Add `brightness=30, blur=10 → Passed`.
- `PresentationAttackDetectorTest`: specular 0.15 → Passed; 0.30 → SCREEN_REPLAY_SUSPECTED.

**Done when:** unit tests pass, and on a phone a dim room gives "Too dark" guidance, never "Liveness check failed".

---

## WP2 — Camera pipeline for low light (CameraX 1.4.2, no version bump)

File: `feature/attendance/CameraPreview.kt`. Add `import androidx.camera.camera2.interop.Camera2Interop`, `ExperimentalCamera2Interop`, `android.hardware.camera2.CaptureRequest`, `android.util.Range`.

### WP2.1 Resolution
Change the `ResolutionStrategy` target from `Size(640, 480)` to `Size(1280, 720)`. Use the same value for the `Preview`. Reason: the face crop for ArcFace and the PAD wide-crop get about 2× more real pixels, and binning modes kick in on many sensors. The detector still runs at 640 (WP4.1 letterbox). Keep the existing 150 ms throttle for now. WP8 makes capture event-driven.

Set the output format explicitly: `.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)`. WP3.1 reads the Y plane directly for fast luma, so no bitmap conversion is needed just to measure brightness.

### WP2.2 Let auto-exposure use longer exposures
Apply to **both** the `Preview.Builder` and the `ImageAnalysis.Builder`:

```kotlin
@OptIn(ExperimentalCamera2Interop::class)
private fun <T> Camera2Interop.Extender<T>.lowLightFriendly() {
    // Allow AE to drop to 10 fps -> up to 100 ms exposure. Default is often [30,30].
    setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(10, 30))
    setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
    setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
}
// usage: Camera2Interop.Extender(imageAnalysisBuilder).lowLightFriendly()
```

**Pick a supported range.** After binding, read `Camera2CameraInfo.from(camera.cameraInfo).getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)`. Choose the range with the **lowest lower bound ≥ 7** and upper bound ≥ 24. If none is found, skip the option. Unsupported ranges make some HALs fail the session. Put the selection logic in a pure function `chooseAeFpsRange(ranges: List<IntRange>): IntRange?` in `domain/face/CameraTuning.kt` and unit test it.

**Low-light boost (Android 15+):** if `CONTROL_AE_AVAILABLE_MODES` contains `CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY` (constant 6, API 35), set `CONTROL_AE_MODE` to it. Guard with `Build.VERSION.SDK_INT >= 35`. compileSdk is already 35.

### WP2.3 Face-metered exposure (the biggest low-light win)
Problem: in a dark classroom with a bright window or tube light behind the student, CameraX meters the whole frame, so the face is underexposed.

Add a callback `onFaceBox(box: RectF normalised 0..1)` from the analyzer. In the composable, keep a `Camera` reference returned by `bindToLifecycle`. At most **once every 1500 ms**, while a face is tracked:

```kotlin
val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)   // normalised analysis coords
val point = factory.createPoint(box.centerX(), box.centerY(), max(box.width(), box.height()))
val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AF)
    .setAutoCancelDuration(3, TimeUnit.SECONDS)
    .build()
camera.cameraControl.startFocusAndMetering(action)
```

For a front camera the analysis image is already rotated by our `toBitmap`, but metering points are in **sensor-oriented analysis coordinates**. Create the factory with `SurfaceOrientedMeteringPointFactory(w, h, imageAnalysis)`, which maps correctly. Pass the box in un-rotated analysis pixels, i.e. convert back before calling.

**Exposure compensation loop** (pure logic in `CameraTuning.kt`, unit tested):
```kotlin
/** Returns the new EV index. Target face luma 110±20. Step at most 1 index per call. */
fun nextExposureIndex(faceLuma: Float, current: Int, range: IntRange): Int = when {
    faceLuma < 90f && current < range.last -> current + 1
    faceLuma > 150f && current > range.first -> current - 1
    else -> current
}
```
Call `camera.cameraControl.setExposureCompensationIndex(idx)` at most every 500 ms, using `camera.cameraInfo.exposureState.exposureCompensationRange`. Reset to 0 in `onDispose`.

### WP2.4 Screen flash ("Retina-flash" equivalent) for very low light
Front cameras have no LED. We use the screen as the light source.

- New composable `ScreenFlashOverlay(active: Boolean, color: Color)` in `ui/components/biometric/`. When active, draw a full-screen fill of `color` with a transparent **oval cut-out** where the face preview is (the existing `FaceFrameOverlay` oval). Set the window brightness to max:
  ```kotlin
  val window = (LocalContext.current as Activity).window
  DisposableEffect(active) {
      val lp = window.attributes; val old = lp.screenBrightness
      if (active) { lp.screenBrightness = 1f; window.attributes = lp }
      onDispose { lp.screenBrightness = old; window.attributes = lp }
  }
  ```
- **Trigger rule** (pure, in `CameraTuning.kt`): `shouldUseScreenFlash(faceLuma, evIndex, evMax)` returns true when `faceLuma < 60` **and** EV compensation is already at max (or `faceLuma < 35` regardless). Hysteresis: turn off only when `faceLuma > 85` for 1 s.
- Default colour: warm white `Color(0xFFFFF4E5)`. WP7.3 reuses this overlay with random colours for the flash-liveness test.
- Keep the flash on for **at most 6 s per attempt** (eye comfort, battery).

### WP2.5 Tests
`CameraTuningTest`: fps-range selection (e.g. `[[15,15],[7,30],[30,30]] → 7..30`; `[[30,30]] → null`), the EV stepping table, and flash hysteresis.

**Done when:** in a room lit only by a phone torch pointing at the ceiling, preview face luma is visibly brighter than before, and the flash overlay switches on automatically when it is very dark.

---

## WP3 — Low-light image enhancement and temporal denoising

### WP3.1 Fast face-ROI luma from the Y plane
In `CameraPreview.kt`, replace `computeBitmapBrightness` (per-pixel `getPixel`, slow) with a Y-plane reader. Sample the face box on a stride grid of ~32×32 samples and return the mean Y (0..255). Also return a **noise estimate**: the median absolute value of a 3×3 Laplacian on the same samples × 1.4826 (MAD-sigma). Add `noiseSigma: Float` to `FaceQualitySignals` (default `0f` for back-compat).

### WP3.2 `LowLightEnhancer` (pure Kotlin, operates on `IntArray` ARGB)
Create `domain/face/LowLightEnhancer.kt`. Apply it to two things:
- **(a) The detector input** when the *whole-frame* mean luma < 60. Do this before letterboxing in `YoloFaceDetector.detect`, using a gamma-only LUT, which is cheap.
- **(b) The 112×112 aligned crop** before ArcFace and the PAD CNN, when face luma < 70. Use gamma + CLAHE.

```kotlin
object LowLightEnhancer {
    /** Gamma that maps current mean luma to ~0.45*255. Clamped so we never over-brighten noise. */
    fun gammaFor(meanLuma: Float): Float {
        val m = (meanLuma / 255f).coerceIn(0.02f, 0.95f)
        return (ln(0.45) / ln(m.toDouble())).toFloat().coerceIn(0.35f, 1.0f)
    }

    fun gammaLut(gamma: Float): IntArray = IntArray(256) { i ->
        (255.0 * (i / 255.0).pow(gamma.toDouble())).roundToInt().coerceIn(0, 255)
    }

    /**
     * Luma-only CLAHE: convert to Y, equalise Y with tiles (default 4x4 for 112px), clip=2.0,
     * bilinear tile interpolation, then scale R,G,B by (Ynew+1)/(Yold+1) to keep hue.
     */
    fun clahe(argb: IntArray, w: Int, h: Int, tiles: Int = 4, clip: Float = 2.0f): IntArray { /* see spec */ }

    fun enhance(argb: IntArray, w: Int, h: Int, meanLuma: Float): IntArray {
        val lut = gammaLut(gammaFor(meanLuma))
        val g = IntArray(argb.size) { i ->
            val p = argb[i]
            (0xFF shl 24) or (lut[(p shr 16) and 255] shl 16) or (lut[(p shr 8) and 255] shl 8) or lut[p and 255]
        }
        return clahe(g, w, h)
    }
}
```

**CLAHE spec**, so it can be implemented without OpenCV:
1. `Y = (77R + 150G + 29B) >> 8`.
2. Split the image into `tiles × tiles` regions. For each region, build a 256-bin histogram. `limit = max(1, clip * regionPixels / 256)`. Clip every bin at `limit`, sum the excess, and add `excess / 256` to every bin (spread the remainder from bin 0 upward). Build the CDF and the map `lut_t[v] = round(255 * cdf[v] / regionPixels)`.
3. For each pixel, find the 4 surrounding tile centres and bilinearly interpolate the 4 LUT outputs. At the borders, clamp to the nearest tile.
4. `ratio = (Ynew + 1f) / (Yold + 1f)`; `R' = clamp(R * ratio)`, and the same for G and B.

**Important (calibration):** ArcFace was trained on normal-light faces. Enhancement **must** be validated by WP9 `exp5_lowlight.py`. Keep it only if genuine scores rise in dark conditions **and** impostor scores do not rise. If CLAHE raises impostor scores, keep gamma only for ArcFace and use CLAHE only for the detector and PAD.

### WP3.3 Temporal denoising of aligned crops (free registration)
Aligned 112×112 crops of the **same track** are already registered by the 5-point similarity transform, so averaging them is equivalent to multi-frame noise reduction (√N SNR gain, the same idea as Night Sight).

Create `domain/face/TemporalDenoiser.kt` (pure; works on `IntArray` 112×112):
- Input: the last N ≤ 4 aligned crops of the same track, plus their landmark-alignment MSE.
- Reject a crop from the stack if its normalised cross-correlation (NCC) with the newest crop is < 0.92. That means motion or a blink, so ghosting would occur.
- Output: the per-pixel mean of the accepted crops. If fewer than 2 are accepted, return the newest crop unchanged.
- Use it only when `noiseSigma > 6` or face luma < 70 (bright frames don't need it, and it saves time).

Wire it in: `CameraPreview` keeps a ring buffer of the last 4 `alignedCrop` pixel arrays per `trackId` (WP4.3). The **embedding** in `AttendanceScreen.captureClick()` / WP8 auto-capture is computed on the denoised crop.

### WP3.4 Quality gate change
In `FaceQualityConfig` v4 (WP5.1): replace the hard `minBrightness = 40` with:
- reject `DARK` only if **post-enhancement** face luma < 55 **or** `noiseSigma > 18` **after** denoise;
- `blur` gate: compute blur on the **denoised, enhanced** crop. In low light, `minBlurVariance` scales down: `effectiveMinBlur = minBlurVariance * clamp(faceLuma/110, 0.5, 1.0)`.

### WP3.5 Tests
`LowLightEnhancerTest`: `gammaFor(128) ≈ 1.0` (±0.05) and `gammaFor(20) == 0.35`; the LUT is monotonic; CLAHE on a constant image returns the same constant (±1); CLAHE output mean on a dark gradient > input mean. `TemporalDenoiserTest`: averaging 4 copies of (image + independent ±20 noise) lowers the std-dev of the noise by ≥ 40 %; a shifted crop (NCC < 0.92) is excluded.

**Done when:** tests pass and `exp5_lowlight.py` (WP9) shows the improvement numbers recorded in `tools/biometric_eval/results/exp5_lowlight.json`.

---

## WP4 — Detection, 3D landmarks and face tracking

### WP4.1 Letterbox the YOLO input (stop stretching)
`YoloFaceDetector.detect` draws the 640×480 (soon 1280×720) frame into 640×640 with `dstRect` = full square. This stretches faces vertically, which hurts landmark accuracy at off-angles. Doc-18 EXP-1b compared stretch vs letterbox. Re-run it with the WP2 resolution and adopt letterbox unless recall drops.

Implementation:
1. `scale = 640f / max(frameW, frameH)`, `newW = round(frameW*scale)`, `newH = round(frameH*scale)`, `padX = (640-newW)/2`, `padY = (640-newH)/2`.
2. Fill the canvas with gray `0xFF727272` (114/255, the YOLOv8 convention), then draw the frame into `RectF(padX, padY, padX+newW, padY+newH)`.
3. In `parseDetections`, un-map every box and landmark coordinate: `x_frame = (x_640 - padX) / scale`, `y_frame = (y_640 - padY) / scale`. Currently the code multiplies by `frameW/640` and `frameH/640`. Replace **all** of those.
4. Add a pure helper `LetterboxMapping(scale, padX, padY)` with `toFrame(x, y)` in `domain/face/` and unit test round-trips.
5. The ArcFace aligned crop changes slightly (better landmarks), so run `exp2b` again to confirm genuine/impostor distributions moved < 0.01. If they moved more, bump `calibrationId` (not `profileId`).

### WP4.2 Low-light detection recall
- When whole-frame luma < 60, run the gamma LUT (WP3.2a) on the 640×640 canvas pixels before writing the input buffer.
- Make `CONFIDENCE_THRESHOLD` adaptive: `0.50` normally; `0.35` when (frame luma < 60) **and** there is an active track whose last box has IoU ≥ 0.3 with the candidate. A new face appearing from nowhere still needs 0.50.

### WP4.3 IoU face tracker → stable `trackId`
Create `domain/face/FaceTracker.kt` (pure):
- Keeps `activeTrack: Track?` with `id`, `lastBox`, `lastSeenNanos`, `hits`.
- `update(boxes, nowNanos)`: pick the detection with the highest IoU to `lastBox`. If IoU ≥ 0.3, keep the same id. Otherwise, or if more than 400 ms have passed since last seen, create a new id (incrementing Int).
- `FrameBundle.trackId` is filled from it (the field already exists, currently always 1).
- **Security rule:** every buffer that mixes frames (TemporalDenoiser, PAD, parallax, track-fused embedding, adaptive engine) must be **cleared when `trackId` changes**. This stops someone from swapping faces mid-attempt.
- Tests: same box drifting 5 px/frame keeps its id; jump to the other side gives a new id; a 500 ms gap gives a new id.

### WP4.4 MediaPipe Face Landmarker (478 points, blendshapes, true 3D head pose)
This gives Apple/Google-style eye and pose understanding on RGB.

1. Dependency: `implementation("com.google.mediapipe:tasks-vision:<latest ≥ 0.10.20>")`. After adding, verify 16 KB page alignment of the native libs, because the project requires it for TFLite: build the APK and run `zipalign -c -P 16 -v 4 app-debug.apk | grep -i "libmediapipe"`. Every entry must say `(OK)`. If not, use a newer tasks-vision version.
2. Asset: download `face_landmarker.task` (float16) from
   `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`
   into `app/src/main/assets/`. Record its SHA-256 in `BiometricModelProfile` (`faceMeshAsset`, `faceMeshAssetSha256`). Add to `app/build.gradle.kts`:
   `androidResources { noCompress += listOf("tflite", "task") }`.
3. Create the adapter `platform/face/FaceMeshEngine.kt`:
   ```kotlin
   class FaceMeshEngine(context: Context) : Closeable {
       private val landmarker = FaceLandmarker.createFromOptions(context,
           FaceLandmarker.FaceLandmarkerOptions.builder()
               .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task")
                   .setDelegate(Delegate.CPU).build())   // try GPU later, CPU is deterministic
               .setRunningMode(RunningMode.VIDEO)
               .setNumFaces(2)
               .setMinFaceDetectionConfidence(0.4f)
               .setMinFacePresenceConfidence(0.4f)
               .setMinTrackingConfidence(0.4f)
               .setOutputFaceBlendshapes(true)
               .setOutputFacialTransformationMatrixes(true)
               .build())

       /** Must be called with strictly increasing timestamps (use imageProxy.imageInfo.timestamp / 1_000_000). */
       fun process(bitmap: Bitmap, timestampMs: Long): FaceMeshResult? { ... }
       override fun close() = landmarker.close()
   }
   ```
   Feed it the **same rotated bitmap** given to YOLO. If you drop a frame, still keep timestamps monotonic, otherwise MediaPipe throws.
4. Pure model `domain/face/FaceMeshResult.kt`:
   ```kotlin
   data class FaceMeshResult(
       val landmarks: List<Point3>,           // 478, normalised x,y in 0..1, z relative
       val yawDeg: Float, val pitchDeg: Float, val rollDeg: Float,   // from the 4x4 matrix
       val eyeBlinkLeft: Float, val eyeBlinkRight: Float,            // blendshapes 0..1
       val jawOpen: Float, val mouthSmile: Float,                    // (mouthSmileLeft+Right)/2
       val faceCount: Int,
   )
   data class Point3(val x: Float, val y: Float, val z: Float)
   ```
   **Euler angles from the transformation matrix** (column-major 16 floats `m`; rotation R = upper-left 3×3), in `domain/face/HeadPose.kt`:
   ```kotlin
   fun eulerFromMatrix(m: FloatArray): Triple<Float, Float, Float> {
       val r00 = m[0]; val r10 = m[1]; val r20 = m[2]
       val r21 = m[6]; val r22 = m[10]
       val pitch = atan2(r21, r22)                       // x-axis
       val yaw = atan2(-r20, sqrt(r00 * r00 + r10 * r10)) // y-axis
       val roll = atan2(r10, r00)                        // z-axis
       return Triple(Math.toDegrees(yaw.toDouble()).toFloat(),
                     Math.toDegrees(pitch.toDouble()).toFloat(),
                     Math.toDegrees(roll.toDouble()).toFloat())
   }
   ```
   Then **verify the signs on a device**: turn the head to the student's left and write down the sign; look down and write it down. Put fixed sign multipliers `MESH_YAW_SIGN` and `MESH_PITCH_SIGN` in `HeadPose.kt` so that the convention matches the existing yaw (negative = LEFT per `FaceQualityEngine`) and pitch (negative = DOWN). The front camera is mirrored: if the sign is flipped for the front camera only, use `FrameBundle.isMirrored`.
5. Blendshape lookup: find categories by **name** (`"eyeBlinkLeft"`, `"eyeBlinkRight"`, `"jawOpen"`, `"mouthSmileLeft"`, `"mouthSmileRight"`), never by index.
6. `FaceQualitySignals` changes (all with defaults so old call sites compile):
   - `yawDegrees` / `pitchDegrees` / `rollDegrees` now come from the mesh when available, falling back to the YOLO estimator.
   - add `poseSource: PoseSource = PoseSource.YOLO_RATIO` (`MESH`, `YOLO_RATIO`).
   - fill `leftEyeOpenProbability = 1 - eyeBlinkLeft` and the same for the right eye. These fields already exist and are currently `null` (R13). The existing eye logic in `LivenessEngine` and `FaceQualityEngine` starts working automatically.
   - `faceCount = max(yoloCount, mesh.faceCount)`.
7. **Do not** use mesh landmarks for ArcFace alignment. Templates were enrolled with YOLO 5-point alignment, and changing the alignment source changes embeddings (that would need a `profileId` bump and re-enrollment). The mesh is used for pose, eyes, liveness and the flash-test skin regions only.
8. **Do not** trust mesh `z` as depth evidence. The network hallucinates a face-shaped depth even for a flat photo. Real 3D evidence comes only from WP7.

### WP4.5 Performance budget
Per analysed frame on a mid-range phone: YUV→bitmap 8 ms, YOLO 640 (XNNPACK, 4 threads) 45–70 ms, mesh 12–20 ms, enhancement 3 ms. Do these things:
- `Interpreter.Options().setNumThreads(4).setUseXNNPACK(true)` for YOLO and ArcFace (check `getOrInitInterpreter`).
- Optional GPU delegate for YOLO: `org.tensorflow:tensorflow-lite-gpu:2.16.1` + `tensorflow-lite-gpu-api`. Use `CompatibilityList().isDelegateSupportedOnThisDevice` and fall back to CPU. Keep it behind a flag and compare detector outputs CPU vs GPU on 50 frames (max box diff < 2 px) before enabling.
- Lower the analysis throttle from 150 ms to 66 ms **only if** the measured p90 frame time is < 60 ms. Otherwise keep 150 ms.

**Done when:** logs show `poseSource=MESH`, eye-open probabilities are non-null, the tracker id is stable while you hold the phone, and the yaw/pitch signs have been verified on a device.

---

## WP5 — Pose-tolerant quality and stronger recognition

### WP5.1 `FaceQualityConfig` version 4 (degree-true thresholds)
Add `FaceQualityConfig.v4Defaults()`. Keep `calibrationDefaults()` for the YOLO-ratio fallback path. Select v4 when `signals.poseSource == MESH`.

| Field | v1 (now) | v4 | Why |
|---|---|---|---|
| `straightMaxYawDegrees` | 12 | 20 | Real degrees now; 20° is still near-frontal for ArcFace |
| `maxPoseDegrees` (pitch) | 15 (symmetric) | replace with `minPitchDegrees = -25`, `maxPitchDegrees = 20` | Phones are held below the face, so pitch is mostly negative |
| new `maxRollDegrees` | — | 35 | Similarity alignment removes roll; only extreme roll hurts |
| new `maxAttendanceYawDegrees` | — | 35 | Any frame up to 35° can be used for attendance |
| `minBrightness` | 40 | see WP3.4 (post-enhancement 55 + noise) | |
| `minFaceRatio` | 0.10 | 0.08 | 720p gives more pixels per face |
| `requireEyes` | false | true (only when `poseSource == MESH`) | Eyes-closed frames make bad embeddings |
| `minEyeOpenProbability` | 0.6 | 0.45 | Blendshape-derived; calibrate in WP9 |

Edit `FaceQualityEngine.evaluate`: use the asymmetric pitch window and the roll limit, and add an `ExpectedPose.ANY` used by attendance. For `ANY`, accept `abs(yaw) ≤ maxAttendanceYawDegrees`.

### WP5.2 Attendance no longer needs a straight first frame
In `AttendanceScreen.kt` (line ~1078 block), the live-attendance branch for `captureStep == 0` calls `FaceQualityEngine.evaluate(signals, calibrationDefaults())` with STRAIGHT. Change it to `ExpectedPose.ANY` with v4 config. Remove the special-case "supporting frames" branch (`isAcceptableSupportPose`), because every frame now uses the same ANY window. **Enrollment keeps its strict STRAIGHT/LEFT/RIGHT steps** (good templates need them), but uses v4 degree windows: LEFT/RIGHT = relative yaw 15–35° from the anchor.

### WP5.3 Enrollment: add a "phone-low" template, and adaptive template updates
**(a) Extra enrollment pose (flag-free, small change):** add `ExpectedPose.DOWN_HOLD` as an optional 4th step: "Hold the phone at chest height and look at the screen". Accept when relative pitch is −10…−30° from the anchor and `abs(yaw) ≤ 15`. Store it with `poseBucket = "DOWN"`. This matches how students actually hold phones and removes the main remaining angle failure. `EnrollmentBatchValidator` must accept 3 or 4 samples. Update its `InsufficientSamples` logic and tests.

**(b) Adaptive templates (what Face ID does over time).** File: `AttractRepository.kt`, plus a new pure policy `domain/face/TemplateUpdatePolicy.kt`:
```kotlin
object TemplateUpdatePolicy {
    const val MIN_SCORE = 0.65f          // match must be clearly above accept (0.50)
    const val MIN_MARGIN = 0.15f         // and clearly above runner-up
    const val MIN_ANCHOR_SIM = 0.55f     // must still resemble ENROLLED templates (drift guard)
    const val MAX_AUTO_PER_BUCKET = 2
    const val MAX_AUTO_TOTAL = 8
    const val MIN_HOURS_BETWEEN = 20     // at most ~1 update per student per day

    fun shouldAdd(score: Float, margin: Float, risk: LivenessRisk, quality: Float,
                  maxSimToEnrolled: Float, hoursSinceLastAuto: Double): Boolean =
        risk == LivenessRisk.LOW && score >= MIN_SCORE && margin >= MIN_MARGIN &&
        quality >= 0.55f && maxSimToEnrolled >= MIN_ANCHOR_SIM && hoursSinceLastAuto >= MIN_HOURS_BETWEEN
}
```
- On a final `Match` that satisfies `shouldAdd`, insert a `FaceTemplateEntity` with `source = "AUTO_UPDATE"`, `poseBucket` = bucket from mesh pose (`FRONTAL/LEFT/RIGHT/UP/DOWN`), same `modelVersion`/`embeddingDim`/crypto path as `enrollStudentFace` (reuse its encryption code, do not duplicate the crypto).
- If the bucket already holds `MAX_AUTO_PER_BUCKET` auto templates, replace the one with the **lowest quality**. If the total is over `MAX_AUTO_TOTAL`, delete the oldest auto template. **Never** delete or replace `source = ENROLLMENT` templates.
- Add a DAO query `countAutoTemplates(studentId)` and `lastAutoTemplateAt(studentId)`. No schema change is needed (`source`, `pose_bucket`, `quality_score`, `captured_at` already exist). If a DAO change needs a Room migration, add `Migration(N, N+1)` with no-op SQL and a migration test.
- A teacher action "Reset learned face data" on `StudentDetailScreen` deletes auto templates only.
- Tests: `TemplateUpdatePolicyTest` (every boundary), and a repository test with `FakeFaceTemplateDao` for bucket replacement.

This lets dim-light and odd-angle appearances accumulate safely, so recognition gets better each day without the student moving the phone around.

### WP5.4 Track-fused embedding and optional flip test-time augmentation
In `AdaptiveVerificationEngine`, add a fusion step that runs **before** the per-frame decision when ≥ 2 usable frames from the **same trackId** exist:
1. Take up to the 3 highest-quality usable frames. They must have pairwise cosine ≥ 0.50 (same person). If not, finish with `Outcome.Error("identity changed")`, which shows "Hold the phone steady".
2. `fused = l2Normalize(Σ w_i * e_i)`, where `w_i = qualityScore_i`.
3. Score `fused` with `IdentityScorer.scoreGroupedMax` and decide with the existing `RecognitionDecisionEngine` thresholds.
4. Keep the existing first-frame fast path (`confidence ≥ confirmBelow` → immediate Match). Fusion helps the borderline and off-angle cases.

Flip-TTA (flag `flipTta`, default OFF): embedding = `l2Normalize(e(crop) + e(hflip(crop)))`. This changes embeddings, so it needs `profileId = "arcface512_bgr_noseyaw_v6_tta"` and re-enrollment. Enable it **only** if `exp6` shows EER improves ≥ 10 % relative. Otherwise leave it off forever.

### WP5.5 Recalibrate thresholds for the new pose range
Re-run `tools/biometric_eval/calibrate.py` with the WP9 additions (pose-mixed pairs up to 35° yaw, low-light synthetic pairs). Rule: keep the false-match rate at 1:10,000 or better on pairs with **both** sides frontal, and check that the FMR on mixed pairs is ≤ 1:1,000. If an off-angle query needs a lower threshold to reach the acceptance target, do **not** lower the global accept. Instead require **two-frame fused confirmation** whenever `abs(yaw) > 25`. Update `BiometricModelProfile.calibrationId`.

**Done when:** at a 30° yaw or with the phone at chest height, attendance succeeds without moving the phone, and calibrate.py reports the FMR numbers above.

---

## WP6 — Learned passive anti-spoofing (PAD CNN)

### WP6.1 Model choice
Use **MiniFASNetV2 (scale 2.7) + MiniFASNetV1SE (scale 4.0)** from `minivision-ai/Silent-Face-Anti-Spoofing` (Apache-2.0). Each is ~0.4 M params, 80×80 input, 3-class softmax (index **1 = live**), and they run in < 5 ms each on CPU. They are widely deployed on Android and complement our pixel statistics. **Check the license file in the repo at download time and record it in `docs/00-architecture-decisions.md`.**

### WP6.2 Conversion (offline, `tools/model_conversion/convert_minifasnet.py`)
```bash
git clone https://github.com/minivision-ai/Silent-Face-Anti-Spoofing /tmp/sfas
pip install torch onnx onnxruntime onnx2tf tensorflow==2.16.1 numpy opencv-python
python tools/model_conversion/convert_minifasnet.py --repo /tmp/sfas --out app/src/main/assets/
```
The script must:
1. Import `src.model_lib.MiniFASNet` (`MiniFASNetV2`, `MiniFASNetV1SE`) from the repo. Load `resources/anti_spoof_models/2.7_80x80_MiniFASNetV2.pth` and `4_0_0_80x80_MiniFASNetV1SE.pth`. Strip a leading `module.` from state-dict keys if present. Set `kernel_size` from the input size like the repo's `get_kernel(80, 80)` → `(5, 5)`. Call `model.eval()`.
2. `torch.onnx.export(model, torch.zeros(1,3,80,80), "x.onnx", opset_version=13, input_names=["input"], output_names=["logits"])`.
3. `onnx2tf -i x.onnx -o out_x` → take `out_x/x_float32.tflite`. onnx2tf converts to **NHWC** `[1,80,80,3]`.
4. **Parity test:** 20 random face crops; compare PyTorch softmax vs TFLite softmax; require max abs diff < 1e-3. Print the input convention the repo uses (BGR, **0..255 without /255 normalisation**: the repo's custom `ToTensor` does not divide; confirm by reading `src/data_io/functional.py` and assert it in the script).
5. Write `minifasnet_v2_27.tflite` and `minifasnet_v1se_40.tflite` plus `SHA256SUMS`.

### WP6.3 Crops (must match training exactly)
Port the repo's `CropImage._get_new_box` to a pure function `PadCrop.box(src W,H, faceBox, scale)`:
```
box_w, box_h = faceBox.w, faceBox.h
scale = min((H-1)/box_h, (W-1)/box_w, scale)
new_w, new_h = box_w*scale, box_h*scale
cx, cy = faceBox.x + box_w/2, faceBox.y + box_h/2
left = cx - new_w/2; top = cy - new_h/2; right = cx + new_w/2; bottom = cy + new_h/2
shift into the image if out of bounds (keep size), then int()
```
Crop from the **full, un-enhanced** rotated frame (enhancement changes texture statistics the model relies on), then resize to 80×80 with bilinear filtering. In `CameraPreview.kt` produce both crops as `IntArray(80*80)` for frames with exactly one face and put them on `FrameBundle.padCrops: Pair<IntArray, IntArray>?`. For the front camera, do **not** mirror (the training data was not mirrored; the CameraX analysis image is not mirrored either).

### WP6.4 Engine
`platform/face/PadCnnEngine.kt` (TFLite, 2 interpreters, lazy init like `EmbeddingEngine`, verify SHA-256 at first load like the existing assets do):
```kotlin
fun liveScore(crops: Pair<IntArray, IntArray>): Float {
    val p1 = softmax(run(model27, crops.first))   // FloatArray(3)
    val p2 = softmax(run(model40, crops.second))
    return (p1[1] + p2[1]) / 2f                    // 0..1, higher = live
}
```
The input buffer is NHWC float32, channel order **B, G, R**, raw 0..255 values (per WP6.2 step 4).

### WP6.5 Temporal smoothing and decision
- PAD score of the attempt = **median** of the scores of the last 5 frames of the same track (this resists single-frame flukes).
- `padScore ≥ padLiveAccept (0.80)` → PAD says LIVE. `< padSpoofReject (0.25)` → HIGH risk. In between → MEDIUM (triggers WP7 challenge).
- In low light (face luma < 50), PAD CNNs drift toward "spoof". Widen the MEDIUM band: `padSpoofReject = 0.12`. The flash test (WP7.3) then decides.

### WP6.6 Shadow mode first
Add `padEnforce: Boolean = false` to flags. For the first rollout week, PAD only logs `padScore` in `AttemptTrace` and never rejects. Use the logs plus WP0.3 collected attacks to set the thresholds (WP9.4), then turn on enforcement.

**Done when:** the parity test passes, the engine runs in < 12 ms for both models on a mid-range phone, and the shadow logs show live users with median `padScore > 0.9`.

---

## WP7 — 3D-style and active liveness (the "Face ID" layer on RGB)

All of these go into a new `domain/face/liveness/` package (pure) plus thin adapters.

### WP7.1 Motion-parallax depth test (passive 3D, no user action needed)
Idea: when the head or phone moves even slightly, points on a **flat** photo or screen move together according to a single homography. A real face's nose tip, cheeks and ears move differently (parallax).

Algorithm, `ParallaxDepthTest` (pure):
1. Keep mesh landmarks for the last 15 frames of the track. Use this **stable subset** of MediaPipe indices (rigid points, not lips/eyes): `1 (nose tip), 4, 6, 168, 197, 195, 5, 33, 133, 362, 263, 234, 454, 93, 323, 132, 361, 58, 288, 172, 397, 136, 365, 152, 10, 109, 338, 67, 297, 21, 251, 127, 356`.
2. Pick a frame pair (a, b) with `|yaw_a − yaw_b| ≥ parallaxMinYawDeltaDeg (4°)` **or** a nose-to-cheek displacement difference ≥ 2 % of the inter-ocular distance. If there is no such pair, the result is `INCONCLUSIVE` (the user is perfectly still; WP7.4 then asks for a tiny head turn).
3. Convert normalised landmarks to pixels. Fit a homography H from a→b with DLT on all subset points (8×9 SVD, or 4-point RANSAC with 50 iterations, threshold 1.5 % IOD, then refit on inliers). Implement a small 3×3 solver; there is no need for OpenCV. Normalise points (Hartley normalisation) before the DLT.
4. `residual = median_i ‖H·p_a,i − p_b,i‖ / IOD_b`.
5. Real face: residual grows with yaw delta (typically 0.03–0.08 at 5–10°). Flat attack: < 0.015 even when the photo is tilted. A bent photo gives 0.015–0.03 → MEDIUM.
   `depthScore = clamp((residual − 0.010) / (parallaxLiveResidual(0.020)·2 − 0.010), 0, 1)`.
6. **Limitation (document it):** a *video replay* of the real person turning their head also shows parallax. That is why WP6 (texture) and WP7.3 (flash) exist. Parallax defeats prints and photos; flash and PAD defeat screens.
7. Tests: synthetic 3D face point cloud (use the canonical MediaPipe face model or a simple ellipsoid with a protruding nose) projected under a 6° rotation → `depthScore > 0.7`; the same points flattened to z = 0 and projected under any rotation or tilt → `depthScore < 0.2`. Add pixel noise σ = 0.5 % IOD and confirm the results still hold.

### WP7.2 Blink detection
`BlinkDetector` (pure) over the per-frame mesh blendshapes of the track: a blink = `max(eyeBlinkLeft, eyeBlinkRight) ≥ blinkClosedScore (0.55)` for 1–8 consecutive frames, preceded **and** followed by a value ≤ `blinkOpenScore (0.30)`. Humans blink every 2–10 s, so a passive blink is a bonus signal, not required. A printed photo with cut-out eyes plus a real blinking person behind it is caught by WP7.1 (the face plane is flat) and WP6.

### WP7.3 Screen-flash photometric liveness (the low-light hero)
This both **lights the face in the dark** and **proves 3D skin**. It reuses `ScreenFlashOverlay` (WP2.4).

Sequence (≈ 1.2 s, runs only when risk is MEDIUM **or** face luma < 60):
1. Lock AE **and** AWB so the camera doesn't cancel out the colours:
   ```kotlin
   Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(
       CaptureRequestOptions.Builder()
           .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
           .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true).build())
   ```
   Wait 150 ms first, with the overlay showing warm white, so exposure settles under the flash light before locking. Unlock in `finally`.
2. Generate a random sequence of 4 colours from {RED `#FF2020`, GREEN `#20FF20`, BLUE `#2040FF`, WHITE `#FFFFFF`} using `SecureRandom`, with no two consecutive colours the same. Show each colour for 250 ms.
3. For every analysed frame, record `(timestampNanos, emittedColorIndex)`. Discard frames captured within 80 ms after a colour switch (display latency and rolling shutter).
4. From the mesh, sample mean linear RGB on skin patches: forehead (indices 10, 67, 297, 109, 338), left cheek (50, 101, 118, 117), right cheek (280, 330, 347, 346), nose bridge (6, 197, 195). Use the **un-enhanced** frame. Convert sRGB→linear with the standard formula.
5. **Correlation test:** for each channel c ∈ {R, G, B}, compute the Pearson correlation between the emitted intensity of c (1 for the active colour channel, also 1 for WHITE on all channels) and the observed skin chroma `c / (R+G+B)` across the kept frames. `flashCorr = mean over channels`. A live face (diffuse reflection of the screen light) gives > 0.6. A phone or laptop screen replay emits its own light and reflects little diffusely, so it gives < 0.3. A print in a dark room *does* reflect, which is why test 6 exists.
6. **Shading (3D) test:** under the flash, the light source is the phone screen, which is below and in front of the face. Compute the ratio of the luminance increase (flash on vs the 150 ms warm-white baseline) on **nose bridge + cheek-front** patches vs **cheek-side** patches near the jaw (234, 454, 93, 323). On a real 3D face the front patches gain more (surface normals face the screen): `shadingRatio > flashMinShadingRatio (1.06)`. On a flat print, all patches gain about the same (ratio ≈ 1.00 ± 0.03).
7. `flashScore = 0.6·clamp((flashCorr − 0.3)/(0.55 − 0.3), 0, 1) + 0.4·clamp((shadingRatio − 1.0)/(0.06·2), 0, 1)`.
8. **Bonus:** the same frames are well lit, so feed them to recognition (WP3.3 denoise + embedding). In a very dark room this is how recognition succeeds.
9. Camera-injection resistance: the random colour sequence comes from the app at runtime, so a pre-recorded or injected video cannot match it.
10. Tests (pure): a synthetic sequence where the observed chroma follows the emitted colours plus noise → corr > 0.8; constant observed colour → corr ≈ 0; the shading-ratio function with equal gains → ≈ 1.0.

### WP7.4 Randomised micro-challenge (only when risk is MEDIUM)
`ChallengeController` (pure state machine) picks one challenge at random (`SecureRandom`) from:
- `BLINK` — detect a blink (WP7.2) within the time limit.
- `TURN_SLIGHT_LEFT` / `TURN_SLIGHT_RIGHT` — yaw changes by ≥ 10° in the requested direction relative to the attempt start. This also produces the parallax pair for WP7.1.
- `SMILE` — `mouthSmile ≥ 0.5` after starting from < 0.2.

Time limit `maxChallengeMs = 4000`. On timeout → risk becomes HIGH for this attempt (the student can retry; after 3 HIGH results in 2 minutes, show "Ask your teacher" and route to manual attendance, the existing flow). UI: a single short line in `GuidanceCard`, e.g. "Blink once" or "Turn slightly left", with an animated arrow. **Never** chain more than one challenge per attempt.

### WP7.5 `LivenessOrchestrator` and risk fusion (replaces `LivenessEngine` at call sites)
```kotlin
enum class LivenessRisk { LOW, MEDIUM, HIGH }
data class LivenessEvidence(
    val legacyPixelVeto: LivenessReason?,   // WP1 PresentationAttackDetector result (null = passed)
    val padScore: Float?, val depthScore: Float?, val depthInconclusive: Boolean,
    val blinkSeen: Boolean, val flashScore: Float?, val challengePassed: Boolean?,
    val faceLuma: Float, val trackFrames: Int, val injectionSuspected: Boolean,
)
object RiskFusion {
    fun decide(e: LivenessEvidence, p: LivenessPolicy): LivenessRisk
}
```
Rules, evaluated **in this order** (first match wins). Copy them exactly and test every row:

| # | Condition | Risk |
|---|---|---|
| 1 | `injectionSuspected` (WP11) | HIGH |
| 2 | `legacyPixelVeto != null` | HIGH |
| 3 | `padScore != null && padScore < padSpoofReject` (0.25, or 0.12 if luma < 50) | HIGH |
| 4 | `flashScore != null && flashScore < 0.35` | HIGH |
| 5 | `challengePassed == false` | HIGH |
| 6 | `depthScore != null && depthScore < 0.2 && !depthInconclusive` | HIGH |
| 7 | `padScore ≥ padLiveAccept` and (`depthScore ≥ 0.5` or `blinkSeen` or `flashScore ≥ 0.6`) | LOW |
| 8 | `challengePassed == true` and (`padScore ≥ 0.5` or `flashScore ≥ 0.6`) and (`depthScore == null` or `depthScore ≥ 0.3`) | LOW |
| 9 | `flashScore ≥ 0.75` and `depthScore ≥ 0.5` (dark-room path, PAD unreliable) | LOW |
| 10 | otherwise | MEDIUM |

Flow per attempt: collect passive evidence for up to ~1 s (≥ 6 track frames). If LOW → recognise. If MEDIUM → run the flash test (if luma < 60, or the challenge has already been used) or else one challenge, then decide again. If still MEDIUM → treat as HIGH. If HIGH → "Liveness check failed — use your real face, not a photo or screen" and log it to `SecurityEventLoggerImpl` (event type `PRESENTATION_ATTACK_SUSPECTED`, no images).

Replace the two `LivenessEngine.check(...)` call sites in `AttendanceScreen.kt` (capture ~1130 and `submitRecognition` ~1288) with the orchestrator result. `FrameObservation.liveness` stays a `LivenessResult`: map LOW → `Passed` and HIGH → `Rejected(reason)`. MEDIUM never reaches `AdaptiveVerificationEngine`. Keep `LivenessEngine` for enrollment captures, and also run the orchestrator once per enrollment (enrollment is the most valuable thing to spoof: a spoofed enrollment poisons the gallery).

**Done when:** `RiskFusionTest` covers all 10 rows, and the device attack matrix (WP9.4) meets the APCER/BPCER targets.

---

## WP8 — "Just look at it" UX (auto-capture, no hunting for an angle)

1. **Auto-capture state machine** in a new `feature/attendance/AutoCaptureController.kt` (pure logic, unit tested), driven by every `FrameBundle`:
   `SEARCHING → TRACKING (same trackId ≥ 3 frames, quality ACCEPT with ExpectedPose.ANY) → LIVENESS (WP7.5) → RECOGNISING → RESULT`.
   No button press is required. Keep the existing capture button as a manual fallback (it calls the same controller with `force = true`).
2. **Best-frame selection:** during TRACKING keep the top-4 frames by `qualityScore` (+0.1 bonus if both eyes are open ≥ 0.6). Recognition uses those frames (denoised + fused, WP3.3/WP5.4) rather than "whatever frame was there at click time".
3. **Guidance is specific and only shown after 1.5 s without progress**, one message at a time, priority order: too dark (and flash not yet on) → face too small ("Bring the phone a little closer") → yaw > 35 → pitch outside the window → eyes closed → motion blur ("Hold still"). Strings go in `AttendanceStrings.kt`.
4. **Latency targets:** the first decision ≤ 1.2 s after the face is stable. Run ArcFace on a background dispatcher (`Dispatchers.Default`), never on the main thread. Pre-warm all interpreters (`YOLO`, `ArcFace`, `PAD`, `Mesh`) in `StartupCoordinator` by running one dummy inference each.
5. **Haptics:** a short success vibration on Match (`FeedbackUtils` already exists).
6. **Accessibility:** announce the guidance through `LiveRegion` semantics.

**Done when:** a student can open the attendance screen, look at the phone held naturally, and be marked present without touching anything, in normal light and in dim light.

---

## WP9 — Evaluation and calibration (do not ship thresholds without this)

### WP9.1 Offline scripts (extend `tools/biometric_eval/`)
- `app_pipeline.py`: port the WP3 `LowLightEnhancer` and `TemporalDenoiser`, WP4.1 letterbox, and WP5.4 fusion **line by line** (same constants).
- `exp5_lowlight.py`: synthesise low light on LFW and Pointing'04. For each image: linearise sRGB, scale by k ∈ {0.25, 0.12, 0.06, 0.03}, add Poisson-Gaussian noise (`shot = Poisson(I·g)/g` with gain g = 40·k, read noise σ = 2/255), re-apply sRGB gamma, JPEG q = 85. Produce 4 noisy "frames" per image with independent noise (this simulates the burst for the denoiser). Report detection recall, genuine/impostor score distributions and EER for each arm: {none, gamma, gamma+CLAHE, denoise, denoise+gamma+CLAHE}. Write `results/exp5_lowlight.json`. **Pick the arm with the lowest EER and the lowest impostor mean shift.**
- `exp6_pose_fusion.py`: Pointing'04 queries at yaw ∈ {0, ±15, ±30, ±45} and pitch ∈ {0, ±15, ±30} against galleries of {3 poses, 4 poses with DOWN, 4 poses + 4 simulated auto templates}. Report TAR at FAR = 1e-3 for single-frame vs 3-frame fusion vs flip-TTA.
- `calibrate.py`: add the pose-mixed and low-light pairs (WP5.5 rules) and write the new `calibration_report.json`.

### WP9.2 Unit-test parity
For every ported function, create a golden vector file in `app/src/test/resources/golden/` (input pixels → expected output) generated by Python. The Kotlin unit test must match within ±1 (8-bit) or 1e-4 (float). Do this for: CLAHE, gamma, denoiser, letterbox mapping, homography residual, flash correlation.

### WP9.3 Device protocol (genuine users)
- 20 volunteers (mixed skin tones, glasses/no glasses, ≥ 3 with beards, ≥ 3 with long hair) × 3 phones (one low-end, e.g. a Helio G-series or SD 4-series phone; one mid; one flagship).
- Conditions (measure lux with the phone light sensor or a lux-meter app): bright 300+ lux; indoor 100–150; dim 10–30; very dim 1–10 (flash path); backlit (window behind).
- Holds: eye level; chest level (pitch ≈ −20°); yaw ±30°; walking slowly.
- Per cell, 10 attempts. Record success, time to decision and retries. Use the `collect` build so the AttemptTrace logs are saved.
- Fill the table in `docs/face_verification_test_report.md` (new section "Doc-20 results").

### WP9.4 Device protocol (attacks)
Attack instruments × 3 phones × 2 lighting conditions (indoor, dim) × 20 attempts each, using the enrolled volunteers' faces:
- A4 colour print (matte), glossy photo print, print with cut-out eyes and a real person behind it, print curved around a bottle.
- Phone screen photo and video, tablet video, laptop video (the volunteer's own selfie video, including blinks and head turns).
- Optional: a paper 3D mask (folded print).

Compute APCER per instrument and BPCER. Choose `padLiveAccept`, `padSpoofReject`, `flashMinCorrelation`, `flashMinShadingRatio`, `parallaxLiveResidual` with this rule: **first fix BPCER ≤ 2 %**, then minimise the worst-instrument APCER. Write the chosen values into `LivenessPolicy` defaults and record the data in `tools/biometric_eval/results/pad_calibration.json` (scores only, never images).

### WP9.5 CI
Add `./gradlew :app:testDebugUnitTest` (if not already present) to `.github/workflows/ci.yml` before the release-bundle step, so every golden-vector and policy test runs on each PR.

---

## WP10 — (Optional, after WP1–9 ship) Stronger recognizer

Only start this if WP9 shows the recognizer, not the pipeline, is the bottleneck (e.g. TAR@FAR=1e-3 < 97 % on the pose/low-light sets after fusion).

1. Candidates: an IR-18/IR-50 backbone trained with AdaFace or ArcFace on WebFace4M/12M (AdaFace is designed for low-quality, low-light images). **Check the weights license for commercial use before evaluating.** Many InsightFace model-zoo weights are non-commercial only. Record the decision in `docs/00-architecture-decisions.md`.
2. Convert with the WP6.2 pipeline (PyTorch→ONNX→onnx2tf), then apply FP16 or full-int8 post-training quantisation with a 500-image representative dataset. The size budget is ≤ 30 MB, and latency ≤ 40 ms on a mid-range CPU (4 threads, XNNPACK).
3. Evaluate in `tools/biometric_eval` against the current MobileFaceNet on the WP9 sets. Adopt only if TAR@FAR=1e-3 improves ≥ 2 points absolute **and** latency fits.
4. Migration: new `profileId`. `TemplateCompatibility` already marks dimension/model mismatches as stale. Add a "Re-enrollment needed" banner per class and a guided re-enroll flow. **Keep both models in the APK for one release**, so that students can still be recognised with the old templates while re-enrolling. Delete the old model in the next release.

---

## WP11 — Device and camera integrity (anti-injection)

1. Play Integrity: `firebase-appcheck-playintegrity` is already a dependency. Before starting a face session, require a valid App Check token. If it fails, allow only teacher-supervised manual attendance.
2. Reuse `DeviceSecurityCheckerImpl` (root/emulator/debugger checks). Add: reject when the bound camera's `INFO_SUPPORTED_HARDWARE_LEVEL == EXTERNAL` (USB/virtual cameras) in kiosk sessions.
3. Frame-timing sanity (pure `FrameTimingGuard`): `imageProxy.imageInfo.timestamp` must strictly increase, and the deltas must match the AE fps range (no identical consecutive timestamps, no deltas < 20 ms, no long series of perfectly identical deltas ±0.1 ms, which is typical of synthetic feeds). Repeated violations → `injectionSuspected = true` (RiskFusion rule 1).
4. The flash challenge (WP7.3) is the strongest anti-injection measure, because the colour sequence is unpredictable.

---

## 12. Rollout order and release gates

| Release | Contents | Gate to ship |
|---|---|---|
| R1 (1–2 days of work) | WP0, WP1, WP2.1–2.3 | Unit tests green. On a device, dim-room attempts no longer show "Liveness check failed" |
| R2 | WP2.4, WP3, WP4.1–4.3 | exp5 shows an EER gain in low light; device dim-light success ≥ 90 % |
| R3 | WP4.4–4.5, WP5 | exp6 + calibrate meet the WP5.5 FMR rules; chest-hold success ≥ 97 % |
| R4 | WP6 (shadow), WP7, WP8 | RiskFusion tests green; one week of shadow PAD logs reviewed |
| R5 | Enforce PAD, WP9.4 thresholds, WP11 | §0.4 targets all met |
| R6 (optional) | WP10 | WP10.3 adoption rule met |

Rollback: every subsystem has a flag (WP0.1). If a release regresses, flip the flag in `BiometricFeatureFlags.CURRENT`, rebuild and ship. Templates are unaffected unless `profileId` changed.

## 13. File checklist (new or changed)

New (pure, `domain/face/`): `BiometricFeatureFlags.kt`, `AttemptTrace.kt`, `LivenessPolicy.kt`, `CameraTuning.kt`, `LowLightEnhancer.kt`, `TemporalDenoiser.kt`, `LetterboxMapping.kt`, `FaceTracker.kt`, `FaceMeshResult.kt`, `HeadPose.kt`, `TemplateUpdatePolicy.kt`, `PadCrop.kt`, `liveness/ParallaxDepthTest.kt`, `liveness/Homography.kt`, `liveness/BlinkDetector.kt`, `liveness/FlashLivenessTest.kt`, `liveness/ChallengeController.kt`, `liveness/RiskFusion.kt`, `liveness/LivenessOrchestrator.kt`, `FrameTimingGuard.kt`.
New (Android): `platform/face/FaceMeshEngine.kt`, `platform/face/PadCnnEngine.kt`, `ui/components/biometric/ScreenFlashOverlay.kt`, `feature/attendance/AutoCaptureController.kt`.
New assets: `face_landmarker.task`, `minifasnet_v2_27.tflite`, `minifasnet_v1se_40.tflite` (SHA-256 pinned in `BiometricModelProfile`).
New tools: `tools/model_conversion/convert_minifasnet.py`, `tools/biometric_eval/exp5_lowlight.py`, `exp6_pose_fusion.py`.
Changed: `CameraPreview.kt`, `AttendanceScreen.kt`, `YoloFaceDetector.kt`, `FaceQualityConfig.kt`, `FaceQualityEngine.kt`, `FaceQualitySignals.kt`, `FrameBundle.kt`, `LivenessEngine.kt`, `PresentationAttackDetector.kt`, `AdaptiveVerificationEngine.kt`, `EnrollmentBatch.kt`, `BiometricModelProfile.kt`, `AttractRepository.kt`, `Daos.kt`, `AttendanceStrings.kt`, `StudentDetailScreen.kt`, `StartupCoordinator.kt`, `app/build.gradle.kts`, `.github/workflows/ci.yml`.
New tests (`app/src/test/.../domain/face/`): `CameraTuningTest`, `LowLightEnhancerTest`, `TemporalDenoiserTest`, `LetterboxMappingTest`, `FaceTrackerTest`, `HeadPoseTest`, `TemplateUpdatePolicyTest`, `PadCropTest`, `ParallaxDepthTestTest`, `BlinkDetectorTest`, `FlashLivenessTestTest`, `ChallengeControllerTest`, `RiskFusionTest`, `AutoCaptureControllerTest`, `FrameTimingGuardTest`, plus golden-vector tests (WP9.2).
