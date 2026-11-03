# 21 — Why recognition got WORSE after doc 20, and the measured fix (low light, backlight, "Too blurry", "Screen detected")

> 12 nodes

## Key Concepts

- **21 — Why recognition got WORSE after doc 20, and the measured fix (low light, backlight, "Too blurry", "Screen detected")** (9 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **1. The root cause in one paragraph** (3 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **2. Measurements (shipped YOLOv8n-face + ArcFace, BGR input exactly as `EmbeddingEngine.kt`)** (3 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **6. Device validation protocol (must run before merging to main)** (3 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **2.1 exp5 — 52 LFW identities, enrolled in normal light, probed with a *different* photo** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **2.2 exp6 — gate calibration on 34 genuine crops** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **4. Expected effect (from the measurements; confirm on a device)** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **5. Explicit non-goals and risks** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **6.1 Genuine (per lighting: good, dim tube light, window behind, evening)** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **6.2 Attacks** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`
- **7. How to reproduce** (1 connections) — `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`

## Relationships

- [LivenessOrchestrator](LivenessOrchestrator.md) (2 shared connections)
- [CameraPreview.kt](CameraPreview.kt.md) (1 shared connections)
- [FaceQualitySignals](FaceQualitySignals.md) (1 shared connections)

## Source Files

- `docs/21-lowlight-blur-gate-and-pad-false-positive-fix-plan.md`

## Audit Trail

- EXTRACTED: 13 (87%)
- INFERRED: 2 (13%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*