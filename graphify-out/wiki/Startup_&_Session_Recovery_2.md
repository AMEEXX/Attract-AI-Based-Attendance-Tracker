# Startup & Session Recovery

> 22 nodes

## Key Concepts

- **AesGcmEmbeddingCipher** (14 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **EmbeddingCipherTest** (12 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.generateKey()** (6 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **.buildAad()** (5 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **.decrypt()** (4 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **.encrypt()** (4 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **.embeddingCipher_encryptDecryptInFakeSessionContext()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **InvalidatedKeyTest** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/InvalidatedKeyTest.kt`
- **.aadBindsAllFourFieldsInCorrectOrder()** (3 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **ByteArray** (3 connections)
- **.decrypt_withAlteredCiphertext_fails()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.decrypt_withAlteredIv_fails()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.decrypt_withDifferentKey_failsAuthentication()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/InvalidatedKeyTest.kt`
- **.reEncryptionWithKey1StillWorks_afterKey2Failure()** (2 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/InvalidatedKeyTest.kt`
- **SecretKey** (2 connections)
- **.clear()** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- **.ciphertextSwappedBetweenTwoTemplatesOfSameStudent_fails()** (1 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.decrypt_withDifferentModelVersion_fails()** (1 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.decrypt_withDifferentStudentId_fails()** (1 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.decrypt_withDifferentTemplateId_fails()** (1 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.decryptDoesNotRequireBiometricPrompt()** (1 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- **.encryptDecrypt_roundTrip_recoversExactBytes()** (1 connections) — `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (7 shared connections)
- [Drive Backup & Export](Drive_Backup_&_Export.md) (4 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (3 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/lockdown/data/crypto/AesGcmEmbeddingCipher.kt`
- `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- `app/src/test/java/com/attract/attendance/lockdown/crypto/EmbeddingCipherTest.kt`
- `app/src/test/java/com/attract/attendance/lockdown/crypto/InvalidatedKeyTest.kt`

## Audit Trail

- EXTRACTED: 43 (96%)
- INFERRED: 2 (4%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*