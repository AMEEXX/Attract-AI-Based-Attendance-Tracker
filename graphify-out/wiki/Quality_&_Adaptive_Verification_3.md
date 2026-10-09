# Quality & Adaptive Verification

> 42 nodes

## Key Concepts

- **.check()** (25 connections) — `app/src/main/java/com/attract/attendance/domain/face/LivenessEngine.kt`
- **.analyze()** (13 connections) — `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackDetector.kt`
- **PresentationAttackSignals** (11 connections) — `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackSignals.kt`
- **PresentationAttackDetectorTest** (10 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.signals()** (9 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **LivenessReason** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/LivenessEngine.kt`
- **LivenessResult** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/LivenessEngine.kt`
- **LivenessEngineTest** (7 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.signals()** (7 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **PresentationAttackAnalyzer.kt** (6 connections) — `app/src/main/java/com/attract/attendance/feature/attendance/PresentationAttackAnalyzer.kt`
- **.analyze()** (5 connections) — `app/src/main/java/com/attract/attendance/feature/attendance/PresentationAttackAnalyzer.kt`
- **.livenessCheck_integratesPresentationAttackGates()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **PresentationAttackAnalyzer** (4 connections) — `app/src/main/java/com/attract/attendance/feature/attendance/PresentationAttackAnalyzer.kt`
- **.closedEyes_rejected()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.lowLight_rejected()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.naturalMovementCrossFrame_passed()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.staticCrossFramePresentationAttack_rejected()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.staticLowQualityBlur_rejected()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- **.printedPhoto_flatSharpTexture_rejectedAsPrintPhoto()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.screenGlare_specularHighlights_rejectedAsScreenReplay()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.screenReplay_highFrequencyMoireEnergy_rejected()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **LivenessEngine.kt** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/LivenessEngine.kt`
- **PresentationAttackDetector** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackDetector.kt`
- **.centerStdDev()** (3 connections) — `app/src/main/java/com/attract/attendance/feature/attendance/PresentationAttackAnalyzer.kt`
- **.meanAbsoluteLaplacian()** (3 connections) — `app/src/main/java/com/attract/attendance/feature/attendance/PresentationAttackAnalyzer.kt`
- *... and 17 more nodes in this community*

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (17 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (4 shared connections)
- [UI Theme & Components](UI_Theme_&_Components.md) (3 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (3 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (3 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (2 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (2 shared connections)
- [Galleryloadresultkt Emptyhealthy](Galleryloadresultkt_Emptyhealthy.md) (2 shared connections)
- [Glowbuttonstate Error](Glowbuttonstate_Error.md) (1 shared connections)
- [Full Biometric Pipeline Integration Test](Full_Biometric_Pipeline_Integration_Test.md) (1 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/LivenessEngine.kt`
- `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackDetector.kt`
- `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackSignals.kt`
- `app/src/main/java/com/attract/attendance/feature/attendance/PresentationAttackAnalyzer.kt`
- `app/src/test/java/com/attract/attendance/domain/face/LivenessEngineTest.kt`
- `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`

## Audit Trail

- EXTRACTED: 99 (87%)
- INFERRED: 15 (13%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*