# 22 — Backlight "Screen replay detected" false rejects + laptop-photo acceptance: root cause, measurements, fix plan

Status: IN PROGRESS (checkpoint 1 — code audit only). Base: `origin/dev` @ `f85e7a8`.
Branch: `genspark_ai_developer_backlight_spoof`.

## 0. Progress log (read first if resuming)
| # | Item | State |
|---|---|---|
| 0.1 | Code audit of dev liveness path | DONE (see §1) |
| 0.2 | Experiments (backlight FP, planar-vs-3D parallax, PAD CNN) | TODO |
| 0.3 | Final implementation plan | TODO |

## 1. Audit findings on dev (f85e7a8)
1. `AttendanceScreen.captureClick` → `livenessOrchestrator.onFrame(signals, patSignals, mesh, padScore = null)`.
   **padScore is always null**: no MiniFASNet model exists in `app/src/main/assets` (only yolov8n_face, arcface, face_landmarker).
2. `LivenessOrchestrator.evaluate`: `RiskFusion` returns MEDIUM when there is no PAD score, no flash score,
   no challenge result and depth is inconclusive. **MEDIUM is mapped to `LivenessResult.Passed`**.
   `startChallenge()` and `setFlashScore()` are **never called** anywhere. So in practice only two things can reject:
   (a) the pixel veto, (b) a conclusive flat parallax result.
3. Parallax is never conclusive: `onFrame` is called only on capture (not per preview frame), auto-capture fires
   when `trackFrameCount in 2..4`, so `meshHistory` holds 1–2 frames with < 4° yaw change → `depthInconclusive=true`.
   **=> a still laptop/phone photo always ends in MEDIUM → Passed → recognised. This is the laptop bypass.**
4. Pixel PAD runs on `wideCrop` = 2.7× face box (PadCrop scale 2.7) → **most of that crop is background**.
   `specularHighlightRatio` = fraction of pixels with R,G,B > 235. A blown-out window / white wall fills > 25 %
   of the wide crop → "Screen glare detected" → `SCREEN_REPLAY_SUSPECTED`. The face/bright-background silhouette
   edge also inflates `highFrequencyEnergy`. **=> backlight false reject.** A dark laptop background has no
   saturated pixels → passes. The gate is inverted relative to reality.
5. 2-of-3 temporal confirmation bug: `onFrame` appends the trip reason, then `evaluate` appends it AGAIN when the
   list has < 3 entries → a single tripped frame produces two identical entries → immediate veto on first capture.

## 2. Checkpoint 2 — measured (exp7 scenes 200 LFW ids; exp8 Pointing'04 15 subjects)

### 2.1 exp7 — dev pixel PAD on the 2.7x wide crop (exact port of PadCrop + PresentationAttackAnalyzer + Detector)
| scene | dev pixel PAD reject | reason | MiniFASNetV2 live p50 |
|---|---|---|---|
| genuine, normal background | 1/200 (0.5 %) | GLARE | 0.961 |
| **genuine, white sun-lit wall** | **200/200 (100 %)** | **SCREEN_GLARE** (wide-crop specular p50 = 0.49 > 0.25) | 0.983 |
| **genuine, window behind** | **200/200 (100 %)** | SCREEN_GLARE | 0.978 |
| **genuine, hard window backlight** | **182/182 (100 %)** | SCREEN_GLARE | 0.988 |
| **attack, photo on laptop, dark bg** | **0/198 (0 %)** | — (passes) | **0.002** |
| attack, photo on phone, dark bezel | 0/199 (0 %) | — | 0.435 |
| attack, A4 print | 0/200 (0 %) | — | 0.941 |
Inner face box (1.0x) specular ratio p95 = 0.000–0.002 in every genuine scene => the "glare" is 100 % background.
**Root cause of the backlight false reject is proven: `specularHighlightRatio` is measured on a crop that is mostly background.**
**Root cause of the laptop acceptance is proven: no pixel gate fires on dark-background screens, and nothing else can reject (§1.2–1.3).**

### 2.2 exp8 — MediaPipe 3D parallax (ParallaxDepthTest port) on REAL head rotation vs FLAT photo moved/tilted
| pair | dev residual p5 / p50 / p95 | MediaPipe |Δyaw| p50 / max |
|---|---|---|
| real head pan 15° | 0.042 / 0.060 / 0.091 | 11.6 / 17.4 |
| real head pan 30° | 0.109 / 0.154 / 0.224 | 23.1 / 30.5 |
| real head tilt 15° | 0.015 / 0.046 / 0.073 | 2.1 / 9.1 |
| flat photo turned 5° | 0.004 / 0.006 / 0.010 | 1.1 / 2.2 |
| flat photo turned 15° | 0.009 / 0.013 / 0.021 | 2.9 / 5.0 |
| flat photo turned 25° | 0.016 / 0.021 / 0.031 | 4.9 / 7.6 |
| flat photo tilted 15° | 0.006 / 0.013 / 0.020 | 0.4 / 4.4 |
| flat photo static + hand jitter | 0.002 / 0.004 / 0.007 | 0.4 / 1.4 |
AUC real-pan15 vs all planar = **0.998**. Threshold 0.030: real yaw turn passes 97 %, planar passes 1 %; 0.035: 97 % / 0 %.
**The 3D test itself works. It never runs** because (a) meshHistory gets 1–2 frames only, (b) a static photo gives Δyaw < 4° so the
result is "inconclusive" and inconclusive → MEDIUM → Passed. Fix = make depth MANDATORY (inconclusive must NOT pass) and collect
mesh on every preview frame while prompting a small head turn. Note: MediaPipe yaw of a flat photo turned 25° is only ~5°,
so the trigger must be the yaw of a real head (≥ 8°), and the current 0.020 "live" residual is too low (planar p95 0.031) → use 0.035.
