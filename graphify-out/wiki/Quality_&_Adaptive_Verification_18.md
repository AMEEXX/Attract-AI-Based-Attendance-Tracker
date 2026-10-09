# Quality & Adaptive Verification

> 53 nodes

## Key Concepts

- **AttendanceSessionEntity** (36 connections) — `app/src/main/java/com/attract/attendance/data/local/Entities.kt`
- **FakeSessionDao** (26 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **TeacherEntity** (18 connections) — `app/src/main/java/com/attract/attendance/data/local/Entities.kt`
- **StartupCoordinatorTest.kt** (17 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **ClassSectionEntity** (12 connections) — `app/src/main/java/com/attract/attendance/data/local/Entities.kt`
- **FakeTeacherDao** (12 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **RoomDatabaseIntegrationTest** (8 connections) — `app/src/androidTest/java/com/attract/attendance/data/local/RoomDatabaseIntegrationTest.kt`
- **Flow** (8 connections)
- **StartupCoordinator** (7 connections) — `app/src/main/java/com/attract/attendance/feature/app/StartupCoordinator.kt`
- **.testRepositoryEnrollmentAtomicityOnDatabaseFail()** (7 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/CameraProcessingAndMlIntegrationTest.kt`
- **DatabaseConstraintsTest** (6 connections) — `app/src/androidTest/java/com/attract/attendance/data/local/DatabaseConstraintsTest.kt`
- **StartupCoordinatorTest** (6 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.faceTemplateDao_insertEncryptedBlob_retrieveIntact()** (6 connections) — `app/src/androidTest/java/com/attract/attendance/data/local/RoomDatabaseIntegrationTest.kt`
- **.activeSessionExists_returnsRecoverSession()** (6 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.allReadyNoActiveSession_returnsTeacherDashboard()** (6 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.missingSecurityChecks_returnsBlockingSetup()** (6 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.noTeacher_returnsOnboarding()** (5 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.teacherWithEmptyPin_returnsOnboarding()** (5 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.insertStudent_sameClassAndRoll_rejectsDuplicate()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/data/local/DatabaseConstraintsTest.kt`
- **.insertStudent_sameRollInDifferentClasses_isAllowed()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/data/local/DatabaseConstraintsTest.kt`
- **.classDao_archiveClass_hiddenFromActiveQuery()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/data/local/RoomDatabaseIntegrationTest.kt`
- **.studentDao_insertMultiple_queryByClass()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/data/local/RoomDatabaseIntegrationTest.kt`
- **.setUp()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/data/local/TemplateMigrationAndroidTest.kt`
- **.classDao_insertAndQuery_returnsCorrectClass()** (3 connections) — `app/src/androidTest/java/com/attract/attendance/data/local/RoomDatabaseIntegrationTest.kt`
- **.observeActiveForClass()** (3 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- *... and 28 more nodes in this community*

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (39 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (27 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (3 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (3 shared connections)
- [Phoneaddstudentsps Bounds](Phoneaddstudentsps_Bounds.md) (2 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (2 shared connections)
- [UI Theme & Components](UI_Theme_&_Components.md) (1 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/data/local/DatabaseConstraintsTest.kt`
- `app/src/androidTest/java/com/attract/attendance/data/local/RoomDatabaseIntegrationTest.kt`
- `app/src/androidTest/java/com/attract/attendance/data/local/TemplateMigrationAndroidTest.kt`
- `app/src/androidTest/java/com/attract/attendance/domain/face/CameraProcessingAndMlIntegrationTest.kt`
- `app/src/main/java/com/attract/attendance/data/local/Entities.kt`
- `app/src/main/java/com/attract/attendance/domain/session/SessionRecoveryManager.kt`
- `app/src/main/java/com/attract/attendance/feature/app/StartupCoordinator.kt`
- `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`

## Audit Trail

- EXTRACTED: 146 (83%)
- INFERRED: 30 (17%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*