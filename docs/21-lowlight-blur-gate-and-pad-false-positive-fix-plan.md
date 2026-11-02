# 21 — Why recognition got WORSE after doc 20, and the measured fix (low light, backlight, "Too blurry", "Screen detected")

Status: ANALYSIS + MEASURED PLAN (no app Kotlin changed yet). Date: 2026-11-02.
Base: `origin/main` = `origin/dev` = `cf22e17`.
Evidence: `tools/biometric_eval/exp5_lowlight.py`, `exp6_gates.py`, `results/exp5_lowlight_run_60ids.txt`, `results/exp6_gates.json`.

---

## 0. Progress log (read first if resuming)

| # | Item | State |
|---|---|---|
| 0.1 | Looked for commit `39a221b` (doc-20 implementation) | **NOT FOUND.** Not on any remote branch, and GitHub API says "No commit found for SHA 39a221b". `origin/dev` == `origin/main` == `cf22e17`. **The doc-20 code was never pushed.** Run `git push origin dev` from the machine that has it. Everything below is written so the fix applies on top of it. |
| 0.2 | Checked whether any doc-20 code is on main | None (`LowLightEnhancer`, `LivenessPolicy`, `BiometricFeatureFlags`, `FaceLandmarker`, `MiniFASNet`, `LivenessOrchestrator`: 0 hits). |
| 0.3 | Built the low-light / backlight test harness on the **shipped** TFLite models | Done: `exp5_lowlight.py` (LFW, 6 lighting conditions, 640×480 vs 1280×720, 6 enhancement variants, impostor check). |
| 0.4 | Calibrated the new sharpness gate and the PAD screen gate | Done: `exp6_gates.py`. |
| 0.5 | Kotlin changes (WP-A…WP-F below) | **TODO.** Need the `39a221b` code pushed first, otherwise the changes conflict with it. |
| 0.6 | Real-device validation (§6) | **TODO.** The sandbox has no camera or phone. All numbers here come from simulation and must be confirmed on a device. |

---

## 1. The root cause in one paragraph

On `main`, the "Too blurry" decision is `FaceQualityEngine`: `signals.blurVariance < minBlurVariance (60)`. `blurVariance` comes from `CameraPreview.computeBitmapLaplacianVariance`, the variance of a 4-neighbour Laplacian on **raw pixel values of the full camera frame**. That number is not a blur measure. It mostly measures **contrast × resolution**:

1. **Dark or backlit face → low contrast → small Laplacian → "Too blurry"**, even when the face is perfectly sharp. The Laplacian scales with brightness: halve the light and the variance drops about 4×.
2. **Higher resolution → each pixel step is smaller → variance drops.** Doc 20 WP2.1 changes the analysis stream from 640×480 to **1280×720**. At 720p, the same sharp face in the same light gives a much lower `blurVariance`, so the old gate fires far more often. This is the most likely reason the doc-20 build says "Too blurry" at almost every angle. The second reason is that 16:9 frames make `faceRatio` smaller, so `TOO_SMALL` also fires more (see §2).
3. In good light on main, the face has enough contrast to clear 60, so it worked. That matches your experience exactly.

The second false-reject source is **"Screen detected" in dim rooms.** `PresentationAttackAnalyzer.highFrequencyEnergy` = mean |Laplacian| / 255 on the 96×96 crop, and the gate is `> 0.18`. In dim light the phone raises ISO and the ISP sharpens. Sensor noise plus sharpening produces exactly the high-frequency energy the gate treats as screen moiré. Measured on genuine faces: **34/34 (100 %) rejected as "screen"** for dim + ISP sharpening (§2.2).

---

## 2. Measurements (shipped YOLOv8n-face + ArcFace, BGR input exactly as `EmbeddingEngine.kt`)

### 2.1 exp5 — 52 LFW identities, enrolled in normal light, probed with a *different* photo

| condition | detected | **passes current gate** | passes proposed gate | genuine ≥0.50: raw | **avg of 4 frames + levels** | wrong-student (FAR) |
|---|---|---|---|---|---|---|
| normal | 40 | 32 | 40 | 0.900 | 0.850 | 0 |
| dim (~20–30 lux) | 45 | **0** (39× BLUR) | 45 | 0.800 | **0.867** | 0 |
| very dim (~5 lux) | 27 | **0** | 26 | 0.630 | **0.852** | 0 |
| backlit (window behind) | 41 | **0** (35× BLUR) | 41 | 0.732 | **0.854** | 0 |
| hard backlight | 31 | **0** | 31 | 0.484 | **0.742** | 0 |

