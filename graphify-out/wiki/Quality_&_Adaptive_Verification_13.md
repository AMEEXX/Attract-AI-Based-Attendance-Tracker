# Quality & Adaptive Verification

> 23 nodes

## Key Concepts

- **.cosineSimilarity()** (12 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **.`R05 reproduction - finished engine throws IllegalStateException if called before reset`()** (11 connections) — `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- **DefectReproductionTest** (9 connections) — `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- **TemplateMatcherTest** (6 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **.findBestMatch()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **.unit()** (6 connections) — `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- **TemplateMatcher** (5 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **.`R03 reproduction - candidate identical to existing student must be flagged as duplicate`()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- **.`R06 reproduction - associate keeps last template score while grouped max keeps highest`()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- **.`R07 reproduction - zero-vector has zero norm and must be rejected as invalid`()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- **.mix()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- **.findBestMatch_selectsHighestSimilarityCandidate()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **FloatArray** (3 connections)
- **.toByteArray()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **.toFloatArray()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **.`R01 reproduction - candidate list for enrollment must be NOT_ENROLLED only`()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- **.`R04 reproduction - active-only sweep misses ENROLLED students with deactivated templates`()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- **.findBestMatch_emptyCandidates_returnsNegativeOneAndZeroScore()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **.findBestMatch_tieBreakSelection_selectsFirstOccurrence()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **.identicalVectors_returnSimilarityOne()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **.orthogonalVectors_returnSimilarityZero()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **FloatArray** (2 connections)
- **ByteArray** (1 connections)

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (8 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (5 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (4 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (2 shared connections)
- [UI Theme & Components](UI_Theme_&_Components.md) (2 shared connections)
- [Full Biometric Pipeline Integration Test](Full_Biometric_Pipeline_Integration_Test.md) (1 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (1 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- `app/src/test/java/com/attract/attendance/domain/face/DefectReproductionTest.kt`
- `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`

## Audit Trail

- EXTRACTED: 48 (79%)
- INFERRED: 13 (21%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*