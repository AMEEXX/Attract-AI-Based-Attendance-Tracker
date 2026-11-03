# StudentTemplatePair

> God node · 43 connections · `app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt`

**Community:** [StudentTemplatePair](StudentTemplatePair.md)

## Connections by Relation

### calls
- .engine() `INFERRED`
- .recognitionDiagnosticsAndMatrix() `EXTRACTED`
- .runFullLfwThreeFrameBenchmark() `INFERRED`
- .runPipeline() `INFERRED`
- .loadGallery() `EXTRACTED`
- .`R05 reproduction - finished engine throws IllegalStateException if called before reset`() `INFERRED`
- .realHumanBiometricRecognitionAndImposterEvaluation() `INFERRED`
- .template() `INFERRED`
- .testEndToEndSimulationAndInvariants() `INFERRED`
- .`all fusion strategies preserve strong single-frame match`() `INFERRED`
- .getActiveTemplatesForClass() `EXTRACTED`
- .terminalEngineCannotBeReused_andMustBeClearedBeforeNewAttempt() `EXTRACTED`
- .testEmbeddingMatchingAndRosterIsolation() `INFERRED`
- .`no truncation or padding conversion exists for stale vectors`() `INFERRED`
- .`no compatible templates fails fast to unknown`() `INFERRED`
- .testMalformedBiometricInputs() `INFERRED`
- .`R03 reproduction - candidate identical to existing student must be flagged as duplicate`() `INFERRED`
- .`R06 reproduction - associate keeps last template score while grouped max keeps highest`() `INFERRED`
- .duplicateChecker_matchingExistingStudent_returnsSuspicious() `INFERRED`
- .realImageTest_multiPoseRecognitionFlow() `INFERRED`
- *…and 5 more `calls` connection(s) not listed (lowest-degree first to go)*

### contains
- RecognitionDecisionEngine.kt `EXTRACTED`

### imports
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) `EXTRACTED`
- RecognitionPathDiagnosticTest.kt `EXTRACTED`
- CoordinatorAndAuthTest.kt `EXTRACTED`

### method
- .hashCode() `EXTRACTED`
- .equals() `EXTRACTED`

### references
- .fuse() `EXTRACTED`
- .checkDuplicate() `EXTRACTED`
- .evaluate() `EXTRACTED`
- .fuse() `EXTRACTED`
- .fuse() `EXTRACTED`
- .fuse() `EXTRACTED`
- .fuse() `EXTRACTED`
- .scoreGroupedMax() `EXTRACTED`
- .partition() `EXTRACTED`
- .fuse() `EXTRACTED`
- .fuse() `EXTRACTED`
- .fuse() `EXTRACTED`

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*