At 1280×720, the current gate also passes **only 9/40 even in NORMAL light** (29× `TOO_SMALL`, plus BLUR in dim). This is the doc-20 regression mechanism.

The other enhancement variants (raw / gamma / levels / gamma+CLAHE / avg4):
- **gamma + CLAHE on the ArcFace input LOWERS genuine scores** (very dim 0.63→0.59, hard backlight 0.48→0.45). **Do not feed CLAHE output to ArcFace.** If CLAHE is kept at all, use it only for the detector/PAD. Doc 20 WP3.2 allowed this fallback; it is now mandatory.
- Gamma-only: neutral to slightly positive.
- **Robust "levels" (2–98 percentile stretch) on the aligned crop: consistently positive.**
- **Averaging 4 aligned frames, then levels: best in every degraded condition.** It never raised impostor scores above 0.50 (the sample is small: 52 identities, ~2,600 impostor comparisons per condition).

### 2.2 exp6 — gate calibration on 34 genuine crops

| case | new sharpness p5/p50/p95 | current PAD hfe p50 | **current "Screen" false rejects** | proposed PAD false rejects |
|---|---|---|---|---|
| sharp, good light | 0.169 / 0.262 / 0.378 | 0.041 | 0/34 | 0/34 |
| dim, ISO noise | 0.337 / 0.454 / 0.689 | 0.098 | 0/34 | 0/34 |
| **dim, ISO noise + ISP sharpening** | 0.693 / 0.906 / 1.302 | **0.216** | **34/34** | 0/34 |
| **very dim + sharpening** | 1.239 / 1.593 / 2.115 | **0.371** | **34/34** | 0/34 |
| defocus σ=1.5 | 0.083 / 0.121 / 0.183 | 0.018 | 0 | 0 |
| defocus σ=2.5 | 0.059 / 0.085 / 0.144 | 0.013 | 0 | 0 |
| motion 9 px | 0.105 / 0.163 / 0.238 | 0.021 | 0 | 0 |
| motion 15 px | 0.093 / 0.148 / 0.218 | 0.018 | 0 | 0 |

Conclusions:
- A hard sharpness floor of **0.09** (contrast-normalised, see WP-B) passes 100 % of sharp genuine crops (p5 0.169) and rejects about half of heavy defocus. Noise *inflates* any sharpness metric, so in dim light blur detection is inherently weak. **Blur must be a soft signal; the recognition score plus multi-frame fusion decide.** Never use blur as a spoof signal.
- The current `maxHighFrequencyEnergy = 0.18` is **below** what a genuine face produces in a dim room on any phone that sharpens. That is the "screen replay detected" false positive.
- **Caveat:** the proposed PAD gate was tested only for *false rejects*. Its attack-detection rate on real screen and print replays is **unmeasured** (no attack data in the sandbox). Treat the pixel PAD as a weak signal. The real anti-spoof layer is doc 20 WP6 (MiniFASNet) + WP7 (parallax / blink / flash), shipped in shadow mode first.

---

## 3. Fix plan (apply on top of the doc-20 code once `39a221b` is pushed)

Order matters: WP-A, then WP-B, then WP-C. These three alone remove the regression. Each WP is one commit.

### WP-A — Stop the raw-frame Laplacian from deciding anything (fixes "Too blurry")
Files: `domain/face/FaceQualityEngine.kt`, `domain/face/FaceQualityConfig.kt`, `domain/face/FaceQualitySignals.kt`, `feature/attendance/CameraPreview.kt`.
1. Add to `FaceQualitySignals`: `cropSharpness: Float = -1f` (−1 = not computed), `faceLuma: Float = -1f`, `noiseSigma: Float = 0f`.
2. In `CameraPreview`, after `FaceAligner.align(...)` produces the 112×112 crop, compute `cropSharpness`, `noiseSigma` and `faceLuma` **on the crop** (WP-B). Keep `blurVariance` only for logging.
3. `FaceQualityConfig` → `version = 4`, `qualityConfigId = "quality_v4"` in `BiometricModelProfile`. New fields: `minCropSharpness = 0.09f`, `minFaceLuma = 12f`, `maxFaceLuma = 235f`.
4. `FaceQualityEngine.evaluate`: replace
   `if (signals.blurVariance < config.minBlurVariance) → BLUR` with
   `if (signals.cropSharpness >= 0f && signals.cropSharpness < config.minCropSharpness) → BLUR`.
   Replace the `brightness < minBrightness (40)` check with `faceLuma < minFaceLuma (12)` → `DARK`. Use `faceLuma > 235` → `OVEREXPOSED`. Fall back to the old checks only when `cropSharpness < 0` (old callers and tests).
