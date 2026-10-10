# 19 — Master Production Plan (v2)

**Prepared:** 10 Oct 2026 for Amit Kumar Hota.  
**Basis:** Merges the earlier Opus 5.5 code-level plan (PR-01 to PR-12, with file and line evidence) with the comprehensive release, compliance, and biometric audit. Grounded on `graphify` report (87 files, 2,637 nodes, 7,133 edges), repository code, README, SDD v3.1, `gradle.properties`, and the root listing.

---

## 1. Verdict and Merge Decisions

Ten P0 blockers (**BL-01** to **BL-10**) gate any public release, and 19 work packages (**WP-01** to **WP-19**) clear them and harden the app over ~7 weeks. 
- **WP-01 to WP-10** are mandatory for public release.
- **WP-11 to WP-19** make the app hold up and scale reliably in production.

### Evidence Tags
- **`[P]`** File and line facts from the earlier plan which inspected code directly. Every work package opens with a quick check of its `[P]` items.
- **`[C]`** Confirmed from the README, SDD, root listing, `gradle.properties`, or Google Play policy.
- **`[G]`** Inferred from graphify class and edge names.
- **`[V]`** Standard production item that is not visible either way.

### Audit Corrections & Calibrations
1. **Gemini Secrets:** Gemini goes through Firebase AI with App Check; no raw API key ships in the client. The actual vulnerability is `firebase-appcheck-debug` compiled into the release build.
2. **Schema Drift:** The codebase already targets Room schema v6 while the README states v4; migration tests must target the actual current schema version.

### Key Overrides & Architectural Nuances

| Topic | Earlier Plan | Decision Here | Rationale |
|:---|:---|:---|:---|
| **Backup encryption key** | Derived from teacher PIN | **Separate backup passphrase (12+ chars) or generated recovery key; never the PIN** | A 6-digit PIN has only $10^6$ combinations. An offline attacker downloading a backup from Drive can brute-force a PIN in seconds on a GPU even at 310k iterations. |
| **PIN hash cost** | PBKDF2-SHA256, 310k+ iterations | **PBKDF2-SHA256 benchmarked to ~300 ms on low-end hardware (floor 310k, target 600k) + unexportable Android Keystore HMAC pepper** | Storing `hmacSha256(pepperKey, derived)` inside Keystore (`KeyProperties.PURPOSE_SIGN`) ensures an extracted database or backup file cannot be brute-forced off-device. |
| **Face templates in backups** | Excluded (SDD §66) | **Keep excluded in v1; portable encrypted export is post-MVP** | Keystore keys are hardware non-exportable; cross-device migration is deferred post-MVP. |
| **Crash reporting** | Crashlytics or Sentry | **Play Android Vitals + local diagnostics export by default; Crashlytics only as an opt-in toggle** | Keeps the README zero-telemetry claim true while maintaining production observability. |
| **Dependency Injection** | Hilt or manual graph | **Manual `AppGraph`, constructor injection** | 87 files; Hilt adds KSP build overhead with no safety gain at this project scale. |
| **`assertTrue` rewrite** | Replace boolean asserts across codebase | **Typed assertions in new tests only; no mass rewrite** | 195 of the 199 graph edges on `assertTrue` are `INFERRED` graph noise. Priority belongs on coverage and behavior tests. |
| **Quantized detector** | YOLO at 320 + int8 if EER within 0.5 pp | **fp16 first; int8 only if landmark error and alignment hold** | Quantizing the landmark regression head to int8 can distort 5-point alignment before recognition EER moves. |
| **Biometric pilot gate** | "0 wrong marks in 10-person pilot" | **Statistical gate: 0 errors in 3,000+ impostor trials (Rule of Three, 95% confidence)** | A 10-person pilot cannot statistically prove a False Accept Rate ($\text{FAR} \le 0.1\%$). Evaluate on 30k+ offline pairs. |
| **Drive Client implementation** | Replace with OkHttp immediately | **Measure after R8 first; replace only if it still costs > 1.5 MB** | Sign-in and tokens stay with Google's authorization client; only the 3 REST calls would be rewritten. Measure after R8 before taking on handwritten REST code. |
| **Navigation-Compose** | P1, early | **Sequence after WP-07** | Extracting `AttendanceViewModel` removes the primary source of navigation bugs first. |
| **Modularisation** | P2 | **Post-launch and optional** | Zero import cycles exist in the graph, making future modularisation straightforward without blocking launch. |

### Deliberately Skipped (Out of MVP Scope)
- **Hilt migration:** Manual `AppGraph` is sufficient for 87 files.
- **Mass rewrite of `assertTrue`:** Focus effort on new unit/integration tests.
- **Vector database:** ~500 vectors are scored in under 1 ms via flat dot products.
- **SQLCipher:** Android device encryption + Keystore-encrypted templates is adequate; revisit only if cloud sync is introduced.
- **Device-owner kiosk mode** (SDD §57) and **template cloud sync / cross-device migration** (SDD §§67, 68): Post-MVP.

---

## 2. Blocker Board (25 Blockers)

