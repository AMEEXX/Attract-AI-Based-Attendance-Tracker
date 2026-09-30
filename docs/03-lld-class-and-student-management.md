# LLD-03 — Class & Student Management

**Status:** Draft — ready for review and freeze  
**Requirements source:** SDD §§3, 9–12, 45, 47–49, 52–53  
**Depends on:** LLD-02

## Goal

Implement teacher-only class and roster maintenance without damaging historical attendance. This feature owns class creation/edit/archive, student creation/edit/archive, and eligibility-boundary changes. It never exposes this data in student attendance mode.

## Components and contracts

```text
feature/classworkspace/  Dashboard, class form, class workspace ViewModels
feature/students/        Student list, form, archive dialogs
domain/classstudent/     CreateClass, UpdateClass, ArchiveClass,
                         AddStudent, UpdateStudent, ArchiveStudent,
                         SetEligibilityBoundary use cases
data/repository/         ClassRepository, StudentRepository
```

Use cases take validated commands, not UI text fields. ViewModels trim display input, map validation errors to fields, and call one use case per user action.

## Rules and validation

| Object | Required validation | Failure behavior |
|---|---|---|
| Class | nonblank name; percentage 0–100; trimmed optional section/batch | Inline field error; preserve draft |
| Student | nonblank name; nonblank roll number; normalized roll number unique within class | Inline error or duplicate-row message |
| Edit | internal IDs never change | Update display fields only |
| Archive class | no ACTIVE session for that class | Block with “End or recover attendance first” |
| Archive student | no ACTIVE session for that class | Block; preserve templates and history |
| Eligibility | target session belongs to same class | Reject invalid boundary |

Roll-number normalization is deterministic: trim outer whitespace, collapse repeated internal spaces, and compare case-insensitively. Preserve the teacher’s preferred display casing separately or write the normalized value consistently. Do not silently merge two existing students.

When a new student is added, `eligibleFromSessionId` is set to the next class session boundary: one greater than the newest existing session ID for that class, or the first created session when none exist. The teacher may later choose a prior session only through the explicit eligibility editor; this warns that past statistics will change.

## Screen behavior

- Dashboard displays active classes only; each card shows name, optional section, ended-session count, and active-student count.
- Archived Classes is a separate teacher route. Restore is permitted only if the class has no ACTIVE session.
- Class workspace has four explicit actions: Students, Calendar/History, Manual Attendance, Take Attendance. It never mixes camera controls into the teacher list UI.
- Student list supports a simple search on name/roll number, enrollment badge, and archive state. It does not show templates or scores.
- Forms use one primary Save action. Unsaved edits prompt on back only if data changed.
- Archive is reversible. Destructive permanent deletion is out of MVP.

## Persistence and concurrency

Repositories use LLD-02 transactions. A class/student archive command re-checks for an ACTIVE session inside the transaction, so a stale screen cannot archive during a just-started session. Editing a roll number checks the unique constraint and maps a race to `DuplicateRollNumber`.

No class/student write runs during an ACTIVE FACE session. The root navigation already hides teacher screens; repositories enforce the rule independently.

## Build steps

1. Implement pure command validators and error types with JVM tests.
2. Add repository CRUD methods and active-session guards.
3. Build dashboard flow from Room observable queries.
4. Build class form, student list/form, archive/restore dialogs, and eligibility editor.
5. Add ViewModel draft restoration with non-sensitive `SavedStateHandle` fields.
6. Connect attendance-count/statistic read models only after LLD-05 exposes them.

## Tests

| Level | Scenario | Expected result |
|---|---|---|
| Unit | blank class name / invalid percentage | Correct field error; no repository call |
| Unit | roll normalization variants | Same canonical roll value |
| DAO/repository | duplicate roll in one class | Rejected |
| DAO/repository | duplicate roll in different classes | Allowed |
| Repository | archive while class has ACTIVE session | `ActiveSessionExists`; no archive |
| Repository | archive enrolled student | Student is archived, templates/history unchanged |
| Unit | add student after five sessions | Eligibility starts at session six |
| Unit | move eligibility boundary | Statistics recalculation input changes only for target student |
| ViewModel | duplicate error after save | Draft remains and error is visible |
| UI | archived class absent from dashboard, present in archive list | Correct filters |
| UI | teacher routes unavailable during active session | Navigation guard holds |

## Definition of done

All class/student lifecycle changes are validated, transactional, reversible where specified, and cannot alter historical IDs or bypass active-session safeguards.

