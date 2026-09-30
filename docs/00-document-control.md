# 00 â€” Document Control and Waterfall Delivery Plan

**Project:** Attract â€” Face-Based Attendance Tracker  
**Status:** Active  
**Phase:** Low-Level Design  
**Effective date:** 2026-08-06

## Purpose

This document controls how the frozen HLD is converted into implementation-ready LLDs. It keeps the MVP focused on a teacher operating one Android phone offline, rather than expanding into a school platform or adding speculative features.

## Document hierarchy

1. **00 Main SDD** is the canonical product and high-level architecture baseline.
2. **00 planning documents** record constraints, unresolved decisions, risks, and technical acceptance gates.
3. **LLD-01 through LLD-14** define exact implementation contracts.
4. Test specifications and implementation must trace back to an approved LLD.

If an LLD exposes a contradiction, a measured technical result invalidates an assumption, or a product requirement changes, the change must be recorded before dependent documents or code are altered.

## Stage-gated waterfall process

| Gate | Deliverable | Exit condition |
|---|---|---|
| G0 | HLD baseline | SDD v3.1 stored and accepted as the source of truth |
| G1 | Design readiness | Critical decision gaps assigned; risks have testable gates |
| G2 | LLD design freeze | LLD-01 through LLD-14 individually approved in dependency order |
| G3 | Technical validation | Screen-pinning and face-pipeline spikes meet their documented acceptance criteria, or a controlled design change is approved |
| G4 | Implementation | Each LLD is implemented, unit-tested, integration-tested, and reviewed before its dependent unit starts |
| G5 | System validation | End-to-end, security, OEM, thermal, and classroom-pilot acceptance evidence complete |
| G6 | Release candidate | No open critical defect; privacy and operational readiness accepted |

No feature is added merely because it is technically interesting. Features explicitly excluded by the SDD remain excluded unless a change request approves them.

## LLD lifecycle

`Planned â†’ Drafting â†’ Technical review â†’ Approved / Frozen â†’ Implemented â†’ Verified`

Only one LLD is drafted at a time in the listed approval order. A later LLD may be opened for reference, but no decision in it becomes binding before dependencies are frozen.

## Change-control record

- **SDD v4.0 Sync & Implementation (2026-08-10)**:
  - Synchronized and implemented Phase 1 (Foundation Reset / AttendanceSource), Phase 2 (Calendar & Roster UI Polish), and Phase 3 (CameraX Selfie Preview, 3-Pose quality capture guidance, Screen Pinning adapter, and Teacher PIN exit dialog).
  - Status of LLD-08 and LLD-13 updated to: **Implemented & Verified**.
  - All 153 unit & functional test suites verified with 100% pass rate.


- **LLD-16 Adaptive Verification (2026-08-25)**:
  - Added LLD-16: adaptive 1→2→3 frame biometric verification (FrameObservation, EvidenceFusion, AdaptiveVerificationEngine). Mandatory STRAIGHT→LEFT→RIGHT flow replaced by adaptive capture; pose now supporting evidence only.
  - Production thresholds UNCHANGED (accept=0.45 / margin=0.10 / calibrationDefaults). Fusion strategy switch to averageIdentityScore recorded as pending decision D-16a with empirical LFW benchmark evidence (100 identities / 1000 images).
  - New tests: AdaptiveVerificationEngineTest (JVM matrix 1–17), AdaptiveStrategyBenchmarkTest + LfwThreeFrameBenchmarkTest (instrumented). All existing suites retained.
- **Template migration fix (2026-08-25)**:
  - ROOT CAUSE (physical phone): legacy 32-D template vs current 192-D model crashed TemplateMatcher ("192 vs 32"). Fixed via TemplateCompatibility gate, Room v2 embedding_dim column + migration, repository retirement sweep, defense-in-depth guards in RecognitionDecisionEngine/AdaptiveVerificationEngine, enrollment validation. Stale students are routed to re-enrollment. TFLite upgraded 2.14.0 -> 2.16.1 (16KB page-size support for modern devices) plus diagnostic error surfacing on debug builds.
  - Tests: TemplateCompatibilityTest (JVM), TemplateMigrationAndroidTest (instrumented), legacy crash-behavior test updated to fail-closed contract. 207 JVM / 34 instrumented green; verified end-to-end on Android 16 (API 36) emulator.
