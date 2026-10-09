# Startup & Session Recovery

> 14 nodes

## Key Concepts

- **AttractViewModelTest** (9 connections) — `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`
- **AttractUiState** (8 connections) — `app/src/main/java/com/attract/attendance/feature/app/AttractViewModel.kt`
- **NoEnrolledStudentsDialogState** (4 connections) — `app/src/main/java/com/attract/attendance/feature/app/AttractViewModel.kt`
- **.prepareRosterImport()** (4 connections) — `app/src/main/java/com/attract/attendance/feature/app/AttractViewModel.kt`
- **PendingRosterImport** (3 connections) — `app/src/main/java/com/attract/attendance/feature/app/AttractViewModel.kt`
- **NoEnrolledStudentsDialog()** (3 connections) — `app/src/main/java/com/attract/attendance/feature/app/AttractApp.kt`
- **.driveSyncState_transitions()** (3 connections) — `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`
- **.faceAttendanceRequested_setsLockdownMessage()** (3 connections) — `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`
- **.noEnrolledStudentsWarning_canBeSetAndDismissed()** (3 connections) — `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`
- **.pendingRosterImport_canBeCancelled()** (3 connections) — `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`
- **.driveSyncState_initialValues()** (2 connections) — `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`
- **.lockdownEvent_updatesMessageState()** (2 connections) — `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`
- **.setUp()** (1 connections) — `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`
- **.tearDown()** (1 connections) — `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (5 shared connections)
- [Livenessreason Eyesclosedorstatic](Livenessreason_Eyesclosedorstatic.md) (4 shared connections)
- [Attract View Model Test](Attract_View_Model_Test.md) (1 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (1 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (1 shared connections)
- [Securitymodelskt Securityevent](Securitymodelskt_Securityevent.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/feature/app/AttractApp.kt`
- `app/src/main/java/com/attract/attendance/feature/app/AttractViewModel.kt`
- `app/src/test/java/com/attract/attendance/feature/app/AttractViewModelTest.kt`

## Audit Trail

- EXTRACTED: 21 (68%)
- INFERRED: 10 (32%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*