| ID | Sev | Blocker Description | Evidence | Fix | Active Status |
|:---|:---:|:---|:---|:---:|:---:|
| **BL-01** | **P0** | Silent data wipe: `fallbackToDestructiveMigration(true)`; crash screen offers "Reset Local Database" | `[P]` `AttractDatabase.kt:136`, `MainActivity.kt:73` | WP-01 | ✅ **COMPLETED (`dev`)** |
| **BL-02** | **P0** | Release build unhardened: minify off, empty `proguard-rules.pro`, `firebase-appcheck-debug` in release, 97 `Log` calls incl. scores/names | `[P]` `app/build.gradle.kts` | WP-02 | ✅ **COMPLETED (`dev`)** |
| **BL-03** | **P0** | Biometric templates can be stored in plaintext | `[P]` `AttractApplication.kt:77` | WP-04 | ✅ **COMPLETED (`dev`)** |
| **BL-04** | **P0** | Backups carry `pinHash` and PII in clear JSON; restore deletes tables before validating | `[P]` `BackupExporter` | WP-05, WP-13 | 🟡 **Partially done (PR-03)** |
| **BL-05** | **P0** | Play: new apps and updates must target API 36; README says 35 | `[C]` Play policy (in force since 31 Aug 2026) | WP-02 | ⏳ Pending upgrade |
| **BL-06** | **P0** | Play: updates without 16 KB page-size support blocked from 1 Feb 2027; legacy TFLite `.so` are 4 KB-aligned | `[C]` Android docs | WP-02 | ⏳ Pending LiteRT 1.4+ |
| **BL-07** | **P0** | Licences: YOLOv8-Face weights (AGPL or GPL lineage), ArcFace weights (InsightFace is non-commercial), no root LICENSE file | `[C]` root listing, `[V]` weight sources | WP-03 | ⏳ Pending |
| **BL-08** | **P0** | README promises zero cloud; app has Gemini roster OCR and Drive backup | `[C]` README, `[G]` `GeminiRosterExtractor`, `DriveBackupWorker` | WP-06 | 🟡 Consent added (PR-03) |
| **BL-09** | **P0** | Students hold phone: PIN brute force; attempt counter previously lived in memory | `[P]` `PinBackedAuthenticator` | WP-05 | ✅ Persistent (`dev`) |
| **BL-10** | **P0** | Public repo: secrets, keystores or real face images may sit in git history | `[V]` | WP-03 | ⏳ Pending gitleaks scan |
| **BL-11** | **P1** | `AttendanceScreen` is 2,073 lines with 46 `remember` vars, DB writes, timers, BroadcastReceiver and lock-task polling | `[P]` `AttendanceScreen.kt` | WP-07 | ⏳ Pending |
| **BL-12** | **P1** | Interrupt handling: swipe-out or screen-off must save session as `PAUSED` and return to Calendar with CONTINUE | `[P]` | WP-07 | ⏳ Pending |
| **BL-13** | **P1** | Camera loop allocates ~6 MB per frame; per-pixel `getPixel()` loops in brightness, Laplacian, PAD | `[P]` `YoloFaceDetector.kt:126`, `CameraPreview`, `PresentationAttackAnalyzer` | WP-08 | ⏳ Next Up |
| **BL-14** | **P1** | YOLOv8n fp32 at 640×640 is dominant CPU cost | `[C]` README | WP-08 | ⏳ Next Up |
| **BL-15** | **P1** | Accept (0.25), margin (0.05), duplicate (0.22), continuity (0.35) uncalibrated | `[C]` README, SDD §25 | WP-09 | ⏳ Pending |
| **BL-16** | **P1** | Liveness is brightness and texture heuristics, not presentation-attack detection | `[P]` | WP-10 | ⏳ Pending |
| **BL-17** | **P1** | `AttractRepository` is 1,701 lines (85 edges); Composables fetch repositories from `Application` | `[P]`, `[G]` | WP-11 | ⏳ Pending |
| **BL-18** | **P1** | Hand-rolled router; `CalendarViewModel` created with `remember`; tab `AnimatedContent` stutter | `[P]` | WP-12 | ⏳ Pending |
| **BL-19** | **P1** | Drive backup hidden in `appDataFolder`, one overwritten file, no restore feedback | `[P]` | WP-13 | ⏳ Pending |
| **BL-20** | **P1** | Roster OCR accuracy on low-contrast/skewed paper rosters | Known issue | WP-14 | ⏳ Pending |
| **BL-21** | **P1** | No crash/ANR visibility; blocking calls on main thread (`cameraProviderFuture.get()` in `onDispose`) | `[P]` | WP-15 | ⏳ Pending |
| **BL-22** | **P1** | App size: ML Kit + Gemini OCR both ship, heavy Drive client, `material-icons-extended` | `[P]` | WP-16 | ⏳ Pending |
| **BL-23** | **P1** | No CI (`.github/` missing); `gradle.properties` is 4 lines; no version catalog | `[C]` | WP-17 | ⏳ Pending |
| **BL-24** | **P1** | `SessionCoordinator` and dead code unwired; hand-written Fake DAOs drift from Room behavior | `[G]` 242 isolated nodes, `[P]` | WP-11, WP-19 | ⏳ Pending |
| **BL-25** | **P2** | Documentation drift (SDD says ML Kit, README says YOLOv8, schema v4 vs v6); `graphify-out/` committed; `XorCipher` test-only audit | `[C]`, `[G]` | WP-18 | ⏳ Pending |

---

## 3. Phase 0 (Week 1): Data Safety, Hardening, Compliance, Security

### WP-01 (P0) — Data safety: Never lose attendance
**Fixes:** BL-01  
**Files:** `data/local/AttractDatabase.kt`, `app/MainActivity.kt`, `app/AttractApplication.kt`, `app/schemas/*`, `androidTest/MigrationTest.kt` (new)  
**Status on `dev`:** ✅ **COMPLETED**
1. Check `[P]`: Verified `fallbackToDestructiveMigration(true)` removed from `AttractDatabase.kt`.
2. Removed `fallbackToDestructiveMigration` and `fallbackToDestructiveMigrationOnDowngrade`.
3. Created `MigrationTest` verifying schema migrations 1→6 with `attendance_records` row-count assertions.
4. Pre-migration snapshot mechanism checkpoints WAL and copies `attract.db` to `files/backups/attract-pre-vN.db` (retains last 3).
5. Crash screen: replaced "Reset Local Database" with "Export diagnostics" and "Restore last snapshot". Destructive reset requires teacher authentication and typed `DELETE`.
6. Crash-loop detection: 3 crashes in 60 s triggers Safe Mode (Dashboard only, camera disabled).
7. Room WAL mode enabled; background integrity check on startup.
8. **Done check:** Deliberately broken migration lands in Safe Mode with zero data loss.

