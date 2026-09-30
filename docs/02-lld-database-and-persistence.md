# LLD-02 — Database & Persistence

**Status:** Draft — ready for review and freeze  
**Approval order:** 2 of 14  
**Requirements source:** SDD §§11–12, 17–20, 30–32, 36–56, 64  
**Depends on:** LLD-01; Decisions D-002 and D-003

## 1. Goal and non-goal

Room is Attract’s offline source of truth. This LLD defines durable facts: classes, roster records, encrypted face templates, sessions, and attendance records. It guarantees that a crash cannot create a half-enrolled student or lose an already accepted check-in.

It does not calculate attendance percentages, parse files, open the camera, or make recognition decisions. Those layers use the repository contracts defined here.

## 2. Storage rules

- Use one Room database, `AttractDatabase`, with explicit migrations. Never use destructive migration in a release build.
- IDs are locally generated `Long` primary keys. Names and roll numbers are display data, never keys.
- Store machine time as epoch milliseconds/`Instant`; store a session reporting date as ISO `YYYY-MM-DD` plus IANA timezone ID captured when the session starts.
- Store enum values as stable strings. Unknown values must fail migration/validation safely rather than silently mapping to a wrong state.
- Foreign keys use `RESTRICT` by default. Archiving is used instead of deleting class/student history.
- Face embeddings are never represented by a Room type converter. The only persisted value is an encrypted byte envelope.

## 3. Schema

### Teacher

```text
teachers(id PK, display_name, pin_hash, created_at, updated_at)
```

MVP supports one teacher row. `pin_hash` is handled by LLD-13; no screen or log reads it.

### ClassSection

```text
class_sections(
  id PK, teacher_id FK, name, subject, section, semester_batch,
  required_attendance_percent, archived, created_at, updated_at
)
```

`required_attendance_percent` is constrained to 0–100. A class name may repeat; IDs distinguish classes.

### Student

```text
students(
  id PK, class_id FK, name, roll_number, serial_number NULL,
  enrollment_status, enrolled_at NULL, eligible_from_session_id NULL,
  archived, created_at, updated_at
)
```

Required invariant: `UNIQUE(class_id, roll_number)`. Enrollment status is `NOT_ENROLLED` or `ENROLLED`. An ENROLLED student must have one or more active templates; this cross-table invariant is only created in the enrollment transaction, never trusted merely because a row says ENROLLED.

### FaceTemplate

```text
face_templates(
  id PK, student_id FK,
  encrypted_embedding BLOB, crypto_version, model_version,
  quality_score, captured_at, source, active
)
```

`source` is `ENROLLMENT`, `REENROLLMENT`, or reserved `APPENDED`. MVP rejects APPENDED writes. `active` permits atomic re-enrollment replacement without a window where the student has no usable profile; old rows are deleted only after the replacement transaction succeeds, then may be physically deleted in the same transaction.

Templates are **class-scoped** through their student. The duplicate query and recognition cache never cross class boundaries.

### AttendanceSession

```text
attendance_sessions(
  id PK, class_id FK, session_date, time_zone_id,
  started_at, ended_at NULL, status, mode,
  created_at, updated_at
)
```

`status` is `ACTIVE`, `ENDED`, or `ABORTED`; `mode` is `FACE` or `MANUAL`. Add this partial unique index:

```sql
CREATE UNIQUE INDEX index_one_active_face_session
ON attendance_sessions(status)
WHERE status = 'ACTIVE' AND mode = 'FACE';
```

This enforces Decision D-003 even if two UI commands race.

### AttendanceRecord

```text
attendance_records(
  id PK, session_id FK, student_id FK, status, check_in_time NULL,
  attendance_method, match_confidence NULL, recognition_metadata NULL,
  created_at, updated_at
)
```

Constraint: `UNIQUE(session_id, student_id)`. `status` is PRESENT or ABSENT. `attendance_method` is FACE, ENROLLMENT, MANUAL, or TEACHER_ASSISTED. `recognition_metadata` may store only a version/config identifier and reason category, never embeddings, crops, model inputs, or full score vectors.

### Required indexes

```text
students(class_id, archived, enrollment_status)
face_templates(student_id, active)
attendance_sessions(class_id, session_date, status)
attendance_records(session_id, student_id) UNIQUE
attendance_records(student_id, session_id)
```

## 4. DAO and repository contracts

DAOs expose database-shaped operations only. Repositories expose domain-shaped operations and run transactions.

