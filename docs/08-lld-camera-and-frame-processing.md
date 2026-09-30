# LLD-08 — Camera & Frame Processing

**Status:** Implemented & Fixed (2026-08-23) — face-crop pipeline corrected
**Requirements source:** SDD §§15–16, 22, 28–29, 71–73
**Depends on:** LLD-01, LLD-06

## Goal

Provide a smooth front-camera preview and a controlled stream of useful face observations. Camera work must never run expensive ML on every preview frame and must never leak an `ImageProxy`.

## Components and ownership

```text
CameraController (platform) → FrameAnalyzer → FaceDetectorAdapter → CandidateSelector
                                              ↓
                                      SessionCoordinator event
```

The attendance screen owns a lifecycle-aware `CameraController`; the session coordinator tells it when to bind/unbind. Use CameraX Preview plus ImageAnalysis with `STRATEGY_KEEP_ONLY_LATEST`. The analyzer closes every `ImageProxy` in `finally`, even after cancellation/error.

`FrameCandidate` contains attempt ID, monotonic capture time, orientation/mirroring metadata, face bounding box/tracking ID, and a **short-lived face-cropped pixel buffer** (NOT the full camera frame). It is not persisted or emitted to UI/logging.

## Pipeline

1. Bind front camera only when session state permits acquisition.
2. Analyze latest frame using ML Kit detection/tracking at a device-tested analysis resolution.
3. Reject zero-face frames (emptySignals, null crop) and multiple-face frames (multipleSignals, null crop) explicitly before passing to quality gate.
4. For exactly one detected face: pass detection data and **face-cropped bitmap** (20% margin via `cropFaceFromBitmap()`) to LLD-09 quality gate.
5. CandidateSelector accepts at most one useful candidate per configurable minimum interval and never starts a second expensive pipeline while one attempt is running.
6. Close original frame immediately; downstream copies expire on attempt cancellation.

**Critical invariant (implemented 2026-08-23):** The `onFrameAnalyzed` callback second parameter is the **face-cropped bitmap** when exactly one face is detected, `null` otherwise. The full camera frame is NEVER passed downstream — the face region occupies <5% of a typical 640×480 frame, which would produce garbage TFLite embeddings if the full frame were used.

`cropFaceFromBitmap(bitmap, box, marginFraction=0.20)` in `CameraPreview.kt` is the authoritative central crop utility per LLD-08 spec: "rotation, mirror transform, crop, color-space conversion are central utilities with golden tests." Do not crop differently in each ML adapter.

## Lifecycle/error behavior

Bind after permission and pinning are ready; unbind on ending, background destruction, camera failure, and configuration recreation before rebinding. Camera unavailable, permission revoked, or no front camera becomes a recoverable session error. Preview may display only the current student; never capture to gallery or save a photo.

## Build steps

1. Create fake FrameSource and transform utility with golden buffers.
2. Implement CameraX bind/unbind and KEEP_ONLY_LATEST analyzer.
3. Integrate ML Kit detector adapter and tracking.
4. Add single-flight CandidateSelector and profiling counters.
5. Test on target rotation, front camera, low memory, and long sessions.

## Tests

| Scenario                                        | Expected result                                    |
| ----------------------------------------------- | -------------------------------------------------- |
| analyzer receives 30fps while inference is busy | only latest useful frame retained; no queue growth |
| every success/error/cancel path                 | ImageProxy closed exactly once                     |
| rotation/mirror/crop fixtures                   | face crop maps to expected coordinates             |
| zero face                                       | emptySignals, null crop — quality gate rejects     |
| multiple faces                                  | multipleSignals, null crop — quality gate rejects  |
| single face                                     | face-cropped bitmap passed (not full frame)        |
| stale track / rapid two people                  | candidate rejected or restart required             |
| unbind during analysis                          | job cancelled and resources released               |
| camera permission revocation                    | recoverable guidance, no crash                     |
| 30-minute simulated session                     | bounded memory/fps and no frame leak               |

## Definition of done

Preview remains responsive, heavy processing is single-flight and sampled, **face-crop is applied before any TFLite call**, transformations are tested, and raw frames never persist.

