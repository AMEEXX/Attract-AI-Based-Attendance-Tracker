# Attendance Camera UI

> 18 nodes

## Key Concepts

- **SessionExportRow** (17 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **AttendanceExporter.kt** (14 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **AttendanceExporter** (10 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **.buildCsv()** (7 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **.exportClassReport()** (6 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **ReportingAndExportE2EAndroidTest** (5 connections) — `app/src/androidTest/java/com/attract/attendance/feature/reporting/ReportingAndExportE2EAndroidTest.kt`
- **.csvExport_buildsValidCsvOutputOnAndroid()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/feature/reporting/ReportingAndExportE2EAndroidTest.kt`
- **.sessionExportRows()** (4 connections) — `app/src/main/java/com/attract/attendance/data/repository/AttractRepository.kt`
- **.backupJson_buildsValidJsonSnapshotOnAndroid()** (3 connections) — `app/src/androidTest/java/com/attract/attendance/feature/reporting/ReportingAndExportE2EAndroidTest.kt`
- **.csvExport_multipleSessionData_correctStructure()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **contentresolver** (3 connections)
- **.appendCsvRow()** (2 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **.timestamp()** (2 connections) — `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- **.sessionExportRows()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.sessionExportRows()** (2 connections) — `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- **Uri** (2 connections)
- **Result** (1 connections)
- **zoneid** (1 connections)

## Relationships

- [Roster Import & OCR](Roster_Import_&_OCR.md) (10 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (9 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (7 shared connections)
- [Teacherprofile Authenticate](Teacherprofile_Authenticate.md) (3 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (3 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (3 shared connections)
- [Historysortorder Newestfirst](Historysortorder_Newestfirst.md) (1 shared connections)
- [Livenessreason Eyesclosedorstatic](Livenessreason_Eyesclosedorstatic.md) (1 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/feature/reporting/ReportingAndExportE2EAndroidTest.kt`
- `app/src/main/java/com/attract/attendance/data/importexport/AttendanceExporter.kt`
- `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- `app/src/main/java/com/attract/attendance/data/repository/AttractRepository.kt`
- `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`
- `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`

## Audit Trail

- EXTRACTED: 60 (95%)
- INFERRED: 3 (5%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*