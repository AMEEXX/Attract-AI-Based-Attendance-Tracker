# ExportAndBackupFunctionalTest

> 12 nodes

## Key Concepts

- **ExportAndBackupFunctionalTest** (12 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **FT-02: `ExportAndBackupFunctionalTest`** (11 connections) — `docs/00-master-implementation-plan.md`
- **.backupJson_roundTripEquality_dataIntegrity()** (9 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **Additional Functional Test Suites Required (Not Yet Implemented)** (6 connections) — `docs/00-master-implementation-plan.md`
- **.backupJson_largeSnapshot_completesWithinTimeLimit()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **.csvExport_multipleSessionData_correctStructure()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **.csvExport_formulaInjectionInName_prefixedWithApostrophe()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **.csvExport_nullSerialNumber_rendersEmptyCell()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **Stage 2 — Functional Testing (Module Integration): Status** (4 connections) — `docs/00-master-implementation-plan.md`
- **.csvExport_emptyClass_producesHeadersOnly()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **.csvExport_specialCharsInClassName_properlyQuoted()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **Objective** (1 connections) — `docs/00-master-implementation-plan.md`

## Relationships

- [assertTrue](assertTrue.md) (8 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (6 shared connections)
- [BackupExporter](BackupExporter.md) (4 shared connections)
- [FakeSessionDao](FakeSessionDao.md) (3 shared connections)
- [StudentEntity](StudentEntity.md) (2 shared connections)
- [AttendanceStatus](AttendanceStatus.md) (1 shared connections)
- [TeacherEntity](TeacherEntity.md) (1 shared connections)
- [ClassSectionEntity](ClassSectionEntity.md) (1 shared connections)
- [AttendanceSessionEntity](AttendanceSessionEntity.md) (1 shared connections)
- [AttractRepository.kt](AttractRepository.kt.md) (1 shared connections)
- [FT-01: `SecurityPipelineFunctionalTest`](FT-01-_`SecurityPipelineFunctionalTest`.md) (1 shared connections)
- [.isEligible](isEligible.md) (1 shared connections)

## Source Files

- `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- `docs/00-master-implementation-plan.md`

## Audit Trail

- EXTRACTED: 32 (65%)
- INFERRED: 17 (35%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*