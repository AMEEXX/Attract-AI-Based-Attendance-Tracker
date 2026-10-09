# Quality & Adaptive Verification

> 20 nodes

## Key Concepts

- **BackupExporter** (20 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **BackupSnapshot** (17 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **BackupFaceTemplate** (7 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **.toJson()** (7 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **BackupExporterTest** (5 connections) — `app/src/test/java/com/attract/attendance/data/importexport/BackupExporterTest.kt`
- **.export()** (5 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **.import()** (5 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **.array()** (4 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **.fromJson()** (4 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **.comma()** (3 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **.field()** (3 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **.roundTrip_faceTemplates_preservedAcrossExportAndImport()** (3 connections) — `app/src/test/java/com/attract/attendance/data/importexport/BackupExporterTest.kt`
- **.toJson_populatedSnapshot_escapesJsonCharactersAndFormatsEntities()** (3 connections) — `app/src/test/java/com/attract/attendance/data/importexport/BackupExporterTest.kt`
- **.backupJson_includesPortableFaceTemplatesAndExcludesRawHardwareKeys()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`
- **Uri** (3 connections)
- **.jsonEscaped()** (2 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **.obj()** (2 connections) — `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- **.toJson_emptySnapshot_serializesCorrectFormat()** (2 connections) — `app/src/test/java/com/attract/attendance/data/importexport/BackupExporterTest.kt`
- **Result** (2 connections)
- **T** (1 connections)

## Relationships

- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (12 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (4 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (2 shared connections)
- [Historysortorder Newestfirst](Historysortorder_Newestfirst.md) (2 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (2 shared connections)
- [Teacherprofile Authenticate](Teacherprofile_Authenticate.md) (2 shared connections)
- [Livenessreason Eyesclosedorstatic](Livenessreason_Eyesclosedorstatic.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/data/importexport/BackupExporter.kt`
- `app/src/test/java/com/attract/attendance/data/importexport/BackupExporterTest.kt`
- `app/src/test/java/com/attract/attendance/functional/ExportAndBackupFunctionalTest.kt`

## Audit Trail

- EXTRACTED: 57 (90%)
- INFERRED: 6 (10%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*