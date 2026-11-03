# Unit Test Suite Assessment (107 Tests, 100% Pass)

> 34 nodes

## Key Concepts

- **Unit Test Suite Assessment (107 Tests, 100% Pass)** (23 connections) — `docs/00-master-implementation-plan.md`
- **ClassReportStudentRow** (21 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **SessionExportRow** (18 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **AttendanceExporter** (16 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **.completeDataPipeline_rosterToExport_executesSuccessfully()** (9 connections) — `app/src/test/java/com/attract/attendance/functional/DataPipelineFunctionalTest.kt`
- **AttendanceExporterTest.kt** (9 connections) — `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- **Stage 1 Identified Gaps (Recommended Before Release)** (9 connections) — `docs/00-master-implementation-plan.md`
- **FinalizeFaceSessionCommandTest** (8 connections) — `app/src/test/java/com/attract/attendance/domain/FinalizeFaceSessionCommandTest.kt`
- **ValidatorsTest** (8 connections) — `app/src/test/java/com/attract/attendance/domain/ValidatorsTest.kt`
- **AttendanceExporterTest** (7 connections) — `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- **.buildCsv()** (7 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **.normalizeForDisplay()** (7 connections) — `app/src/main/java/com/attract/attendance/domain/NormalizeRollNumber.kt`
- **NormalizeRollNumberTest** (6 connections) — `app/src/test/java/com/attract/attendance/domain/NormalizeRollNumberTest.kt`
- **.exportClassReport()** (6 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **Completed Functional Suites (All Passing)** (5 connections) — `docs/00-master-implementation-plan.md`
- **.csvExport_buildsValidCsvOutputOnAndroid()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/feature/reporting/ReportingAndExportE2EAndroidTest.kt`
- **.buildCsv_withReportAndSessionRows_formatsAndEscapesValues()** (4 connections) — `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- **.rosterImportFlow_messyInputData_parsesAndNormalizesRoster()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportFunctionalTest.kt`
- **Stage 1 — Unit Testing: Assessment & Completion Status** (4 connections) — `docs/00-master-implementation-plan.md`
- **DataPipelineFunctionalTest** (3 connections) — `app/src/test/java/com/attract/attendance/functional/DataPipelineFunctionalTest.kt`
- **RosterImportFunctionalTest** (3 connections) — `app/src/test/java/com/attract/attendance/functional/RosterImportFunctionalTest.kt`
- **.normalizeForComparison()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/NormalizeRollNumber.kt`
- **.buildCsv_formulaInjectionInName_prefixedWithApostrophe()** (3 connections) — `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- **.appendCsvRow()** (2 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **.timestamp()** (2 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- *... and 9 more nodes in this community*

## Relationships

- [test](test.md) (15 shared connections)
- [AttendanceStatus](AttendanceStatus.md) (11 shared connections)
- [FakeSessionDao](FakeSessionDao.md) (11 shared connections)
- [assertTrue](assertTrue.md) (8 shared connections)
- [Result](Result.md) (7 shared connections)
- [ExportAndBackupFunctionalTest](ExportAndBackupFunctionalTest.md) (6 shared connections)
- [AttractRepository.kt](AttractRepository.kt.md) (6 shared connections)
- [AttendanceSessionEntity](AttendanceSessionEntity.md) (6 shared connections)
- [DriveBackupWorker.kt](DriveBackupWorker.kt.md) (4 shared connections)
- [BackupExporter](BackupExporter.md) (2 shared connections)
- [AttractApplication.kt](AttractApplication.kt.md) (2 shared connections)
- [AttractRepository](AttractRepository.md) (2 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/feature/reporting/ReportingAndExportE2EAndroidTest.kt`
- `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- `app/src/main/java/com/attract/attendance/domain/NormalizeRollNumber.kt`
- `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- `app/src/test/java/com/attract/attendance/domain/FinalizeFaceSessionCommandTest.kt`
- `app/src/test/java/com/attract/attendance/domain/NormalizeRollNumberTest.kt`
- `app/src/test/java/com/attract/attendance/domain/ValidatorsTest.kt`
- `app/src/test/java/com/attract/attendance/functional/DataPipelineFunctionalTest.kt`
- `app/src/test/java/com/attract/attendance/functional/RosterImportFunctionalTest.kt`
- `docs/00-master-implementation-plan.md`

## Audit Trail

- EXTRACTED: 112 (73%)
- INFERRED: 42 (27%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*