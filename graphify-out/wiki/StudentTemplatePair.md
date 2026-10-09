# StudentTemplatePair

> God node · 41 connections · `app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt`

**Community:** [Integration & Unit Tests](Integration_&_Unit_Tests.md)

## Connections by Relation

### calls
- .engine() `INFERRED`
- .recognitionDiagnosticsAndMatrix() `EXTRACTED`
- .runFullLfwThreeFrameBenchmark() `INFERRED`
- .runPipeline() `INFERRED`
- .`R05 reproduction - finished engine throws IllegalStateException if called before reset`() `INFERRED`
- .realHumanBiometricRecognitionAndImposterEvaluation() `INFERRED`
- .template() `INFERRED`
- .testEndToEndSimulationAndInvariants() `INFERRED`
- .`all fusion strategies preserve strong single-frame match`() `INFERRED`
- .terminalEngineCannotBeReused_andMustBeClearedBeforeNewAttempt() `EXTRACTED`
- .testEmbeddingMatchingAndRosterIsolation() `INFERRED`
- .`no truncation or padding conversion exists for stale vectors`() `INFERRED`
- .`no compatible templates fails fast to unknown`() `INFERRED`
- .testMalformedBiometricInputs() `INFERRED`
- .`R03 reproduction - candidate identical to existing student must be flagged as duplicate`() `INFERRED`
- .`R06 reproduction - associate keeps last template score while grouped max keeps highest`() `INFERRED`
- .duplicateChecker_matchingExistingStudent_returnsSuspicious() `INFERRED`
- .realImageTest_multiPoseRecognitionFlow() `INFERRED`
- .realImposterTest_verifiesCrossIdentityRejection() `INFERRED`
- .duplicateChecker_selfExclusionForReEnrollment_returnsClear() `INFERRED`
- *…and 3 more `calls` connection(s) not listed (lowest-degree first to go)*

### contains
- RecognitionDecisionEngine.kt `EXTRACTED`

### imports
- FullWorkflowAcceptanceTest.kt `EXTRACTED`
- RecognitionPathDiagnosticTest.kt `EXTRACTED`
- CoordinatorAndAuthTest.kt `EXTRACTED`

### method
- .hashCode() `EXTRACTED`
- .equals() `EXTRACTED`

### references
- .fuse() `EXTRACTED`
- .fuse() `EXTRACTED`
- .evaluate() `EXTRACTED`
- .checkDuplicate() `EXTRACTED`
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