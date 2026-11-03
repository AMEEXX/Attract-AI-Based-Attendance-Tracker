# EncryptedEmbedding

> 41 nodes

## Key Concepts

- **EncryptedEmbedding** (22 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **AesGcmEmbeddingCipher** (14 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **EmbeddingCipher** (14 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **EmbeddingCipherTest** (13 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **AesGcmEmbeddingCipher.kt** (7 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **EncryptedEmbeddingCipher** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`
- **.generateKey()** (6 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **EncryptedEmbeddingCipher.kt** (6 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`
- **.buildAad()** (5 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **InvalidatedKeyTest** (4 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/InvalidatedKeyTest.kt`
- **.decrypt()** (4 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **.encrypt()** (4 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **keygenerator** (4 connections)
- **.decrypt()** (3 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **.encrypt()** (3 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- **.aadBindsAllFourFieldsInCorrectOrder()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.decrypt()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`
- **.encrypt()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`
- **TemplateEnvelopeCodec.kt** (3 connections) — `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- **ByteArray** (3 connections)
- **cipher** (3 connections)
- **.decrypt_withAlteredCiphertext_fails()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.decrypt_withAlteredIv_fails()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.decrypt_withDifferentKey_failsAuthentication()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/InvalidatedKeyTest.kt`
- **.reEncryptionWithKey1StillWorks_afterKey2Failure()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/InvalidatedKeyTest.kt`
- *... and 16 more nodes in this community*

## Relationships

- [test](test.md) (12 shared connections)
- [KeystoreEmbeddingCipher.kt](KeystoreEmbeddingCipher.kt.md) (8 shared connections)
- [.decode](decode.md) (7 shared connections)
- [FT-01: `SecurityPipelineFunctionalTest`](FT-01-_`SecurityPipelineFunctionalTest`.md) (2 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (2 shared connections)
- [LockTaskControllerImpl.kt](LockTaskControllerImpl.kt.md) (1 shared connections)
- [LockTaskResult](LockTaskResult.md) (1 shared connections)
- [assertTrue](assertTrue.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownInterfaces.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- `app/src/test/java/com/attract/attendance/lockdown/crypto/EncryptedEmbeddingCipher.kt`
- `app/src/test/java/com/attract/attendance/lockdown/crypto/InvalidatedKeyTest.kt`

## Audit Trail

- EXTRACTED: 95 (97%)
- INFERRED: 3 (3%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*