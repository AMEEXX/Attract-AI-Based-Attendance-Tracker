# FaceBox

> 20 nodes

## Key Concepts

- **FaceBox** (14 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceTracker.kt`
- **FaceTracker** (10 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceTracker.kt`
- **.box()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/PadCrop.kt`
- **FaceTrackerTest** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceTrackerTest.kt`
- **PadCrop** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/PadCrop.kt`
- **PadCropTest** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/PadCropTest.kt`
- **.update()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceTracker.kt`
- **.padCrop_scalesAroundCenterAndClamps()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/PadCropTest.kt`
- **Live Attendance Auto-Capture (Doc 21 WP-F)** (4 connections) — `docs/11-lld-face-recognition-and-decision-engine.md`
- **.faceTracker_driftingBox_maintainsTrackId()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceTrackerTest.kt`
- **.faceTracker_jumpToOtherSide_allocatesNewTrackId()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceTrackerTest.kt`
- **.faceTracker_largeTimeGap_allocatesNewTrackId()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceTrackerTest.kt`
- **.padCrop_nearEdge_shiftsIntoImage()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/PadCropTest.kt`
- **Accepted** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- **Track** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceTracker.kt`
- **CropRect** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/PadCrop.kt`
- **.iou()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceTracker.kt`
- **FaceTracker.kt** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceTracker.kt`
- **.reset()** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceTracker.kt`
- **PadCrop.kt** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/PadCrop.kt`

## Relationships

- [CameraPreview.kt](CameraPreview.kt.md) (6 shared connections)
- [TemporalCropBuffer](TemporalCropBuffer.md) (4 shared connections)
- [13. File checklist (new or changed)](13._File_checklist_new_or_changed.md) (2 shared connections)
- [QualityResult](QualityResult.md) (1 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (1 shared connections)
- [test](test.md) (1 shared connections)
- [assertTrue](assertTrue.md) (1 shared connections)
- [LLD-11 — Face Recognition & Decision Engine](LLD-11_—_Face_Recognition_&_Decision_Engine.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceTracker.kt`
- `app/src/main/java/com/attract/attendance/domain/face/PadCrop.kt`
- `app/src/test/java/com/attract/attendance/domain/face/FaceTrackerTest.kt`
- `app/src/test/java/com/attract/attendance/domain/face/PadCropTest.kt`
- `docs/11-lld-face-recognition-and-decision-engine.md`

## Audit Trail

- EXTRACTED: 32 (67%)
- INFERRED: 16 (33%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*