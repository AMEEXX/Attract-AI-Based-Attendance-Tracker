# LLD-08 — Camera & Frame Processing

**Status:** Implemented & Verified (Updated 2026-11-02 via Doc 20 & Doc 21)
**Requirements source:** SDD §§15–16, 22, 28–29, 71–73, Doc 20 (WP2), Doc 21 (WP-C, WP-E)
**Depends on:** LLD-01, LLD-06

## Goal

Provide a smooth front-camera preview (1280×720 YUV) and a controlled stream of useful face observations for recognition, quality evaluation, and liveness. Camera work must never run expensive ML on every preview frame, must dynamically adapt exposure to dim and backlit environments, and must never leak an `ImageProxy`.

## Components and Architecture

```text
CameraX PreviewView + ImageAnalysis (1280×720 YUV, 10–30 fps)
    │
    ├── Dynamic AE Target FPS (CameraCharacteristics -> CameraTuning.chooseAeFpsRange)
    ├── Face-metered AE (startFocusAndMetering with FLAG_AE re-issued every 1.5s on face center)
    ├── EV Compensation Loop (CameraTuning.nextExposureIndex targeting cropFaceLuma 110±20)
    └── Screen Flash Overlay (CameraTuning.shouldUseScreenFlash on dim/backlit scenes)
    │
    ▼
YoloFaceDetector (YOLOv8n-face, letterbox mapping, IoU FaceTracker)
    │
    ▼
FaceAligner (5-point similarity transform -> 112×112 canonical aligned crop)
    │
    ├── CropMetrics (central 80×80 ROI luma, Immerkær noise sigma, levels stretch, cropSharpness)
    ├── MediaPipe FaceMeshEngine (478 landmarks, blendshapes, 4×4 pose matrix)
    └── PadCrop (wide 160×160 crop for PresentationAttackAnalyzer)
    │
    ▼
TemporalCropBuffer (ring buffer of 4 aligned crops, TTL 600ms, IoU tracking)
    │
    ▼
FrameBundle (emitted to AttendanceScreen for auto-capture and liveness orchestration)
```

The attendance screen owns a lifecycle-aware `CameraPreview`; the session coordinator tells it when to bind/unbind. ImageAnalysis operates with `STRATEGY_KEEP_ONLY_LATEST` and throttles analyzer runs to at most 1 frame per 150ms. The analyzer closes every `ImageProxy` in `finally`, even after cancellation/error.

## Pipeline Details

1. **Resolution & Format:**
   Analysis stream uses 1280×720 YUV_420_888 (`ResolutionSelector` with `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER`), ensuring crisp resolution for face detection and inter-ocular distance measurement.
2. **Dynamic AE Target FPS Selection (Doc 21 WP-E):**
   Queries `CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` for the active lens facing. Uses `CameraTuning.chooseAeFpsRange()` to select an FPS range with lower bound in 7..15 and upper bound $\ge 24$, allowing longer integration times (up to ~100ms) in low-light conditions. Never hard-codes FPS ranges.
3. **Face-Metered AE & EV Compensation Loop (Doc 21 WP-E):**
   - When a face is tracked, `startFocusAndMetering` with `FLAG_AE` is re-issued every 1.5s centered on the face box (with X coordinate horizontally mirrored for the front camera).
   - Every 500ms, an EV adjustment loop evaluates central `cropFaceLuma` against target $110 \pm 20$, stepping EV compensation index up or down by 1 within hardware range limits.
4. **Screen Flash Assistance:**
   If `cropFaceLuma < 35` or `< 60` with EV at maximum, `CameraTuning.shouldUseScreenFlash` activates `ScreenFlashOverlay` with hysteresis (deactivating when luma $> 85$).
5. **Detection & Alignment:**
   - Detects face with `YoloFaceDetector` (`yolov8n_face.tflite`).
   - If 0 faces: emits `emptySignals` and null crop.
   - If > 1 face: emits `multipleSignals` and null crop.
   - For exactly 1 face: aligns with 5-point similarity transform via `FaceAligner.align()` to 112×112 ArcFace canonical dimensions.
6. **Multi-Frame Fused Crop Buffer (Doc 21 WP-C):**
   - `TemporalCropBuffer` retains a ring buffer of the last 4 aligned crops with `timestampNanos` and bounding box.
   - Clears automatically if track changes (IoU < 0.5) or time delta > 600ms.
   - Fuses crops via `TemporalDenoiser.fuse()`: drops frames with NCC < 0.92, averages channels, and applies robust auto-levels when dim (`faceLuma < 90`) or backlit (`frameMeanLuma - faceLuma > 50`).
7. **Downstream Delivery:**
   `FrameBundle` delivers `alignedCrop`, `fusedCrop`, `wideCrop`, `qualitySignals`, `meshResult`, `trackId`, and `trackFrameCount`. Original frames are immediately closed; raw images are never persisted.

## Tests

| Scenario | Expected result |
|---|---|
| Analyzer receives 30fps while inference is busy | Only latest frame retained; no queue growth |
| Every success/error/cancel path | ImageProxy closed exactly once in `finally` |
| Zero face detected | emptySignals, null crop emitted |
| Multiple faces detected | multipleSignals, null crop emitted |
| Single face detected | 112×112 aligned face crop and fusedCrop emitted |
| High noise/low light | Dynamic FPS range chosen; face-metered AE issued every 1.5s |
| Drifting face track | IoU > 0.5 maintains trackId; jump creates new track |
| Buffer capacity & TTL | `TemporalCropBuffer` caps at 4 frames; expires after 600ms |
| Unbind during analysis | Camera unbinds cleanly, resources released |

## Definition of Done

Preview is smooth and responsive; analysis is non-blocking and zero-allocation on the hot path; camera adapts to dim and backlit classrooms; aligned and fused crops are provided cleanly to recognition; raw frames are never persisted.
