# Graph Report - Attract - Face Based Attendance Tracker  (2026-10-07)

## Corpus Check
- 272 files · ~200,377 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 18 file(s) not represented in the graph (top: .xml 4, .bat 3, .csv 3)

## Summary
- 2510 nodes · 7066 edges · 121 communities (101 shown, 20 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 404 edges (avg confidence: 0.85)
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

## God Nodes (most connected - your core abstractions)
1. `assertTrue()` - 208 edges
2. `AttractRepository` - 84 edges
3. `AttendanceSessionEntity` - 62 edges
4. `FakeSessionDao` - 61 edges
5. `FakeAttendanceRecordDao` - 58 edges
6. `StudentEntity` - 57 edges
7. `FakeStudentDao` - 56 edges
8. `AttractViewModel` - 54 edges
9. `StudentTemplatePair` - 43 edges
10. `AttendanceStatus` - 40 edges

## Surprising Connections (you probably didn't know these)
- `rows()` --indirect_call--> `e()`  [INFERRED]
  scripts/report_phone_db.py → tools/biometric_eval/build_feature_cache.py
- `FullBiometricPipelineIntegrationTest` --calls--> `RecognitionDecisionEngine`  [INFERRED]
  app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt → app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt
- `LfwThreeFrameBenchmarkTest` --calls--> `RecognitionDecisionEngine`  [INFERRED]
  app/src/androidTest/java/com/attract/attendance/domain/face/LfwThreeFrameBenchmarkTest.kt → app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt
- `RealHumanRecognitionTest` --calls--> `RecognitionDecisionEngine`  [INFERRED]
  app/src/androidTest/java/com/attract/attendance/domain/face/RealHumanRecognitionTest.kt → app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt
- `RealImageRecognitionTest` --calls--> `RecognitionDecisionEngine`  [INFERRED]
  app/src/androidTest/java/com/attract/attendance/domain/face/RealImageRecognitionTest.kt → app/src/main/java/com/attract/attendance/domain/face/RecognitionDecisionEngine.kt

## Import Cycles
- None detected.

## Communities (121 total, 20 thin omitted)

### Community 0 - "Quality & Adaptive Verification"
Cohesion: 0.06
Nodes (41): AdaptiveStrategyBenchmarkTest, BenchImage, android, Ambiguous, EvidenceFusion, EvidenceFusionStrategy, EvidenceFusionStrategy, EvidenceFusionStrategy (+33 more)

### Community 1 - "Biometrics & Enrollment"
Cohesion: 0.05
Nodes (43): BiometricModelProfile, AlreadyEnrolled, ApprovalExpired, Busy, Cancelled, CaptureRejected, Clear, Committed (+35 more)

### Community 2 - "Biometrics & Enrollment"
Cohesion: 0.07
Nodes (54): animatecolorasstate, animatefloat, animatefloatasstate, animateintasstate, AttendanceScreen(), Modifier, AttendanceStrings, FaceFrameOverlay() (+46 more)

### Community 3 - "Roster Import & OCR"
Cohesion: 0.05
Nodes (32): ActiveSessionExists, AlreadyFinalized, AlreadyPresent, AnotherSessionActive, AppError, DuplicateRollNumber, NotEligible, NotFound (+24 more)

### Community 4 - "Biometrics & Enrollment"
Cohesion: 0.05
Nodes (52): add, animatedcontent, RollNumberComparator, GuidanceCard(), Modifier, Modifier, SearchField(), ClassWorkspaceScreen() (+44 more)

### Community 5 - "Quality & Adaptive Verification"
Cohesion: 0.06
Nodes (34): FaceSessionE2EAndroidTest, AttendanceStatus, ABSENT, PRESENT, AttendanceStudent, EnrollmentStatus, ENROLLED, NOT_ENROLLED (+26 more)

### Community 6 - "Quality & Adaptive Verification"
Cohesion: 0.10
Nodes (17): RosterParseResult, Success, RosterTablePlanner, NormalizeRollNumber, RosterImportFunctionalTest, assertarrayequals, assertequals, assertfalse (+9 more)

### Community 7 - "Attendance Camera UI"
Cohesion: 0.15
Nodes (42): alertdialog, EditableOcrStudent, Modifier, OcrConfirmationDialog(), arrangement, arrowback, attractblue, bottomsheetdefaults (+34 more)

### Community 8 - "Biometrics & Enrollment"
Cohesion: 0.07
Nodes (13): ActiveSessionRow, Flow, SessionDao, SessionStudentRow, StudentDao, AttendanceSessionEntity, StudentEntity, dao (+5 more)

### Community 9 - "Attendance Camera UI"
Cohesion: 0.06
Nodes (45): AttractApp(), androidx, com, LoadingScreen(), NoEnrolledStudentsDialog(), RosterImportPreviewDialog(), SessionHistory, AddStudentBottomSheet() (+37 more)

### Community 10 - "Quality & Adaptive Verification"
Cohesion: 0.14
Nodes (26): abs, after, androidjunit4, SeedWebcamTestData, CreateClassCommand, CreateStudentCommand, LivenessEngine, applicationprovider (+18 more)

### Community 11 - "Biometrics & Enrollment"
Cohesion: 0.08
Nodes (9): DatabaseConstraintsTest, RoomDatabaseIntegrationTest, AttractDatabase, ClassDao, DashboardClassRow, TeacherDao, ClassSectionEntity, TeacherEntity (+1 more)

### Community 12 - "Quality & Adaptive Verification"
Cohesion: 0.08
Nodes (18): ReportingAndExportE2EAndroidTest, AttendancePercentage, AttendanceExporter, Result, Uri, BackupSnapshot, ClassReportStudentRow, SessionExportRow (+10 more)

### Community 13 - "Database & Persistence"
Cohesion: 0.07
Nodes (11): BlockingSetup, Onboarding, RecoverSession, StartupCoordinator, StartupDestination, TeacherDashboard, FakeSessionDao, FakeTeacherDao (+3 more)

### Community 14 - "Quality & Adaptive Verification"
Cohesion: 0.09
Nodes (22): collections, csv, cv2, itertools, json, math, numpy, os (+14 more)

### Community 15 - "Startup & Session Recovery"
Cohesion: 0.09
Nodes (33): alignment, AttractOutlinedButton(), AttractCard(), Color, Modifier, EmptyState(), ImageVector, Modifier (+25 more)

### Community 16 - "Quality & Adaptive Verification"
Cohesion: 0.10
Nodes (37): dataclasses, Image, ndarray, tflite_runtime_interpreter, align(), brightness_and_blur(), decide(), detect() (+29 more)

### Community 17 - "Biometrics & Enrollment"
Cohesion: 0.07
Nodes (33): CrashRecoveryScreen(), MainActivity, androidx, Modifier, OnboardingScreen(), ThemeOptionBox(), biometricmanager, Bundle (+25 more)

### Community 18 - "Biometrics & Enrollment"
Cohesion: 0.14
Nodes (5): CommandResult, Failure, AttractRepository, ByteArray, StaleTemplateReport

### Community 19 - "Biometrics & Enrollment"
Cohesion: 0.10
Nodes (6): RosterImportE2EAndroidTest, FallbackAttendanceAndroidTest, RosterTablePlannerTest, assertTrue(), CoordinatorAndAuthTest, RosterImportEdgeCaseFunctionalTest

### Community 20 - "Database & Persistence"
Cohesion: 0.13
Nodes (6): RecordPresentCommand, SessionCoordinator, FakeAttendanceRecordDao, SessionCoordinatorTest, LiveSessionFunctionalTest, SessionStateMachineComprehensiveFunctionalTest

### Community 21 - "Drive Backup & Export"
Cohesion: 0.08
Nodes (23): activity, apiexception, DriveAuthManager, Result, AttractAppTheme(), AttractExtraColors, AttractTheme, extraColorsFor() (+15 more)

### Community 22 - "Attendance Camera UI"
Cohesion: 0.09
Nodes (29): activityresultcontracts, addaphoto, AppearanceBottomSheet(), AppearancePillOption(), ImageVector, Modifier, ImportOptionCard(), ImportRosterChoiceBottomSheet() (+21 more)

### Community 23 - "Quality & Adaptive Verification"
Cohesion: 0.17
Nodes (3): AdaptiveVerificationEngineTest, FloatArray, EvidenceFusionStrategy

### Community 25 - "Quality & Adaptive Verification"
Cohesion: 0.11
Nodes (12): FullBiometricPipelineIntegrationTest, Context, ImageDegradationValidationTest, Bitmap, Context, FaceQualityConfig, FaceQualityEngine, ExpectedPose (+4 more)

### Community 26 - "Attendance Session Engine"
Cohesion: 0.11
Nodes (15): Job, SessionTamperMonitor, TamperEvent, Flow, Result, SessionTamperMonitorTest, TestLockTaskController, assharedflow (+7 more)

### Community 27 - "Integration & Unit Tests"
Cohesion: 0.10
Nodes (17): activitymanager, Flow, Result, LockTaskController, AlreadyLocked, Error, Locked, LockTaskResult (+9 more)

### Community 28 - "Biometrics & Enrollment"
Cohesion: 0.10
Nodes (20): TeacherAuthenticator, AuthReason, END_SESSION, FIRST_IDENTITY_CONFIRM, PROFILE_MANAGEMENT, RE_ENROLLMENT, RECOVERY_END, RECOVERY_RESUME (+12 more)

### Community 29 - "Biometrics & Enrollment"
Cohesion: 0.09
Nodes (11): AttendanceSource, AI_RECOGNITION, BULK_IMPORT, CORRECTION, ENROLLMENT, MANUAL, MIGRATED, RESTORED (+3 more)

### Community 30 - "Quality & Adaptive Verification"
Cohesion: 0.15
Nodes (8): LivenessResult, Passed, Rejected, PresentationAttackDetector, Thresholds, PresentationAttackSignals, LivenessEngineTest, PresentationAttackDetectorTest

### Community 31 - "Quality & Adaptive Verification"
Cohesion: 0.10
Nodes (22): Any, argparse, asyncio, Path, main(), Builds a compact LFW benchmark subset for on-device Attract three-frame…, read_csv(), find_chrome() (+14 more)

### Community 32 - "Database & Persistence"
Cohesion: 0.10
Nodes (3): SessionRow, FakeSessionDao, Flow

### Community 33 - "Quality & Adaptive Verification"
Cohesion: 0.14
Nodes (23): FrameBundle, CameraPreview(), computeBitmapBrightness(), computeBitmapLaplacianVariance(), cropFaceFromBitmap(), getLuminance(), Bitmap, Modifier (+15 more)

### Community 34 - "Attendance Camera UI"
Cohesion: 0.13
Nodes (10): CameraProcessingAndMlIntegrationTest, Context, RealImageRecognitionTest, Context, SyntheticImageStressTest, EmbeddingEngine, Bitmap, Context (+2 more)

### Community 35 - "UI Theme & Components"
Cohesion: 0.12
Nodes (23): ClassSummary, AttractIconButton(), feedbackClickable(), Modifier, rememberFeedbackClick(), ClassCard(), DashboardScreen(), Modifier (+15 more)

### Community 36 - "Attendance Session Engine"
Cohesion: 0.14
Nodes (22): SessionSummary, CalendarDayCell(), CalendarScreen(), DayOfWeekHeader(), Modifier, MonthGrid(), MonthHeader(), SessionCard() (+14 more)

### Community 37 - "Quality & Adaptive Verification"
Cohesion: 0.08
Nodes (22): AuthOutcome, Cancel, CheckInPressed, EndRequested, EnrollmentCompleted, EnrollmentRequested, EnrollmentSlotCaptured, EnrollmentSlotRetake (+14 more)

### Community 38 - "Quality & Adaptive Verification"
Cohesion: 0.08
Nodes (24): Acquiring, Ambiguous, AssistedActionSelection, AwaitingTeacherApproval, DuplicateChecking, Ended, Ending, EnrollmentCapture (+16 more)

### Community 39 - "Biometrics & Enrollment"
Cohesion: 0.17
Nodes (4): DriveBackupPreferences, AttractViewModel, FaceAttendance, Context

### Community 40 - "Biometrics & Enrollment"
Cohesion: 0.14
Nodes (6): ByteArray, FloatArray, TemplateMatcher, DefectReproductionTest, FloatArray, TemplateMatcherTest

### Community 41 - "Startup & Session Recovery"
Cohesion: 0.15
Nodes (7): FinalizeFaceSessionCommand, SessionRecoveryManager, FakeTeacherAuthenticator, FinalizeFaceSessionCommandTest, SessionRecoveryManagerTest, SecurityPipelineFunctionalTest, SessionRecoveryFunctionalTest

### Community 42 - "Biometrics & Enrollment"
Cohesion: 0.13
Nodes (5): AesGcmEmbeddingCipher, ByteArray, SecretKey, EmbeddingCipherTest, InvalidatedKeyTest

### Community 43 - "Quality & Adaptive Verification"
Cohesion: 0.16
Nodes (19): EnrollmentBatchValidator.validate; each arg = (emb, yaw)., validate_batch(), build_class(), checkin_image(), dup_check(), enrollment_captures(), p4_frames(), p4_key() (+11 more)

### Community 44 - "Roster Import & OCR"
Cohesion: 0.23
Nodes (4): ActivityScenario, com, RealUiFlowAcceptanceTest, UiDevice

### Community 45 - "Biometrics & Enrollment"
Cohesion: 0.17
Nodes (6): androidx, ByteArray, Callback, TemplateMigrationAndroidTest, Callback, Callback

### Community 46 - "Integration & Unit Tests"
Cohesion: 0.17
Nodes (7): ByteArray, CharArray, PinHasher, CharArray, PinBackedAuthenticator, PinHasherTest, PinAuthTest

### Community 47 - "Quality & Adaptive Verification"
Cohesion: 0.10
Nodes (20): PoseBucket, DOWN, FRONTAL, LEFT, RIGHT, UP, QualityReason, BLUR (+12 more)

### Community 48 - "Quality & Adaptive Verification"
Cohesion: 0.20
Nodes (4): FullWorkflowAcceptanceTest, android, Context, QualityScoreWeights

### Community 49 - "Roster Import & OCR"
Cohesion: 0.14
Nodes (15): SharedPreferences, DriveBackupWorker, bytearraycontent, bytearrayoutputstream, contentresolver, context, CoroutineWorker, dispatchers (+7 more)

### Community 50 - "Integration & Unit Tests"
Cohesion: 0.13
Nodes (14): Flow, Job, StateFlow, ViewModel, CalendarState, CalendarViewModel, StateFlow, ViewModel (+6 more)

### Community 51 - "Biometrics & Enrollment"
Cohesion: 0.20
Nodes (8): AlreadyPresent, Failed, FallbackMarkResult, com, Marked, NoActiveSession, StudentNotEligible, StudentNotFound

### Community 52 - "UI Theme & Components"
Cohesion: 0.16
Nodes (14): Modifier, ThemeCard(), ThemeSelectionScreen(), AttractMotion, androidx, Modifier, T, tapScale() (+6 more)

### Community 53 - "Database & Persistence"
Cohesion: 0.13
Nodes (13): hashlib, io, pyarrow_parquet, download_file(), main(), inspect_model(), Read-only Room proof report for the phone acceptance DB pull., rows() (+5 more)

### Community 54 - "Drive Backup & Export"
Cohesion: 0.14
Nodes (16): accountcircle, GoogleDriveSyncCard(), Modifier, AttractShapes, Shapes, checkcircle, circularprogressindicator, cloud (+8 more)

### Community 55 - "Integration & Unit Tests"
Cohesion: 0.25
Nodes (4): android, FloatArray, RecognitionPathDiagnosticTest, FrameObservation

### Community 56 - "Attendance Camera UI"
Cohesion: 0.22
Nodes (4): SessionRecoveryE2EAndroidTest, PrerequisitesChecker, FakeDeviceSecurityChecker, PrerequisitesCheckerTest

### Community 57 - "UI Theme & Components"
Cohesion: 0.15
Nodes (11): T, Factory, SharedPreferences, StateFlow, ThemeRepository, AppThemeMode, DARK, LIGHT (+3 more)

### Community 58 - "Roster Import & OCR"
Cohesion: 0.19
Nodes (9): IllegalArgumentException, RosterImportException, ByteArray, XlsxRosterReader, bytearrayinputstream, DocumentBuilderFactory, Element, xmlconstants (+1 more)

### Community 59 - "Biometrics & Enrollment"
Cohesion: 0.31
Nodes (6): ByteArray, FloatArray, TemplateEnvelopeCodec, FloatArray, TemplateEnvelopeCodecTest, XorCipher

### Community 60 - "YOLO & TFLite Pipeline"
Cohesion: 0.26
Nodes (7): Candidate, DetectedFace, Context, FloatArray, Interpreter, PointF, YoloFaceDetector

### Community 63 - "Quality & Adaptive Verification"
Cohesion: 0.24
Nodes (10): AdaptiveVerificationEngine, Ambiguous, Diagnostics, Error, Final, Match, NeedMoreFrames, Outcome (+2 more)

### Community 64 - "Roster Import & OCR"
Cohesion: 0.17
Nodes (5): AttractUiState, Uri, NoEnrolledStudentsDialogState, PendingRosterImport, AttractViewModelTest

### Community 65 - "Attendance Camera UI"
Cohesion: 0.15
Nodes (12): DeviceSecurityChecker, SetupCheck, CAMERA_PERMISSION, MODEL_RESOURCES_VERIFIED, NO_ACTIVE_FACE_SESSION, SCREEN_PINNING_PROBED, SECURE_DEVICE_LOCK, TEACHER_PIN_CONFIGURED (+4 more)

### Community 67 - "Biometrics & Enrollment"
Cohesion: 0.13
Nodes (10): AppScreen, CreateClass, Dashboard, Loading, ManualAttendance, Onboarding, Settings, StandaloneEnrollment (+2 more)

### Community 68 - "Integration & Unit Tests"
Cohesion: 0.19
Nodes (4): SecurityAndLockTaskAndroidTest, FakeLockTaskController, Result, LockTaskControllerTest

### Community 69 - "Drive Backup & Export"
Cohesion: 0.18
Nodes (10): BackupScheduler, Context, constraints, existingperiodicworkpolicy, existingworkpolicy, networktype, onetimeworkrequestbuilder, periodicworkrequestbuilder (+2 more)

### Community 71 - "Biometrics & Enrollment"
Cohesion: 0.21
Nodes (5): EmbeddingCipher, ByteArray, EncryptedEmbedding, EncryptedEmbeddingCipher, ByteArray

### Community 72 - "Database & Persistence"
Cohesion: 0.17
Nodes (9): Result, Callback, Callback, Context, database, migration, RoomDatabase, SupportSQLiteDatabase (+1 more)

### Community 73 - "Biometrics & Enrollment"
Cohesion: 0.15
Nodes (11): TemplateUnavailableException, base64, keygenparameterspec, keyproperties, keystore, messagedigest, pbekeyspec, RuntimeException (+3 more)

### Community 74 - "Quality & Adaptive Verification"
Cohesion: 0.23
Nodes (9): Bitmap, bytebuffer, byteorder, filechannel, fileinputstream, max, min, rect (+1 more)

### Community 75 - "Startup & Session Recovery"
Cohesion: 0.27
Nodes (9): FinalizeSessionResult, SessionNotFoundOrEnded, Success, AuthFailed, Blocked, NoActiveSession, RecoveryEndResult, RecoveryResumeResult (+1 more)

### Community 76 - "Startup & Session Recovery"
Cohesion: 0.17
Nodes (11): AlreadyPresent, RecordPresentResult, SessionNotActive, StudentNotEligible, StudentNotFound, Success, StateFlow, atomiclong (+3 more)

### Community 77 - "Securitymodelskt Securityevent"
Cohesion: 0.23
Nodes (5): SecurityEvent, SecurityEventLogger, SecurityEventLoggerImpl, FakeSecurityEventLogger, log

### Community 78 - "Quality & Adaptive Verification"
Cohesion: 0.36
Nodes (5): ImageRecord, Bitmap, Context, Rect, LfwThreeFrameBenchmarkTest

### Community 79 - "Drive Backup & Export"
Cohesion: 0.30
Nodes (4): BackupExporter, Result, T, Uri

### Community 81 - "Keystoreembeddingcipher Buildaad"
Cohesion: 0.36
Nodes (3): KeystoreEmbeddingCipher, ByteArray, SecretKey

### Community 83 - "Facealignerkt Facealigner"
Cohesion: 0.33
Nodes (7): FaceAligner, Bitmap, PointF, canvas, hypot, Matrix, paint

### Community 84 - "Biometrics & Enrollment"
Cohesion: 0.18
Nodes (10): ActiveSession, AttendanceRoute, ClassWorkspace, Dashboard, Enrollment, History, ManualSession, Students (+2 more)

### Community 85 - "Attendance Session Engine"
Cohesion: 0.18
Nodes (11): SessionScreenState, ALREADY_PRESENT, AUTO_ENDED, CAPTURING, ERROR, FRAMES_COLLECTED, MATCH_SUCCESS, PROCESSING (+3 more)

### Community 86 - "Runphoneacceptanceps Adb"
Cohesion: 0.35
Nodes (8): Adb(), FillEdit(), Has-Text(), Tap-Desc(), Tap-Text(), Type-In-Field(), Uidump(), Wait-Text()

### Community 87 - "Biometrics & Enrollment"
Cohesion: 0.22
Nodes (4): AuthenticationCallback, AuthenticationCallback, AuthenticationCallback, BiometricPrompt

### Community 88 - "Attendance Session Engine"
Cohesion: 0.20
Nodes (10): SecurityEventCategory, AUTH_ATTEMPT, AUTH_CANCELLED, AUTH_FAILED, AUTH_SUCCESS, CRYPTO_FAILURE, KEY_INVALIDATED, SESSION_END (+2 more)

### Community 90 - "Integration & Unit Tests"
Cohesion: 0.33
Nodes (6): SecretKey, cipher, gcmparameterspec, keygenerator, secretkey, secretkeyspec

### Community 91 - "Integration & Unit Tests"
Cohesion: 0.36
Nodes (8): ensure_dirs(), main(), populate_synthetic_dataset(), prepare_face_test_data.py Test Data Preparation Script for Attract Face…, sync_assets_to_android(), update_gitignore(), validate_real_human_dataset(), shutil

### Community 92 - "Integration & Unit Tests"
Cohesion: 0.39
Nodes (7): ai_edge_litert_interpreter, glob, align_face_5point(), detect_faces(), extract_embedding(), main(), nms()

### Community 93 - "Roster Import & OCR"
Cohesion: 0.36
Nodes (5): CsvRosterImporter, CsvRow, Uri, Rejected, java

### Community 94 - "Drive Backup & Export"
Cohesion: 0.29
Nodes (6): DriveAccountInfo, DriveSyncStatus, Error, Idle, Success, Syncing

### Community 95 - "Galleryloadresultkt Emptyhealthy"
Cohesion: 0.29
Nodes (6): EmptyHealthy, GalleryHealthSummary, GalleryLoadResult, NeedsRepair, Ready, Unavailable

### Community 96 - "Livenessreason Eyesclosedorstatic"
Cohesion: 0.29
Nodes (7): LivenessReason, EYES_CLOSED_OR_STATIC, INSUFFICIENT_VARIANCE, PASSED, PASSIVE_SPOOF_SUSPECTED, PRINT_PHOTO_SUSPECTED, SCREEN_REPLAY_SUSPECTED

### Community 98 - "Quality & Adaptive Verification"
Cohesion: 0.48
Nodes (3): Bitmap, FloatArray, PresentationAttackAnalyzer

### Community 99 - "Attractapplicationkt Appcontainer"
Cohesion: 0.53
Nodes (4): AppContainer, AttractApplication, com, Application

### Community 100 - "Drive Backup & Export"
Cohesion: 0.53
Nodes (3): DriveServiceHelper, Result, Drive

### Community 101 - "Lockdownmodelskt Blocked"
Cohesion: 0.33
Nodes (4): Blocked, PrerequisiteResult, Ready, instant

### Community 102 - "Biometrics & Enrollment"
Cohesion: 0.33
Nodes (3): AuthenticationCallback, AuthenticationCallback, BiometricPrompt

### Community 103 - "Glowbuttonstate Error"
Cohesion: 0.33
Nodes (6): GlowButtonState, ERROR, READY, SUBMIT, SUCCESS, WARNING

### Community 104 - "Biometrics & Enrollment"
Cohesion: 0.33
Nodes (3): AuthenticationCallback, BiometricPrompt, AuthenticationCallback

### Community 106 - "Phoneaddstudentsps Bounds"
Cohesion: 0.67
Nodes (5): Bounds(), Dump(), TapB(), TapDescR(), TapTextR()

### Community 107 - "Roster Import & OCR"
Cohesion: 0.67
Nodes (5): Bounds(), Dump(), TapB(), TapDescR(), TapTextR()

### Community 108 - "Biometrics & Enrollment"
Cohesion: 0.50
Nodes (3): BiometricEvaluationMetrics, Context, RealHumanRecognitionTest

### Community 112 - "Gradlew Gradlewscript"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 114 - "Historysortorder Newestfirst"
Cohesion: 0.67
Nodes (3): HistorySortOrder, NEWEST_FIRST, OLDEST_FIRST

## Knowledge Gaps
- **231 isolated node(s):** `NOT_ENROLLED`, `ENROLLED`, `REENROLL_REQUIRED`, `PRESENT`, `ABSENT` (+226 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 648 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **20 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `assertTrue()` connect `Biometrics & Enrollment` to `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `Roster Import & OCR`, `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Database & Persistence`, `Database & Persistence`, `Quality & Adaptive Verification`, `Quality & Adaptive Verification`, `Quality & Adaptive Verification`, `Quality & Adaptive Verification`, `Attendance Camera UI`, `Biometrics & Enrollment`, `Startup & Session Recovery`, `Biometrics & Enrollment`, `Roster Import & OCR`, `Biometrics & Enrollment`, `Integration & Unit Tests`, `Quality & Adaptive Verification`, `Integration & Unit Tests`, `Attendance Camera UI`, `Biometrics & Enrollment`, `Roster Import & OCR`, `Biometrics & Enrollment`, `Integration & Unit Tests`, `Roster Import & OCR`, `Quality & Adaptive Verification`, `Quality & Adaptive Verification`, `Integration & Unit Tests`, `Biometrics & Enrollment`?**
  _High betweenness centrality (0.139) - this node is a cross-community bridge._
- **Why does `AttractRepository` connect `Biometrics & Enrollment` to `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `Biometrics & Enrollment`, `Roster Import & OCR`, `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Roster Import & OCR`, `Integration & Unit Tests`, `Biometrics & Enrollment`, `Integration & Unit Tests`, `Roster Import & OCR`, `Database & Persistence`, `Attendance Session Engine`, `Attractapplicationkt Appcontainer`, `Teacherprofile Authenticate`?**
  _High betweenness centrality (0.072) - this node is a cross-community bridge._
- **Why does `AttendanceStatus` connect `Quality & Adaptive Verification` to `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Attendance Camera UI`, `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `Quality & Adaptive Verification`, `Biometrics & Enrollment`, `Integration & Unit Tests`, `Roster Import & OCR`, `Database & Persistence`, `Biometrics & Enrollment`?**
  _High betweenness centrality (0.059) - this node is a cross-community bridge._
- **Are the 204 inferred relationships involving `assertTrue()` (e.g. with `.enrollReal()` and `.expectSuccess()`) actually correct?**
  _`assertTrue()` has 204 INFERRED edges - model-reasoned connections that need verification._
- **What connects `NOT_ENROLLED`, `ENROLLED`, `REENROLL_REQUIRED` to the rest of the system?**
  _231 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Quality & Adaptive Verification` be split into smaller, more focused modules?**
  _Cohesion score 0.055364905056051246 - nodes in this community are weakly interconnected._
- **Should `Biometrics & Enrollment` be split into smaller, more focused modules?**
  _Cohesion score 0.05009920634920635 - nodes in this community are weakly interconnected._