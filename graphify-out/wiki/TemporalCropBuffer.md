# TemporalCropBuffer

> 18 nodes

## Key Concepts

- **TemporalCropBuffer** (15 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **TemporalDenoiserTest** (9 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemporalDenoiserTest.kt`
- **.fuse()** (8 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **.denoise()** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **TemporalDenoiser** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **.add()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **IntArray** (6 connections)
- **.clear()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **.fuse_appliesLevelsWhenDimOrBacklit()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemporalDenoiserTest.kt`
- **.getFusedCrop()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **.normalizedCrossCorrelation()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **.denoise_lowersNoiseStandardDeviationByAtLeast40Percent()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemporalDenoiserTest.kt`
- **.temporalCropBuffer_clearsWhenTrackMovesSignificantly()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemporalDenoiserTest.kt`
- **.temporalCropBuffer_keepsAtMostMaxCapacity()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemporalDenoiserTest.kt`
- **Entry** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **.getCrops()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- **.denoise_excludesShiftedOrCorruptedCrop()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemporalDenoiserTest.kt`
- **.size()** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`

## Relationships

- [FaceBox](FaceBox.md) (4 shared connections)
- [sqrt](sqrt.md) (2 shared connections)
- [Change-control record](Change-control_record.md) (2 shared connections)
- [Pipeline Details](Pipeline_Details.md) (2 shared connections)
- [LLD-16 — Adaptive 1→2→3 Frame Biometric Verification](LLD-16_—_Adaptive_1→2→3_Frame_Biometric_Verification.md) (2 shared connections)
- [CropMetricsTest](CropMetricsTest.md) (2 shared connections)
- [assertTrue](assertTrue.md) (2 shared connections)
- [CameraPreview.kt](CameraPreview.kt.md) (1 shared connections)
- [LLD-08 — Camera & Frame Processing](LLD-08_—_Camera_&_Frame_Processing.md) (1 shared connections)
- [20 — "Face-ID-grade" Recognition, Anti-Spoofing & Low-Light Plan (RGB-only)](20_—__Face-ID-grade__Recognition,_Anti-Spoofing_&_Low-Light_Plan_RGB-only.md) (1 shared connections)
- [test](test.md) (1 shared connections)
- [LLD-11 — Face Recognition & Decision Engine](LLD-11_—_Face_Recognition_&_Decision_Engine.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/TemporalDenoiser.kt`
- `app/src/test/java/com/attract/attendance/domain/face/TemporalDenoiserTest.kt`

## Audit Trail

- EXTRACTED: 37 (67%)
- INFERRED: 18 (33%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*