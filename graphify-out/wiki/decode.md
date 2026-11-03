# .decode

> 24 nodes

## Key Concepts

- **.decode()** (16 connections) — `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- **.encode()** (16 connections) — `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- **XorCipher** (13 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **TemplateEnvelopeCodecTest** (10 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.sampleFloats()** (10 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.maybeAutoUpdateTemplate()** (8 connections) — `app/src/main/java/com/attract/attendance/data/repository/AttractRepository.kt`
- **.decode_boundToDifferentStudent_failsClosed_returnsNull()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.decode_tamperedBlob_failsClosed()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.decode_truncatedBlob_failsClosed()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.encode_withoutCipher_legacyPlaintextPassthrough()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.roundTrip_withCipher_restoresNormalizedFloats()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.encrypt()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.decode_legacyPlaintextRow_stillReadableAfterUpgrade()** (4 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.encode_withCipher_producesAeadEnvelope()** (4 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.decrypt()** (4 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **TemplateEnvelopeCodec** (3 connections) — `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- **.key()** (3 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.tag()** (3 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **ByteArray** (3 connections)
- **ByteArray** (2 connections)
- **FloatArray** (2 connections)
- **.clear()** (1 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **FloatArray** (1 connections)
- **FloatArray** (1 connections)

## Relationships

- [EncryptedEmbedding](EncryptedEmbedding.md) (7 shared connections)
- [AttractRepository](AttractRepository.md) (6 shared connections)
- [.loadGallery](loadGallery.md) (5 shared connections)
- [test](test.md) (1 shared connections)
- [.runPipeline](runPipeline.md) (1 shared connections)
- [FaceTemplateDao](FaceTemplateDao.md) (1 shared connections)
- [AttendanceScreen](AttendanceScreen.md) (1 shared connections)
- [assertTrue](assertTrue.md) (1 shared connections)
- [.l2Normalize](l2Normalize.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/data/repository/AttractRepository.kt`
- `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`

## Audit Trail

- EXTRACTED: 78 (99%)
- INFERRED: 1 (1%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*