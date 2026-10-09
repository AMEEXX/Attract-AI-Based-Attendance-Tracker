# Drive Backup & Export

> 19 nodes

## Key Concepts

- **FaceQualitySignals** (34 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualitySignals.kt`
- **FaceQualityEngine** (11 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **FaceQualityConfig** (10 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityConfig.kt`
- **ExpectedPose** (9 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- **.score()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.realImageDegradation_excessiveBlur_rejectedAsBlur()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- **.realImageDegradation_offCenterDisplacement_rejectedAsOffCenter()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- **.isAcceptableSupportPose()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/AdaptiveVerificationEngine.kt`
- **.averageEyeOpenProbability()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.isDistinctFromCaptured()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.isInFrame()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.yawInWindow()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **.logFailure()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/ConsolidatedFaceVerificationPipelineTest.kt`
- **.yawDiversityGuard_rejectsNearIdenticalCaptures()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.eyesClear()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **FaceQualitySignals.kt** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualitySignals.kt`
- **LEFT** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- **RIGHT** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- **STRAIGHT** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (29 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (10 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (3 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (3 shared connections)
- [Full Biometric Pipeline Integration Test](Full_Biometric_Pipeline_Integration_Test.md) (2 shared connections)
- [UI Theme & Components](UI_Theme_&_Components.md) (2 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (2 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)
- [Glowbuttonstate Error](Glowbuttonstate_Error.md) (1 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/domain/face/ImageDegradationValidationTest.kt`
- `app/src/main/java/com/attract/attendance/domain/face/AdaptiveVerificationEngine.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityConfig.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityResult.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceQualitySignals.kt`
- `app/src/test/java/com/attract/attendance/domain/face/ConsolidatedFaceVerificationPipelineTest.kt`
- `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`

## Audit Trail

- EXTRACTED: 63 (79%)
- INFERRED: 17 (21%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*