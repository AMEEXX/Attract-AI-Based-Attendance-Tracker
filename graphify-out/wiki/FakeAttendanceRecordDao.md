# FakeAttendanceRecordDao

> God node · 60 connections · `app/src/sharedTest/java/com/attract/attendance/test/FakeDaos.kt`

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
- .completeDataPipeline_rosterToExport_executesSuccessfully() `EXTRACTED`
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
- *…and 11 more `calls` connection(s) not listed (lowest-degree first to go)*

### contains
- FakeDaos.kt `EXTRACTED`

### implements
- AttendanceRecordDao `EXTRACTED`

### imports
- SessionRecoveryManagerTest.kt `EXTRACTED`
- LiveSessionFunctionalTest.kt `EXTRACTED`
- SessionStateMachineComprehensiveFunctionalTest.kt `EXTRACTED`
- CoordinatorAndAuthTest.kt `EXTRACTED`
- SessionCoordinatorTest.kt `EXTRACTED`
- SessionRecoveryE2EAndroidTest.kt `EXTRACTED`
- FaceSessionE2EAndroidTest.kt `EXTRACTED`
- SessionRecoveryFunctionalTest.kt `EXTRACTED`
- DataPipelineFunctionalTest.kt `EXTRACTED`
- FinalizeFaceSessionCommandTest.kt `EXTRACTED`
- RecordPresentCommandTest.kt `EXTRACTED`

### method
- .observeStudentsForSession() `EXTRACTED`
- .insert() `EXTRACTED`
- .insertAll() `EXTRACTED`
- .forSession() `EXTRACTED`
- .updateStatus() `EXTRACTED`
- .observePresentCountForStudent() `EXTRACTED`
- .all() `EXTRACTED`
- .classReport() `EXTRACTED`
- .sessionExportRows() `EXTRACTED`
- .replaceAll() `EXTRACTED`
- .deleteForSession() `EXTRACTED`
- .deleteActiveFaceSessionRecordsForClass() `EXTRACTED`
- .deleteAllActiveFaceSessionRecords() `EXTRACTED`
- .presentCount() `EXTRACTED`

### references
- FT-04: `SessionStateMachineComprehensiveFunctionalTest` `INFERRED`
- FT-02: `ExportAndBackupFunctionalTest` `INFERRED`

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*