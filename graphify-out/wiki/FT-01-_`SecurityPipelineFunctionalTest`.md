# FT-01: `SecurityPipelineFunctionalTest`

> 19 nodes

## Key Concepts

- **FT-01: `SecurityPipelineFunctionalTest`** (15 connections) — `docs/00-master-implementation-plan.md`
- **PrerequisitesChecker** (11 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/PrerequisitesChecker.kt`
- **SecurityPipelineFunctionalTest** (11 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.remove()** (6 connections) — `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeDeviceSecurityChecker.kt`
- **.prerequisitesGate_cameraPermissionMissing_blocksStart()** (6 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.prerequisitesGate_noPinConfigured_blocksStart()** (6 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.tamperMonitor_dropDuringActiveLockTask_emitsTamperEvent()** (6 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **PrerequisitesCheckerTest** (5 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PrerequisitesCheckerTest.kt`
- **.embeddingCipher_encryptDecryptInFakeSessionContext()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.pinAuth_correctPin_grantsOneCommand()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.pinAuth_emptyPin_returnsFailure()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.pinAuth_wrongPin_returnsFailure()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.prerequisitesGate_allMet_allowsSessionStart()** (5 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.lockTask_startAndStop_completeLifecycle()** (4 connections) — `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- **.allPrerequisitesPresent_returnsReady()** (4 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PrerequisitesCheckerTest.kt`
- **.emptySet_returnsBlockedWithAllRequired()** (4 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PrerequisitesCheckerTest.kt`
- **.incompletePrerequisiteSet_blocksStartWithRemediation()** (4 connections) — `app/src/test/java/com/attract/attendance/lockdown/domain/PrerequisitesCheckerTest.kt`
- **Success** (2 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- **PrerequisitesChecker.kt** (1 connections) — `app/src/main/java/com/attract/attendance/lockdown/domain/PrerequisitesChecker.kt`

## Relationships

- [FakeSessionDao](FakeSessionDao.md) (14 shared connections)
- [assertTrue](assertTrue.md) (12 shared connections)
- [test](test.md) (4 shared connections)
- [FullWorkflowAcceptanceTest.kt](FullWorkflowAcceptanceTest.kt.md) (3 shared connections)
- [AuthResult](AuthResult.md) (2 shared connections)
- [EncryptedEmbedding](EncryptedEmbedding.md) (2 shared connections)
- [SessionTamperMonitor](SessionTamperMonitor.md) (2 shared connections)
- [LockTaskResult](LockTaskResult.md) (1 shared connections)
- [Unit Test Suite Assessment (107 Tests, 100% Pass)](Unit_Test_Suite_Assessment_107_Tests,_100__Pass.md) (1 shared connections)
- [FakeSecurityEventLogger](FakeSecurityEventLogger.md) (1 shared connections)
- [ExportAndBackupFunctionalTest](ExportAndBackupFunctionalTest.md) (1 shared connections)
- [Stage 3 — System Integration Testing (Instrumented on Device/Emulator)](Stage_3_—_System_Integration_Testing_Instrumented_on_Device-Emulator.md) (1 shared connections)

## Source Files

- `app/src/main/java/com/attract/attendance/lockdown/domain/LockdownModels.kt`
- `app/src/main/java/com/attract/attendance/lockdown/domain/PrerequisitesChecker.kt`
- `app/src/sharedTest/java/com/attract/attendance/lockdown/domain/FakeDeviceSecurityChecker.kt`
- `app/src/test/java/com/attract/attendance/functional/SecurityPipelineFunctionalTest.kt`
- `app/src/test/java/com/attract/attendance/lockdown/domain/PrerequisitesCheckerTest.kt`
- `docs/00-master-implementation-plan.md`

## Audit Trail

- EXTRACTED: 43 (56%)
- INFERRED: 34 (44%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*