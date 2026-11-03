# .analyze

> 14 nodes

## Key Concepts

- **.analyze()** (16 connections) — `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackDetector.kt`
- **PresentationAttackSignals** (15 connections) — `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackSignals.kt`
- **PresentationAttackDetectorTest** (13 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.signals()** (9 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.onFrame()** (5 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- **.livenessCheck_integratesPresentationAttackGates()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.printedPhoto_flatTexture_rejectedAsPrintPhoto()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.screenGlare_specularHighlights_rejectedAsScreenReplay()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.screenReplay_highFrequencyMoireEnergy_rejected()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.cleanLiveLikeSignals_passAllGates()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.nonFiniteSignals_safePassNoCrash()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.screenReplay_withHighNoiseSigma_compensatedHfePassesGenuineDimFace()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **.nullOrUnavailableSignals_passClosedNeverBlocksGenuineUser()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`
- **PresentationAttackSignals.kt** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackSignals.kt`

## Relationships

- [.check](check.md) (6 shared connections)
- [LivenessOrchestrator](LivenessOrchestrator.md) (5 shared connections)
- [assertTrue](assertTrue.md) (4 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (2 shared connections)
- [PresentationAttackAnalyzer.kt](PresentationAttackAnalyzer.kt.md) (2 shared connections)
- [.decide](decide.md) (2 shared connections)
- [StudentTemplatePair](StudentTemplatePair.md) (2 shared connections)
- [FaceQualitySignals](FaceQualitySignals.md) (2 shared connections)
- [test](test.md) (1 shared connections)
- [FlashLivenessTestTest](FlashLivenessTestTest.md) (1 shared connections)
- [FaceMeshResult](FaceMeshResult.md) (1 shared connections)
- [LivenessResult](LivenessResult.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackDetector.kt`
- `app/src/main/java/com/attract/attendance/domain/face/PresentationAttackSignals.kt`
- `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- `app/src/test/java/com/attract/attendance/domain/face/PresentationAttackDetectorTest.kt`

## Audit Trail

- EXTRACTED: 46 (79%)
- INFERRED: 12 (21%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*