### WP-02 (P0) — Release hardening and Play compliance
**Fixes:** BL-02, BL-05, BL-06  
**Files:** `app/build.gradle.kts`, `app/proguard-rules.pro`, `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml`, `src/debug/`, `src/release/`, `util/AppLog.kt`  
**Status on `dev`:** 🟡 **Hardening completed; SDK 36 & LiteRT 1.4 pending**
1. Target API 36: update `compileSdk = 36`, `targetSdk = 36`. Test predictive back inside pinned mode, WorkManager job quotas, edge-to-edge.
2. 16 KB pages: migrate from `org.tensorflow:tensorflow-lite*` to Google LiteRT (`com.google.ai.edge.litert:litert` >= 1.4.0). Verify `.so` ELF 16 KB alignment via APK Analyzer.
3. R8: `isMinifyEnabled = true`, `isShrinkResources = true`. Proguard rules keep Room, LiteRT, Drive models, Gson, ML Kit, Firebase AI. *(Completed)*
4. `firebase-appcheck-debug` moved to `debugImplementation` in `src/debug/`. *(Completed)*
5. Logging: `AppLog` wrapper; release build strips `android.util.Log` calls via `-assumenosideeffects`. Never log names, rolls, or vectors. *(Completed)*
6. Signing from environment / `keystore.properties`; App Bundle (`bundleRelease`) for ABI splits.
7. Manifest: `allowBackup="false"`, `data_extraction_rules.xml` excluding DB/prefs, cleartext disabled, `FLAG_SECURE` on PIN and biometric screens. *(Completed)*
8. `androidResources { noCompress += "tflite" }` for zero-copy `mmap`.
**Done check:** `bundleRelease` succeeds; zero app logs in release logcat; no 16 KB warning in Play Console report.

### WP-03 (P0) — Licensing, secrets and repo hygiene
**Fixes:** BL-07, BL-10  
**Files:** `docs/MODELS.md` (new), `LICENSE` (new), `NOTICE` (new), `.gitignore`, `scripts/download_models.py`
1. Commercial vs Private Distribution decision.
2. `docs/MODELS.md`: catalog every `.tflite` model (file, source URL, SHA-256, license, training data).
3. YOLOv8n-Face: verify license or plan swap to Apache-2.0 MediaPipe BlazeFace / ML Kit Face Detection.
4. ArcFace MobileFaceNet: verify weights origin (audit against InsightFace non-commercial restrictions).
5. Add root Apache-2.0 `LICENSE` and `NOTICE` files.
6. Secrets scan: run `gitleaks` and `trufflehog` across full Git history.
7. Ensure zero real student photos or PII exist in repository test fixtures.
8. Maintain single model download strategy via `download_models.py`.
**Done check:** Every binary asset has a license row in `MODELS.md`, `gitleaks` is clean on full history, `LICENSE` exists.

### WP-04 (P0) — Biometric template encryption with no plaintext path
**Fixes:** BL-03  
**Files:** `app/AttractApplication.kt`, `lockdown/data/crypto/KeystoreEmbeddingCipher.kt`, `data/security/TemplateEnvelopeCodec.kt`  
**Status on `dev`:** ✅ **COMPLETED**
1. Removed "falling back to plaintext templates". Keystore failure cleanly disables face mode with banner.
2. `TemplateEnvelopeCodec.encode` requires non-null cipher; plaintext decode path isolated to legacy migration.
3. Key: AES-256-GCM, StrongBox with TEE fallback. Key is not bound to lock-screen auth (prevents template wipe on screen-lock change).
4. AAD = `studentId || modelVersion`. `KeyPermanentlyInvalidatedException` marks templates `REENROLL_REQUIRED` without crash.
5. Reject mixed model profiles via `TemplateCompatibility`.
**Done check:** Database scan confirms 0 plaintext template rows; fresh enrollments write v2 encrypted envelopes.

### WP-05 (P0) — Teacher PIN, lockout and backup cryptography
**Fixes:** BL-04, BL-09  
**Files:** `data/security/PinHasher.kt`, `data/security/PinLockoutManager.kt`, `lockdown/domain/PinBackedAuthenticator.kt`, `data/importexport/BackupExporter.kt`  
**Status on `dev`:** 🟡 **PBKDF2 & Persistent lockout completed; Pepper & separate backup passphrase pending**
1. PIN hash: PBKDF2-HMAC-SHA256 with 16-byte salt, benchmarked to ~300 ms on low-end device (floor 310k, target 600k), followed by HMAC-SHA256 with an unexportable Android Keystore pepper key. Constant-time compare; zero `CharArray` after use. Enforce >= 6-digit PINs.
2. Persistent lockout: track failures, `lockedUntil` (`SystemClock.elapsedRealtime`), and `Settings.Global.BOOT_COUNT`. Delays: 5 fails = 30 s, 8 = 5 min, 10+ = 1 h. If `BOOT_COUNT` changes, re-apply the full delay.
3. `BiometricPrompt` (`BIOMETRIC_STRONG`) as primary authentication; PIN as fallback. Log `AUTH_FAILED` events; display "N failed attempts during last session" to teacher upon unlock.
4. Backups: remove `pinHash` entirely; exclude face templates. Encrypt with AES-256-GCM under a **separate backup passphrase (12+ characters) or generated recovery key**, never the PIN.
**Done check:** 1,000 incorrect PIN entries hit persistent lockout surviving app kills; backup opened in text editor is unreadable; tampered backup rejected.

