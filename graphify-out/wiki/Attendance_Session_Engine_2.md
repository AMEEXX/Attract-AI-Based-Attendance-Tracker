# Attendance Session Engine

> 13 nodes

## Key Concepts

- **LockTaskResult** (10 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **LockTaskController** (9 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **LockdownInterfaces.kt** (5 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **.observeState()** (3 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **.start()** (2 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **.stop()** (2 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **.start()** (2 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeLockTaskController.kt`
- **.start()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`
- **Flow** (2 connections)
- **AlreadyLocked** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **Error** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **Started** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **Result** (1 connections)

## Relationships

- [Roster Import & OCR](Roster_Import_&_OCR.md) (7 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (4 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (2 shared connections)
- [Kiosk Lockdown & Safety](Kiosk_Lockdown_&_Safety.md) (1 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeLockTaskController.kt`
- `app/src/test/java/com/attract/attendance/lockdown/domain/SessionTamperMonitorTest.kt`

## Audit Trail

- EXTRACTED: 28 (100%)
- INFERRED: 0 (0%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*