# Quality & Adaptive Verification

> 14 nodes

## Key Concepts

- **EncryptedEmbedding** (22 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **EmbeddingCipher** (14 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **EncryptedEmbeddingCipher** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`
- **.decrypt()** (3 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **.encrypt()** (3 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **.decrypt()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`
- **.encrypt()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`
- **TemplateEnvelopeCodec.kt** (3 connections) — `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- **ByteArray** (2 connections)
- **ByteArray** (2 connections)
- **.clear()** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **.equals()** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **.hashCode()** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **.clear()** (1 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`

## Relationships

- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (8 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (7 shared connections)
- [Startup & Session Recovery](Startup_&_Session_Recovery.md) (5 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (3 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (2 shared connections)
- [Byte Array](Byte_Array.md) (2 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`

## Audit Trail

- EXTRACTED: 46 (100%)
- INFERRED: 0 (0%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*