```kotlin
interface SessionRepository {
  suspend fun createFaceSession(classId: Long, now: Instant, localDate: LocalDate, zoneId: ZoneId): CreateSessionResult
  suspend fun findActiveFaceSession(): ActiveSession?
  suspend fun markSessionEnded(sessionId: Long, endedAt: Instant): Result<Unit>
}

interface FaceTemplateRepository {
  suspend fun loadActiveTemplatesForClass(classId: Long): List<EncryptedTemplateRow>
}

interface AttendanceRepository {
  suspend fun recordPresentIfAbsent(command: PresentCommand): PresentWriteResult
  suspend fun finalizeSession(command: FinalizeSessionCommand): FinalizeResult
}
```

Repository methods validate IDs and translate SQLite constraint violations into explicit results such as `DuplicateAttendance`, `AnotherSessionActive`, `StudentNotEligible`, or `SessionNotActive`. UI code never receives a raw `SQLiteConstraintException`.

## 5. Mandatory transactions

### Create face session

In one transaction: verify class exists and is not archived → verify no global ACTIVE FACE session → insert ACTIVE FACE session. A partial unique-index failure maps to `AnotherSessionActive`.

### Record recognized PRESENT

In one transaction: verify session is ACTIVE and belongs to the student’s class → verify student is eligible and not archived → insert the one PRESENT record. The unique constraint maps a race/retry to `AlreadyPresent`. A success returns only after commit.

### Enroll + PRESENT

Implemented by LLD-10 but owned here as a transaction: re-read student as NOT_ENROLLED → verify no active template conflict in the same class → insert encrypted templates → set ENROLLED/enrolledAt → insert PRESENT record → commit. Any failure rolls back all steps.

### Finalize session

In one transaction: re-read ACTIVE session → select each eligible, non-archived student in the class with no record → insert ABSENT rows → set ENDED and endedAt. Retrying after a successful commit returns `AlreadyFinalized`, not a second set of rows.

## 6. Migrations, corruption, and lifecycle

- Every schema change has a numbered Room migration, migration test, and an export of the schema JSON.
- Adding a non-null column requires a safe default or a data backfill inside migration.
- Removing/renaming data requires an explicit preservation or retention decision; never rely on destructive migration.
- Startup database corruption is an explicit blocking error. Do not wipe a teacher’s attendance database automatically.
- Repositories return a `StorageUnavailable` error with Retry/Support guidance. Face check-in cannot display success unless the PRESENT transaction committed.

## 7. Implementation sequence

1. Define enums/value objects and Room type converters for time only.
2. Build entities and foreign keys; export schema and add indexes.
3. Create DAOs with observable read queries and narrow write methods.
4. Write migration tests before adding real migrations.
5. Implement repository result mapping and transaction runners.
6. Implement active-template retrieval that returns encrypted rows; LLD-13 decrypts only for the active session.
7. Implement the four mandatory transactions and concurrency tests.

## 8. Tests

| Level | Named test / scenario | Expected result |
|---|---|---|
| DAO | `insertStudent_sameClassAndRoll_rejectsDuplicate` | Unique constraint is enforced. |
| DAO | `insertStudent_sameRollDifferentClasses_isAllowed` | Class-scoped rosters work. |
| DAO | `createSecondActiveFaceSession_rejectsWithUniqueIndex` | Exactly one ACTIVE FACE session exists. |
| DAO | `recordAttendance_sameStudentAndSession_rejectsDuplicate` | One record only. |
| DAO | `activeTemplatesForClass_neverReturnsAnotherClassTemplates` | Decision D-002 holds. |
| Transaction | `enrollThenPresent_whenTemplateInsertFails_rollsBackStudentAndRecords` | Student remains NOT_ENROLLED; no template/record survives. |
| Transaction | `recordPresent_whenWriteSucceeds_returnsAfterCommittedRecord` | Reload sees PRESENT before success is returned. |
| Transaction | `finalizeSession_insertsAbsentOnlyForEligibleStudentsWithoutRecords` | Correct complete attendance set. |
| Transaction | `finalizeSession_twice_isIdempotent` | No duplicate records; second result is AlreadyFinalized. |
| Migration | every version-to-current migration | Existing rows and constraints survive. |
| Repository | SQLite unique/index errors | Maps to named domain errors, no raw exception leaks. |
| Instrumented | simultaneous session creation / PRESENT writes | Database invariants hold under real threading. |

## 9. Definition of done

This LLD is complete when schema, migrations, indexes, typed repository results, and required transactions pass all tests. No other LLD may bypass a repository transaction to write attendance or templates directly.

