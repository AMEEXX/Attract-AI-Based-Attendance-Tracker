# FaceTemplateDao

> 23 nodes

## Key Concepts

- **FaceTemplateDao** (24 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **FaceTemplateEntity** (23 connections) — `app/src/main/java/com/attract/attendance/data/local/Entities.kt`
- **.faceTemplateDao()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/AttractDatabase.kt`
- **.activeForClass()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.all()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.allActive()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.autoTemplatesForStudent()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.forStudent()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.insert()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.insertAll()** (2 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.countAutoTemplates()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.deactivateForStudent()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.deactivateStaleDimension()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.deleteAll()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.deleteAutoTemplatesForStudent()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.deleteById()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.deleteForClass()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.deleteForStudent()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.enrolledStudentIdsNeedingReEnrollment()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.lastAutoTemplateAt()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.reactivateAll()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.setInactive()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- **.studentIdsWithActiveTemplates()** (1 connections) — `app/src/main/java/com/attract/attendance/data/local/Daos.kt`

## Relationships

- [AttendanceSessionEntity](AttendanceSessionEntity.md) (3 shared connections)
- [.loadGallery](loadGallery.md) (3 shared connections)
- [00 — Spec-Driven Master Implementation Plan & Build Stability Guide](00_—_Spec-Driven_Master_Implementation_Plan_&_Build_Stability_Guide.md) (2 shared connections)
- [AttractRepository](AttractRepository.md) (2 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (2 shared connections)
- [ClassSectionEntity](ClassSectionEntity.md) (2 shared connections)
- [.calibrationDefaults](calibrationDefaults.md) (1 shared connections)
- [.decode](decode.md) (1 shared connections)
- [AttractRepository.kt](AttractRepository.kt.md) (1 shared connections)
- [AttendanceStatus](AttendanceStatus.md) (1 shared connections)
- [TemplateMigrationAndroidTest](TemplateMigrationAndroidTest.md) (1 shared connections)
- [WP5.3 Enrollment: add a "phone-low" template, and adaptive template updates](WP5.3_Enrollment-_add_a__phone-low__template,_and_adaptive_template_updates.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/data/local/AttractDatabase.kt`
- `app/src/main/java/com/attract/attendance/data/local/Daos.kt`
- `app/src/main/java/com/attract/attendance/data/local/Entities.kt`

## Audit Trail

- EXTRACTED: 42 (88%)
- INFERRED: 6 (12%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*