# FakeStudentDao

> God node · 58 connections · `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`

**Community:** [FakeSessionDao](FakeSessionDao.md)

## Connections by Relation

### calls
- .processRelaunch_activeSessionDetected_recoveryManagerResumes() `EXTRACTED`
- .liveSession_fullLifecycle_startRecognizeRecordAndFinalize() `EXTRACTED`
- .crashRecovery_activeSessionFound_resumesSessionWhenAuthAndSecurityPass() `EXTRACTED`
- .crashRecovery_endSession_finalizesActiveSession() `EXTRACTED`
- .endSession_authenticated_finalizesAndReleasesLockTask() `EXTRACTED`
- .resumeSession_authenticatedAndSecurityReady_returnsSuccess() `EXTRACTED`
- .resumeSession_authFailed_returnsAuthFailed() `EXTRACTED`
- .crashRecovery_noActiveSession_returnsNoActiveSession() `EXTRACTED`
- .endAuthSucceeds_finalizationPersistsThenEnds() `EXTRACTED`
- .sessionCheckIn_faceRecognized_attendanceMarked() `EXTRACTED`
- .sessionCoordinator_teacherAssistanceWithGrant() `EXTRACTED`
- .endAuthFails_sessionRemainsActive() `EXTRACTED`
- .fullHappyPath_transitionsToEnded() `EXTRACTED`
- .illegalEvent_ignoredSafely() `EXTRACTED`
- .staleAttemptId_discardedNoWrite() `EXTRACTED`
- .twoRetryExhaustion_triggersAssistancePath() `EXTRACTED`
- .execute_partialAbsentScenario_somePresentRestNeedAbsentFinalization() `EXTRACTED`
- .execute_unrecordedStudentsAreMarkedAbsentAndSessionEnded() `EXTRACTED`
- .sessionCoordinator_deterministicRetryTransitions() `EXTRACTED`
- .endRequestedAndAuthSuccess_transitionsToEnded() `EXTRACTED`
- *…and 12 more `calls` connection(s) not listed (lowest-degree first to go)*

### contains
- FakeDaos.kt `EXTRACTED`

### implements
- StudentDao `EXTRACTED`

### imports
- SessionRecoveryManagerTest.kt `EXTRACTED`
- LiveSessionFunctionalTest.kt `EXTRACTED`
- SessionStateMachineComprehensiveFunctionalTest.kt `EXTRACTED`
- CoordinatorAndAuthTest.kt `EXTRACTED`
- SessionCoordinatorTest.kt `EXTRACTED`
- SessionRecoveryE2EAndroidTest.kt `EXTRACTED`
- FaceSessionE2EAndroidTest.kt `EXTRACTED`
- SessionRecoveryFunctionalTest.kt `EXTRACTED`
- FinalizeFaceSessionCommandTest.kt `EXTRACTED`
- RecordPresentCommandTest.kt `EXTRACTED`
- RosterImportEdgeCaseFunctionalTest.kt `EXTRACTED`

### method
- .find() `EXTRACTED`
- .observeActiveForClass() `EXTRACTED`
- .findActiveByRoll() `EXTRACTED`
- .activeForClass() `EXTRACTED`
- .all() `EXTRACTED`
- .insert() `EXTRACTED`
- .insertAll() `EXTRACTED`
- .update() `EXTRACTED`
- .setArchived() `EXTRACTED`
- .resetEnrollmentForClass() `EXTRACTED`
- .resetAllEnrollments() `EXTRACTED`
- .restoreEnrolledWithTemplates() `EXTRACTED`

### references
- FT-02: `ExportAndBackupFunctionalTest` `INFERRED`

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*