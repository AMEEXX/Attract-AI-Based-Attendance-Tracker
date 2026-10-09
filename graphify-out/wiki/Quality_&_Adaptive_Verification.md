# Quality & Adaptive Verification

> 71 nodes

## Key Concepts

- **FakeSessionDao** (61 connections) — `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- **FakeAttendanceRecordDao** (58 connections) — `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- **FakeStudentDao** (57 connections) — `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- **StudentEntity** (31 connections) — `app/src/main/java/com/attract/attendance/data/local/Entities.kt`
- **StudentSummary** (29 connections) — `app/src/main/java/com/attract/attendance/core/model/AttendanceModels.kt`
- **RecordPresentCommand** (28 connections) — `app/src/main/java/com/attract/attendance/domain/RecordPresentCommand.kt`
- **SessionCoordinator** (27 connections) — `app/src/main/java/com/attract/attendance/domain/session/SessionCoordinator.kt`
- **LiveSessionFunctionalTest.kt** (26 connections) — `app/src/test/java/com/attract/attendance/functional/LiveSessionFunctionalTest.kt`
- **SessionStateMachineComprehensiveFunctionalTest.kt** (26 connections) — `app/src/test/java/com/attract/attendance/functional/SessionStateMachineComprehensiveFunctionalTest.kt`
- **CoordinatorAndAuthTest.kt** (25 connections) — `app/src/test/java/com/attract/attendance/domain/session/CoordinatorAndAuthTest.kt`
- **SessionCoordinatorTest.kt** (23 connections) — `app/src/test/java/com/attract/attendance/domain/session/SessionCoordinatorTest.kt`
- **FaceSessionE2EAndroidTest.kt** (21 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FaceSessionE2EAndroidTest.kt`
- **runtest** (16 connections)
- **FinalizeFaceSessionCommandTest.kt** (15 connections) — `app/src/test/java/com/attract/attendance/domain/FinalizeFaceSessionCommandTest.kt`
- **RecordPresentCommandTest.kt** (15 connections) — `app/src/test/java/com/attract/attendance/domain/RecordPresentCommandTest.kt`
- **SessionStateMachineComprehensiveFunctionalTest** (11 connections) — `app/src/test/java/com/attract/attendance/functional/SessionStateMachineComprehensiveFunctionalTest.kt`
- **.liveSession_fullLifecycle_startRecognizeRecordAndFinalize()** (11 connections) — `app/src/test/java/com/attract/attendance/functional/LiveSessionFunctionalTest.kt`
- **TeacherAuthorizationGrant** (9 connections) — `app/src/main/java/com/attract/attendance/domain/session/SessionContext.kt`
- **SessionCoordinatorTest** (9 connections) — `app/src/test/java/com/attract/attendance/domain/session/SessionCoordinatorTest.kt`
- **CoordinatorAndAuthTest** (8 connections) — `app/src/test/java/com/attract/attendance/domain/session/CoordinatorAndAuthTest.kt`
- **.sessionCoordinator_teacherAssistanceWithGrant()** (8 connections) — `app/src/test/java/com/attract/attendance/domain/session/CoordinatorAndAuthTest.kt`
- **.endAuthSucceeds_finalizationPersistsThenEnds()** (8 connections) — `app/src/test/java/com/attract/attendance/functional/SessionStateMachineComprehensiveFunctionalTest.kt`
- **RecordPresentCommandTest** (7 connections) — `app/src/test/java/com/attract/attendance/domain/RecordPresentCommandTest.kt`
- **.sessionCheckIn_faceRecognized_attendanceMarked()** (7 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FaceSessionE2EAndroidTest.kt`
- **.execute_partialAbsentScenario_somePresentRestNeedAbsentFinalization()** (7 connections) — `app/src/test/java/com/attract/attendance/domain/FinalizeFaceSessionCommandTest.kt`
- *... and 46 more nodes in this community*

## Relationships

- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (112 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (69 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (35 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (9 shared connections)
- [Teacherprofile Authenticate](Teacherprofile_Authenticate.md) (9 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (7 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (6 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (4 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (2 shared connections)
- [Startup & Session Recovery](Startup_&_Session_Recovery.md) (2 shared connections)
- [Attractapplicationkt Appcontainer](Attractapplicationkt_Appcontainer.md) (2 shared connections)
- [Attract View Model Test](Attract_View_Model_Test.md) (2 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/feature/session/FaceSessionE2EAndroidTest.kt`
- `app/src/main/java/com/attract/attendance/core/model/AttendanceModels.kt`
- `app/src/main/java/com/attract/attendance/data/local/Entities.kt`
- `app/src/main/java/com/attract/attendance/domain/RecordPresentCommand.kt`
- `app/src/main/java/com/attract/attendance/domain/session/SessionContext.kt`
- `app/src/main/java/com/attract/attendance/domain/session/SessionCoordinator.kt`
- `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- `app/src/test/java/com/attract/attendance/domain/FinalizeFaceSessionCommandTest.kt`
- `app/src/test/java/com/attract/attendance/domain/RecordPresentCommandTest.kt`
- `app/src/test/java/com/attract/attendance/domain/session/CoordinatorAndAuthTest.kt`
- `app/src/test/java/com/attract/attendance/domain/session/SessionCoordinatorTest.kt`
- `app/src/test/java/com/attract/attendance/functional/LiveSessionFunctionalTest.kt`
- `app/src/test/java/com/attract/attendance/functional/SessionStateMachineComprehensiveFunctionalTest.kt`

## Audit Trail

- EXTRACTED: 448 (91%)
- INFERRED: 42 (9%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*