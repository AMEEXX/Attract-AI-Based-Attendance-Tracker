# 19 — Production Readiness: Bottlenecks and Implementation Plan

**Baseline:** `origin/main` @ `e6cb6be` (2026-10-09). **Method:** `graphify-out/GRAPH_REPORT.md` (2,637 nodes, 7,133 edges, 145 communities) to find hubs, then targeted reads of the hub files only.
**Audience:** the implementing engineer/agent. Work packages (PR-xx) are ordered by release risk. Each has exact files, the change, and a done-check.

---

## 0. Verdict

The app is **not release-ready**. The biometric core is sound (doc 18), but release engineering, data safety, lifecycle architecture and the camera hot path are all at prototype level. Top 5 blockers:

| # | Blocker | Evidence | Impact |
|---|---|---|---|
| B1 | **Silent data wipe** | `AttractDatabase.kt:136` `fallbackToDestructiveMigration(true)`; `MainActivity.kt:73` crash screen offers "Reset Local Database" | One bad migration or crash loop deletes a semester of attendance |
| B2 | **Release build unhardened** | `isMinifyEnabled = false`, empty `proguard-rules.pro`, `firebase-appcheck-debug` is `implementation` (ships in release), 97 `Log.*` calls incl. scores/names | Reverse-engineerable, PII in logcat, ~2× APK size |
| B3 | **2,073-line `AttendanceScreen` owns business logic** | 46 `remember` state vars, DB writes, timers, BroadcastReceiver, lock-task polling inside a Composable | Root cause class of the AI-attendance crash, the recovery bugs and stale-state races |
| B4 | **Camera hot path allocates ~6 MB per frame** | `YoloFaceDetector.kt:126` `allocateDirect(4.9 MB)` + `Array(20){FloatArray(8400)}` per call; `toBitmap()`+rotate `createBitmap` per frame; per-pixel `getPixel()` loops in `CameraPreview`/`PresentationAttackAnalyzer` | GC churn, jank, heat, OOM on 3 GB phones |
| B5 | **Biometric templates can be stored in plaintext** | `AttractApplication.kt:77` "falling back to plaintext templates" | Violates SDD §64 / LLD-13; legal risk for biometric data |

---

## 1. Bottleneck map (from graphify)

| Graph signal | Meaning | Action |
|---|---|---|
| `AttractRepository` is god node #2 (85 edges, betweenness 0.082), 1,701 lines | Every feature couples through one class | Split by bounded context (PR-06) |
| `AttendanceScreen()` community: 54 nodes, cohesion 0.09 | UI + domain + platform in one function | Extract `AttendanceViewModel` state machine (PR-04) |
| 242 isolated nodes (e.g. `SessionCoordinator`, `FrameAnalysis`, unused `EnrollmentResult` variants) | Dead or unwired code | Wire `SessionCoordinator` or delete (PR-04/PR-12) |
| `assertTrue()` is god node #1 (199 edges, 195 INFERRED) | Tests assert booleans, not behaviour | Replace with typed assertions + CI gate (PR-11) |
| No import cycles | Good — modularisation is cheap | Gradle modules (PR-12) |

---

## 2. Work packages

### PR-01 (P0) — Data safety: never lose attendance
**Files:** `data/local/AttractDatabase.kt`, `app/MainActivity.kt`, `app/AttractApplication.kt`, `app/schemas/*`, `androidTest/.../MigrationTest.kt` (new)
1. Remove `fallbackToDestructiveMigration(true)` and `...OnDowngrade(true)`. A missing migration must **fail the build/test**, not wipe data.
2. Add `MigrationTestHelper` tests for every path 1→6, 2→6 … 5→6, using the exported schemas. Assert row counts for `attendance_records` and `attendance_sessions` are preserved.
3. Before any migration, `onCreate` takes an automatic snapshot: copy `attract.db` to `files/backups/attract-pre-v{N}.db` (keep the last 3).
4. Crash screen: replace "Reset Local Database" with **"Export diagnostics"** (stack trace + schema version, no PII) and **"Restore last snapshot"**. Destructive reset moves to Settings behind PIN + typed `DELETE`.
5. Crash-loop detection: 3 crashes in 60 s → safe mode (Dashboard only, camera disabled), not DB deletion.
6. Room `setJournalMode(WRITE_AHEAD_LOGGING)`. Run `PRAGMA integrity_check` on startup in a background thread; log the result.
**Done:** a deliberately broken migration in a test build shows safe mode; data is intact after a downgrade attempt.

