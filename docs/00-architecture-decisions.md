# 00 — Approved Architecture Decisions

**Status:** Approved for LLD design  
**Date:** 2026-08-06  
**Authority:** Project architecture baseline

These decisions remove ambiguity from the frozen HLD. They do not add product scope; they make the existing privacy, identity-protection, and recovery requirements implementable.

## D-001 — First-time enrollment is teacher-confirmed

**Problem:** A person selecting an unenrolled roster name cannot prove that they are that person. Liveness proves a live person is present; it does not prove their claimed identity.

**Decision:** A student may request enrolment, but the app stores no face template until the teacher authenticates and confirms the selected roster identity while viewing the live person. The confirmation applies to one named student and expires immediately after approval, cancellation, timeout, or the first failed capture.

**Minimal student flow:** enter roll number → app shows the matching unenrolled record → request teacher → teacher authenticates → teacher confirms → capture begins.

**Reason:** This closes the identity-impersonation hole without adding accounts, cloud verification, QR codes, or a separate enrolment day.

## D-002 — Biometric profiles are class-scoped

**Decision:** `Student` and `FaceTemplate` remain children of one `ClassSection`. Matching, duplicate checks, and the in-memory cache use only that class.

**Consequence:** A student attending two classes may be enrolled twice. This is deliberate MVP scope: it avoids a global biometric identity graph, cross-class privacy exposure, complex merging, and ambiguous roster mapping.

## D-003 — Only one ACTIVE face session exists on a device

**Decision:** The database and session coordinator allow at most one `ACTIVE` face-attendance session across all classes. A manual session cannot be created while a face session is active.

**Reason:** One camera, one template cache, one pinned UI, and one recovery path are easier to make correct.

## D-004 — Model-specific values are configuration, not code

**Decision:** Model file, input size, normalization, score thresholds, margin thresholds, quality thresholds, liveness thresholds, and inference cadence are injected as versioned configuration. Production values are frozen only after Gate B evidence.

**Reason:** A number copied from a paper is not a classroom guarantee. Separating configuration avoids unsafe code edits during calibration.

## D-005 — Security boundary is transparent

**Decision:** Attract uses Android Screen Pinning plus secure device unpin configuration and in-app teacher authentication. It is not advertised as managed-device kiosk security. Only tested device/OEM combinations enter the support matrix.

## Decision change rule

To change a decision, add a dated entry that states the evidence, LLDs affected, migration/testing impact, and approval. Do not silently change persistence, security, or biometric behavior during implementation.

