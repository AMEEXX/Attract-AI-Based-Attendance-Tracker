# LLD-06 â€” Live Attendance Session Engine

**Status:** Draft â€” ready for review and freeze  
**Requirements source:** SDD Â§Â§6â€“8, 13â€“15, 28â€“40, 58â€“59  
**Depends on:** LLD-01, LLD-02, LLD-05, LLD-13

## Goal

Coordinate one pinned face-attendance session as a deterministic state machine. This component receives user, camera, face, persistence, and authentication events. It owns valid transitions and UI state; it does not run SQL, TFLite, or cryptography directly.

## State machine

```text
Initializing â†’ Ready â†’ Acquiring â†’ QualityChecking â†’ LivenessChecking â†’ Recognizing
       â†‘          â†‘          â†“             â†“                 â†“              â†“
       â””â”€â”€ Error/Retry â† Feedback â† retryable result â† retryable result â† accepted/unknown
Recognizing â†’ PersistingPresent â†’ SuccessFeedback â†’ Ready
Recognizing â†’ EnrollmentRequest | TeacherAssistance | Ready
Any active state â†’ Ending â†’ Ended
```

Each state carries `sessionId`, a monotonic `attemptId`, and render-safe message IDs. Increment attempt ID on Check In, cancellation, or exit from acquisition. Any callback with a non-current attempt ID is discarded. This stops a late inference from marking the next student's attendance.

## Events and serialization

Use a `SessionCoordinator` with one `Channel<SessionEvent>`, one reducer coroutine, `StateFlow<AttendanceUiState>`, and one-off `SharedFlow<AttendanceEffect>`. Events: StartReady, CheckInPressed, FrameCandidate, QualityResult, LivenessResult, RecognitionResult, EnrollmentRequested, TeacherAssistRequested, PersistResult, EndRequested, AuthResult, Cancel, and DependencyError.

Heavy child jobs belong to their attempt and are cancelled before state exit. A `SupervisorJob` converts analysis exceptions to DependencyError instead of crashing the session. Dependencies return events to the channel; no callback mutates UI state directly.

## Start, decision, and end rules

Start: validate prerequisites â†’ create ACTIVE session â†’ replace root graph â†’ acquire pinning â†’ load/decrypt class cache â†’ bind camera â†’ Ready. Do not expose Ready until dependencies are usable.

Map face outcomes:
- `ACCEPTED` identity â†’ LLD-05 `RecordPresent` (`AttendanceSource.AI_RECOGNITION`);
- `ALREADY_PRESENT` â†’ show feedback pill, maintain present set;
- Quality/no/multiple face â†’ short guidance alert, return to Ready/Capturing;
- `UNKNOWN` / `AMBIGUOUS` â†’ bounded retry, then route to `EnrollmentRequest` / `UNKNOWN_STUDENT` with "Select ID to Enroll" action;
- Face-engine unavailable / error â†’ safe `Error` state, teacher assist/manual fallback.

End / Discard:
- On Save & Exit: authenticate PIN â†’ finalize ABSENT for un-marked eligible students â†’ mark `ENDED` â†’ clear memory cache â†’ stop lock task.
- On Discard & Exit: authenticate PIN â†’ mark `ABORTED` (0 attendance records written) â†’ clear memory cache â†’ stop lock task.
- On Cancel: dismiss dialog, maintain active session in camera mode without calling `stopLockTask()`.

## Build order

1. Write sealed states/events and exhaustive reducer tests with fake dependencies.
2. Add attempt token/cancellation tests.
3. Connect start/end/persistence/navigation.
4. Attach LLD-08â€“12 adapters as event sources only.

## Tests

| Scenario | Expected result |
|---|---|
| every legal transition | exact next state/effect |
| illegal event in a state | ignored or safe error; no write |
| stale Quality/Liveness/Recognition result | discarded |
| two normal attempts exhausted | enrollment/assist path, never infinite loop |
| accepted write fails | no success feedback |
| duplicate PRESENT | Already Checked In feedback, one record |
| end auth fails/cancels | state returns safely; session remains ACTIVE |
| finalization fails | remains pinned/Ending with retry |
| cancellation/configuration change | jobs released; no duplicate write |
| process death | LLD-07 recovery path |

