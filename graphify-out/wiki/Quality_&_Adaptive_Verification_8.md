# Quality & Adaptive Verification

> 31 nodes

## Key Concepts

- **assertTrue()** (199 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **FallbackAttendanceAndroidTest** (13 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **RosterTablePlannerTest** (11 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **RosterImportEdgeCaseFunctionalTest** (9 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **.presentRecords()** (6 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **.setUp()** (6 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **.terminalEngineCannotBeReused_andMustBeClearedBeforeNewAttempt()** (6 connections) — `app/src/test/java/com/attract/attendance/domain/session/CoordinatorAndAuthTest.kt`
- **.atomicCommit_oneRowConflict_rollsBackAll()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **.atomicCommit_validPlan_insertsAllOrNothing()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **.alreadyCheckedIn_returnsAlreadyPresent_withoutDuplicate()** (3 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **.ineligibleStudent_isRejected_withNoRecord()** (3 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **.noActiveSession_isRejected_withNoRecord()** (3 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **.selectingOneStudentThenAnother_onlyFinalSelectionIsRecorded()** (3 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **.student()** (3 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **.unknownFace_thenTeacherSelectsAmit_marksPresentExactlyOnce_withManualSource()** (3 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **.databaseFailure_rejectsSafely_withoutPartialRecord()** (2 connections) — `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- **.blankRowsAreSkipped()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **.duplicateRollNumberInRoster_returnsRejectedWithErrorDetails()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **.invalidStudentNameOrRoll_returnsRejected()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **.missingNameColumn_returnsRejected()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **.missingRollColumn_returnsRejected()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **.noStudentRows_returnsRejected()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **.validRosterWithAliasHeadersAndWhitespace_parsesSuccessfully()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **.validRosterWithCanonicalHeaders_parsesSuccessfully()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **.validRosterWithoutOptionalSerialColumn_parsesSuccessfully()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- *... and 6 more nodes in this community*

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (84 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (37 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (16 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (14 shared connections)
- [UI Theme & Components](UI_Theme_&_Components.md) (11 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (10 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (9 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (5 shared connections)
- [Startup & Session Recovery](Startup_&_Session_Recovery.md) (4 shared connections)
- [Glowbuttonstate Error](Glowbuttonstate_Error.md) (1 shared connections)
- [Full Biometric Pipeline Integration Test](Full_Biometric_Pipeline_Integration_Test.md) (1 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/feature/session/FallbackAttendanceAndroidTest.kt`
- `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- `app/src/test/java/com/attract/attendance/domain/session/CoordinatorAndAuthTest.kt`
- `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`

## Audit Trail

- EXTRACTED: 54 (22%)
- INFERRED: 196 (78%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*