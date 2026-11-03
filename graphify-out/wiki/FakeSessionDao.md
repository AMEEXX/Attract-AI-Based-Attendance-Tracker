# FakeSessionDao

> 86 nodes

## Key Concepts

- **FakeSessionDao** (61 connections) — `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- **FakeAttendanceRecordDao** (60 connections) — `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- **FakeStudentDao** (58 connections) — `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- **RecordPresentCommand** (31 connections) — `app/src/main/java/com/attract/attendance/domain/RecordPresentCommand.kt`
- **FakeDeviceSecurityChecker** (31 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeDeviceSecurityChecker.kt`
- **StudentSummary** (29 connections) — `app/src/main/java/com/attract/attendance/core/model/AttendanceModels.kt`
- **FakeLockTaskController** (29 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeLockTaskController.kt`
- **SessionCoordinator** (28 connections) — `app/src/main/java/com/attract/attendance/domain/session/SessionCoordinator.kt`
- **FakeTeacherAuthenticator** (26 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeTeacherAuthenticator.kt`
- **LiveSessionFunctionalTest.kt** (26 connections) — `app/src/test/java/com/attract/attendance/functional/LiveSessionFunctionalTest.kt`
- **SessionStateMachineComprehensiveFunctionalTest.kt** (26 connections) — `app/src/test/java/com/attract/attendance/functional/SessionStateMachineComprehensiveFunctionalTest.kt`
- **SessionRecoveryE2EAndroidTest.kt** (22 connections) — `app/src/androidTest/java/com/attract/attendance/feature/recovery/SessionRecoveryE2EAndroidTest.kt`
- **FinalizeFaceSessionCommand** (21 connections) — `app/src/main/java/com/attract/attendance/domain/FinalizeFaceSessionCommand.kt`
- **FaceSessionE2EAndroidTest.kt** (21 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FaceSessionE2EAndroidTest.kt`
- **SessionRecoveryFunctionalTest.kt** (21 connections) — `app/src/test/java/com/attract/attendance/functional/SessionRecoveryFunctionalTest.kt`
- **FT-04: `SessionStateMachineComprehensiveFunctionalTest`** (16 connections) — `docs/00-master-implementation-plan.md`
- **SessionRecoveryManager** (13 connections) — `app/src/main/java/com/attract/attendance/domain/session/SessionRecoveryManager.kt`
- **SessionStateMachineComprehensiveFunctionalTest** (12 connections) — `app/src/test/java/com/attract/attendance/functional/SessionStateMachineComprehensiveFunctionalTest.kt`
- **SessionCoordinatorTest** (11 connections) — `app/src/test/java/com/attract/attendance/domain/session/SessionCoordinatorTest.kt`
- **.processRelaunch_activeSessionDetected_recoveryManagerResumes()** (11 connections) — `app/src/androidTest/java/com/attract/attendance/feature/recovery/SessionRecoveryE2EAndroidTest.kt`
- **.liveSession_fullLifecycle_startRecognizeRecordAndFinalize()** (11 connections) — `app/src/test/java/com/attract/attendance/functional/LiveSessionFunctionalTest.kt`
- **.crashRecovery_activeSessionFound_resumesSessionWhenAuthAndSecurityPass()** (11 connections) — `app/src/test/java/com/attract/attendance/functional/SessionRecoveryFunctionalTest.kt`
- **.crashRecovery_endSession_finalizesActiveSession()** (11 connections) — `app/src/test/java/com/attract/attendance/functional/SessionRecoveryFunctionalTest.kt`
- **AuthMethod** (10 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **.endSession_authenticated_finalizesAndReleasesLockTask()** (10 connections) — `app/src/test/java/com/attract/attendance/domain/session/SessionRecoveryManagerTest.kt`
- *... and 61 more nodes in this community*

## Relationships

- [AttendanceSessionEntity](AttendanceSessionEntity.md) (54 shared connections)
- [test](test.md) (39 shared connections)
- [assertTrue](assertTrue.md) (36 shared connections)
- [AuthResult](AuthResult.md) (23 shared connections)
- [AttendanceStatus](AttendanceStatus.md) (22 shared connections)
- [StudentEntity](StudentEntity.md) (17 shared connections)
- [AttractRepository.kt](AttractRepository.kt.md) (16 shared connections)
- [FT-01: `SecurityPipelineFunctionalTest`](FT-01-_`SecurityPipelineFunctionalTest`.md) (14 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (11 shared connections)
- [SessionState](SessionState.md) (8 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (6 shared connections)
- [QualityResult](QualityResult.md) (5 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/feature/recovery/SessionRecoveryE2EAndroidTest.kt`
- `app/src/androidTest/java/com/attract/attendance/feature/session/FaceSessionE2EAndroidTest.kt`
- `app/src/main/java/com/attract/attendance/core/model/AttendanceModels.kt`
- `app/src/main/java/com/attract/attendance/domain/FinalizeFaceSessionCommand.kt`
- `app/src/main/java/com/attract/attendance/domain/RecordPresentCommand.kt`
- `app/src/main/java/com/attract/attendance/domain/session/SessionCoordinator.kt`
- `app/src/main/java/com/attract/attendance/domain/session/SessionRecoveryManager.kt`
- `app/src/main/java/com/attract/attendance/domain/session/SessionState.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeDeviceSecurityChecker.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeLockTaskController.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeTeacherAuthenticator.kt`
- `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- `app/src/test/java/com/attract/attendance/domain/FinalizeFaceSessionCommandTest.kt`
- `app/src/test/java/com/attract/attendance/domain/RecordPresentCommandTest.kt`
- `app/src/test/java/com/attract/attendance/domain/session/CoordinatorAndAuthTest.kt`
- `app/src/test/java/com/attract/attendance/domain/session/SessionCoordinatorTest.kt`
- `app/src/test/java/com/attract/attendance/domain/session/SessionRecoveryManagerTest.kt`
- `app/src/test/java/com/attract/attendance/functional/LiveSessionFunctionalTest.kt`
- `app/src/test/java/com/attract/attendance/functional/SessionRecoveryFunctionalTest.kt`

## Audit Trail

- EXTRACTED: 496 (85%)
- INFERRED: 87 (15%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*