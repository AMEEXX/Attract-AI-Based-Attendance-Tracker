# Attendance Session Engine

> 12 nodes

## Key Concepts

- **SecurityEvent** (10 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SecurityModels.kt`
- **LockdownModels.kt** (9 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **SecurityEventLogger** (6 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SecurityModels.kt`
- **SecurityModels.kt** (4 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SecurityModels.kt`
- **SecurityEventLoggerImpl.kt** (4 connections) — `app/src/main/java/com/attract/attendance/lockdown/platform/SecurityEventLoggerImpl.kt`
- **log** (4 connections)
- **SecurityEventLoggerImpl** (3 connections) — `app/src/main/java/com/attract/attendance/lockdown/platform/SecurityEventLoggerImpl.kt`
- **.getEventsForCategory()** (3 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`
- **instant** (3 connections)
- **.log()** (2 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/SecurityModels.kt`
- **.log()** (2 connections) — `app/src/main/java/com/attract/attendance/lockdown/platform/SecurityEventLoggerImpl.kt`
- **.log()** (2 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`

## Relationships

- [Roster Import & OCR](Roster_Import_&_OCR.md) (5 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (4 shared connections)
- [Broadcastreceiver Onreceive](Broadcastreceiver_Onreceive.md) (2 shared connections)
- [Enrollstudentface Bytearray](Enrollstudentface_Bytearray.md) (2 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (2 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (2 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (2 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)
- [Kiosk Lockdown & Safety](Kiosk_Lockdown_&_Safety.md) (1 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/SecurityModels.kt`
- `app/src/main/java/com/attract/attendance/lockdown/platform/SecurityEventLoggerImpl.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeSecurityEventLogger.kt`

## Audit Trail

- EXTRACTED: 36 (97%)
- INFERRED: 1 (3%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*