### PR-02 (P0) — Release build hardening
**Files:** `app/build.gradle.kts`, `app/proguard-rules.pro`, new `app/src/release/`, `util/AppLog.kt` (new)
1. `release { isMinifyEnabled = true; isShrinkResources = true }`. Keep rules for:
   - Room entities and DAOs;
   - TFLite (`org.tensorflow.lite.**`);
   - Google API client / Drive model classes (`com.google.api.services.drive.model.**`, `com.google.api.client.**`, `@Key` fields);
   - Gson;
   - ML Kit;
   - Firebase AI.
2. `firebase-appcheck-debug` → `debugImplementation`. Move the `DebugAppCheckProviderFactory` reference into `src/debug/` so release cannot even compile it.
3. A `signingConfigs.release` read from env/`keystore.properties` (git-ignored). Enable Play App Signing.
4. **Logging:** add an `AppLog` wrapper. Release builds drop it via R8 `-assumenosideeffects class android.util.Log { *; }`. Never log names, roll numbers, scores or embeddings (grep gate in CI: `Log\..*(name|roll|score|embedding)` fails the build).
5. Ship an **App Bundle** (`bundleRelease`) for per-ABI native-lib splitting. TFLite and ML Kit natives are the bulk.
6. `versionCode` comes from CI (`GITHUB_RUN_NUMBER`). `versionName` comes from the git tag.
7. Add `android:dataExtractionRules` + `fullBackupContent="false"` (keep `allowBackup=false`) and `networkSecurityConfig` with cleartext disabled.
**Done:** `bundleRelease` succeeds; the APK diff shows no `firebase.appcheck.debug`; a logcat check during one session shows zero app logs.

### PR-03 (P0) — Biometric security model
**Files:** `app/AttractApplication.kt`, `lockdown/data/crypto/KeystoreEmbeddingCipher.kt`, `data/security/TemplateEnvelopeCodec.kt`, `data/security/PinHasher.kt`, `lockdown/domain/PinBackedAuthenticator.kt`, `data/importexport/BackupExporter.kt`
1. **No plaintext fallback.** If Keystore init fails, face features are *disabled* (manual attendance still works) with a clear banner. `TemplateEnvelopeCodec.encode` takes a non-null cipher. Plaintext decode only runs inside a one-time legacy migration.
2. Keystore key: AES-256-GCM, `setIsStrongBoxBacked(true)` when available (fallback TEE), `setUnlockedDeviceRequired(true)` (API 28+). AAD = studentId‖modelVersion (already present — keep it).
3. Handle `KeyPermanentlyInvalidatedException` by marking templates `REENROLL_REQUIRED`, never crashing.
4. **PIN:** PBKDF2-HMAC-SHA256 with ≥ 310k iterations (benchmark on a low-end device; target ≤ 300 ms) and a 16-byte salt. Persist the failed-attempt counter and exponential backoff in an encrypted pref; the in-memory `attemptCount` resets on restart today.
5. **Backups:**
   - Drive and file backups currently carry `pinHash` and all PII in clear JSON. Encrypt them with AES-GCM, using a key derived from the teacher PIN via PBKDF2 (portable to a new device).
   - Exclude face templates from backups (SDD §66).
   - Add `format` version + SHA-256 integrity check. `restoreBackup` must validate the schema/version **before** deleting any table, and run inside one transaction with an automatic pre-restore snapshot (PR-01 §3).
6. **Gemini OCR sends roster photos to the cloud.** This contradicts local-first (SDD §2.3). Make it opt-in with a consent dialog, keep ML Kit on-device as the default, and enforce App Check plus a 15 s timeout. Load the model list from Remote Config instead of hard-coding it.
7. **Liveness:** the brightness/texture heuristics are not PAD. Ship the relative-turn challenge from doc 18 WP-G, and label the feature honestly in the UI.
**Done:** a Keystore-failure test shows face mode disabled and no plaintext row written; a backup file opened in a text editor is unreadable.

