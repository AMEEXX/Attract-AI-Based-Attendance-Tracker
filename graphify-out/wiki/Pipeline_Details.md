# Pipeline Details

> 18 nodes

## Key Concepts

- **Pipeline Details** (9 connections) — `docs/08-lld-camera-and-frame-processing.md`
- **CameraTuningTest** (7 connections) — `app/src/test/java/com/attract/attendance/domain/face/CameraTuningTest.kt`
- **.chooseAeFpsRange()** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/CameraTuning.kt`
- **CameraTuning** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/CameraTuning.kt`
- **WP2 — Camera pipeline for low light (CameraX 1.4.2, no version bump)** (6 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **.nextExposureIndex()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/CameraTuning.kt`
- **.shouldUseScreenFlash()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/CameraTuning.kt`
- **.shouldUseScreenFlash_appliesHysteresisAndThresholds()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/CameraTuningTest.kt`
- **.chooseAeFpsRange_returnsNullWhenNoRangeMeetsCriteria()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/CameraTuningTest.kt`
- **.chooseAeFpsRange_selectsLowestLowerBoundMeetingCriteria()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/CameraTuningTest.kt`
- **.nextExposureIndex_stepsTowardsTargetLuma()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/CameraTuningTest.kt`
- **IntRange** (2 connections)
- **WP2.3 Face-metered exposure (the biggest low-light win)** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP2.4 Screen flash ("Retina-flash" equivalent) for very low light** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP2.5 Tests** (2 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **CameraTuning.kt** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/CameraTuning.kt`
- **WP2.1 Resolution** (1 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`
- **WP2.2 Let auto-exposure use longer exposures** (1 connections) — `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`

## Relationships

- [CameraPreview.kt](CameraPreview.kt.md) (4 shared connections)
- [AttendanceScreen.kt](AttendanceScreen.kt.md) (2 shared connections)
- [TemporalCropBuffer](TemporalCropBuffer.md) (2 shared connections)
- [test](test.md) (1 shared connections)
- [13. File checklist (new or changed)](13._File_checklist_new_or_changed.md) (1 shared connections)
- [Change-control record](Change-control_record.md) (1 shared connections)
- [AttendanceScreen](AttendanceScreen.md) (1 shared connections)
- [assertTrue](assertTrue.md) (1 shared connections)
- [LLD-08 — Camera & Frame Processing](LLD-08_—_Camera_&_Frame_Processing.md) (1 shared connections)
- [.align](align.md) (1 shared connections)
- [FrameBundle](FrameBundle.md) (1 shared connections)
- [YoloFaceDetector](YoloFaceDetector.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/CameraTuning.kt`
- `app/src/test/java/com/attract/attendance/domain/face/CameraTuningTest.kt`
- `docs/08-lld-camera-and-frame-processing.md`
- `docs/20-faceid-grade-recognition-liveness-lowlight-plan.md`

## Audit Trail

- EXTRACTED: 27 (66%)
- INFERRED: 14 (34%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*