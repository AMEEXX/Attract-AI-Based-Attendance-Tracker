# .evaluate

> 27 nodes

## Key Concepts

- **.evaluate()** (50 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- **FaceQualityEngineTest** (32 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.signals()** (24 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.rejectedReason()** (18 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.v4Defaults()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/FaceQualityConfig.kt`
- **.darkSharpCrop_withV4Config_isAccepted()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.leftStep_onlyNegativeProfileYawAccepted()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.lowCropSharpness_withV4Config_isRejectedAsBlur()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.rightStep_onlyPositiveProfileYawAccepted()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
- **.smallFaceRatio_withSufficientInterOcularDistance_isAccepted()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`
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
- *... and 2 more nodes in this community*

## Relationships

- [FaceQualitySignals](FaceQualitySignals.md) (12 shared connections)
- [assertTrue](assertTrue.md) (8 shared connections)
- [ImageDegradationValidationTest](ImageDegradationValidationTest.md) (4 shared connections)
- [.calibrationDefaults](calibrationDefaults.md) (3 shared connections)
- [StudentTemplatePair](StudentTemplatePair.md) (3 shared connections)
- [PoseEstimatorTest](PoseEstimatorTest.md) (3 shared connections)
- [.isAcceptableSupportPose](isAcceptableSupportPose.md) (2 shared connections)
- [QualityResult](QualityResult.md) (2 shared connections)
- [test](test.md) (1 shared connections)
- [.check](check.md) (1 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (1 shared connections)
- [LLD-09 — Face Quality Engine](LLD-09_—_Face_Quality_Engine.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityConfig.kt`
- `app/src/main/java/com/attract/attendance/domain/face/FaceQualityEngine.kt`
- `app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt`

## Audit Trail

- EXTRACTED: 119 (88%)
- INFERRED: 17 (12%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*