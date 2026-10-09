# Quality & Adaptive Verification

> 54 nodes

## Key Concepts

- **.validate()** (13 connections) — `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- **EnrollmentResult** (11 connections) — `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- **EnrollmentAndDuplicateTest** (10 connections) — `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`
- **.checkDuplicate()** (9 connections) — `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- **.createUnitVector()** (9 connections) — `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`
- **EnrollmentSample** (8 connections) — `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- **.scoreGroupedMax()** (8 connections) — `app/src/main/java/com/attract/attendance/domain/face/IdentityScorer.kt`
- **EnrollmentBatch.kt** (8 connections) — `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- **ScoreEvaluationResult** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/IdentityScorer.kt`
- **.cosineSimilarity()** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/IdentityScorer.kt`
- **.evaluate()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/IdentityScorer.kt`
- **.enrollmentValidator_personSwap_rejected()** (6 connections) — `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`
- **.enrollmentValidator_validThreeSamples_passes()** (6 connections) — `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`
- **DuplicateResult** (5 connections) — `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- **.duplicateChecker_matchingExistingStudent_returnsSuspicious()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`
- **.enrollmentValidator_insufficientSamples_rejected()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`
- **.enrollmentValidator_reusedFrameTimestamp_rejected()** (5 connections) — `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`
- **BiometricModelProfile** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/BiometricModelProfile.kt`
- **ValidationResult** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- **IdentityScorer** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/IdentityScorer.kt`
- **.duplicateChecker_selfExclusionForReEnrollment_returnsClear()** (4 connections) — `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`
- **EnrollmentBatchValidator** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- **ScoredCandidate** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/IdentityScorer.kt`
- **.hashCode()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- **.createOrthogonalVector()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`
- *... and 29 more nodes in this community*

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (8 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (5 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (1 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (1 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (1 shared connections)
- [Galleryloadresultkt Emptyhealthy](Galleryloadresultkt_Emptyhealthy.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/BiometricModelProfile.kt`
- `app/src/main/java/com/attract/attendance/domain/face/EnrollmentBatch.kt`
- `app/src/main/java/com/attract/attendance/domain/face/IdentityScorer.kt`
- `app/src/test/java/com/attract/attendance/domain/face/EnrollmentAndDuplicateTest.kt`

## Audit Trail

- EXTRACTED: 97 (90%)
- INFERRED: 11 (10%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*