# Quality & Adaptive Verification

> 23 nodes

## Key Concepts

- **.evaluate()** (44 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **FaceQualityEngineTest** (24 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.signals()** (21 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.rejectedReason()** (17 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.leftStep_onlyNegativeProfileYawAccepted()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.rightStep_onlyPositiveProfileYawAccepted()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.straightStep_frontalYawAccepted_extremeYawRejectedWithStepReason()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.blurredFace_rejectedAsBlur()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.darkFace_rejectedAsDark()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.extremePose_rejectedAsPose()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.eyesNotRequired_nullEyeProbabilities_safeConfiguredFallbackNoCrash()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.eyesRequired_lowEyeProbability_rejectedAsEyesUnclear()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.eyesRequired_nullEyeProbabilities_rejectedAsEyesUnclear()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.malformedNonFiniteSignal_safeRejectNoCrash()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.multipleFaces_rejectedAsMultipleFaces()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.noFace_rejectedAsNoFace()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.offFrameFace_rejectedAsOffCenter()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.overexposedFace_rejectedAsOverexposed()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.profileSteps_pitchStillGloballyBounded()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.smallFace_rejectedBeforeLivenessOrModel()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.validFrontalFace_acceptedWithUnitRangeScoreAndFrontalBucket()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.poseBuckets_distinguishLeftRightUpDownFrontal()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.thresholdConfigVersionChange_acceptedCarriesCurrentVersion()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (11 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (9 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (4 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (3 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (3 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (1 shared connections)
- [UI Theme & Components](UI_Theme_&_Components.md) (1 shared connections)
- [Glowbuttonstate Error](Glowbuttonstate_Error.md) (1 shared connections)
- [Full Biometric Pipeline Integration Test](Full_Biometric_Pipeline_Integration_Test.md) (1 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (1 shared connections)
- [Startup & Session Recovery](Startup_&_Session_Recovery.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`

## Audit Trail

- EXTRACTED: 103 (94%)
- INFERRED: 7 (6%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*