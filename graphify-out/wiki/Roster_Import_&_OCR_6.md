# Roster Import & OCR

> 17 nodes

## Key Concepts

- **AttendanceRulesIntegrationFunctionalTest.kt** (14 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.percentage()** (7 connections) — `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`
- **AttendanceRulesIntegrationFunctionalTest** (6 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **AttendancePercentage** (5 connections) — `app/src/main/java/com/attract/attendance/core/model/AttendanceModels.kt`
- **AttendanceRulesTest** (5 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **.isEligible()** (5 connections) — `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`
- **AttendanceRules** (4 connections) — `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`
- **.eligibility_enrolledAfterSessionStart_notCountedAsAbsent()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.eligibility_enrolledBeforeFirstSession_countedFromFirst()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.multipleSessionSameDay_countedSeparately()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.isEligible_lateJoiner_excludesEarlierSessions()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **.isEligible_originalRosterStudent_isAlwaysEligible()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **AttendanceRules.kt** (3 connections) — `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`
- **.percentage_noClassesHeld_hasHumanSafeDisplay()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **.percentage_roundsOnlyForDisplay()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **.absentMarking_somePresentSomeAbsent_correctPercent()** (2 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.lateJoiner_percentBasedOnEligibleOnly()** (2 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (9 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (5 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (4 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (2 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (2 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)
- [Teacherprofile Authenticate](Teacherprofile_Authenticate.md) (1 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/core/model/AttendanceModels.kt`
- `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`
- `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`

## Audit Trail

- EXTRACTED: 46 (92%)
- INFERRED: 4 (8%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*