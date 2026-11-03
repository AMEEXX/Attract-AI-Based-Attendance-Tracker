# .decide

> 21 nodes

## Key Concepts

- **RiskFusionTest** (15 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.decide()** (15 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/RiskFusion.kt`
- **LivenessEvidence** (13 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/RiskFusion.kt`
- **.evaluate()** (11 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- **LivenessRisk** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/RiskFusion.kt`
- **RiskFusion.kt** (5 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/RiskFusion.kt`
- **RiskFusion** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/RiskFusion.kt`
- **.rule10_inconclusiveEvidence_returnsMedium()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.rule1_injectionSuspected_returnsHigh()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.rule2_legacyPixelVeto_returnsHigh()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.rule3_padBelowSpoofReject_returnsHigh()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.rule4_flashScoreBelowThreshold_returnsHigh()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.rule5_challengeFailed_returnsHigh()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.rule6_conclusiveFlatDepth_returnsHigh()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.rule7_highPadCorroboratedByBlink_returnsLow()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.rule8_passedChallengeWithPadSupport_returnsLow()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **.rule9_darkRoomFlashAndDepth_returnsLow()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- **Multi-Factor Risk Fusion (`RiskFusion`)** (2 connections) — `docs/12-lld-liveness-and-anti-spoof-engine.md`
- **HIGH** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/RiskFusion.kt`
- **LOW** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/RiskFusion.kt`
- **MEDIUM** (1 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/RiskFusion.kt`

## Relationships

- [LivenessOrchestrator](LivenessOrchestrator.md) (4 shared connections)
- [LivenessResult](LivenessResult.md) (3 shared connections)
- [FlashLivenessTestTest](FlashLivenessTestTest.md) (2 shared connections)
- [.analyze](analyze.md) (2 shared connections)
- [Change-control record](Change-control_record.md) (1 shared connections)
- [test](test.md) (1 shared connections)
- [13. File checklist (new or changed)](13._File_checklist_new_or_changed.md) (1 shared connections)
- [.hasBlink](hasBlink.md) (1 shared connections)
- [FaceQualitySignals](FaceQualitySignals.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/liveness/LivenessOrchestrator.kt`
- `app/src/main/java/com/attract/attendance/domain/face/liveness/RiskFusion.kt`
- `app/src/test/java/com/attract/attendance/domain/face/liveness/RiskFusionTest.kt`
- `docs/12-lld-liveness-and-anti-spoof-engine.md`

## Audit Trail

- EXTRACTED: 44 (73%)
- INFERRED: 16 (27%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*