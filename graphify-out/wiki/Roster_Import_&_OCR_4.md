# Roster Import & OCR

> 20 nodes

## Key Concepts

- **SessionTamperMonitor** (14 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SessionTamperMonitor.kt`
- **FakeSecurityEventLogger** (11 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- **TestLockTaskController** (11 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.multipleUnscheduledDrops_logsMultipleTamperEvents()** (7 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.authenticatedStop_doesNotTriggerTamper()** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.tamperEventEmittedAsFlow()** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.unexpectedLockStateDrop_logsTamperDetected()** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **SessionTamperMonitorTest** (5 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.tamperMonitor_dropDuringActiveLockTask_emitsTamperEvent()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.dropToUnlockedWithoutAuth()** (5 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.startMonitoring()** (4 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SessionTamperMonitor.kt`
- **TamperEvent** (2 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SessionTamperMonitor.kt`
- **.getDeviceInfo()** (2 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SessionTamperMonitor.kt`
- **.lock()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.stop()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **.noteAuthenticatedStop()** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SessionTamperMonitor.kt`
- **.stopMonitoring()** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SessionTamperMonitor.kt`
- **.reset()** (1 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- **FakeSecurityEventLogger.kt** (1 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- **Result** (1 connections)

## Relationships

- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (13 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (6 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (2 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/lockdown/domain/SessionTamperMonitor.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`

## Audit Trail

- EXTRACTED: 44 (77%)
- INFERRED: 13 (23%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*