### WP-06 (P0) — Privacy truth and cloud consent
**Fixes:** BL-08  
**Files:** `GeminiRosterExtractor.kt`, roster UI, Settings, README, `docs/PRIVACY.md` (new)  
**Status on `dev`:** 🟡 **Consent dialog added in PR-03; docs sync pending**
1. Default to `Engine.ON_DEVICE` (ML Kit). Gemini OCR runs only after explicit per-use consent dialog; record timestamp. Never send face images or embeddings over network.
2. Enforce App Check (Play Integrity) on Firebase AI; 15 s timeout.
3. Drive backup off by default; described transparently in Settings.
4. Privacy policy and in-app consent: add biometric notice and consent on first enrollment, per-student "delete face data" action, and retention purge rules.
**Done check:** Full attendance workflow functions in Airplane Mode with zero network calls; README matches runtime behavior.

---

## 4. Phase 1 (Weeks 2–3): Attendance Core and Biometric Quality

### WP-07 (P0) — Attendance architecture and correct session recovery
**Fixes:** BL-11, BL-12  
**Files:** `feature/attendance/AttendanceScreen.kt`, `AttendanceViewModel.kt`, `AttendanceUiState.kt`, `domain/session/SessionCoordinator.kt`, `MainActivity.kt`  
**Status on `dev`:** ⏳ **PENDING (Reverted; to be cleanly redesigned)**
1. Moved all logic out of Composable into `AttendanceViewModel` with `SavedStateHandle`. Screen decomposed into pure render sub-composables < 300 lines.
2. Single sealed `AttendanceUiState` and event channel with `Mutex` serialization and `attemptId` tokens.
3. Correct session recovery:
   - Interrupt detected in one place: `ProcessLifecycleOwner` `ON_STOP` while FACE session active, plus `lockTaskModeState == NONE` on `onResume`.
   - On interrupt: call `repository.pauseFaceSession(sessionId)` (status `PAUSED`). Never call `saveFaceAttendance` (which writes ABSENTs).
   - On start/resume: `AttractViewModel` routes to `ClassWorkspace(tab = CALENDAR)` with "Session paused — tap CONTINUE". Never auto-reopens camera.
   - CONTINUE calls `resumeFaceSession(PAUSED -> ACTIVE)` and reloads present student IDs.
   - Sessions `PAUSED` > 24 h are finalized by background `WorkManager` job.
4. Never render with null workspace/session; exceptions surface as typed `AttendanceUiState.Error`.
**Done check:** Instrumented interrupt-to-Calendar test passes; 10,000-event monkey test produces 0 crashes.

### WP-08 (P0) — Camera and ML hot path
**Fixes:** BL-13, BL-14  
**Files:** `CameraPreview.kt`, `YoloFaceDetector.kt`, `EmbeddingEngine.kt`, `PresentationAttackAnalyzer.kt`, `FaceAligner.kt`
1. **Zero per-frame allocations:** Pre-allocate YOLO direct `ByteBuffer`, output arrays, pixel buffers, scaled Bitmaps, and 112×112 aligned Bitmaps once per interpreter. Eliminate 37 MB/s garbage churn.
2. **CameraX pipeline:** `ResolutionSelector` for 640×480 analysis, `OUTPUT_IMAGE_FORMAT_RGBA_8888` (eliminates YUV-to-RGB conversion), `setOutputImageRotationEnabled(true)`, capped at 15 fps.
3. **Batch pixel reads:** Replace per-pixel `getPixel()` loops with single `getPixels()` into reused `IntArray` on the 112×112 face crop.
4. **Model optimization:** fp16 for ArcFace and YOLOv8n detector; benchmark 320×320 input size. Only test int8 if 5-point landmark residuals hold.
5. **Delegates & Cadence:** XNNPACK with `min(4, cores/2)` threads. GPU delegate wrapped in `try-catch` with persisted `gpu_failed` flag. Detector throttled to 15 fps with IoU tracking; ArcFace runs only on quality-gated frames.
6. **Threading:** Dedicated single-thread dispatcher for interpreters; zero contention with analysis executor. Memory-map `.tflite` assets. Zero-fill embedding vectors and template caches at session end.
**Done check:** Memory profiler shows < 200 KB allocation/frame; detect+embed p95 < 120 ms on Snapdragon 6-series; end-to-end check-in < 2.5 s; peak heap < 150 MB.

### WP-08T (P1) — Thermal and battery management (The Thermal Governor)
**Files:** `domain/thermal/ThermalGovernor.kt` (new), `CameraPreview.kt`, `YoloFaceDetector.kt`, `PrerequisitesChecker.kt`, soak-test script
1. **45-minute soak benchmark:** Build harness logging `PowerManager.getCurrentThermalStatus()`, `getThermalHeadroom()`, battery temperature, and fps every 10 s across ambient temperatures (25°C and 32°C).
2. **Duty-cycle throttling:** Preview only while idle; analysis runs strictly during the 2–3 s active scan window plus 1.5 s cooldown. (Cuts active inference from 45 minutes to ~3 minutes per class).
3. **Cap pipeline rates:** 15 fps target range via Camera2Interop, 640×480 analysis, 320×320 detector.
4. **Thermal Governor ladder:**
   - **Normal (NONE / LIGHT):** Detector up to 15 fps at 320×320.
   - **Warm (MODERATE / Headroom >= 0.8 / Battery >= 42°C):** Detector 8 fps, analysis 480×360, GPU delegate off, dim idle screen, "Phone is warm" hint.
   - **Hot (SEVERE / Headroom >= 0.95 / Battery >= 45°C):** Detector 3 fps, press-to-scan only, banner "Phone is hot: let it cool or switch to manual attendance".
   - **Critical (CRITICAL):** Stop camera, save and pause session via WP-07 path, offer manual roster.
   *(Step down immediately; step up only after 60 s below lower threshold to prevent flapping).*
