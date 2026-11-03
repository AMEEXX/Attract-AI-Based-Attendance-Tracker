# .isEligible

> 17 nodes

## Key Concepts

- **.isEligible()** (8 connections) — `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`
- **AttendanceRulesIntegrationFunctionalTest** (7 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.percentage()** (7 connections) — `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`
- **FT-05: `AttendanceRulesIntegrationFunctionalTest`** (7 connections) — `docs/00-master-implementation-plan.md`
- **AttendanceRulesTest** (6 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **AttendancePercentage** (5 connections) — `app/src/main/java/com/attract/attendance/core/model/AttendanceModels.kt`
- **AttendanceRules** (5 connections) — `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`
- **.eligibility_enrolledAfterSessionStart_notCountedAsAbsent()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.eligibility_enrolledBeforeFirstSession_countedFromFirst()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.multipleSessionSameDay_countedSeparately()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.isEligible_lateJoiner_excludesEarlierSessions()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **.isEligible_originalRosterStudent_isAlwaysEligible()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **.absentMarking_somePresentSomeAbsent_correctPercent()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.lateJoiner_percentBasedOnEligibleOnly()** (3 connections) — `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- **.percentage_noClassesHeld_hasHumanSafeDisplay()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **.percentage_roundsOnlyForDisplay()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- **AttendanceRules.kt** (2 connections) — `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`

## Relationships

- [assertTrue](assertTrue.md) (4 shared connections)
- [test](test.md) (3 shared connections)
- [AttractRepository](AttractRepository.md) (3 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (2 shared connections)
- [AttractRepository.kt](AttractRepository.kt.md) (2 shared connections)
- [StudentEntity](StudentEntity.md) (2 shared connections)
- [DriveBackupWorker.kt](DriveBackupWorker.kt.md) (1 shared connections)
- [AttendanceStatus](AttendanceStatus.md) (1 shared connections)
- [AttendanceSessionEntity](AttendanceSessionEntity.md) (1 shared connections)
- [ExportAndBackupFunctionalTest](ExportAndBackupFunctionalTest.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/core/model/AttendanceModels.kt`
- `app/src/main/java/com/attract/attendance/domain/AttendanceRules.kt`
- `app/src/test/java/com/attract/attendance/domain/AttendanceRulesTest.kt`
- `app/src/test/java/com/attract/attendance/functional/AttendanceRulesIntegrationFunctionalTest.kt`
- `docs/00-master-implementation-plan.md`

## Audit Trail

- EXTRACTED: 38 (78%)
- INFERRED: 11 (22%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*