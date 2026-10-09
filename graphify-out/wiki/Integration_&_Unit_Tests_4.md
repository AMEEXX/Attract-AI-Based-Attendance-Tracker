# Integration & Unit Tests

> 21 nodes

## Key Concepts

- **PinHasher** (35 connections) — `app/src/main/java/com/attract/attendance/data/security/PinHasher.kt`
- **PinBackedAuthenticator** (9 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/PinBackedAuthenticator.kt`
- **PinAuthTest** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PinAuthTest.kt`
- **PinHasherTest** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/PinHasherTest.kt`
- **.derive()** (5 connections) — `app/src/main/java/com/attract/attendance/data/security/PinHasher.kt`
- **.correctPin_returnsSuccess()** (4 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PinAuthTest.kt`
- **.pinBufferIsClearedBeforeReturn()** (4 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PinAuthTest.kt`
- **.hash()** (3 connections) — `app/src/main/java/com/attract/attendance/data/security/PinHasher.kt`
- **.verify()** (3 connections) — `app/src/main/java/com/attract/attendance/data/security/PinHasher.kt`
- **.hash_andVerify_acceptsOnlyTheOriginalPin()** (3 connections) — `app/src/test/java/com/attract/attendance/data/security/PinHasherTest.kt`
- **.hashAndVerify_emptyPin_handlesCorrectly()** (3 connections) — `app/src/test/java/com/attract/attendance/data/security/PinHasherTest.kt`
- **.hashAndVerify_maxCharacterLimitPin_handlesCorrectly()** (3 connections) — `app/src/test/java/com/attract/attendance/data/security/PinHasherTest.kt`
- **.constantTimeComparison_pathIsUsed()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PinAuthTest.kt`
- **.malformedStoredPinHash_failsClosed()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PinAuthTest.kt`
- **.wrongPin_returnsFailure()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PinAuthTest.kt`
- **CharArray** (3 connections)
- **.verifyPin()** (2 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/PinBackedAuthenticator.kt`
- **.verify_malformedStoredValue_failsClosed()** (2 connections) — `app/src/test/java/com/attract/attendance/data/security/PinHasherTest.kt`
- **PinBackedAuthenticator.kt** (2 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/PinBackedAuthenticator.kt`
- **ByteArray** (1 connections)
- **CharArray** (1 connections)

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (20 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (4 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (4 shared connections)
- [UI Theme & Components](UI_Theme_&_Components.md) (1 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (1 shared connections)
- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/data/security/PinHasher.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/PinBackedAuthenticator.kt`
- `app/src/test/java/com/attract/attendance/data/security/PinHasherTest.kt`
- `app/src/test/java/com/attract/attendance/lockdown/domain/PinAuthTest.kt`

## Audit Trail

- EXTRACTED: 53 (79%)
- INFERRED: 14 (21%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*