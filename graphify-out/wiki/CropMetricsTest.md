# CropMetricsTest

> 23 nodes

## Key Concepts

- **CropMetricsTest** (13 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **CropMetrics** (11 connections) — `app/src/main/java/com/attract/attendance/domain/face/CropMetrics.kt`
- **.makeRgbPixel()** (9 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **.computeFaceLuma()** (8 connections) — `app/src/main/java/com/attract/attendance/domain/face/CropMetrics.kt`
- **.applyLevels()** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/CropMetrics.kt`
- **.computeCropSharpness()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/CropMetrics.kt`
- **.computeNoiseSigma()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/CropMetrics.kt`
- **.extractRoiLuma()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/CropMetrics.kt`
- **.makeUniformCrop()** (6 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **.applyLevels_dimFace_increasesBrightnessAndContrast()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **.computeNoiseSigma_withSyntheticNoise_returnsExpectedOrderOfMagnitude()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **IntArray** (5 connections)
- **.lumaOf()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/CropMetrics.kt`
- **.computeCropSharpness_flatCrop_returnsZero()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **.computeCropSharpness_sharpEdgeVsBlurredEdge()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **.computeFaceLuma_blackAndWhite_returnsExpected()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **.computeFaceLuma_uniformGrey_returnsExactLuma()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **.computeNoiseSigma_smoothCrop_returnsZero()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **.applyLevels_lowContrastCrop_returnsUnchanged()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **16. Face Quality** (3 connections) — `docs/00-main-sdd.md`
- **.nextGaussian()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- **FloatArray** (1 connections)
- **IntArray** (1 connections)

## Relationships

- [CameraPreview.kt](CameraPreview.kt.md) (5 shared connections)
- [assertTrue](assertTrue.md) (3 shared connections)
- [LLD-09 — Face Quality Engine](LLD-09_—_Face_Quality_Engine.md) (2 shared connections)
- [TemporalCropBuffer](TemporalCropBuffer.md) (2 shared connections)
- [sqrt](sqrt.md) (1 shared connections)
- [Change-control record](Change-control_record.md) (1 shared connections)
- [test](test.md) (1 shared connections)
- [00-main-sdd.md](00-main-sdd.md.md) (1 shared connections)
- [exp5_lowlight.py](exp5_lowlight.py.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/CropMetrics.kt`
- `app/src/test/java/com/attract/attendance/domain/face/CropMetricsTest.kt`
- `docs/00-main-sdd.md`

## Audit Trail

- EXTRACTED: 61 (88%)
- INFERRED: 8 (12%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*