### PR-04 (P0) — Attendance architecture: ViewModel state machine + correct session recovery
**Files:** `feature/attendance/AttendanceScreen.kt` (split), new `AttendanceViewModel.kt`, `AttendanceUiState.kt`, `domain/session/SessionCoordinator.kt` (wire it), `feature/app/AttractApp.kt`, `feature/app/AttractViewModel.kt`, `app/MainActivity.kt`
1. Move **all** logic out of the Composable:
   - capture, enrollment, recognition and DB writes;
   - the lock-task controller and the interrupt receiver;
   - timers / `feedbackJob`.

   These go into `AttendanceViewModel` (`SavedStateHandle` for sessionId/classId). The UI becomes `AttendanceRoute(vm)` → `AttendanceContent(state, onEvent)`, a pure render, each sub-component < 300 lines.
2. One sealed `AttendanceUiState` and one event channel. Transitions are serialized through a `Mutex`, and each attempt carries an `attemptId` so stale callbacks are ignored (doc 17 §5.1).
3. **Session recovery, the intended behaviour:** if a student swipes out, the phone locks, or pinning is lost, the session must be **saved and exited automatically**. When the teacher unlocks, the app opens on the **Calendar** with a **CONTINUE** button. The attendance screen never shows an "unpinned" prompt.
   - Detect the interrupt in **one place**: `ProcessLifecycleOwner` `ON_STOP` while a FACE session is active, plus `ActivityManager.lockTaskModeState == NONE` on `onResume`. Delete the composable `BroadcastReceiver(SCREEN_OFF)` and the 1 s polling loop.
   - On interrupt: `repository.pauseFaceSession(sessionId)`. Status `PAUSED` is new (add an enum value plus migration). It persists marks without writing ABSENT rows. Do **not** call `saveFaceAttendance` (that ENDs the session and writes ABSENTs, which makes "continue" lossy).
   - `AttractViewModel` routes on start/resume: if a `PAUSED`/`ACTIVE` session exists → `ClassWorkspace(classId, tab = CALENDAR)` with a snackbar "Session paused — tap CONTINUE". Never auto-open `FaceAttendance`.
   - CONTINUE → `resumeFaceSession` (PAUSED→ACTIVE) → `FaceAttendance`, which reloads present IDs from Room.
   - Sessions paused for more than 24 h are finalised (ABSENT rows written) by a `WorkManager` job, not on the UI startup path.
4. Never render `AttendanceScreen` with a null workspace or session. The route waits on `vm.state` = `Ready`, and exceptions become `AttendanceUiState.Error` (fixes the composition-time crash class seen in the stack trace).
5. Wrap camera binding, TFLite init and lock-task calls in typed `Result`s so they surface as state, never as uncaught throws during composition.
**Done:**
- Instrumentation test: start session → mark 2 → `moveTaskToBack`/stop → relaunch lands on Calendar with CONTINUE → continue shows 2 present → end → no duplicate rows.
- Monkey test of 10k events on the attendance screen produces 0 crashes.

### PR-05 (P0) — Camera & ML hot-path optimisation
**Files:** `feature/attendance/CameraPreview.kt`, `domain/face/YoloFaceDetector.kt`, `domain/face/EmbeddingEngine.kt`, `feature/attendance/PresentationAttackAnalyzer.kt`, `domain/face/FaceAligner.kt`
1. **Zero per-frame allocation:**
   - Pre-allocate the YOLO input `ByteBuffer`, the output array, the `IntArray` pixels, the scaled `Bitmap` and the 112² aligned bitmap once per interpreter and reuse them.
   - Today ~5.6 MB is allocated every 150 ms, which is about 37 MB/s of garbage.
2. **CameraX config:**
   - `ResolutionSelector` targeting 640×480 for analysis;
   - `setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)` so the YUV→JPEG→Bitmap path is no longer needed;
   - `setOutputImageRotationEnabled(true)` (removes the rotate `createBitmap`);
   - `setTargetFrameRate(15)`.
3. **Replace `getPixel()` loops** (brightness, Laplacian, PAD) with one `getPixels()` into a reused `IntArray`, on the 112² crop rather than the full frame. That is ~100× fewer JNI calls.
4. **Models:**
   - YOLOv8n-face fp32 at 640² (12.6 MB) → export at **320²** (faces are ≥ 10% of the frame, so 320 is enough) and **int8 or fp16 quantise** it (≈ 3 MB, about 4× faster).
   - ArcFace fp32 (4.9 MB) → fp16 (2.5 MB).
   - Re-run `tools/biometric_eval` and accept only if EER and the classroom simulation stay within 0.5 pp of fp32.
   - Use XNNPACK (default) with threads = `min(4, cores/2)`. Try the GPU delegate behind a feature flag with an allow-list.
