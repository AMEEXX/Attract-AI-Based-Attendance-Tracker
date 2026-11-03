# .hasBlink

> 8 nodes

## Key Concepts

- **BlinkDetectorTest** (9 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/BlinkDetectorTest.kt`
- **.hasBlink()** (9 connections) — `app/src/main/java/com/attract/attendance/domain/face/liveness/BlinkDetector.kt`
- **.frameWithBlink()** (7 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/BlinkDetectorTest.kt`
- **.fullBlinkCycle_openToClosedToOpen_returnsTrue()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/BlinkDetectorTest.kt`
- **.alwaysClosed_noPrecedingOpen_returnsFalse()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/BlinkDetectorTest.kt`
- **.alwaysOpen_returnsFalse()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/BlinkDetectorTest.kt`
- **.closedForTooManyFrames_sleepingOrPhoto_returnsFalse()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/BlinkDetectorTest.kt`
- **.insufficientFrames_returnsFalse()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/liveness/BlinkDetectorTest.kt`

## Relationships

- [FaceMeshResult](FaceMeshResult.md) (2 shared connections)
- [test](test.md) (1 shared connections)
- [FlashLivenessTestTest](FlashLivenessTestTest.md) (1 shared connections)
- [13. File checklist (new or changed)](13._File_checklist_new_or_changed.md) (1 shared connections)
- [ChallengeController](ChallengeController.md) (1 shared connections)
- [.decide](decide.md) (1 shared connections)
- [Change-control record](Change-control_record.md) (1 shared connections)
- [assertTrue](assertTrue.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/liveness/BlinkDetector.kt`
- `app/src/test/java/com/attract/attendance/domain/face/liveness/BlinkDetectorTest.kt`

## Audit Trail

- EXTRACTED: 22 (88%)
- INFERRED: 3 (12%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*