- **Fallback attendance fix (2026-08-25)**:
  - ROOT CAUSE: adaptive terminal outcomes never released the isValidatingFrame capture lock (UI dead-lock after biometric UNKNOWN), and the roster-selection path enrolled instead of marking attendance. Fixed: capture lock released on every terminal outcome; new production repository method markFallbackPresent reuses RecordPresentCommand with AttendanceSource.MANUAL; roster now lists ALL not-yet-present students (not only NOT_ENROLLED); selection marks PRESENT immediately via stable studentId; AlreadyPresent/eligibility/DB-failure states surfaced; ATTRACT_ATTENDANCE_FALLBACK debug logging added.
  - Tests: FallbackAttendanceAndroidTest (primary flow, already-checked-in, ineligible, no-session, DB-failure, select-A-then-B). 40/40 instrumented + 207/0 JVM green. Docs: LLD-06 amendment.
- **Full functional acceptance (2026-08-26)**:
  - Added FullWorkflowAcceptanceTest: clean-DB end-to-end run through production classes only (createClass/addStudent/startFaceSession/enrollStudentFace with real ML Kit+TFLite 192-D pipeline, AdaptiveVerificationEngine wired exactly like the UI, markRecognizedPresent/markFallbackPresent, reconciling saveFaceAttendance, retireIncompatibleTemplates). Phases: clean env, class+students, session ACTIVE with 0 records, 192-D enrollment, recognized attendance (1 frame), duplicate->AlreadyPresent, real UNKNOWN (no record, roster excludes present students), fallback MANUAL write, fallback duplicate protection, no-session rejection + new-session recovery, ineligible/cross-class rejection, session isolation, class isolation, 32-D retirement + re-enrollment, adaptive terminal-lock contract. ALL PASS on API-35; critical paths re-verified on Android 16 (API 36).
  - ARCHITECTURE FIX: recognition now persists IMMEDIATELY via RecordPresentCommand (source=AI_RECOGNITION) per LLD-06 PersistingPresent — previously records were only written at session end from in-memory presentIds. saveFaceAttendance converted to a RECONCILING end-save (fills only missing rows, preserves original sources) eliminating a guaranteed SQLiteConstraintException crash when manual fallback was mixed with recognition in one session.
  - Totals: 41/41 instrumented, 207/0 JVM.
- **Session lifecycle fix (2026-08-26)**:
  - ROOT CAUSE (phone loop bug): no production code path ever created the attendance session; persistence correctly returned NoActiveSession on every mark. Fixed via canonical ensureFaceSession entry invoked at AttendanceScreen start; DB row = single source of truth; ATTRACT_ATTENDANCE_PIPELINE id-chain logging added; acceptance regression asserts fallback-refused-before-ensure and same-session reuse. 41/41 instrumented + 207/0 JVM + Android 16 re-run green.
- **REAL-UI acceptance (2026-08-26)**:
  - Added RealUiFlowAcceptanceTest: drives the REAL MainActivity/navigation into AttendanceScreen with UiAutomator taps. Proves through the actual UI: screen entry creates the session ([START_SESSION] sessionId/classId in Room BEFORE first capture), capture taps feed the real CameraX->MLKit pipeline, session survives activity recreation, ended-session + re-entry creates a NEW session id via the UI with full isolation, zero stray attendance rows.
  - Emulator limits documented as YELLOW verdicts inside the test: virtual-scene camera frames cannot pass the production quality gate and PIN-dialog/camera automation is unstable post-recreate; those legs are covered end-to-end by FullWorkflowAcceptanceTest. Real-phone run remains the final manual gate.
- **Recognition-path investigation (2026-08-26)**:
  - PHASE 1 instrumentation added: ATTRACT_RECOGNITION logs per-frame usability/rejection reason, embedding dim/norm, gallery size, incompatible-filter count, per-template top1/top2/margin, threshold comparison, final decision (JVM-safe).
  - RecognitionPathDiagnosticTest (emulator, real JPEGs -> real pipeline): clean DB, production enrollment for AMIT/B/C, Room table verified (1 template each, 192-D, mobilefacenet_192d_v2), gallery class-scoping verified, SAME-image cosine=1.0, DIFFERENT-image genuine top1 correct with sims 0.53–0.77 and margins 0.48–0.61, impostor sims 0.07–0.08, adaptive Match framesUsed=1 -> markRecognizedPresent -> AI_RECOGNITION row + AlreadyPresent duplicate guard. ALL MATRIX ROWS PASS on API-35 AND Android 16.
  - ROOT CAUSE CLASSIFICATION (phone symptom): Category K/A — biometric path objectively sound; phone behavior matches ZERO persisted templates at recognition time (pre-fix DB pull: 0 templates/0 sessions), i.e., enrollment never completed/stored. Post-enrollment template-count log added to make any recurrence instantly visible. Fallback/session logic untouched; thresholds unchanged.