5. **Screen & Animation load:** Pause infinite Compose animations while idle; dim idle screen; maintain `FLAG_KEEP_SCREEN_ON` strictly while session is active.
6. **Preflight checks:** `PrerequisitesChecker` requires thermal status LIGHT or lower and battery >= 25% or charging. Show "unplug or move to shade" if phone is charging while warm.
**Done check:** In 32°C ambient classroom, 45-minute session with 60 check-ins stays below SEVERE thermal status, battery temp < 42°C, minute-40 latency within 1.3× of minute-5, battery drop <= 12% per 30 minutes.

### WP-09 (P1) — Calibrate recognition thresholds with data
**Fixes:** BL-15  
**Files:** `tools/biometric_eval/build_feature_cache.py`, `RecognitionDecisionEngine.kt`, `AdaptiveVerificationEngine.kt`, versioned thresholds config
1. Collect empirical dataset: >= 40 subjects, 3+ sessions on different days, 3 lighting conditions, with/without glasses, 5+ frames per check-in.
2. Statistical validation: evaluate across 30k+ offline impostor pairs (Rule of Three for $\text{FAR} \le 0.1\%$ at classroom gallery $N = 60\text{--}100$).
3. Set accept threshold for per-attempt $\text{FAR} \le 0.1\%$; set margin from impostor top-2 gap; target $\text{FRR} \le 5\%$ on first attempt.
4. Enforce consecutive-frame verification: top-1 identity must match across at least 2 consecutive frames.
5. Derive duplicate gate (0.22) and continuity threshold (0.35) mathematically from score distributions.
6. Centralize thresholds into single versioned configuration file; log `thresholdSetVersion` on every record.
7. Fairness gate: evaluate FRR across demographic subgroups (glasses, beard, head covering, skin tone, low light). If any group > 2× average, adjust capture guidance rather than global threshold.
**Done check:** Calibration report committed under `docs/`, thresholds load from versioned config, CI regression fails if EER degrades.

### WP-10 (P1) — Honest liveness and real anti-spoof
**Fixes:** BL-16  
**Files:** `PresentationAttackAnalyzer.kt`, `LivenessEngine.kt`, new PAD model asset, `PoseStepper.kt`
1. Label current check "basic liveness" in UI and documentation (brightness/texture heuristics).
2. Integrate trained passive PAD classifier (MiniFASNet / Silent-Face style) operating on context-expanded face crop, with moiré/specular cues as secondary scores.
3. Grey-zone passive scores trigger randomized active challenge (turn left, turn right, blink) reusing `TurnChallengeDetector` and `PoseStepper`.
4. Proxy cross-check: compare new embedding against students already marked PRESENT; flag close matches as proxy attempts.
5. Measure APCER and BPCER across printed paper, smartphone screen, tablet screen, and replay video (target <= 5% for each).
**Done check:** PAD evaluation report committed; active-challenge instrumented test passes; UI wording accurately describes check capabilities.

---

## 5. Phase 2 (Weeks 3–5): Structure, Resilience, Delivery

### WP-11 (P1) — Split the god repository, wire the graph, fix the tests
**Fixes:** BL-17, BL-24  
**Files:** `AttractRepository.kt`, `AppContainer.kt`, Fake DAO test helpers
1. Check `[P]`: `AttractRepository` is 1,701 lines with 85 graph edges.
2. Split into `ClassRepository`, `StudentRepository`, `SessionRepository`, `BiometricRepository`, and `BackupRepository` behind interfaces with constructor-injected DAOs and `Dispatchers.IO`. Retain `AttractRepository` as a temporary facade.
3. Replace `AppContainer` with a manual `AppGraph` (no Hilt). Composables must never fetch repositories through `context.applicationContext as AttractApplication`. Pass ViewModels. Replace `remember`-created `CalendarViewModel` with `viewModel(factory)`.
4. Use-case classes for multi-repository operations (`SelfEnrollAndCheckIn`, `FinalizeSession`, `RestoreBackup`).
5. Tests: repository tests run against in-memory Room (Robolectric / AndroidTest). Retain Fake DAOs strictly for ViewModel unit tests. Use typed assertions for new tests.
6. Prune dead/unwired code flagged by graphify (`SessionCoordinator` after WP-07, legacy enrollment methods, test-only `EvidenceFusion` strategies).
**Done check:** No file in `data/` or `feature/` exceeds 600 lines; graphify shows zero non-test nodes with > 40 edges.

