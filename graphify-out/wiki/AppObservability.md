# AppObservability

> 12 nodes

## Key Concepts

- **AppObservability** (8 connections) — `app/src/main/java/com/attract/attendance/core/observability/AppObservability.kt`
- **AppObservabilityTest** (6 connections) — `app/src/test/java/com/attract/attendance/core/observability/AppObservabilityTest.kt`
- **.logSessionState()** (4 connections) — `app/src/main/java/com/attract/attendance/core/observability/AppObservability.kt`
- **.recordNonFatal()** (4 connections) — `app/src/main/java/com/attract/attendance/core/observability/AppObservability.kt`
- **.initialize()** (3 connections) — `app/src/main/java/com/attract/attendance/core/observability/AppObservability.kt`
- **.logScreenTransition()** (3 connections) — `app/src/main/java/com/attract/attendance/core/observability/AppObservability.kt`
- **.scrubMetadata()** (3 connections) — `app/src/main/java/com/attract/attendance/core/observability/AppObservability.kt`
- **.initialization_doesNotThrowWhenFirebaseNotInitialized()** (2 connections) — `app/src/test/java/com/attract/attendance/core/observability/AppObservabilityTest.kt`
- **.logScreenTransition_doesNotThrowInUnitTestEnvironment()** (2 connections) — `app/src/test/java/com/attract/attendance/core/observability/AppObservabilityTest.kt`
- **.logSessionState_doesNotThrowInUnitTestEnvironment()** (2 connections) — `app/src/test/java/com/attract/attendance/core/observability/AppObservabilityTest.kt`
- **.piiScrubbing_dropsPiiKeysAndRetainsSafeKeys()** (2 connections) — `app/src/test/java/com/attract/attendance/core/observability/AppObservabilityTest.kt`
- **.recordNonFatal_doesNotThrowInUnitTestEnvironment()** (2 connections) — `app/src/test/java/com/attract/attendance/core/observability/AppObservabilityTest.kt`

## Relationships

- [AttractViewModel.kt](AttractViewModel.kt.md) (2 shared connections)
- [AttractApplication.kt](AttractApplication.kt.md) (1 shared connections)
- [AppLog](AppLog.md) (1 shared connections)
- [test](test.md) (1 shared connections)
- [BackupScheduler.kt](BackupScheduler.kt.md) (1 shared connections)
- [AttendanceScreen](AttendanceScreen.md) (1 shared connections)
- [AttractViewModel](AttractViewModel.md) (1 shared connections)
- [assertTrue](assertTrue.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/core/observability/AppObservability.kt`
- `app/src/test/java/com/attract/attendance/core/observability/AppObservabilityTest.kt`

## Audit Trail

- EXTRACTED: 24 (96%)
- INFERRED: 1 (4%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*