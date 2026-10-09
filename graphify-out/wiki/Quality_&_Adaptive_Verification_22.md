# Quality & Adaptive Verification

> 9 nodes

## Key Concepts

- **.combineEmbeddings()** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/EmbeddingEngine.kt`
- **.l2Normalize()** (7 connections) — `app/src/main/java/com/attract/attendance/domain/face/EmbeddingEngine.kt`
- **EmbeddingEngineTest** (6 connections) — `app/src/test/java/com/attract/attendance/domain/face/EmbeddingEngineTest.kt`
- **.combineEmbeddingsAveragesAndNormalizes()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/EmbeddingEngineTest.kt`
- **.combineEmbeddingsEmptyListReturnsZeroVector()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/EmbeddingEngineTest.kt`
- **.l2NormalizeHandlesZeroVectorGracefully()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/EmbeddingEngineTest.kt`
- **.l2NormalizeProducesUnitLength()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/EmbeddingEngineTest.kt`
- **FloatArray** (3 connections)
- **.embeddingSizeIs512()** (1 connections) — `app/src/test/java/com/attract/attendance/domain/face/EmbeddingEngineTest.kt`

## Relationships

- [UI Theme & Components](UI_Theme_&_Components.md) (4 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (4 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (2 shared connections)
- [Galleryloadresultkt Emptyhealthy](Galleryloadresultkt_Emptyhealthy.md) (1 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/domain/face/EmbeddingEngine.kt`
- `app/src/test/java/com/attract/attendance/domain/face/EmbeddingEngineTest.kt`

## Audit Trail

- EXTRACTED: 20 (83%)
- INFERRED: 4 (17%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*