### WP-12 (P1) — Navigation, Compose performance and startup
**Fixes:** BL-18  
**Files:** `AttractApp.kt`, `AttractViewModel.kt`, `ClassWorkspaceScreen.kt`, baseline-profile module
1. Migrate from hand-rolled `AnimatedContent(screen)` to Navigation-Compose type-safe routes with real back-stack handling (`popBackStack()`). Use standard 280 ms `FastOutSlowIn` slide transitions.
2. Tabs: `HorizontalPager` with `TabRow`. Keep tab ViewModels above the pager to prevent recomposition flow reconstruction.
3. Annotate UI models with `@Immutable` (`SessionSummary`, `StudentSummary`, `ClassWorkspace`). Use `ImmutableList` for collections; assign stable `key` and `contentType` to `LazyColumn` items.
4. Split `AttractUiState` so camera overlays do not recompose entire screens; draw face bounding box in `Canvas` draw lambda.
5. Generate Baseline Profiles and Macrobenchmarks. Zero model initialization in `Application.onCreate()`.
**Done check:** Macrobenchmark cold start < 800 ms on mid-range phone; tab-switch frame time p90 < 16 ms.

### WP-13 (P1) — Drive backup a teacher can trust
**Fixes:** BL-04 (restore side), BL-19  
**Files:** `data/drive/*`, `GoogleDriveSyncCard.kt`, `AttractViewModel.kt`, `BackupExporter.kt`
1. Scope decision: retain `appDataFolder` scope (narrowest permissions, least consent friction). Display transparent status in UI ("Last backup: 2 min ago · 48 KB · Restore") with version list.
2. Maintain 7 rolling versions (`attract_backup_<timestamp>.json.enc`), encrypted via WP-05 passphrase. Never include face templates.
3. Disconnect flow revokes tokens via authorization client and clears cached account; connect always prompts account chooser.
4. Transactional restore: download $\to$ decrypt $\to$ validate format version and integrity $\to$ pre-restore snapshot $\to$ atomic database replace $\to$ reschedule workers. Never delete tables prior to validation. Surface typed errors (network, auth, corrupt, version).
5. WorkManager constraints: optional `UNMETERED`, exponential backoff.
6. Measure Drive client size after R8; replace `google-api-client` with lightweight OkHttp client only if it still costs > 1.5 MB.
**Done check:** Backup and restore across devices produces identical data; corrupted or tampered backups rejected with zero database modification.

### WP-14 (P1) — Roster OCR accuracy
**Fixes:** BL-20  
**Files:** `OcrRosterParser.kt`, `RosterGeometry.kt`, `RollNumberRepair.kt`, `GeminiRosterExtractor.kt`, `OcrConfirmationDialog.kt`
1. Pipeline: ML Kit Document Scanner (deskew & crop) $\to$ ML Kit Text Recognition $\to$ `RosterGeometry` row/column clustering $\to$ column classification $\to$ `RollNumberRepair` $\to$ confirmation dialog $\to$ optional consent-based Gemini on low-confidence cells.
2. Image preprocessing: minimum 1600 px width, blur detection retake prompt. Decode with `inSampleSize`, cap long edge at 2048 px, retain `Uri` in state (never `Bitmap`), clean temp files in `finally`.
3. Roll number column: 2× upscale and adaptive binarization. Learn schema pattern from majority (prefix, length, character classes). Position-based OCR disambiguation (`O`/`0`, `I`/`1`, `S`/`5`, `B`/`8`, `Z`/`2`). Neighbor gap interpolation. Mark rows `FIXED`, `INFERRED`, or `SUSPECT`.
4. Name verification: render cropped image segment adjacent to editable text for `SUSPECT` rows.
5. Golden test set: 30 labeled roster photos (print, handwriting, skew, glare).
**Done check:** Golden test set achieves roll number exact-match >= 95%, name CER <= 5%, enforced in CI.

### WP-15 (P1) — Reliability matrix and local observability
**Fixes:** BL-21  
**Files:** `MainActivity.kt` (crash screen), `CameraPreview.kt`, `SecurityEventLogger.kt`, `AppError.kt`
1. Local diagnostics: uncaught exception handler writes to rolling, size-capped log (max 512 KB) stripped of PII. Crash screen offers user-initiated "Export diagnostics". Rely on Play Android Vitals for production crash/ANR tracking.
2. Debug builds: `StrictMode` (disk/network on main, leaked closables) and `LeakCanary`.
3. ANR prevention: eliminate all blocking calls on main thread (`cameraProviderFuture.get()` in `onDispose` uses cached provider).
4. Typed `AppError` surfaced across all UI screens.
5. Coroutine safety: injected dispatchers, no `GlobalScope`, never suppress `CancellationException` in `runCatching`, inference timeouts.
6. Property-based state machine tests over `SessionState`: random event streams must never reach illegal states or double-mark attendance.
7. **Failure Matrix Test Automation:**

| Failure Mode | Required Behavior | Test Verification |
|:---|:---|:---|
| Camera contention / `CameraState.Error` | Retry with backoff; manual fallback; session stays `ACTIVE` | Instrumented test, fake provider |
| CAMERA permission revoked mid-session | Block check-in; teacher auth path to settings | UiAutomator |
| Model load or delegate failure | GPU to CPU fallback; "Ask teacher" + manual mode; never crash | Unit test, injected failing loader |
| Process killed mid-check-in / enrollment | Session resumes; atomic transaction prevents partial enrollment | `adb shell am kill` E2E test |
| Keystore key invalidated | Templates flagged `REENROLL_REQUIRED`; history intact | `InvalidatedKeyTest` + UI |
| Database write failure (disk full / locked) | Suppress success animation; retry; alert teacher | Fault-injecting mock DAO |
| Phone call / notification in pinned mode | Call UI cannot expose teacher apps; camera resumes cleanly | OEM matrix test |
| Low memory / severe thermal | Degrade gracefully per WP-08T | 45-minute soak test |
| Clock or timezone change | Monotonic time for lockouts; UTC timestamps for records | Unit test |
| Rotation / fold / split-screen | Lock portrait or preserve state | Instrumented test |
| OEM refuses `startLockTask` | Block session start; fail closed (`PrerequisitesChecker`) | OEM matrix test |
| Restore onto a new device | Templates unavailable; guided re-enrollment; history intact | Device transfer integration test |

