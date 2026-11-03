# FakeSessionDao

> God node · 61 connections · `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`

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
- *…and 10 more `calls` connection(s) not listed (lowest-degree first to go)*

### contains
- FakeDaos.kt `EXTRACTED`

### implements
- SessionDao `EXTRACTED`

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

### method
- .observeActiveForClass() `EXTRACTED`
- .observeActiveSummaryForClass() `EXTRACTED`
- .observeEndedForClass() `EXTRACTED`
- .observeSessionsForDate() `EXTRACTED`
- .activeFaceSession() `EXTRACTED`
- .activeForClass() `EXTRACTED`
- .all() `EXTRACTED`
- .find() `EXTRACTED`
- .insert() `EXTRACTED`
- .insertAll() `EXTRACTED`
- .findStaleActiveSessions() `EXTRACTED`
- .finish() `EXTRACTED`
- .observeSessionDaysForMonth() `EXTRACTED`
- .observeEndedSessionCountForClass() `EXTRACTED`
- .latestIdForClass() `EXTRACTED`
- .reactivate() `EXTRACTED`
- .deleteEnded() `EXTRACTED`
- .deleteActiveFaceSessionsForClass() `EXTRACTED`
- .deleteAllActiveFaceSessions() `EXTRACTED`

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*