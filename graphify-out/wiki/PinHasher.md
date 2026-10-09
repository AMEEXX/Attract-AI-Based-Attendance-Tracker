# PinHasher

> God node · 35 connections · `app/src/main/java/com/attract/attendance/data/security/PinHasher.kt`

**Community:** [Integration & Unit Tests](Integration_&_Unit_Tests.md)

## Connections by Relation

### calls
- .realUiAttendanceFlow() `EXTRACTED`
- .recognitionDiagnosticsAndMatrix() `EXTRACTED`
- .doWork() `EXTRACTED`
- .testRepositoryEnrollmentAtomicityOnDatabaseFail() `EXTRACTED`
- .setUp() `EXTRACTED`
- .seed() `EXTRACTED`
- .setUp() `EXTRACTED`
- .pinAuth_correctPin_grantsOneCommand() `EXTRACTED`
- .pinAuth_emptyPin_returnsFailure() `EXTRACTED`
- .pinAuth_wrongPin_returnsFailure() `EXTRACTED`
- .correctPin_returnsSuccess() `EXTRACTED`
- .pinBufferIsClearedBeforeReturn() `EXTRACTED`
- .phase0_cleanEnvironment() `EXTRACTED`
- .constantTimeComparison_pathIsUsed() `EXTRACTED`
- .malformedStoredPinHash_failsClosed() `EXTRACTED`
- .wrongPin_returnsFailure() `EXTRACTED`
- .hash_andVerify_acceptsOnlyTheOriginalPin() `INFERRED`
- .hashAndVerify_emptyPin_handlesCorrectly() `INFERRED`
- .hashAndVerify_maxCharacterLimitPin_handlesCorrectly() `INFERRED`
- .verify_malformedStoredValue_failsClosed() `INFERRED`

### contains
- PinHasher.kt `EXTRACTED`

### imports
- FullWorkflowAcceptanceTest.kt `EXTRACTED`
- RecognitionPathDiagnosticTest.kt `EXTRACTED`
- CameraProcessingAndMlIntegrationTest.kt `EXTRACTED`
- RealUiFlowAcceptanceTest.kt `EXTRACTED`
- FallbackAttendanceAndroidTest.kt `EXTRACTED`
- SecurityPipelineFunctionalTest.kt `EXTRACTED`
- TemplateMigrationAndroidTest.kt `EXTRACTED`
- SeedWebcamTestData.kt `EXTRACTED`
- DriveBackupWorker.kt `EXTRACTED`
- PinAuthTest.kt `EXTRACTED`
- PinBackedAuthenticator.kt `EXTRACTED`

### method
- .derive() `EXTRACTED`
- .hash() `EXTRACTED`
- .verify() `EXTRACTED`

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*