**Done check:** Complete failure matrix passes; 45-minute soak test with 60 check-ins completes without ANR or crash.

### WP-16 (P1) — Size, dependencies and data volume
**Fixes:** BL-22  
**Files:** `app/build.gradle.kts`, DAOs, entities, icon assets
1. Post-R8 size audit: measure large dependencies. If Gemini + Firebase AI exceed 2 MB, isolate to dynamic feature module.
2. `material-icons-extended`: verify R8 shrinking or copy only used vector drawables. WebP raster images, `localeFilters`, AAB splits.
3. Database indices: unique on `(classId, rollNumber)`, unique on `(sessionId, studentId)`, `FaceTemplate(studentId)`, `AttendanceRecord(studentId)`, `session(classId, date)`. Single-transaction roster imports.
4. Stress test: seed 500 students × 300 sessions (~150k records); verify p95 query latency < 50 ms for calendar and history queries.
**Done check:** Release download size < 20 MB per ABI; seeded database queries pass 50 ms gate.

### WP-17 (P1) — CI/CD and quality gates
**Fixes:** BL-23  
**Files:** `.github/workflows/*.yml`, `gradle.properties`, `gradle/libs.versions.toml`, `detekt.yml`
1. Overhaul `gradle.properties` (see appendix). Add version catalog and dependency verification; pin LiteRT.
2. PR CI workflow: unit tests, Android Lint (`warningsAsErrors = true`), `detekt`, `ktlint`, unsigned `assembleRelease`, schema-diff validation, log-PII grep gate, `gitleaks`.
3. Nightly CI: Gradle Managed Devices executing migration and instrumented suites on API 29, 35, 36, and 16 KB emulator image.
4. Tag CI: signed AAB and `mapping.txt` release artifacts.
5. Golden test gates: `tools/biometric_eval` regression (EER and 0 wrong-IDs on mock class) and OCR golden set from WP-14.
6. Coverage gate: >= 70% on `domain/` and repositories. Architectural rule: UI must not import data layer directly; no test classes in production source sets.
**Done check:** PR with leaked secret, missing migration, or PII log call fails CI.

---

## 6. Phase 3 (Weeks 5–7): Operations, Release Gates & Sequencing

### WP-18 (P1) — Compliance, OEM matrix, pilot and release operations
**Fixes:** BL-25  
**Files:** `docs/*`, `README.md`, Play Console
1. Documentation synchronization: update SDD and README (YOLOv8 architecture, offline claims per WP-06, schema v6, toolchain versions); add `docs/MODELS.md`, `docs/PRIVACY.md`, `docs/RELEASE.md`.
2. Compliance: Privacy Policy URL, accurate Play Data Safety declaration, in-app biometric notice and consent, institutional pilot approval.
3. OEM test matrix: test on Google Pixel, Samsung One UI, Xiaomi HyperOS, Vivo, and Oppo across the SDD §62 list (gestures, notification shade, system unpin, BiometricPrompt, `stopLockTask`, process death).
4. Classroom pilot: 1 teacher with 10 students in Week 1; full classroom (60 students) for 2–3 weeks across >= 5 physical devices (including 3 GB RAM device). Freeze biometric thresholds and database schemas during final week.
5. Release rollout: Play App Signing; Internal testing $\to$ Closed testing (20 testers for 14 days) $\to$ Staged rollout at 5%, 20%, and 100%. Archive `mapping.txt` per release.
**Done check:** Every gate in the Release Gates table passes on the release candidate.

### WP-19 (P2) — Modularisation and cleanup (Post-Launch, Optional)
- Split into `:core:model`, `:core:database`, `:core:security`, `:feature:attendance`, `:feature:workspace`, `:feature:backup`, and `:ml:face`.
- Mechanical split enabled by zero existing import cycles; accelerates build caching and strictly enforces domain boundaries.

---

## 7. Sequencing & Release Gates

### Sequencing Roadmap

```
Phase 0 (Week 1)   : WP-01 to WP-06 (Data safety, release build hardening, licensing, Keystore, PIN pepper, privacy truth)
Phase 1 (Weeks 2-3): WP-07 to WP-10 (Attendance state machine, hot path zero allocations, Thermal Governor, calibration, PAD)
Phase 2 (Weeks 3-5): WP-11 to WP-17 (Repository split, Navigation-Compose, Drive trust, OCR harness, failure matrix, CI)
Phase 3 (Weeks 5-7): WP-18 (OEM matrix, 3-week classroom pilot, Play Console compliance, staged rollout)
Post-Launch        : WP-19 (Modularisation)
```

**Order Constraints:**
- WP-01 must run before any schema modifications (WP-07 introduces `PAUSED` status).
- WP-03 licensing decisions must precede any model swap in WP-08 or WP-10.
- WP-04 and WP-05 must precede WP-13 backup implementation.
- WP-07 must precede WP-11 and WP-12.

### Quantitative Release Gates (Go / No-Go)

