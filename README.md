# Attract — Face-Based Attendance Tracker

Attract is a native Android attendance tracker for a teacher’s personal phone. It is intentionally local-first and minimal:

- student self-check-in with on-device face recognition;
- progressive enrollment from a teacher-managed class roster;
- immediate offline attendance persistence;
- teacher-only operations protected during a pinned attendance session; and
- encrypted biometric templates, never permanently retained raw face photos.

## Current phase

**Documentation-first, stage-gated waterfall design.** All 14 LLD drafts are complete; no production application code has started.

The binding high-level design is [docs/00-main-sdd.md](docs/00-main-sdd.md). The next activity is review and freeze of [LLD-01](docs/01-lld-application-foundation-and-navigation.md), then the documents in the approved sequence. Implementation begins only after the applicable LLDs, technical-spike gates, and acceptance criteria are approved.

## Documentation map

| Document | Purpose | Status |
|---|---|---|
| [00 Main SDD](docs/00-main-sdd.md) | Canonical HLD / SDD v3.1 baseline | Frozen source |
| [00 Document control](docs/00-document-control.md) | Waterfall process and change control | Active |
| [00 Risks and gates](docs/00-architecture-risks-and-gates.md) | Bottlenecks, decision gates, and spike acceptance | Active |
| [00 Architecture decisions](docs/00-architecture-decisions.md) | Approved resolutions for identity proof, scope, session, and security | Approved |
| [00 Implementation runbook](docs/00-implementation-runbook.md) | Common build, error, testing, and completion instructions | Active |
| [00 LLD plan](docs/00-lld-plan.md) | Scope and approval order for LLD-01 through LLD-14 | Active |
| [99 LLD template](docs/99-lld-template.md) | Required structure for every LLD | Approved template |

All future implementation, design, and test work must be traceable to one of the numbered LLDs.
