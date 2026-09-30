# 00 — LLD Plan and Approval Order

**Status:** Active  
**Implementation status:** Partially implemented. LLD-01 through LLD-05 and LLD-14 have core code committed (foundation, database, class/student management, roster import, attendance engine, export/backup). LLD-09 (Face Quality Engine) is fully implemented and unit-tested. LLD-06 through LLD-08, LLD-10 through LLD-13 remain unimplemented (face pipeline deliberately gated pending Gate A/B validation).  
**See also:** [00-master-implementation-plan.md](00-master-implementation-plan.md) — root-cause analysis, build rules, full gap analysis, and phase-by-phase plan.

## Dependency-aware approval order

The document number identifies the architectural unit; it is not the work sequence. To use a strict, efficient waterfall flow, documents will be drafted and frozen in this order:

1. LLD-01 — Application Foundation & Navigation
2. LLD-02 — Database & Persistence
3. LLD-03 — Class & Student Management
4. LLD-04 — CSV/XLSX Roster Import
5. LLD-05 — Attendance Domain Engine
6. LLD-13 — Lockdown / Authentication / Security
7. LLD-06 — Live Attendance Session Engine
8. LLD-07 — Session Recovery
9. LLD-08 — Camera & Frame Processing
10. LLD-09 — Face Quality Engine
11. LLD-10 — Face Enrollment & Template Management
12. LLD-12 — Liveness / Anti-Spoof Engine
13. LLD-11 — Face Recognition & Decision Engine
14. LLD-14 — Reporting / Export / Backup / Data Lifecycle

LLD-11 is deliberately approved after enrollment and liveness because its acceptance decision depends on their shared data and controls. LLD-13 is moved earlier than its document number because secure session isolation is foundational to the app flow.

## LLD register

| ID | Design unit | Current state | Primary dependencies |
|---|---|---|---|
| [01](01-lld-application-foundation-and-navigation.md) | Application Foundation & Navigation | Partially implemented | 00 baseline |
| [02](02-lld-database-and-persistence.md) | Database & Persistence | Partially implemented | 01, ADR-002, ADR-003 |
| [03](03-lld-class-and-student-management.md) | Class & Student Management | Partially implemented | 02 |
| [04](04-lld-roster-import.md) | CSV/XLSX Roster Import | Partially implemented | 02, 03 |
| [05](05-lld-attendance-domain-engine.md) | Attendance Domain Engine | Partially implemented | 02, 03 |
| [06](06-lld-live-attendance-session-engine.md) | Live Attendance Session Engine | Planned | 01, 02, 05, 13 |
| [07](07-lld-session-recovery.md) | Session Recovery | Planned | 02, 05, 06 |
| [08](08-lld-camera-and-frame-processing.md) | Camera & Frame Processing | Planned | 01, 06 |
| [09](09-lld-face-quality-engine.md) | Face Quality Engine | Implemented (unit-tested) | 08 |
| [10](10-lld-face-enrollment-and-template-management.md) | Face Enrollment & Template Management | Planned | 02, 05, 06, 09, 12, ADR-001/002 |
| [11](11-lld-face-recognition-and-decision-engine.md) | Face Recognition & Decision Engine | Planned | 02, 06, 08, 09, 10, 12 |
| [12](12-lld-liveness-and-anti-spoof-engine.md) | Liveness / Anti-Spoof Engine | Planned | 08, 09 |
| [13](13-lld-lockdown-authentication-and-security.md) | Lockdown / Authentication / Security | Planned | 01, 02 |
| [14](14-lld-reporting-export-backup-and-data-lifecycle.md) | Reporting / Export / Backup / Data Lifecycle | Partially implemented | 02, 05 |
| [16](16-lld-adaptive-multiframe-verification.md) | Adaptive 1→2→3 Frame Verification | Implemented (fusion switch pending calibration D-16a) | 08–12 |

## Build environment notes

- **CameraX:** Pinned to 1.4.2 (compatible with compileSdk 35 / AGP 8.8.2). CameraX 1.6.1+ requires compileSdk 36 and AGP 8.9.1+.
- **AGP:** 8.8.2 / Gradle 8.10.2 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28

## Definition of design completion

An LLD becomes **Approved / Frozen** only when it contains the complete structure from [the LLD template](99-lld-template.md), has no unresolved decision that affects its public contract, and has testable acceptance criteria. Experimental calibration values may remain configuration placeholders only when their measurement plan and decision authority are explicit.

The next working session begins LLD-01. No Android project code, dependency choice, or UI build is authorized by this planning baseline.