## Definition of done

Exactly one reducer and one active attempt exist. No callback can outlive its attempt, persistence precedes success UI, and every state has an explicit recovery path.

## LLD-16 amendment (2026-08-25): adaptive multi-frame capture

The session states and events are UNCHANGED. The adaptive 1→2→3 frame engine (see [LLD-16](16-lld-adaptive-multiframe-verification.md)) operates inside the Acquiring→QualityChecking→Recognizing window of a single attempt: NeedMoreFrames keeps the attempt alive in the capturing state, Final(Match) emits RecognitionEvaluated with the matched student, Final(Ambiguous|Unknown) emits RecognitionEvaluated(null) which triggers the existing 2-attempt retry/teacher-assistance path, and Final(Error) maps onto the recoverable Error state. Attempt-id staleness protection is unaffected.


## Fallback attendance amendment (2026-08-25): teacher-selected marking after biometric UNKNOWN

**Production incident:** after an unrecognized face, selecting a student from the roster
dead-locked the screen (isValidatingFrame capture lock was never released by the adaptive
terminal outcomes) and no attendance record was ever created — the old path performed an
ENROLLMENT with the unrecognized face instead of marking attendance.

**Contract now implemented:**

`
UNKNOWN (biometric) -> roster of ALL students not yet PRESENT this session
   -> teacher taps student (stable database id only)
   -> AttractRepository.markFallbackPresent(classId, studentId)
      -> active FACE-session check -> RecordPresentCommand.execute(source = MANUAL)
      -> exactly one PRESENT record / AlreadyPresent (idempotent)
   -> MATCH_SUCCESS ("PRESENT: name") | ALREADY_PRESENT | recoverable ERROR
   -> reset to READY for the next capture.
`

Rules: source is always AttendanceSource.MANUAL with metadata="teacher_fallback_after_unknown"
(never mislabeled as AI recognition); identity travels as the stable studentId through every
hop; eligibility enforced by the production command (exists, not archived, same class,
eligibleFromSessionId gate, ACTIVE session); failures surface a recoverable ERROR — never a
silent freeze. Debug tag ATTRACT_ATTENDANCE_FALLBACK logs state transitions without any
biometric data. Regression coverage: FallbackAttendanceAndroidTest (scenarios A–E).

## Persistence amendment (2026-08-26): immediate writes + reconciling end-save

Recognition marks are persisted IMMEDIATELY (markRecognizedPresent -> RecordPresentCommand,
source=AI_RECOGNITION) exactly as the manual fallback does (source=MANUAL). The session-end
saveFaceAttendance is now a pure RECONCILIATION pass: it inserts ONLY students that still
have no record (mostly ABSENT) and finishes the session. Existing rows keep their original
source. This removes the previous design where Room writes happened solely at session end
from an in-memory set — which crashed with the unique (session_id, student_id) index whenever
a teacher-fallback mark had already been made. Verified by FullWorkflowAcceptanceTest
(mixed AI+MANUAL session saves cleanly with correct per-record sources).

## SESSION LIFECYCLE FIX (2026-08-26): canonical session entry point

**Production incident:** the attendance screen NEVER created a session. startFaceSession
had zero UI callers; navigation (openFaceAttendance) only changed screens. Persistence
correctly refused every mark with NoActiveSession -> UNKNOWN -> select -> error -> reset ->
infinite loop. Old code masked this because saveFaceAttendance auto-created the session at
END time; immediate persistence exposed it.

**Fix:** AttractRepository.ensureFaceSession(classId) is THE canonical entry point:
resolves the ACTIVE FACE session for the class (reusing it) or creates one; refuses only if
a DIFFERENT class owns the global active slot. Called by AttendanceScreen during startup.
The attendance_sessions DB row is the single source of truth — there is no in-memory
session flag; recognition and fallback resolve the same row inside their transactions.

Pipeline logging tag ATTRACT_ATTENDANCE_PIPELINE prints sessionId/classId/studentId at
recognition-match and roster-selection. Regression: FullWorkflowAcceptanceTest PHASE 2 now
uses ensureFaceSession and asserts fallback-before-ensure = NoActiveSession (the phone bug).
