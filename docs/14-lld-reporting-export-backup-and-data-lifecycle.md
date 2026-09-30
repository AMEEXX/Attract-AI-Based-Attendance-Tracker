# LLD-14 — Reporting, Export, Backup & Data Lifecycle

**Status:** Draft — ready for review and freeze  
**Requirements source:** SDD §§41–49, 65–69, 71–72  
**Depends on:** LLD-02, LLD-05

## Goal

Present correct attendance history and export teacher-owned reports without expanding MVP into cloud sync, migration, analytics, or raw-biometric backup.

## Read models and screens

Repositories expose immutable projections:
- `CalendarDaySummary(date, sessionCount)`
- `SessionSummary(id, class, start/end, presentCount, absentCount, status)`
- `StudentAttendanceSummary(id, name, roll, present, eligibleSessions, percent)`
- `ExportRow(roll, name, perSessionStatus, percent)`

Calendar groups ENDED sessions by captured local session date; multiple sessions on one date remain separate. History opens a session record list for teacher correction (LLD-05). Percentages always use LLD-05 values; UI never recomputes them from a visible list.

## Export flow

Teacher chooses class, scope (session/date range/student report), and destination through Storage Access Framework. `ExportService` streams CSV UTF-8 and, when the validated library is integrated, XLSX. Both adapters receive the same `ExportModel`; business queries do not know a file format.

CSV/XLSX cell values beginning with `=`, `+`, `-`, or `@` are prefixed with an apostrophe to prevent spreadsheet formula injection. Use sanitized teacher display fields, write atomically to a temporary provider stream when possible, close/flush on cancellation, and show success only after close succeeds. Do not export templates, biometrics, match scores, liveness, PIN/security configuration, or diagnostics.

## Session deletion and lifecycle

Only a teacher-authenticated delete of an ENDED accidental session is allowed. It calls LLD-05 transaction, removes records/session, and all statistics/calendars derive updated values on next query. ACTIVE is recovered/ended, not deleted. Class/student archive retains data. Permanent biometric/deletion/retention controls require an approved policy before real deployment.

`BackupRepository` is an interface only in MVP. It may return NotSupported; no cloud, portable encrypted backup, migration, or multi-device code is implemented. This prevents accidental export of Keystore-bound template data.

## Tests

| Scenario | Expected result |
|---|---|
| two ENDED sessions same date | both appear separately |
| late joiner statistics | denominator matches LLD-05 |
| delete ENDED session | calendar/stats recompute; records gone |
| delete ACTIVE session | blocked |
| CSV special commas/newlines/Unicode | valid escaped UTF-8 output |
| formula-like name/roll | literal sanitized cell |
| cancelled/revoked destination URI | no false success; recoverable error |
| export rows | excludes every biometric/security field |
| empty date range/class | useful empty report, no crash |

## Definition of done

Teacher reports are correct, streaming, privacy-minimal, formula-safe, and local-only; future backup is an explicit extension point, not hidden MVP scope.