5. **Pipeline cadence:** run the detector every frame at 320², but run ArcFace **only on the selected frame** (it already does), and run quality/PAD only on the crop.
6. **Threading:**
   - Both interpreters live on one dedicated `HandlerThread`/single-thread dispatcher. Drop `@Synchronized` contention with the analysis executor.
   - Close and shut down on `onDispose` *and* on ViewModel `onCleared`.
   - Lazy-init models in `Application` on a background thread at startup (warm-up inference with a blank tensor), so the first tap is fast.
7. **Memory:** never keep `latestFrameBitmap` in Compose state. The VM holds only the last aligned 112² crop; recycle it on replacement. Wipe embedding `FloatArray`s with `fill(0f)` after use.
**Done:** Android Studio profiler shows allocation < 200 KB/frame and no GC jank during a 5-minute session; detect+embed p95 < 120 ms on a Snapdragon 6-series; peak heap < 150 MB.

### PR-06 (P1) — Split the god repository + DI
**Files:** `data/repository/AttractRepository.kt` → `ClassRepository`, `StudentRepository`, `SessionRepository`, `BiometricRepository`, `BackupRepository`; `app/AttractApplication.kt` (AppContainer → Hilt or a manual `AppGraph`)
1. Each repository < 400 lines, constructor-injected DAOs, `Dispatchers.IO` injected for tests.
2. Composables must **never** do `context.applicationContext as AttractApplication` to get a repository (it does today in `AttractApp`/`ClassWorkspaceScreen`). Pass ViewModels instead. `CalendarViewModel` is currently created with `remember{}`, so it is not lifecycle-scoped; use `viewModel(factory)`.
3. Use-case classes for multi-repo transactions (`SelfEnrollAndCheckIn`, `FinalizeSession`, `RestoreBackup`).
**Done:** no file > 600 lines in `data/` or `feature/`; graphify re-run shows no node with > 40 edges outside tests.

### PR-07 (P1) — Navigation & UI performance
**Files:** `feature/app/AttractApp.kt`, `feature/app/AttractViewModel.kt`, `ui/screens/classworkspace/ClassWorkspaceScreen.kt`
1. Replace the hand-rolled `AnimatedContent(screen)` router with **Navigation-Compose** (type-safe routes):
   - a real back stack fixes "back from session → wrong page" and "delete → wrong page" generically (`popBackStack()`);
   - standard slide transitions (`enterTransition`/`popEnterTransition`, 280 ms `FastOutSlowIn`, no fade-plus-slide double animation).
2. Tabs: `HorizontalPager` + `TabRow` (pager state survives recomposition) instead of `AnimatedContent(activeTab)`. Keep each tab's ViewModel above the pager so switching doesn't rebuild flows (the source of the "50% then 50%" stutter).
3. Mark UI models `@Immutable` (`SessionSummary`, `StudentSummary`, `ClassWorkspace`). Use `ImmutableList` (kotlinx-collections-immutable) so lists skip recomposition. `LazyColumn` items need stable `key` + `contentType`.
4. Add **Baseline Profiles** (`androidx.baselineprofile`) for startup → dashboard → class → calendar → attendance. That cuts first-frame jank by ~30%.
5. Compose compiler metrics in CI; fail on newly unstable params in hot composables.
**Done:** Macrobenchmark cold start < 800 ms (mid-range), frame time p90 < 16 ms on tab switches.

### PR-08 (P1) — Google Drive backup that a teacher can trust
**Files:** `data/drive/*`, `ui/screens/settings/GoogleDriveSyncCard.kt`, `feature/app/AttractViewModel.kt`
1. `appDataFolder` is **hidden** in the Drive UI by design. Either keep it, and show in-app "Last backup: 2 min ago · 48 KB · Restore" with a list of backup versions, or switch to `drive.file` scope with a visible `Attract Backups/` folder. Pick one, and explain it in the UI ("Backups are stored privately in your Google Drive and don't appear in My Drive").
2. Keep 7 rolling versions (`attract_backup_<ts>.json.enc`), not one overwritten file.
3. Disconnect = revoke access (`AuthorizationClient.revokeAccess` / `clearToken`) and clear the cached account. Connect always shows the account chooser.
4. Restore:
   - download → decrypt → validate → take a pre-restore snapshot → transactional replace → re-schedule work;
   - show the actual error category: network / auth / corrupt / version.
