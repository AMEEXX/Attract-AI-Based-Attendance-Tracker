# .runPipeline

> 19 nodes

## Key Concepts

- **.runPipeline()** (13 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt`
- **.cosineSimilarity()** (13 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **FullBiometricPipelineIntegrationTest** (8 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt`
- **TemplateMatcherTest** (8 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **.findBestMatch()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **TemplateMatcher** (5 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **.setUp()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt`
- **.findBestMatch_selectsHighestSimilarityCandidate()** (3 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **FloatArray** (3 connections)
- **.executeFullEndToEndBiometricPipeline_lenientConfig()** (2 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt`
- **.executeFullEndToEndBiometricPipeline_productionConfig()** (2 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt`
- **.toByteArray()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **.toFloatArray()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- **.findBestMatch_emptyCandidates_returnsNegativeOneAndZeroScore()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **.findBestMatch_tieBreakSelection_selectsFirstOccurrence()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **.identicalVectors_returnSimilarityOne()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **.orthogonalVectors_returnSimilarityZero()** (2 connections) — `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`
- **Context** (2 connections)
- **ByteArray** (1 connections)

## Relationships

- [assertTrue](assertTrue.md) (3 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (2 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (2 shared connections)
- [YoloFaceDetector](YoloFaceDetector.md) (2 shared connections)
- [.extractEmbedding](extractEmbedding.md) (2 shared connections)
- [FaceQualitySignals](FaceQualitySignals.md) (2 shared connections)
- [StudentTemplatePair](StudentTemplatePair.md) (2 shared connections)
- [.`R05 reproduction - finished engine throws IllegalStateException if called before reset`](`R05_reproduction_-_finished_engine_throws_IllegalStateException_if_called_before_reset`.md) (2 shared connections)
- [.calibrationDefaults](calibrationDefaults.md) (1 shared connections)
- [RecognitionDecisionEngine](RecognitionDecisionEngine.md) (1 shared connections)
- [sqrt](sqrt.md) (1 shared connections)
- [test](test.md) (1 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt`
- `app/src/main/java/com/attract/attendance/domain/face/TemplateMatcher.kt`
- `app/src/test/java/com/attract/attendance/domain/face/TemplateMatcherTest.kt`

## Audit Trail

- EXTRACTED: 48 (87%)
- INFERRED: 7 (13%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*