5. `faceRatio`: compute it as `boxHeight / frameHeight` squared-equivalent, **or** make `minFaceRatio` resolution and aspect aware: `minFaceRatio = 0.06f` when the frame is 16:9. Simplest correct rule: gate on **inter-ocular distance in pixels ≥ 28 px** (ArcFace needs about 30 px between the eyes before upscaling to 112), independent of frame size. Add `minInterOcularPx = 28f` and compute it from `landmarks[0..1]`.
6. Update `AdaptiveVerificationEngine` log line 167 to print `sharp=` and `luma=`.
7. Tests (`FaceQualityEngineTest`): dark (luma 25) sharp crop is accepted; cropSharpness 0.05 is rejected as BLUR; a 720p-sized face with interocular 60 px is not TOO_SMALL.

### WP-B — Exact algorithms (pure Kotlin, `domain/face/CropMetrics.kt`, operating on the 112×112 ARGB `IntArray`)
Port these exactly. They match `exp5_lowlight.py` (`crop_sharpness`, `noise_sigma`, `normalize_luma`).
```
luma(p) = 0.299 R + 0.587 G + 0.114 B                    // float
ROI     = rows 20..99, cols 16..95  (80×80 central face)

faceLuma   = mean(luma over ROI)                            // on the RAW crop

noiseSigma (Immerkær):  r = conv(Y_roi, [[1,-2,1],[-2,4,-2],[1,-2,1]])  (valid region)
            noiseSigma = sqrt(pi/2) * mean(|r|) / 6

levels(crop):            // robust auto-levels; also used as ArcFace input in WP-C
   lo = P2(luma ROI), hi = P98(luma ROI)     // 256-bin histogram percentiles
   if (hi - lo < 8) return crop
   gain = min(200/(hi-lo), 4.0)
   each channel c' = clamp((c - lo)*gain + 28, 0, 255)

cropSharpness:   on levels(crop):
   Y = luma ROI; Y = gaussianBlur(Y, sigma=0.6)   // 5-tap kernel [0.0219,0.2285,0.4992,0.2285,0.0219] separable
   L = 4-neighbour Laplacian(Y) (valid region)
   cropSharpness = std(L) / (std(Y) + 1e-3)
```
Cost: about 30 k operations per crop, under 1 ms. Unit test against Python golden values: dump 3 crops plus expected numbers from `exp5_lowlight.py` into `app/src/test/resources/cropmetrics_golden.json`, with tolerance ±2 %.

### WP-C — Multi-frame fused crop for the embedding (the low-light accuracy win: +7…+26 points)
Files: `CameraPreview.kt`, `AttendanceScreen.kt` (`captureClick` / the doc-20 WP8 auto-capture), new `domain/face/TemporalDenoiser.kt`.
1. Ring buffer of the last **4** aligned crops (`IntArray` 112×112) plus their `timestampNanos` and track box. Clear it when the track changes (box IoU with the previous box < 0.5) or after 600 ms.
2. Before embedding: drop any crop whose NCC with the newest crop is < 0.92 (motion or blink). If ≥ 2 crops remain, average them per channel. Otherwise use the newest crop.
3. Apply `levels()` (WP-B) to the averaged crop **when `faceLuma < 90` or the face is backlit** (backlit = frame mean luma − faceLuma > 50). Otherwise use the crop as is. In normal light, levels cost about 5 points (0.90→0.85), so do **not** apply it unconditionally.
4. **Never** apply CLAHE to the ArcFace input (§2.1).
5. Enrollment stays unchanged (normal light). Optionally, also store a levels-normalised template: run `exp5` with a levelled gallery before deciding.
6. Keep `acceptThreshold 0.50 / margin 0.08 / confirmBelow 0.60`. FAR stayed 0 with the fused crop. Re-run `calibrate.py` on the fused pipeline before changing any threshold.

### WP-D — PAD pixel gates: stop false "Screen detected" (and stop calling quality problems spoofs)
Files: `PresentationAttackAnalyzer.kt`, `PresentationAttackDetector.kt`, `LivenessEngine.kt` (or the doc-20 `LivenessPolicy` / `LivenessOrchestrator`).
1. Add `noiseSigma` (WP-B, on the same 96×96 grey image) to `PresentationAttackSignals`. Then `hfeCompensated = max(0, hfe*255 − 3.6*noiseSigma)/255`.
2. Thresholds: `maxHighFrequencyEnergy` 0.18 → **0.22, applied to `hfeCompensated`**. `maxSpecularHighlightRatio` 0.10 → **0.25**. Print gate: `centerTextureStdDev < 3.0` with **no** blur condition.
3. Delete the `blurVariance < 15` and `brightness < 45` → `PASSIVE_SPOOF_SUSPECTED` checks in `LivenessEngine` (doc 20 WP1.1). If doc 20 WP1 is already in `39a221b`, verify it is really gone.
4. Run the PAD on the **un-levelled, un-averaged** newest crop. Averaging removes moiré, which would hide real screens.
5. **A single-frame pixel PAD must never reject on its own.** Require the same PAD reason on ≥ 2 of the last 3 frames before rejecting. A one-off trip only adds risk to the doc-20 risk fusion.
6. Attack detection must come from the doc-20 WP6 MiniFASNet and WP7 cues. These must be measured on real attacks (§6.2) before they are allowed to reject.

