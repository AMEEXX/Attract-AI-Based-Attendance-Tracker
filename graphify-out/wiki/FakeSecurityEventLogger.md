# FakeSecurityEventLogger

> 13 nodes

## Key Concepts

- **FakeSecurityEventLogger** (11 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- **TestLockTaskController** (11 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.multipleUnscheduledDrops_logsMultipleTamperEvents()** (7 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **SessionTamperMonitorTest** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.authenticatedStop_doesNotTriggerTamper()** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.tamperEventEmittedAsFlow()** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.unexpectedLockStateDrop_logsTamperDetected()** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.dropToUnlockedWithoutAuth()** (5 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.getEventsForCategory()** (3 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- **.log()** (2 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- **.lock()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.reset()** (1 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- **FakeSecurityEventLogger.kt** (1 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`

## Relationships

- [SessionTamperMonitor](SessionTamperMonitor.md) (7 shared connections)
- [FakeSessionDao](FakeSessionDao.md) (4 shared connections)
- [BiometricTeacherAuthenticator.kt](BiometricTeacherAuthenticator.kt.md) (3 shared connections)
- [FT-01: `SecurityPipelineFunctionalTest`](FT-01-_`SecurityPipelineFunctionalTest`.md) (1 shared connections)
- [test](test.md) (1 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (1 shared connections)
- [LockTaskControllerImpl.kt](LockTaskControllerImpl.kt.md) (1 shared connections)
- [LockTaskResult](LockTaskResult.md) (1 shared connections)
- [Result](Result.md) (1 shared connections)
- [SecurityEventCategory](SecurityEventCategory.md) (1 shared connections)

## Source Files

- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`

## Audit Trail

- EXTRACTED: 31 (70%)
- INFERRED: 13 (30%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*