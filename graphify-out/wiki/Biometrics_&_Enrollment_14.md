# Biometrics & Enrollment

> 17 nodes

## Key Concepts

- **XorCipher** (13 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.decode()** (11 connections) — `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- **TemplateEnvelopeCodecTest** (10 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.encode()** (10 connections) — `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- **.sampleFloats()** (10 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.decode_boundToDifferentStudent_failsClosed_returnsNull()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.decode_tamperedBlob_failsClosed()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.decode_truncatedBlob_failsClosed()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.encode_withoutCipher_legacyPlaintextPassthrough()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.roundTrip_withCipher_restoresNormalizedFloats()** (5 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.decode_legacyPlaintextRow_stillReadableAfterUpgrade()** (4 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **.encode_withCipher_producesAeadEnvelope()** (4 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **TemplateEnvelopeCodec** (3 connections) — `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- **ByteArray** (2 connections)
- **FloatArray** (2 connections)
- **.clear()** (1 connections) — `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`
- **FloatArray** (1 connections)

## Relationships

- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (7 shared connections)
- [Byte Array](Byte_Array.md) (4 shared connections)
- [Database & Persistence](Database_&_Persistence.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/data/security/TemplateEnvelopeCodec.kt`
- `app/src/test/java/com/attract/attendance/data/security/TemplateEnvelopeCodecTest.kt`

## Audit Trail

- EXTRACTED: 53 (98%)
- INFERRED: 1 (2%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*