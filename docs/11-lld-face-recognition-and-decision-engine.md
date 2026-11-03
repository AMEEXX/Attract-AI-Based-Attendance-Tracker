# LLD-11 — Face Recognition & Decision Engine

**Status:** Implemented & Verified (Updated 2026-11-02 via Doc 20 & Doc 21)
**Requirements source:** SDD §§21–26, 29–31, 71–73, Doc 20 (WP5), Doc 21 (WP-C, WP-F)
**Depends on:** LLD-02, LLD-06, LLD-08–10, LLD-12, Decision D-004

## Goal

Recognize an enrolled student accurately under challenging classroom lighting (normal, dim, and backlit) using ArcFace 512-D BGR embeddings, multi-frame crop fusion, and calibrated decision thresholds. Uncertainty fails closed to teacher assistance or retry without risking false acceptance.

## Biometric Model Profile: ArcFace 512-D BGR

The production model profile is `arcface512_bgr_noseyaw_v5`:

```kotlin
data class BiometricModelProfile(
    val profileId: String = "arcface512_bgr_noseyaw_v5",
    val modelAssetPath: String = "arcface_512.tflite",
    val embeddingDimension: Int = 512,
    val inputWidth: Int = 112,
    val inputHeight: Int = 112,
    val colorOrder: ColorOrder = ColorOrder.BGR,
    val qualityConfigId: String = "quality_v4",
    val acceptThreshold: Float = 0.50f,
    val marginThreshold: Float = 0.08f,
    val confirmBelow: Float = 0.60f,
    val continuityThreshold: Float = 0.55f,
)
```

Input alignment uses 5-point similarity transform (`FaceAligner.align`) producing a 112×112 canonical aligned crop, normalized with BGR channel ordering.

## Multi-Frame Fused Crop Pipeline (Doc 21 WP-C)

To prevent raw camera sensor noise from corrupting ArcFace embeddings in low light or backlit conditions, embeddings are extracted from a temporally fused crop:

```text
Camera Frame Stream
    │
    ▼
FaceAligner (112×112 canonical crop)
    │
    ▼
TemporalCropBuffer (ring buffer of 4 aligned crops, TTL 600ms, IoU tracking >= 0.5)
    │
    ▼
TemporalDenoiser.fuse():
    1. Drop crops whose Normalized Cross-Correlation (NCC) with newest crop < 0.92
    2. If >= 2 crops remain: average pixel values per channel across crops
    3. Apply robust auto-levels (CropMetrics.applyLevels) IF:
       faceLuma < 90 OR backlit (frameMeanLuma - faceLuma > 50)
    4. Otherwise use averaged crop as-is
    │
    ▼
ArcFace 512-D Embedding Extraction (EmbeddingEngine)
```

### Critical Guardrails:
1. **Never apply CLAHE to ArcFace input:** Empirical evaluation in Doc 21 §2.1 proved that CLAHE on ArcFace input degrades genuine scores by 4–5 points (dim 0.63 $\to$ 0.59, hard backlight 0.48 $\to$ 0.45). CLAHE is permitted only for detection/PAD.
2. **Levels Conditioning:** Auto-levels are applied only in degraded lighting (`faceLuma < 90` or backlit), preserving unmodified raw colors in standard ambient light.

## Matching & Decision Algorithm

1. **Active Template Cache:**
   At session start, Room decrypts active 512-D templates for the class into `ClassTemplateCache`. Templates with stale dimensions (e.g. legacy 192-D) are safely filtered by `TemplateCompatibility` without crashing.
2. **Scoring:**
   Compute cosine similarity:
   $$\text{sim}(u, v) = \frac{u \cdot v}{\|u\| \|v\|}$$
   For each student, retain their maximum similarity across all enrolled templates.
3. **Ranking:**
   - Best score = $B$ (highest similarity student)
   - Second score = $S$ (second-highest similarity student)
   - Margin = $B - S$
4. **Decision Engine:**
   - **Match(studentId, confidence):** $B \ge 0.50$ AND Margin $\ge 0.08$. If $B < 0.60$ and adaptive frames remain, the adaptive engine requests one more frame to confirm.
   - **Ambiguous:** $B \ge 0.50$ but Margin $< 0.08$. Prompts for another look; never guesses.
   - **Unknown:** $B < 0.50$.
   - **Unavailable:** Model asset missing or corrupted; fails closed.

## Live Attendance Auto-Capture (Doc 21 WP-F)

Live attendance features seamless auto-capture:
1. As the student stands in front of the camera, frames are tracked by `FaceTracker`.
2. When the quality engine approves frames (`QualityResult.Accepted`) and the `TemporalCropBuffer` accumulates 2 to 4 usable frames along the same track within 600ms, verification auto-triggers.
3. No manual screen taps or retry buttons are required for standard check-in.

## Tests

| Test Case | Verification Target |
|---|---|
| `TemporalDenoiserTest` (5 tests) | Validates channel fusion, $\ge 40\%$ noise standard deviation reduction, NCC outlier exclusion, ring buffer capacity (4 frames), and TTL/IoU clearance. |
| `RecognitionDecisionEngineTest` (4 tests) | High match accepted; low similarity returns Unknown; close scores return Ambiguous; empty templates return NoTemplatesAvailable. |
| `TemplateCompatibilityTest` (8 tests) | Stale dimension vectors filtered closed; current 512-D vectors match; mixed dimension galleries ignore stale templates safely. |

## Definition of Done

ArcFace 512-D embeddings are robust to dim and backlit illumination; multi-frame fusion achieves 0 FAR with high low-light recall; decisions fail closed; live auto-capture operates seamlessly without manual retries.
