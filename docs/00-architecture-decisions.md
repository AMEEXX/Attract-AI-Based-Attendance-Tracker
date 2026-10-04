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

## D-006 — YOLOv8n-Face Detection and ArcFace 512-D Biometric Pipeline

**Date:** 2026-10-02  
**Status:** Approved & Implemented  
**Problem:** Legacy ML Kit face detection combined with unaligned 192-D embeddings yielded inadequate separation margins on diverse real-world classroom images with varying head poses and lighting conditions.

**Decision:**
1. **Detection:** Integrated `YOLOv8n-face` (`yolov8n_face.tflite`) as a dedicated on-device TFLite face detector. Emits bounding boxes, 5 facial landmarks (eyes, nose, mouth corners), and landmark-derived head pose geometry (yaw, pitch, roll). Completely eliminates ML Kit dependency and external Google Play Services requirements.
2. **Alignment:** Canonical 5-point affine similarity alignment (`FaceAligner`) warps detected faces to standard ArcFace 112×112 geometry based on eye landmarks, normalizing scale, tilt, and translation.
3. **Embedding:** ArcFace MobileFaceNet (`arcface_mobilefacenet.tflite`) extracts 512-D L2-normalized biometric vectors from 112×112 aligned crops (normalized `(pixel - 127.5) / 128.0`).
4. **Calibrated Thresholds:** Decision engine calibrated from empirical benchmarks on real human subjects: `acceptThreshold = 0.25f`, `ambiguousMargin = 0.05f`, achieving perfect separation on real test dataset (min genuine = 0.2489, max impostor = 0.1567, separation margin = +0.0923).
5. **Database & Compatibility:** Schema bumped to version 3 with `MIGRATION_2_3` deactivating legacy templates (`embedding_dim != 512`). `TemplateCompatibility` updated to model `arcface_512d_v3`.

## D-007 — First-time enrollment is self-service (supersedes D-001)

**Date:** 2026-10-04
**Status:** Approved by product owner; implementation per doc 18 WP-D.
**Problem:** The D-001 teacher-PIN gate sits in the student check-in path and blocks enrollment during a busy session. Combined with the broken pose gate (doc 18 RC-1), no student could ever complete enrollment.
**Decision:** When a capture is NOT FOUND, the student picks their own name from the **NOT_ENROLLED** list, confirms "this is me", and completes the guided 3-pose capture. The face is then bound to that name for the semester. No teacher PIN is needed for first enrollment.
**Retained safeguards:** recognition runs first against all enrolled students; enrolled names are never listed; duplicate-face check (calibrated 0.50) blocks the same face under a second name; the teacher can reset a single student's face profile from the student page; the teacher PIN still guards session exit and data reset.
**Accepted risk:** a student could deliberately claim a classmate's not-yet-enrolled name. The owner accepts this; it surfaces when the real owner finds their name taken.
**Also superseded:** D-006 item 4 thresholds (0.25 / 0.05). Measured values are in doc 18 §3.3.

## Decision change rule

To change a decision, add a dated entry that states the evidence, LLDs affected, migration/testing impact, and approval. Do not silently change persistence, security, or biometric behavior during implementation.