5. Worker constraints: `UNMETERED` optional, `setBackoffCriteria(EXPONENTIAL)`, and treat `UserRecoverableAuthException` as a notification, not a silent retry loop.

### PR-09 (P1) — Stability & observability
1. Firebase Crashlytics (or Sentry) with PII scrubbing. Breadcrumbs carry state names only.
2. `StrictMode` in debug (disk/network on main, leaked closables). Add LeakCanary to debug.
3. ANR guard: all DB/crypto/TFLite off the main thread. Audit `runBlocking`/`.get()` on main (`CameraPreview` `cameraProviderFuture.get()` inside `onDispose` → use the cached provider).
4. Typed `AppError` all the way to the UI. Replace the generic "Your data could not be saved" with actionable messages per error class.

### PR-10 (P1) — App size & dependencies
1. Drop either ML Kit OCR **or** Gemini OCR from the default build (both ship today). Put Gemini behind a dynamic feature module.
2. Replace `google-api-client-android` + `google-api-services-drive` (heavy, reflection-based) with a small OkHttp REST client for the 3 Drive calls used (list/upload/download).
3. `material-icons-extended` (~10 MB pre-R8) → copy only the used icons, or rely on R8 shrinking (verify).
4. Target: release AAB download size < 20 MB per ABI (models included after quantisation).

### PR-11 (P1) — Quality gates / CI
1. `.github/workflows/android.yml`:
   - `./gradlew lint testDebugUnitTest assembleRelease bundleRelease`;
   - detekt + ktlint;
   - Room migration tests on an emulator (API 29 + 34) via Gradle Managed Devices;
   - the log-PII grep gate.
2. Replace boolean `assertTrue` assertions with Truth/AssertK typed assertions (graph: 199 edges on `assertTrue`).
3. Golden tests: `tools/biometric_eval` regression (EER, wrong-ID = 0 on the mock class) runs in CI on the model assets.
4. Coverage gate ≥ 70% on `domain/` and the repositories.

### PR-12 (P2) — Modularisation & cleanup
Modules:
- `:core:model`
- `:core:database`
- `:core:security`
- `:feature:attendance`
- `:feature:workspace`
- `:feature:backup`
- `:ml:face` (TFLite + assets)

Delete the dead code the graph flags as isolated (`EvidenceFusion` test-only strategies, the unused `TeacherAuthorizationGrant` paths after D-007, legacy `enrollStudentFace`, `markFallbackPresent`), or wire it in. This lowers build times and enforces the boundaries PR-06 creates.

---

## 3. Release checklist (go / no-go)

| Gate | Target |
|---|---|
| Crash-free sessions (internal test, 2 weeks, ≥ 5 devices incl. 3 GB RAM) | ≥ 99.8% |
| Migration tests 1→6 … 5→6 | all green, data preserved |
| Plaintext template rows | 0 (enforced by test) |
| Release logcat app lines in one session | 0 |
| Camera pipeline allocation / frame | < 200 KB |
| Detect+embed p95 (mid-range) | < 120 ms |
| Cold start (Macrobenchmark) | < 800 ms |
| Wrong-student marks (doc 18 simulation + 10-person pilot) | 0 |
| Interrupt → relaunch lands on Calendar with CONTINUE | pass (instrumented) |
| Backup encrypted, restore validated + rollback | pass |
| AAB download size per ABI | < 20 MB |

## 4. Sequencing

| Week | Work |
|---|---|
| 1 | PR-01, PR-02, PR-03 (data and security blockers) |
| 2 | PR-04 (architecture + correct recovery), PR-05 (hot path) |
| 3 | PR-06, PR-07, PR-08 |
| 4 | PR-09, PR-10, PR-11, then pilot on 2 classrooms; PR-12 after launch |

PR-01 to PR-05 are mandatory for any public release. PR-06 to PR-11 are needed for a "doesn't break" app at scale. PR-12 is maintainability work.
