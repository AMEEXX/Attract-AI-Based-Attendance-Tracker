# LLD-05 — Attendance Domain Engine

**Status:** Draft — ready for review and freeze  
**Requirements source:** SDD §§30–32, 36–47, 55–56  
**Depends on:** LLD-02, LLD-03

## Goal

Define the rules that make attendance correct even when it is entered by face recognition, enrollment, teacher assistance, or manual editing. This package is pure Kotlin wherever possible; UI and camera do not decide eligibility or percentages.

## Core types and invariants

```kotlin
enum class AttendanceStatus { PRESENT, ABSENT }
enum class SessionMode { FACE, MANUAL }
enum class SessionStatus { ACTIVE, ENDED, ABORTED }
data class Eligibility(studentId: Long, sessionId: Long, isEligible: Boolean)
```

Rules:

1. A class held equals one ENDED session. ACTIVE and ABORTED sessions do not count.
2. A student has at most one record per session.
3. A student is eligible when the session belongs to their class, the student was not archived when the session was created, and `eligibleFromSessionId <= sessionId` (or the boundary is null for an original roster student).
4. PRESENT is inserted immediately after a successful accepted method.
5. ABSENT is not stored during an ACTIVE face session. It is created only by finalization.
6. Percent = eligible PRESENT records / eligible ENDED sessions × 100. If denominator is zero, expose “No classes held” rather than 0%.
7. Corrections on an ENDED session directly replace status and update timestamp. No audit/reason feature is added in MVP.
8. Deleting an accidental ENDED session deletes its records in one transaction and statistics recalculate from remaining data. ACTIVE sessions cannot be deleted; they must be recovered/ended.

Archive guards in LLD-03 forbid archiving while a class has an ACTIVE session. This makes eligibility deterministic without creating an unnecessary session-membership snapshot table.

## Use cases

| Use case | Input | Output |
|---|---|---|
| `CreateManualSession` | class, local date/time | draft session ID or validation error |
| `SaveManualAttendance` | session + selected student IDs | ENDED session with complete records |
| `RecordPresent` | active session, student, method, optional safe metadata | Recorded / AlreadyPresent / NotEligible / NotActive |
| `FinalizeFaceSession` | active session | absent count + ended session |
| `CorrectRecord` | ended session, student, new status | updated record |
| `DeleteEndedSession` | ended session | deleted / blocked |
| `GetStudentStatistics` | class/student | immutable statistics view |

Manual save validates all selected IDs belong to the class, inserts PRESENT for selected eligible students and ABSENT for all other eligible students, then marks the session ENDED in one transaction.

## Calculation algorithm

For a student, query ENDED sessions in their class where session ID is on/after the eligibility boundary and session contains a record for that student. The denominator is the count of those eligible sessions; numerator is PRESENT count. A missing record in an ENDED session is a persistence invariant violation and must be reported, not silently assumed ABSENT.

All rounding happens at display time: retain a precise ratio/domain `BigDecimal` or numerator/denominator; render percentage to one decimal place.

## Errors and recovery

`AlreadyPresent` renders the existing check-in time, not a generic failure. `NotEligible`, `WrongClass`, `SessionNotActive`, and `IncompleteFinalization` are distinct technical errors. Database failure means no success feedback. Teacher manual attendance remains the approved fallback for a face-engine failure.

## Build steps

1. Implement pure eligibility and percentage functions with parameterized JVM tests.
2. Add repository queries that return only immutable domain projections.
3. Implement transactional commands in order: RecordPresent, Finalize, Manual Save, Correct, Delete.
4. Build read models for calendar/history/statistics; LLD-14 supplies their screens/export.
5. Test every command twice to prove retry behavior.

## Tests

| Level | Scenario | Expected result |
|---|---|---|
| Unit | no eligible ended sessions | “No classes held”; no divide by zero |
| Unit | late joiner after session 10 | sessions 1–10 excluded |
| Unit | two sessions same date | each counts separately |
| Transaction | record present retry | one PRESENT and AlreadyPresent result |
| Transaction | finalization | absent records only for eligible students without PRESENT |
| Transaction | manual save | complete PRESENT/ABSENT set and ENDED status |
| Unit/repository | correction PRESENT↔ABSENT | percentage changes correctly |
| Transaction | delete ended session | records/session removed; aggregate recomputes |
| Repository | missing record in ENDED session | invariant error/repair signal, never silent math |
| Property test | random roster/session sequences | no duplicate record and numerator ≤ denominator |

## Definition of done

All attendance results are deterministic, transactionally persisted, retry-safe, and independent of the UI or face implementation.