| Gate Metric | Target Threshold | Validation Method |
|:---|:---|:---|
| **Crash-free sessions** | $\ge 99.8\%$ | 2-week internal pilot on $\ge 5$ devices (incl. 3 GB RAM phone) |
| **Room schema migration tests** | 100% green | `MigrationTest` covering every schema to current |
| **Plaintext biometric template rows** | 0 rows | Automated database scan test |
| **Release logcat leakage** | 0 app log lines in release session | Logcat grep gate |
| **Camera pipeline allocations** | $< 200 \text{ KB per frame}$ | Android Studio memory profiler |
| **Detect + embed p95 (mid-tier)** | $< 120 \text{ ms}$ | Telemetry benchmark |
| **End-to-end check-in latency (p95)**| $< 2.5 \text{ seconds}$ | Live session trace |
| **Cold start to interactive UI** | $< 800 \text{ ms}$ | AndroidX Macrobenchmark |
| **Memory footprint after 60 check-ins**| $< 300 \text{ MB PSS}$ (no upward trend) | 60-student session memory profile |
| **Continuous session thermal stability**| No ANR or crash; status below SEVERE; minute-40 p95 latency within 1.3× of minute-5 | 45-minute soak test harness (WP-08T) |
| **False Accept Rate per attempt ($N = 100$)**| $\le 0.1\%$ | Proved on $\ge 30,000$ offline impostor pairs (Rule of Three) |
| **False Reject Rate (genuine)** | $\le 5\%$ (1st attempt), $\le 1\%$ (within 2 attempts) | Benchmark evaluation suite |
| **Presentation Attack Errors (APCER / BPCER)**| $\le 5\% \text{ / } \le 5\%$ | Printed photo, screen replay, video replay test suite |
| **Session recovery on interrupt** | Pass | Instrumented test: interrupt $\to$ Calendar CONTINUE $\to$ accurate counts |
| **Backup encryption and restore** | Pass | Validated restore on 2nd device; tampered file rejected |
| **Roster OCR golden set** | Roll exact-match $\ge 95\%$, Name CER $\le 5\%$ | Automated OCR evaluation suite |
| **Automated test suite** | 100% green on API 35, API 36, and 16 KB image | CI nightly run |
| **Lint, detekt, gitleaks** | 0 errors | CI pull request gate |
| **Release AAB footprint** | R8 enabled; no 4 KB `.so`; no secrets; $< 20 \text{ MB}$ per ABI | APK Analyzer |
| **Privacy guarantee** | Full attendance workflow operates in Airplane Mode | Physical device test |
| **OEM lock-task pinning** | Pass SDD §62 criteria across Pixel, Samsung, Xiaomi, and Vivo/Oppo | Physical OEM test suite |

---

## 8. Appendix — Production Configuration Snippets

### `gradle.properties` (WP-17)
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

### `app/build.gradle.kts` (WP-02)
```kotlin
android {
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        noCompress += "tflite" // Enables zero-copy memory mapping
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        warningsAsErrors = true
        checkReleaseBuilds = true
    }
}
```

### `AndroidManifest.xml` & `res/xml/data_extraction_rules.xml` (WP-02)
```xml
<!-- AndroidManifest.xml -->
<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:usesCleartextTraffic="false">
</application>
```

```xml
<!-- res/xml/data_extraction_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" />
        <exclude domain="sharedpref" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" />
        <exclude domain="sharedpref" />
    </device-transfer>
</data-extraction-rules>
```

### CameraX Zero-Allocation RGBA_8888 Configuration (WP-08)
```kotlin
val analysis = ImageAnalysis.Builder()
    .setResolutionSelector(
        ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .build()
    )
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
    .setOutputImageRotationEnabled(true)
    .build()

analysis.setAnalyzer(inferenceExecutor) { proxy ->
    try {
        pipeline.process(proxy)
    } finally {
        proxy.close()
    }
}
```

### PIN Lockout Policy with Reboot Tracking (WP-05)
```kotlin
fun lockDelayMs(failures: Int): Long = when {
    failures < 5 -> 0L
    failures < 8 -> 30_000L      // 30 seconds
    failures < 10 -> 300_000L    // 5 minutes
    else -> 3_600_000L           // 1 hour
}

// Persist failures, lockedUntilElapsed (SystemClock.elapsedRealtime), and Settings.Global.BOOT_COUNT.
// If BOOT_COUNT changed while a lockout was active, re-apply the full delay.
```

### PIN Hashing with Hardware Keystore Pepper (WP-05)
```kotlin
// pepperKey: non-exportable HMAC key in Android Keystore (KeyProperties.PURPOSE_SIGN)
val derived = pbkdf2(pin, salt, iterations) // iterations calibrated to ~300 ms on low-end device
val storedVerifier = hmacSha256(pepperKey, derived) // database copy is useless off-device
```

### Room Builder without Destructive Fallback (WP-01)
```kotlin
// Before build(): if DB user_version < current version, checkpoint WAL and copy to files/backups/attract-pre-vN.db
Room.databaseBuilder(context, AttractDatabase::class.java, "attract.db")
    .addMigrations(*ALL_MIGRATIONS) // Missing migration fails tests, never wipes data
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .build()
```

### Room Migration Test Helper (WP-01)
```kotlin
@get:Rule val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(), 
    AttractDatabase::class.java
)

@Test fun migrateEveryVersionToLatest() {
    for (from in 1 until CURRENT_VERSION) {
        helper.createDatabase(DB_NAME, from).apply { 
            seedAttendance(this)
            close() 
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, CURRENT_VERSION, true, *ALL_MIGRATIONS)
        assertEquals(SEEDED_RECORDS, db.count("attendance_records"))
    }
}
```

### R8 Proguard Log Stripping (WP-02)
```proguard
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
```

### `.github/workflows/ci.yml` (WP-17)
```yaml
name: Continuous Integration

on:
  push:
    branches: [main, dev]
  pull_request:
    branches: [main, dev]

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run Unit Tests & Lint
        run: ./gradlew testDebugUnitTest lintDebug detekt

      - name: Scan for Leaked Secrets
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```
