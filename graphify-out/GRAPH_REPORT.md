# Graph Report - Attract - Face Based Attendance Tracker  (2026-10-09)

## Corpus Check
- 87 files · ~316,231 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2637 nodes · 7133 edges · 145 communities (119 shown, 26 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 397 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Quality & Adaptive Verification
- Biometrics & Enrollment
- Biometrics & Enrollment
- Roster Import & OCR
- Biometrics & Enrollment
- Quality & Adaptive Verification
- Quality & Adaptive Verification
- Attendance Camera UI
- Biometrics & Enrollment
- Attendance Camera UI
- Quality & Adaptive Verification
- Biometrics & Enrollment
- Quality & Adaptive Verification
- Database & Persistence
- Quality & Adaptive Verification
- Startup & Session Recovery
- Quality & Adaptive Verification
- Biometrics & Enrollment
- Biometrics & Enrollment
- Biometrics & Enrollment
- Database & Persistence
- Drive Backup & Export
- Attendance Camera UI
- Quality & Adaptive Verification
- Quality & Adaptive Verification
- Quality & Adaptive Verification
- Attendance Session Engine
- Integration & Unit Tests
- Biometrics & Enrollment
- Biometrics & Enrollment
- Quality & Adaptive Verification
- Quality & Adaptive Verification
- Database & Persistence
- Quality & Adaptive Verification
- Attendance Camera UI
- UI Theme & Components
- Attendance Session Engine
- Quality & Adaptive Verification
- Quality & Adaptive Verification
- Biometrics & Enrollment
- Biometrics & Enrollment
- Startup & Session Recovery
- Biometrics & Enrollment
- Quality & Adaptive Verification
- Roster Import & OCR
- Biometrics & Enrollment
- Integration & Unit Tests
- Quality & Adaptive Verification
- Quality & Adaptive Verification
- Roster Import & OCR
- Integration & Unit Tests
- Biometrics & Enrollment
- UI Theme & Components
- Database & Persistence
- Drive Backup & Export
- Integration & Unit Tests
- Attendance Camera UI
- UI Theme & Components
- Roster Import & OCR
- Biometrics & Enrollment
- YOLO & TFLite Pipeline
- Roster Import & OCR
- Biometrics & Enrollment
- Quality & Adaptive Verification
- Roster Import & OCR
- Attendance Camera UI
- Biometrics & Enrollment
- Biometrics & Enrollment
- Integration & Unit Tests
- Drive Backup & Export
- Roster Import & OCR
- Biometrics & Enrollment
- Database & Persistence
- Biometrics & Enrollment
- Quality & Adaptive Verification
- Startup & Session Recovery
- Startup & Session Recovery
- Securitymodelskt Securityevent
- Quality & Adaptive Verification
- Drive Backup & Export
- Quality & Adaptive Verification
- Keystoreembeddingcipher Buildaad
- Attendance Session Engine
- Facealignerkt Facealigner
- Biometrics & Enrollment
- Attendance Session Engine
- Runphoneacceptanceps Adb
- Biometrics & Enrollment
- Attendance Session Engine
- Integration & Unit Tests
- Integration & Unit Tests
- Integration & Unit Tests
- Integration & Unit Tests
- Roster Import & OCR
- Drive Backup & Export
- Galleryloadresultkt Emptyhealthy
- Livenessreason Eyesclosedorstatic
- Integration & Unit Tests
- Quality & Adaptive Verification
- Attractapplicationkt Appcontainer
- Drive Backup & Export
- Lockdownmodelskt Blocked
- Biometrics & Enrollment
- Glowbuttonstate Error
- Biometrics & Enrollment
- Biometrics & Enrollment
- Phoneaddstudentsps Bounds
- Roster Import & OCR
- Biometrics & Enrollment
- Teacherprofile Authenticate
- Bytearray Decrypt
- Integration & Unit Tests
- Gradlew Gradlewscript
- Broadcastreceiver Onreceive
- Historysortorder Newestfirst
- Success
- Enrollstudentface Bytearray
- Appbuildgradlekts
- Buildgradlekts
- Integration & Unit Tests
- Settingsgradlekts
- Bounds
- Bounds
- Full Biometric Pipeline Integration Test
- Kiosk Lockdown & Safety
- Engine
- Frame State
- Byte Array
- Attract View Model Test
- Roll Number Comparator Test
- Gradlew
- Theme System & Styling
- Session History & Records
- Success
- Biometric Face Pipeline
- Result
- Char Array
- Job
- State Flow
- View Model
- Module 141

## God Nodes (most connected - your core abstractions)
1. `assertTrue()` - 199 edges
2. `AttractRepository` - 85 edges
3. `FakeSessionDao` - 61 edges
4. `FakeAttendanceRecordDao` - 58 edges
5. `FakeStudentDao` - 57 edges
6. `AttractViewModel` - 54 edges
7. `StudentTemplatePair` - 41 edges
8. `AttendanceStatus` - 40 edges
9. `AttendanceSessionEntity` - 36 edges
10. `PinHasher` - 35 edges

## Surprising Connections (you probably didn't know these)
- `rows()` --indirect_call--> `e()`  [INFERRED]
  scripts/report_phone_db.py → tools/biometric_eval/build_feature_cache.py
- `AdaptiveStrategyBenchmarkTest` --calls--> `RecognitionDecisionEngine`  [INFERRED]
  app/src/androidTest/java/com/attract/attendance/domain/face/AdaptiveStrategyBenchmarkTest.kt → app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt
- `FullBiometricPipelineIntegrationTest` --calls--> `RecognitionDecisionEngine`  [INFERRED]
  app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt → app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt
- `LfwThreeFrameBenchmarkTest` --calls--> `RecognitionDecisionEngine`  [INFERRED]
  app/src/androidTest/java/com/attract/attendance/domain/face/LfwThreeFrameBenchmarkTest.kt → app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt
- `RealHumanRecognitionTest` --calls--> `RecognitionDecisionEngine`  [INFERRED]
  app/src/androidTest/java/com/attract/attendance/domain/face/RealHumanRecognitionTest.kt → app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt

## Import Cycles
- None detected.

## Communities (145 total, 26 thin omitted)

### Community 0 - "Quality & Adaptive Verification"
Cohesion: 0.09
Nodes (17): FaceSessionE2EAndroidTest, StudentSummary, StudentEntity, RecordPresentCommand, TeacherAuthorizationGrant, SessionCoordinator, FakeAttendanceRecordDao, FakeSessionDao (+9 more)

### Community 1 - "Biometrics & Enrollment"
Cohesion: 0.05
Nodes (35): ActiveSessionExists, AlreadyFinalized, AlreadyPresent, AnotherSessionActive, AppError, DuplicateRollNumber, NotEligible, NotFound (+27 more)

### Community 2 - "Biometrics & Enrollment"
Cohesion: 0.05
Nodes (14): ActiveSessionRow, AttendanceSessionEntity, Flow, SessionDao, SessionRow, SessionStudentRow, AttendanceSessionEntity, Flow (+6 more)

### Community 3 - "Roster Import & OCR"
Cohesion: 0.06
Nodes (57): add, alertdialog, animatedcontent, SessionSummary, Modifier, SessionCard(), WorkspaceHistoryTab(), appthememode (+49 more)

### Community 4 - "Biometrics & Enrollment"
Cohesion: 0.08
Nodes (18): SessionRecoveryE2EAndroidTest, SecurityAndLockTaskAndroidTest, FinalizeFaceSessionCommand, SessionRecoveryManager, AuthMethod, BIOMETRIC, PIN, PrerequisitesChecker (+10 more)

### Community 5 - "Quality & Adaptive Verification"
Cohesion: 0.06
Nodes (35): BiometricModelProfile, AlreadyEnrolled, ApprovalExpired, Busy, Cancelled, CaptureRejected, Clear, Committed (+27 more)

### Community 6 - "Quality & Adaptive Verification"
Cohesion: 0.07
Nodes (11): DatabaseConstraintsTest, RoomDatabaseIntegrationTest, AttendanceSessionEntity, ClassSectionEntity, TeacherEntity, StartupCoordinator, FakeSessionDao, FakeTeacherDao (+3 more)

### Community 7 - "Attendance Camera UI"
Cohesion: 0.13
Nodes (40): alignment, animatefloat, Modifier, PoseStep, PoseStepper(), ImportOptionCard(), ImportRosterChoiceBottomSheet(), ImageVector (+32 more)

### Community 8 - "Biometrics & Enrollment"
Cohesion: 0.07
Nodes (40): accountcircle, RollNumberComparator, AttractOutlinedButton(), AttractPrimaryButton(), AttractTextButton(), Modifier, AttractCard(), Color (+32 more)

### Community 9 - "Attendance Camera UI"
Cohesion: 0.07
Nodes (39): animatecolorasstate, animateintasstate, AttendanceStrings, FaceFrameOverlay(), Color, Modifier, Modifier, VerificationMeter() (+31 more)

### Community 10 - "Quality & Adaptive Verification"
Cohesion: 0.08
Nodes (35): AppearanceBottomSheet(), AppearancePillOption(), ImageVector, Modifier, EmptyState(), ImageVector, Modifier, ErrorState() (+27 more)

### Community 11 - "Biometrics & Enrollment"
Cohesion: 0.08
Nodes (24): collections, csv, cv2, itertools, json, math, numpy, os (+16 more)

### Community 12 - "Quality & Adaptive Verification"
Cohesion: 0.09
Nodes (18): LivenessReason, EYES_CLOSED_OR_STATIC, INSUFFICIENT_VARIANCE, PASSED, PASSIVE_SPOOF_SUSPECTED, PRINT_PHOTO_SUSPECTED, SCREEN_REPLAY_SUSPECTED, LivenessResult (+10 more)

### Community 13 - "Database & Persistence"
Cohesion: 0.12
Nodes (9): RosterImportE2EAndroidTest, RosterTablePlanner, DataPipelineFunctionalTest, assertarrayequals, assertequals, assertfalse, asserttrue, first (+1 more)

### Community 14 - "Quality & Adaptive Verification"
Cohesion: 0.17
Nodes (23): after, androidjunit4, SeedWebcamTestData, AttractDatabase, CreateClassCommand, CreateStudentCommand, LivenessEngine, applicationprovider (+15 more)

### Community 15 - "Startup & Session Recovery"
Cohesion: 0.09
Nodes (33): AddStudentBottomSheet(), Modifier, Modifier, RosterSelectionBottomSheet(), EditableOcrRow(), EditableOcrStudent, Modifier, OcrConfirmationDialog() (+25 more)

### Community 16 - "Quality & Adaptive Verification"
Cohesion: 0.11
Nodes (35): dataclasses, Image, ndarray, tflite_runtime_interpreter, align(), brightness_and_blur(), decide(), detect() (+27 more)

### Community 17 - "Biometrics & Enrollment"
Cohesion: 0.07
Nodes (19): AttendanceStatus, ABSENT, PRESENT, SessionMode, ASSISTED, FACE, MANUAL, SessionStatus (+11 more)

### Community 18 - "Biometrics & Enrollment"
Cohesion: 0.16
Nodes (6): CommandResult, Failure, AttractRepository, AttendanceSessionEntity, ByteArray, StaleTemplateReport

### Community 19 - "Biometrics & Enrollment"
Cohesion: 0.18
Nodes (15): Ambiguous, EvidenceFusion, EvidenceFusionStrategy, EvidenceFusionStrategy, EvidenceFusionStrategy, EvidenceFusionStrategy, EvidenceFusionStrategy, EvidenceFusionStrategy (+7 more)

### Community 20 - "Database & Persistence"
Cohesion: 0.06
Nodes (27): CrashRecoveryScreen(), T, Factory, AttractApp(), androidx, com, LoadingScreen(), RosterImportPreviewDialog() (+19 more)

### Community 21 - "Drive Backup & Export"
Cohesion: 0.08
Nodes (8): AttendanceRecordDao, AttendanceRecordEntity, StudentEntity, StudentDao, AttendanceRecordEntity, FinalizeSessionResult, SessionNotFoundOrEnded, Success

### Community 22 - "Attendance Camera UI"
Cohesion: 0.14
Nodes (15): EnrollmentValidation, FloatArray, ModelProfile, Ok, Partitioned, Rejected, TemplateCompatibility, VectorClass (+7 more)

### Community 23 - "Quality & Adaptive Verification"
Cohesion: 0.09
Nodes (24): AuthFailed, Blocked, NoActiveSession, RecoveryEndResult, RecoveryResumeResult, Success, BlockingSetup, Onboarding (+16 more)

### Community 24 - "Quality & Adaptive Verification"
Cohesion: 0.17
Nodes (3): AdaptiveVerificationEngineTest, FloatArray, EvidenceFusionStrategy

### Community 25 - "Quality & Adaptive Verification"
Cohesion: 0.12
Nodes (4): FallbackAttendanceAndroidTest, RosterTablePlannerTest, assertTrue(), RosterImportEdgeCaseFunctionalTest

### Community 26 - "Attendance Session Engine"
Cohesion: 0.11
Nodes (28): CalendarDayCell(), CalendarScreen(), DayOfWeekHeader(), Modifier, MonthGrid(), MonthHeader(), SessionCard(), AttractIconButton() (+20 more)

### Community 27 - "Integration & Unit Tests"
Cohesion: 0.11
Nodes (10): Ambiguous, FloatArray, Match, NoTemplatesAvailable, RecognitionOutcome, StudentTemplatePair, Unknown, ConsolidatedFaceVerificationPipelineTest (+2 more)

### Community 28 - "Biometrics & Enrollment"
Cohesion: 0.10
Nodes (22): Any, argparse, asyncio, Path, main(), Builds a compact LFW benchmark subset for on-device Attract three-frame…, read_csv(), find_chrome() (+14 more)

### Community 29 - "Biometrics & Enrollment"
Cohesion: 0.14
Nodes (23): FrameBundle, CameraPreview(), computeBitmapBrightness(), computeBitmapLaplacianVariance(), cropFaceFromBitmap(), getLuminance(), Bitmap, Modifier (+15 more)

### Community 30 - "Quality & Adaptive Verification"
Cohesion: 0.09
Nodes (22): animatefloatasstate, MainActivity, button, clipboardmanager, clipdata, collectispressedasstate, enableedgetoedge, FragmentActivity (+14 more)

### Community 31 - "Quality & Adaptive Verification"
Cohesion: 0.08
Nodes (22): AuthOutcome, Cancel, CheckInPressed, EndRequested, EnrollmentCompleted, EnrollmentRequested, EnrollmentSlotCaptured, EnrollmentSlotRetake (+14 more)

### Community 32 - "Database & Persistence"
Cohesion: 0.08
Nodes (24): Acquiring, Ambiguous, AssistedActionSelection, AwaitingTeacherApproval, DuplicateChecking, Ended, Ending, EnrollmentCapture (+16 more)

### Community 33 - "Quality & Adaptive Verification"
Cohesion: 0.14
Nodes (3): AttractViewModel, FaceAttendance, Context

### Community 34 - "Attendance Camera UI"
Cohesion: 0.11
Nodes (19): GlowButtonState, ERROR, READY, SUBMIT, SUCCESS, WARNING, GlowCaptureButton(), Modifier (+11 more)

### Community 35 - "UI Theme & Components"
Cohesion: 0.15
Nodes (10): CameraProcessingAndMlIntegrationTest, Context, RealImageRecognitionTest, Context, SyntheticImageStressTest, EmbeddingEngine, Bitmap, Context (+2 more)

### Community 36 - "Attendance Session Engine"
Cohesion: 0.10
Nodes (11): ActiveSessionSummary, AttendanceStudent, ClassSummary, TeacherProfile, Flow, ClassCard(), DashboardScreen(), Modifier (+3 more)

### Community 38 - "Quality & Adaptive Verification"
Cohesion: 0.14
Nodes (6): ByteArray, FloatArray, TemplateMatcher, DefectReproductionTest, FloatArray, TemplateMatcherTest

### Community 39 - "Biometrics & Enrollment"
Cohesion: 0.12
Nodes (13): apiexception, DriveAuthManager, Result, DriveBackupPreferences, SharedPreferences, authorizationrequest, AuthorizationResult, drivescopes (+5 more)

### Community 40 - "Biometrics & Enrollment"
Cohesion: 0.16
Nodes (15): awaitTask(), Bitmap, Context, T, Uri, OcrRosterParser, Run, cancellationexception (+7 more)

### Community 41 - "Startup & Session Recovery"
Cohesion: 0.13
Nodes (5): AesGcmEmbeddingCipher, ByteArray, SecretKey, EmbeddingCipherTest, InvalidatedKeyTest

### Community 42 - "Biometrics & Enrollment"
Cohesion: 0.16
Nodes (19): EnrollmentBatchValidator.validate; each arg = (emb, yaw)., validate_batch(), build_class(), checkin_image(), dup_check(), enrollment_captures(), p4_frames(), p4_key() (+11 more)

### Community 43 - "Quality & Adaptive Verification"
Cohesion: 0.23
Nodes (4): ActivityScenario, com, RealUiFlowAcceptanceTest, UiDevice

### Community 44 - "Roster Import & OCR"
Cohesion: 0.17
Nodes (6): androidx, ByteArray, Callback, TemplateMigrationAndroidTest, Callback, Callback

### Community 45 - "Biometrics & Enrollment"
Cohesion: 0.10
Nodes (19): AttendanceSource, AI_RECOGNITION, BULK_IMPORT, CORRECTION, ENROLLMENT, MANUAL, MIGRATED, RESTORED (+11 more)

### Community 46 - "Integration & Unit Tests"
Cohesion: 0.17
Nodes (7): ByteArray, CharArray, PinHasher, CharArray, PinBackedAuthenticator, PinHasherTest, PinAuthTest

### Community 47 - "Quality & Adaptive Verification"
Cohesion: 0.14
Nodes (14): DriveBackupWorker, Result, DriveServiceHelper, Result, bytearraycontent, bytearrayoutputstream, CoroutineWorker, dispatchers (+6 more)

### Community 48 - "Quality & Adaptive Verification"
Cohesion: 0.20
Nodes (7): BackupExporter, BackupFaceTemplate, BackupSnapshot, T, Uri, BackupExporterTest, Result

### Community 49 - "Roster Import & OCR"
Cohesion: 0.19
Nodes (6): SessionTamperMonitor, TamperEvent, FakeSecurityEventLogger, Result, SessionTamperMonitorTest, TestLockTaskController

### Community 50 - "Integration & Unit Tests"
Cohesion: 0.13
Nodes (17): activityresultcontracts, addaphoto, Modifier, OcrImportSheet(), AttractShapes, Shapes, bitmap, fileprovider (+9 more)

### Community 51 - "Biometrics & Enrollment"
Cohesion: 0.21
Nodes (4): FullWorkflowAcceptanceTest, android, Context, QualityScoreWeights

### Community 52 - "UI Theme & Components"
Cohesion: 0.23
Nodes (4): android, FloatArray, RecognitionPathDiagnosticTest, FrameObservation

### Community 53 - "Database & Persistence"
Cohesion: 0.22
Nodes (7): Candidate, DetectedFace, Context, FloatArray, Interpreter, PointF, YoloFaceDetector

### Community 54 - "Drive Backup & Export"
Cohesion: 0.16
Nodes (7): FaceQualityConfig, FaceQualityEngine, ExpectedPose, LEFT, RIGHT, STRAIGHT, FaceQualitySignals

### Community 55 - "Integration & Unit Tests"
Cohesion: 0.21
Nodes (4): Parsed, RawRosterRow, RepairedRow, RollNumberRepair

### Community 56 - "Attendance Camera UI"
Cohesion: 0.16
Nodes (7): ReportingAndExportE2EAndroidTest, AttendanceExporter, Result, Uri, SessionExportRow, contentresolver, zoneid

### Community 58 - "Roster Import & OCR"
Cohesion: 0.22
Nodes (8): AlreadyPresent, Failed, FallbackMarkResult, com, Marked, NoActiveSession, StudentNotEligible, StudentNotFound

### Community 59 - "Biometrics & Enrollment"
Cohesion: 0.14
Nodes (16): androidx, AppThemeMode, Modifier, OnboardingScreen(), ThemeOptionBox(), attractoutlinedbutton, attractprimarybutton, biometricmanager (+8 more)

### Community 60 - "YOLO & TFLite Pipeline"
Cohesion: 0.16
Nodes (13): ai, GeminiRosterExtractor, Bitmap, Context, Uri, loadUprightScaled(), content, exifinterface (+5 more)

### Community 61 - "Roster Import & OCR"
Cohesion: 0.18
Nodes (4): AttendancePercentage, AttendanceRules, AttendanceRulesTest, AttendanceRulesIntegrationFunctionalTest

### Community 62 - "Biometrics & Enrollment"
Cohesion: 0.31
Nodes (6): ByteArray, FloatArray, TemplateEnvelopeCodec, FloatArray, TemplateEnvelopeCodecTest, XorCipher

### Community 63 - "Quality & Adaptive Verification"
Cohesion: 0.16
Nodes (13): hashlib, download_file(), main(), inspect_model(), Read-only Room proof report for the phone acceptance DB pull., rows(), sqlite3, sys (+5 more)

### Community 64 - "Roster Import & OCR"
Cohesion: 0.17
Nodes (10): activity, activitymanager, Locked, LockTaskState, Unlocked, Flow, Result, LockTaskControllerImpl (+2 more)

### Community 65 - "Attendance Camera UI"
Cohesion: 0.20
Nodes (10): BiometricEvaluationMetrics, Context, RealHumanRecognitionTest, FaceAligner, Bitmap, PointF, canvas, hypot (+2 more)

### Community 66 - "Biometrics & Enrollment"
Cohesion: 0.24
Nodes (10): AdaptiveVerificationEngine, Ambiguous, Diagnostics, Error, Final, Match, NeedMoreFrames, Outcome (+2 more)

### Community 67 - "Biometrics & Enrollment"
Cohesion: 0.16
Nodes (11): CalendarState, CalendarViewModel, StateFlow, ViewModel, combine, flatmaplatest, mutablestateflow, sharingstarted (+3 more)

### Community 68 - "Integration & Unit Tests"
Cohesion: 0.14
Nodes (13): AuthReason, END_SESSION, FIRST_IDENTITY_CONFIRM, PROFILE_MANAGEMENT, RE_ENROLLMENT, RECOVERY_END, RECOVERY_RESUME, TEACHER_ASSISTANCE (+5 more)

### Community 69 - "Drive Backup & Export"
Cohesion: 0.25
Nodes (3): OcrWord, RosterGeometry, RosterGeometryTest

### Community 70 - "Roster Import & OCR"
Cohesion: 0.15
Nodes (3): ClassReportStudentRow, AttendanceExporterTest, ExportAndBackupFunctionalTest

### Community 71 - "Biometrics & Enrollment"
Cohesion: 0.13
Nodes (10): AttendanceRecordEntity, ClassSectionEntity, StudentEntity, clock, map, Mutex, sqliteconstraintexception, validationexception (+2 more)

### Community 72 - "Database & Persistence"
Cohesion: 0.13
Nodes (10): AppScreen, CreateClass, Dashboard, Loading, ManualAttendance, Onboarding, Settings, StandaloneEnrollment (+2 more)

### Community 73 - "Biometrics & Enrollment"
Cohesion: 0.16
Nodes (11): Job, Flow, assharedflow, async, coroutinescope, experimentalcoroutinesapi, launch, mutablesharedflow (+3 more)

### Community 74 - "Quality & Adaptive Verification"
Cohesion: 0.21
Nodes (5): EmbeddingCipher, ByteArray, EncryptedEmbedding, EncryptedEmbeddingCipher, ByteArray

### Community 75 - "Startup & Session Recovery"
Cohesion: 0.14
Nodes (14): QualityReason, BLUR, CORRUPTED, DARK, EYES_UNCLEAR, MULTIPLE_FACES, NO_FACE, OFF_CENTER (+6 more)

### Community 76 - "Startup & Session Recovery"
Cohesion: 0.20
Nodes (5): NoEnrolledStudentsDialog(), AttractUiState, NoEnrolledStudentsDialogState, PendingRosterImport, AttractViewModelTest

### Community 78 - "Quality & Adaptive Verification"
Cohesion: 0.19
Nodes (9): BackupScheduler, Context, constraints, existingperiodicworkpolicy, existingworkpolicy, networktype, onetimeworkrequestbuilder, periodicworkrequestbuilder (+1 more)

### Community 79 - "Drive Backup & Export"
Cohesion: 0.15
Nodes (11): TemplateUnavailableException, base64, keygenparameterspec, keyproperties, keystore, messagedigest, pbekeyspec, RuntimeException (+3 more)

### Community 80 - "Quality & Adaptive Verification"
Cohesion: 0.15
Nodes (9): Accepted, PoseBucket, DOWN, FRONTAL, LEFT, RIGHT, UP, QualityResult (+1 more)

### Community 81 - "Keystoreembeddingcipher Buildaad"
Cohesion: 0.15
Nodes (13): SessionScreenState, ALREADY_PRESENT, AUTO_ENDED, CAPTURING, ERROR, FRAMES_COLLECTED, MATCH_SUCCESS, PROCESSING (+5 more)

### Community 82 - "Attendance Session Engine"
Cohesion: 0.17
Nodes (7): Flow, Result, LockTaskController, AlreadyLocked, Error, LockTaskResult, Started

### Community 83 - "Facealignerkt Facealigner"
Cohesion: 0.21
Nodes (12): AttractAppTheme(), AttractExtraColors, AttractTheme, extraColorsFor(), compositionlocalprovider, darkcolorscheme, issystemindarktheme, lightcolorscheme (+4 more)

### Community 85 - "Attendance Session Engine"
Cohesion: 0.36
Nodes (5): ImageRecord, Bitmap, Context, Rect, LfwThreeFrameBenchmarkTest

### Community 86 - "Runphoneacceptanceps Adb"
Cohesion: 0.24
Nodes (8): SharedPreferences, StateFlow, ThemeRepository, AppThemeMode, DARK, LIGHT, SYSTEM, asstateflow

### Community 87 - "Biometrics & Enrollment"
Cohesion: 0.36
Nodes (3): KeystoreEmbeddingCipher, ByteArray, SecretKey

### Community 88 - "Attendance Session Engine"
Cohesion: 0.23
Nodes (5): SecurityEvent, SecurityEventLogger, SecurityEventLoggerImpl, instant, log

### Community 89 - "Integration & Unit Tests"
Cohesion: 0.20
Nodes (8): Callback, Callback, Context, database, migration, RoomDatabase, SupportSQLiteDatabase, typeconverters

### Community 90 - "Integration & Unit Tests"
Cohesion: 0.25
Nodes (3): ClassDao, DashboardClassRow, ClassSectionEntity

### Community 91 - "Integration & Unit Tests"
Cohesion: 0.20
Nodes (8): Bitmap, bytebuffer, byteorder, filechannel, fileinputstream, max, min, rect

### Community 92 - "Integration & Unit Tests"
Cohesion: 0.25
Nodes (3): NormalizeRollNumber, NormalizeRollNumberTest, RosterImportFunctionalTest

### Community 93 - "Roster Import & OCR"
Cohesion: 0.18
Nodes (10): ActiveSession, AttendanceRoute, ClassWorkspace, Dashboard, Enrollment, History, ManualSession, Students (+2 more)

### Community 94 - "Drive Backup & Export"
Cohesion: 0.35
Nodes (8): Adb(), FillEdit(), Has-Text(), Tap-Desc(), Tap-Text(), Type-In-Field(), Uidump(), Wait-Text()

### Community 95 - "Galleryloadresultkt Emptyhealthy"
Cohesion: 0.33
Nodes (6): abs, file, filewriter, instrumentationregistry, random, sqrt

### Community 96 - "Livenessreason Eyesclosedorstatic"
Cohesion: 0.20
Nodes (6): Flow, Uri, Job, rosterparseresult, StateFlow, ViewModel

### Community 97 - "Integration & Unit Tests"
Cohesion: 0.20
Nodes (10): SecurityEventCategory, AUTH_ATTEMPT, AUTH_CANCELLED, AUTH_FAILED, AUTH_SUCCESS, CRYPTO_FAILURE, KEY_INVALIDATED, SESSION_END (+2 more)

### Community 99 - "Attractapplicationkt Appcontainer"
Cohesion: 0.22
Nodes (7): InteractionContext, SessionContext, TeacherAuthAction, FIRST_ENROLLMENT, RE_ENROLLMENT, TEACHER_ASSISTED_CHECKIN, uuid

### Community 100 - "Drive Backup & Export"
Cohesion: 0.33
Nodes (6): SecretKey, cipher, gcmparameterspec, keygenerator, secretkey, secretkeyspec

### Community 101 - "Lockdownmodelskt Blocked"
Cohesion: 0.36
Nodes (8): ensure_dirs(), main(), populate_synthetic_dataset(), prepare_face_test_data.py Test Data Preparation Script for Attract Face…, sync_assets_to_android(), update_gitignore(), validate_real_human_dataset(), shutil

### Community 102 - "Biometrics & Enrollment"
Cohesion: 0.39
Nodes (7): ai_edge_litert_interpreter, glob, align_face_5point(), detect_faces(), extract_embedding(), main(), nms()

### Community 103 - "Glowbuttonstate Error"
Cohesion: 0.54
Nodes (3): AdaptiveStrategyBenchmarkTest, BenchImage, android

### Community 104 - "Biometrics & Enrollment"
Cohesion: 0.46
Nodes (5): attendancerecordentity, attendancesessionentity, classsectionentity, measuretimemillis, studententity

### Community 108 - "Biometrics & Enrollment"
Cohesion: 0.43
Nodes (3): ImageDegradationValidationTest, Bitmap, Context

### Community 109 - "Teacherprofile Authenticate"
Cohesion: 0.33
Nodes (5): EnrollmentStatus, ENROLLED, NOT_ENROLLED, REENROLL_REQUIRED, AttendanceMethod

### Community 110 - "Bytearray Decrypt"
Cohesion: 0.29
Nodes (6): DriveAccountInfo, DriveSyncStatus, Error, Idle, Success, Syncing

### Community 111 - "Integration & Unit Tests"
Cohesion: 0.29
Nodes (6): EmptyHealthy, GalleryHealthSummary, GalleryLoadResult, NeedsRepair, Ready, Unavailable

### Community 112 - "Gradlew Gradlewscript"
Cohesion: 0.33
Nodes (6): SessionHistory, androidx, Modifier, Quadruple, SessionHistoryScreen(), StatTile()

### Community 114 - "Historysortorder Newestfirst"
Cohesion: 0.53
Nodes (4): AppContainer, AttractApplication, com, Application

### Community 115 - "Success"
Cohesion: 0.33
Nodes (6): RollStatus, FIXED, INFERRED, MISSING, OK, SUSPECT

### Community 116 - "Enrollstudentface Bytearray"
Cohesion: 0.33
Nodes (5): build, countdownlatch, executor, requiresapi, timeunit

### Community 117 - "Appbuildgradlekts"
Cohesion: 0.33
Nodes (3): AuthenticationCallback, AuthenticationCallback, BiometricPrompt

### Community 118 - "Buildgradlekts"
Cohesion: 0.33
Nodes (3): AuthenticationCallback, BiometricPrompt, AuthenticationCallback

### Community 120 - "Settingsgradlekts"
Cohesion: 0.33
Nodes (3): io, pyarrow_parquet, Stream the HuggingFace parquet mirrors to plain JPEG folders (low memory).…

### Community 121 - "Bounds"
Cohesion: 0.67
Nodes (5): Bounds(), Dump(), TapB(), TapDescR(), TapTextR()

### Community 122 - "Bounds"
Cohesion: 0.67
Nodes (5): Bounds(), Dump(), TapB(), TapDescR(), TapTextR()

### Community 124 - "Kiosk Lockdown & Safety"
Cohesion: 0.40
Nodes (3): Blocked, PrerequisiteResult, Ready

### Community 125 - "Engine"
Cohesion: 0.40
Nodes (5): Engine, CLOUD, MIXED, NONE, ON_DEVICE

### Community 126 - "Frame State"
Cohesion: 0.40
Nodes (5): FrameState, ERROR, NEUTRAL, SUCCESS, WARNING

### Community 128 - "Attract View Model Test"
Cohesion: 0.40
Nodes (4): assertnull, resetmain, setmain, standardtestdispatcher

### Community 130 - "Gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 132 - "Session History & Records"
Cohesion: 0.67
Nodes (3): HistorySortOrder, NEWEST_FIRST, OLDEST_FIRST

## Knowledge Gaps
- **242 isolated node(s):** `FrameAnalysis`, `NoTemplatesAvailable`, `Unknown`, `AlreadyEnrolled`, `ApprovalExpired` (+237 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 678 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **26 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `assertTrue()` connect `Quality & Adaptive Verification` to `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Quality & Adaptive Verification`, `Quality & Adaptive Verification`, `Database & Persistence`, `Biometrics & Enrollment`, `Drive Backup & Export`, `Attendance Camera UI`, `Quality & Adaptive Verification`, `Integration & Unit Tests`, `UI Theme & Components`, `Quality & Adaptive Verification`, `Quality & Adaptive Verification`, `Startup & Session Recovery`, `Quality & Adaptive Verification`, `Roster Import & OCR`, `Integration & Unit Tests`, `Biometrics & Enrollment`, `UI Theme & Components`, `Database & Persistence`, `Drive Backup & Export`, `Attendance Camera UI`, `Roster Import & OCR`, `Biometrics & Enrollment`, `Attendance Camera UI`, `Roster Import & OCR`, `Startup & Session Recovery`, `Attendance Session Engine`, `Integration & Unit Tests`, `Quality & Adaptive Verification`, `Glowbuttonstate Error`, `Roster Import & OCR`, `Biometrics & Enrollment`, `Full Biometric Pipeline Integration Test`?**
  _High betweenness centrality (0.124) - this node is a cross-community bridge._
- **Why does `AttractRepository` connect `Biometrics & Enrollment` to `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Attendance Camera UI`, `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `Database & Persistence`, `Quality & Adaptive Verification`, `Attendance Session Engine`, `Quality & Adaptive Verification`, `Roster Import & OCR`, `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `UI Theme & Components`, `Attendance Camera UI`, `Roster Import & OCR`, `Biometrics & Enrollment`, `Roster Import & OCR`, `Biometrics & Enrollment`, `Livenessreason Eyesclosedorstatic`, `Historysortorder Newestfirst`?**
  _High betweenness centrality (0.082) - this node is a cross-community bridge._
- **Why does `AttendanceStatus` connect `Biometrics & Enrollment` to `Quality & Adaptive Verification`, `Livenessreason Eyesclosedorstatic`, `Biometrics & Enrollment`, `Roster Import & OCR`, `Attendance Session Engine`, `Biometrics & Enrollment`, `Biometrics & Enrollment`, `Attendance Camera UI`, `Biometrics & Enrollment`, `Teacherprofile Authenticate`, `Quality & Adaptive Verification`, `Securitymodelskt Securityevent`, `Database & Persistence`, `Biometrics & Enrollment`, `Drive Backup & Export`, `Roster Import & OCR`?**
  _High betweenness centrality (0.047) - this node is a cross-community bridge._
- **Are the 195 inferred relationships involving `assertTrue()` (e.g. with `.enrollReal()` and `.expectSuccess()`) actually correct?**
  _`assertTrue()` has 195 INFERRED edges - model-reasoned connections that need verification._
- **What connects `FrameAnalysis`, `NoTemplatesAvailable`, `Unknown` to the rest of the system?**
  _242 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Quality & Adaptive Verification` be split into smaller, more focused modules?**
  _Cohesion score 0.09215291750503019 - nodes in this community are weakly interconnected._
- **Should `Biometrics & Enrollment` be split into smaller, more focused modules?**
  _Cohesion score 0.053410893707033315 - nodes in this community are weakly interconnected._