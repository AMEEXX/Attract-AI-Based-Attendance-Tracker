# 00 — Architecture Risks, Bottlenecks, and Decision Gates

**Status:** Active — must be reviewed before LLD-02 and LLD-10 are frozen.

## Core conclusion

“Most accurate” face recognition and “best” anti-spoofing are not properties we can safely declare from a model name or published benchmark. For Attract, they mean the solution that meets documented false-accept, false-reject, liveness, latency, thermal, and fairness evidence on the actual pilot devices and student population. A false accept is more serious than a false reject because it records attendance for the wrong person.

## Critical architecture decisions

| ID | Decision gap | Why it matters | Proposed minimal direction | Blocks |
|---|---|---|---|---|
| ADR-001 | **First-time identity proof** | Selecting an unenrolled roster name plus a duplicate-face check cannot prove that a new face belongs to that name. A student could claim an absent, not-yet-enrolled identity. | Require teacher-supervised approval for first-time identity claims. The exact low-friction interaction belongs to LLD-10; a roster list alone is not sufficient proof. | LLD-10 |
| ADR-002 | **Template scope** | `FaceTemplate → Student → ClassSection` makes templates class-scoped, while the SDD describes a duplicate check against every enrolled student. A global check would block the same real person from enrolling in a second class unless a global person identity is introduced. | Preserve the minimal MVP schema: templates and duplicate checks are **within the selected class only**. A shared cross-class biometric profile is explicitly out of MVP. | LLD-02, LLD-10, LLD-11 |
| ADR-003 | **One active session rule** | Multiple concurrent ACTIVE sessions on one phone create ambiguous recovery, camera ownership, and template-cache state. | Permit at most one ACTIVE face-attendance session per device/database. Enforce in Room and the session coordinator. | LLD-02, LLD-06, LLD-07 |
| ADR-004 | **Screen-pinning assurance boundary** | Android Screen Pinning is not Device Owner kiosk mode and varies by OEM. It protects normal student hand-off but must not be represented as a stronger managed-device security control. | Validate on a defined supported-device matrix; block session start if the required secure-unpin configuration cannot be verified or the device is outside the approved policy. | LLD-01, LLD-13 |

ADR-001 and ADR-002 are the two HLD contradictions that need explicit approval before data and enrollment design are finalized. They are not new features; they make the existing “prevent identity misuse” and multi-class data model coherent.

## Risk register

| Risk | Impact | Required control / evidence | Governing LLD |
|---|---|---|---|
| Monocular RGB spoof detection is bypassed by print, display, or replay attacks | False attendance, loss of trust | Passive model feasibility plus active challenge fallback; report APCER/BPCER and attack outcomes. Never claim hardware depth-sensing security. | 12 |
| Recognition threshold is calibrated only on public benchmarks | Biased errors or false accepts in real classrooms | Consent-based pilot evaluation across lighting, pose, glasses, appearance variation, and relevant population groups; calibrate threshold and best-vs-second margin. | 11 |
| Heavy inference runs on too many frames | Slow check-in, heat, battery drain, camera jank | Single-flight frame processing, quality gating, frame sampling, p95 latency and long-session thermal tests. | 08–12 |
| First-day enrollment is slow | Queue and poor teacher experience | Automatic collection of a small, quality-diverse set of templates; bounded retry and clear student guidance; teacher-assisted fallback. | 06, 09, 10 |
| Camera/ML callbacks race with session exit | Writes after end, wrong UI state, crashes | One serialized session reducer, cancellation rules, idempotent persistence commands, lifecycle tests. | 06–08 |
| Process death during attendance | Lost or inconsistent records | Immediate PRESENT writes, transactional enrollment, deterministic ACTIVE-session recovery and finalization. | 02, 05, 07 |
| Template encryption is incomplete | Biometric exposure | Versioned AEAD envelope, Android Keystore key policy, no plaintext database/cache/logs, memory clearing. | 02, 13 |
| Roster selection exposes student data | Unnecessary privacy leakage | Show only the minimum needed for approved enrollment flow; do not expose teacher-world data in student mode. | 01, 10, 13 |
| OEM pinning behavior differs | Student reaches teacher or device UI | Representative-device matrix, onboarding checks, explicit support policy, recovery behavior. | 13 |
| Import data is malformed or duplicate | Corrupt roster and bad attendance statistics | Column mapping, validation preview, transactional import, duplicate report, formula-safe export. | 04 |
| Eligibility and corrections are ambiguous | Incorrect percentages and statistics | Deterministic eligibility boundary, session finalization rules, recalculation and deletion tests. | 05, 14 |

## Technical validation gates

### Gate A — Screen pinning

The prototype must demonstrate, on each supported OEM and navigation mode:

- expected `startLockTask()` / `stopLockTask()` behavior;
- configured authentication barrier for unpinning;
- inaccessible teacher navigation while the session is active;
- safe behavior for Home, Back, Recents, notification shade, interruption, and process restart; and
- a tested teacher-authenticated end-session path.

A failed device is removed from the supported-device release matrix or requires a controlled design change. Attract will not pretend that a non-validated device is secure.

### Gate B — Face pipeline

The prototype must include CameraX input, detection/tracking, face crop/alignment and preprocessing, quality gates, liveness, embedding inference, class-scoped multi-template matching, and the decision engine.

It must collect evidence for:

- genuine and impostor recognition pairs: FAR, FRR, failure-to-enroll, and failure-to-acquire;
- best-score and best-vs-second-score distributions;
- print, phone-display, tablet-display, and practical replay attacks: APCER/BPCER or equivalent documented liveness metrics;
- p50/p95 check-in latency against the SDD’s normal 2–3 second target;
- CPU, memory, battery/thermal behavior, and long-session stability;
- device/model compatibility; and
- population and lighting variation, without retaining raw biometric images beyond the approved test process.

The chosen model, preprocessing, thresholds, liveness thresholds, number of enrollment templates, and inference cadence are frozen only from this evidence. If passive liveness does not meet the gate, the active challenge becomes the MVP path rather than silently lowering security.

## Non-negotiable safety and privacy constraints

- Raw face photos are processed transiently and never stored as product data.
- Face embeddings are encrypted at rest and decrypted only for a current, active class session.
- Recognition metadata must be minimized, redacted from logs, and excluded from student UI.
- All biometric consent, retention/deletion, and institutional requirements must be defined before a real classroom pilot.
- Manual teacher attendance is the recoverable fallback; it is not an excuse to persist insecure data.

