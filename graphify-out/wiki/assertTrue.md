# assertTrue

> 33 nodes

## Key Concepts

- **assertTrue()** (244 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **RosterTablePlannerTest** (12 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- **RosterImportEdgeCaseFunctionalTest** (10 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **FT-03: `RosterImportEdgeCaseFunctionalTest`** (9 connections) — `docs/00-master-implementation-plan.md`
- **LockTaskControllerTest** (8 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/LockTaskControllerTest.kt`
- **.atomicCommit_oneRowConflict_rollsBackAll()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **.atomicCommit_validPlan_insertsAllOrNothing()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **RosterImportE2EAndroidTest** (4 connections) — `app/src/androidTest/java/com/attract/attendance/feature/importroster/RosterImportE2EAndroidTest.kt`
- **.prerequisitesChecker_evaluatesDeviceChecks()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/lockdown/SecurityAndLockTaskAndroidTest.kt`
- **SecurityAndLockTaskAndroidTest** (3 connections) — `app/src/androidTest/java/com/attract/attendance/lockdown/SecurityAndLockTaskAndroidTest.kt`
- **.lockTaskController_startAndStop_updatesLockState()** (3 connections) — `app/src/androidTest/java/com/attract/attendance/lockdown/SecurityAndLockTaskAndroidTest.kt`
- **.csvImport_blankRequiredField_rowErrorWithRowNumber()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **.csvImport_duplicateRollInFile_bothMarkedDuplicate()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **.csvImport_quotedCommasInCells_parsedAsSingleCell()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **.csvImport_quotedNewlineInCell_parsedAsSingleCell()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **.xlsxImport_formulaCellTreatedAsLiteral()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- **.alreadyLocked_onSecondStart_returnsAlreadyLocked()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/LockTaskControllerTest.kt`
- **.startFailure_returnsError()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/LockTaskControllerTest.kt`
- **.startSuccess_transitionsToLocked()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/LockTaskControllerTest.kt`
- **.stopFailure_returnsFailure()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/LockTaskControllerTest.kt`
- **.stopSuccess_transitionsToUnlocked()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/LockTaskControllerTest.kt`
- **.csvImport_fileParser_createsValidImportPlan()** (2 connections) — `app/src/androidTest/java/com/attract/attendance/feature/importroster/RosterImportE2EAndroidTest.kt`
- **.csvImport_missingRequiredHeader_rejectsWithReason()** (2 connections) — `app/src/androidTest/java/com/attract/attendance/feature/importroster/RosterImportE2EAndroidTest.kt`
- **.buildCsv_emptyRows_generatesHeadersAndMetadata()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- **.blankRowsAreSkipped()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- *... and 8 more nodes in this community*

## Relationships

- [FakeSessionDao](FakeSessionDao.md) (36 shared connections)
- [AdaptiveVerificationEngineTest](AdaptiveVerificationEngineTest.md) (16 shared connections)
- [FT-01: `SecurityPipelineFunctionalTest`](FT-01-_`SecurityPipelineFunctionalTest`.md) (12 shared connections)
- [test](test.md) (9 shared connections)
- [.extractEmbedding](extractEmbedding.md) (9 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (8 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (8 shared connections)
- [StudentTemplatePair](StudentTemplatePair.md) (8 shared connections)
- [.evaluate](evaluate.md) (8 shared connections)
- [ExportAndBackupFunctionalTest](ExportAndBackupFunctionalTest.md) (8 shared connections)
- [FallbackAttendanceAndroidTest](FallbackAttendanceAndroidTest.md) (7 shared connections)
- [TemplateCompatibility](TemplateCompatibility.md) (7 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/feature/importroster/RosterImportE2EAndroidTest.kt`
- `app/src/androidTest/java/com/attract/attendance/lockdown/SecurityAndLockTaskAndroidTest.kt`
- `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- `app/src/test/java/com/attract/attendance/data/importexport/RosterTablePlannerTest.kt`
- `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- `app/src/test/java/com/attract/attendance/functional/RosterImportEdgeCaseFunctionalTest.kt`
- `app/src/test/java/com/attract/attendance/lockdown/domain/LockTaskControllerTest.kt`
- `docs/00-master-implementation-plan.md`

## Audit Trail

- EXTRACTED: 44 (15%)
- INFERRED: 257 (85%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*