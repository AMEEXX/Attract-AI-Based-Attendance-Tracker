# Roster Import & OCR

> 15 nodes

## Key Concepts

- **ClassReportStudentRow** (21 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **ExportAndBackupFunctionalTest** (10 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **AttendanceExporterTest** (5 connections) — `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- **.buildCsv_withReportAndSessionRows_formatsAndEscapesValues()** (4 connections) — `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- **.buildCsv_formulaInjectionInName_prefixedWithApostrophe()** (3 connections) — `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- **.classReport()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.classReportRows()** (2 connections) — `app/src/main/java/com/attract/attendance/data/repository/AttractRepository.kt`
- **.classReport()** (2 connections) — `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- **.buildCsv_emptyRows_generatesHeadersAndMetadata()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- **.backupJson_largeSnapshot_completesWithinTimeLimit()** (2 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **.backupJson_roundTripEquality_dataIntegrity()** (2 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **.csvExport_formulaInjectionInName_prefixedWithApostrophe()** (2 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **.csvExport_nullSerialNumber_rendersEmptyCell()** (2 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **.csvExport_emptyClass_producesHeadersOnly()** (1 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **.csvExport_specialCharsInClassName_properlyQuoted()** (1 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`

## Relationships

- [Attendance Camera UI](Attendance_Camera_UI.md) (8 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (8 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (6 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (3 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (2 shared connections)
- [Teacherprofile Authenticate](Teacherprofile_Authenticate.md) (1 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- `app/src/main/java/com/attract/attendance/data/repository/AttractRepository.kt`
- `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- `app/src/test/java/com/attract/attendance/data/importexport/AttendanceExporterTest.kt`
- `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`

## Audit Trail

- EXTRACTED: 41 (91%)
- INFERRED: 4 (9%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*