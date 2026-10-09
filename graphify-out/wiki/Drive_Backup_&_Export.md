# Drive Backup & Export

> 35 nodes

## Key Concepts

- **AttendanceRecordDao** (23 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **StudentDao** (21 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **AttendanceRecordEntity** (12 connections) — `app/src/main/java/com/attract/attendance/data/local/Entities.kt`
- **.completeDataPipeline_rosterToExport_executesSuccessfully()** (9 connections) — `app/src/test/java/com/attract/attendance/functional/DataPipelineFunctionalTest.kt`
- **FinalizeFaceSessionCommand.kt** (9 connections) — `app/src/main/java/com/attract/attendance/domain/FinalizeFaceSessionCommand.kt`
- **RecordPresentCommand.kt** (9 connections) — `app/src/main/java/com/attract/attendance/domain/RecordPresentCommand.kt`
- **StudentEntity** (8 connections)
- **FinalizeSessionResult** (5 connections) — `app/src/main/java/com/attract/attendance/domain/FinalizeFaceSessionCommand.kt`
- **AttendanceRecordEntity** (5 connections)
- **.execute()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/FinalizeFaceSessionCommand.kt`
- **.observeActiveForClass()** (3 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **Success** (2 connections) — `app/src/main/java/com/attract/attendance/domain/FinalizeFaceSessionCommand.kt`
- **.studentDao()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/AttractDatabase.kt`
- **.all()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.forSession()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.insert()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.insertAll()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.replaceAll()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.updateStatus()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.activeForClass()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.all()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.find()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.findActiveByRoll()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.insert()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.insertAll()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- *... and 10 more nodes in this community*

## Relationships

- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (24 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (15 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (4 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (3 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (2 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/data/local/AttractDatabase.kt`
- `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- `app/src/main/java/com/attract/attendance/data/local/Entities.kt`
- `app/src/main/java/com/attract/attendance/domain/FinalizeFaceSessionCommand.kt`
- `app/src/main/java/com/attract/attendance/domain/RecordPresentCommand.kt`
- `app/src/test/java/com/attract/attendance/functional/DataPipelineFunctionalTest.kt`

## Audit Trail

- EXTRACTED: 97 (99%)
- INFERRED: 1 (1%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*