### WP-E — Camera (verify doc-20 WP2 in `39a221b`; these are the settings that matter for backlight)
1. **Face-metered AE** (`startFocusAndMetering` with `FLAG_AE` on the face box, re-issued every 1.5 s) plus an **EV compensation loop** targeting face luma 110±20. This is the single biggest win for "window behind the student". Check the metering-point coordinate mapping with the front camera (mirroring) by drawing the point in debug builds.
2. AE target-fps range with lower bound 7–15, chosen from the supported list (never hard-code `Range(10,30)`).
3. **If 1280×720 stays,** WP-A must ship in the same build (gate independent of resolution). Otherwise go back to 640×480 analysis. That is still enough: an interocular of about 60 px at arm's length.
4. Screen flash (WP2.4) only when faceLuma < 35, or < 60 with EV already at max.

### WP-F — UX: the student should not have to "find the light"
1. Per-frame guidance priority: `NO_FACE > MULTIPLE > TOO_SMALL (move closer) > POSE > DARK (screen flash on automatically, no text) > BLUR (hold still)`. Show "hold still" only if BLUR repeats on 3 consecutive frames, never on one.
2. Auto-capture once 2–4 usable frames of the same track exist (≤ 600 ms). No manual retries.
3. Rename strings: "Too blurry" → "Hold the phone still for a moment".

---

## 4. Expected effect (from the measurements; confirm on a device)

| condition | current main (pass gate × genuine) | after WP-A…C |
|---|---|---|
| normal | 0.80 × 0.90 ≈ 72 % per frame | 1.00 × 0.90 ≈ 90 % per frame |
| dim | 0 % (always "Too blurry") | ≈ 87 % |
| backlit | 0 % | ≈ 85 % |
| very dim / hard backlight | 0 % | 74–85 % per attempt (higher with face-metered AE + flash) |
| "Screen detected" on genuine, dim + sharpening | 100 % | 0 % (attack rate unmeasured) |

(LFW probes are different photos of the person taken years apart. Same-day classroom probes score higher, so the absolute genuine numbers are pessimistic.)

## 5. Explicit non-goals and risks
- Pixel-statistic PAD is weak by nature. Relaxing it to stop false rejects lowers attack detection. That is acceptable **only** with doc-20 WP6/WP7 in place, plus 2-of-3 temporal confirmation.
- Simulated low light is not real sensor data. ISP denoising differs per phone. §6 is mandatory before release.

## 6. Device validation protocol (must run before merging to main)
### 6.1 Genuine (per lighting: good, dim tube light, window behind, evening)
- 10 students × 5 attempts each, normal sitting posture, phone at chest height. Record time-to-mark and retries (enable `adb logcat -s AdaptiveVerify CameraPreview`).
- Pass criteria: ≥ 95 % marked within 2 attempts in good and dim light, ≥ 85 % with the window behind, 0 wrong-student marks.
### 6.2 Attacks
- Enrolled student's photo on: phone screen (max brightness), laptop screen, A4 colour print. 20 attempts each, under good and dim light.
- Record the per-frame PAD signals (hfe, hfeCompensated, noiseSigma, highlight, centerStd) and the MiniFASNet score into the debug CSV (doc 20 WP0.3).
- Then set the final thresholds: choose the PAD threshold so that genuine false rejects ≤ 2 % and attack acceptance is as low as possible. Record the result in `results/exp7_device_pad.json`.

## 7. How to reproduce
```bash
pip install ai-edge-litert pyarrow opencv-python pillow numpy
mkdir -p .datasets && curl -L -o .datasets/lfw.parquet https://huggingface.co/api/datasets/bitmind/lfw/parquet/default/train/0.parquet
python tools/biometric_eval/extract_datasets.py lfw
python tools/biometric_eval/exp6_gates.py 40       # ~15 s
python tools/biometric_eval/exp5_lowlight.py 60    # ~10 min (writes results incrementally)
```
