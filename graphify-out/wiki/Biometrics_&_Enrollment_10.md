# Biometrics & Enrollment

> 56 nodes

## Key Concepts

- **FakeDeviceSecurityChecker** (31 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeDeviceSecurityChecker.kt`
- **FakeLockTaskController** (27 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeLockTaskController.kt`
- **SessionRecoveryManagerTest.kt** (26 connections) — `app/src/test/java/com/attract/attendance/domain/session/SessionRecoveryManagerTest.kt`
- **FakeTeacherAuthenticator** (25 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeTeacherAuthenticator.kt`
- **SessionRecoveryE2EAndroidTest.kt** (22 connections) — `app/src/androidTest/java/com/attract/attendance/feature/recovery/SessionRecoveryE2EAndroidTest.kt`
- **SessionRecoveryFunctionalTest.kt** (21 connections) — `app/src/test/java/com/attract/attendance/functional/SessionRecoveryFunctionalTest.kt`
- **FinalizeFaceSessionCommand** (20 connections) — `app/src/main/java/com/attract/attendance/domain/FinalizeFaceSessionCommand.kt`
- **SessionRecoveryManager** (13 connections) — `app/src/main/java/com/attract/attendance/domain/session/SessionRecoveryManager.kt`
- **.add()** (13 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeDeviceSecurityChecker.kt`
- **.processRelaunch_activeSessionDetected_recoveryManagerResumes()** (12 connections) — `app/src/androidTest/java/com/attract/attendance/feature/recovery/SessionRecoveryE2EAndroidTest.kt`
- **.crashRecovery_activeSessionFound_resumesSessionWhenAuthAndSecurityPass()** (12 connections) — `app/src/test/java/com/attract/attendance/functional/SessionRecoveryFunctionalTest.kt`
- **PrerequisitesChecker** (11 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/PrerequisitesChecker.kt`
- **.resumeSession_authenticatedAndSecurityReady_returnsSuccess()** (11 connections) — `app/src/test/java/com/attract/attendance/domain/session/SessionRecoveryManagerTest.kt`
- **.crashRecovery_endSession_finalizesActiveSession()** (11 connections) — `app/src/test/java/com/attract/attendance/functional/SessionRecoveryFunctionalTest.kt`
- **AuthMethod** (10 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **SecurityPipelineFunctionalTest** (10 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.endSession_authenticated_finalizesAndReleasesLockTask()** (10 connections) — `app/src/test/java/com/attract/attendance/domain/session/SessionRecoveryManagerTest.kt`
- **.resumeSession_authFailed_returnsAuthFailed()** (10 connections) — `app/src/test/java/com/attract/attendance/domain/session/SessionRecoveryManagerTest.kt`
- **.crashRecovery_noActiveSession_returnsNoActiveSession()** (10 connections) — `app/src/test/java/com/attract/attendance/functional/SessionRecoveryFunctionalTest.kt`
- **LockTaskControllerTest** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/LockTaskControllerTest.kt`
- **.remove()** (6 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeDeviceSecurityChecker.kt`
- **.prerequisitesGate_cameraPermissionMissing_blocksStart()** (6 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.prerequisitesGate_noPinConfigured_blocksStart()** (6 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **SessionRecoveryManagerTest** (5 connections) — `app/src/test/java/com/attract/attendance/domain/session/SessionRecoveryManagerTest.kt`
- **.prerequisitesChecker_evaluatesDeviceChecks()** (5 connections) — `app/src/androidTest/java/com/attract/attendance/lockdown/SecurityAndLockTaskAndroidTest.kt`
- *... and 31 more nodes in this community*

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (102 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (16 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (11 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (10 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (7 shared connections)
- [Kiosk Lockdown & Safety](Kiosk_Lockdown_&_Safety.md) (5 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (5 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (3 shared connections)
- [Enrollstudentface Bytearray](Enrollstudentface_Bytearray.md) (1 shared connections)
- [Startup & Session Recovery](Startup_&_Session_Recovery.md) (1 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/feature/recovery/SessionRecoveryE2EAndroidTest.kt`
- `app/src/androidTest/java/com/attract/attendance/lockdown/SecurityAndLockTaskAndroidTest.kt`
- `app/src/main/java/com/attract/attendance/domain/FinalizeFaceSessionCommand.kt`
- `app/src/main/java/com/attract/attendance/domain/session/SessionRecoveryManager.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/PrerequisitesChecker.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeDeviceSecurityChecker.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeLockTaskController.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeTeacherAuthenticator.kt`
- `app/src/test/java/com/attract/attendance/domain/session/SessionRecoveryManagerTest.kt`
- `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- `app/src/test/java/com/attract/attendance/functional/SessionRecoveryFunctionalTest.kt`
- `app/src/test/java/com/attract/attendance/lockdown/domain/LockTaskControllerTest.kt`
- `app/src/test/java/com/attract/attendance/lockdown/domain/PrerequisitesCheckerTest.kt`

## Audit Trail

- EXTRACTED: 233 (81%)
- INFERRED: 56 (19%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*