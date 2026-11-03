# StartupCoordinator

> 7 nodes

## Key Concepts

- **StartupCoordinator** (10 connections) — `app/src/main/java/com/attract/attendance/feature/app/StartupCoordinator.kt`
- **StartupCoordinatorTest** (7 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.missingSecurityChecks_returnsBlockingSetup()** (6 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.activeSessionExists_returnsRecoverSession()** (5 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.allReadyNoActiveSession_returnsTeacherDashboard()** (5 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.noTeacher_returnsOnboarding()** (5 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`
- **.teacherWithEmptyPin_returnsOnboarding()** (5 connections) — `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`

## Relationships

- [FakeSessionDao](FakeSessionDao.md) (10 shared connections)
- [TeacherEntity](TeacherEntity.md) (5 shared connections)
- [LLD-01 — Application Foundation & Navigation](LLD-01_—_Application_Foundation_&_Navigation.md) (3 shared connections)
- [AuthResult](AuthResult.md) (1 shared connections)
- [20 — "Face-ID-grade" Recognition, Anti-Spoofing & Low-Light Plan (RGB-only)](20_—__Face-ID-grade__Recognition,_Anti-Spoofing_&_Low-Light_Plan_RGB-only.md) (1 shared connections)
- [AttendanceSessionEntity](AttendanceSessionEntity.md) (1 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (1 shared connections)
- [assertTrue](assertTrue.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/feature/app/StartupCoordinator.kt`
- `app/src/test/java/com/attract/attendance/feature/app/StartupCoordinatorTest.kt`

## Audit Trail

- EXTRACTED: 23 (70%)
- INFERRED: 10 (30%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*