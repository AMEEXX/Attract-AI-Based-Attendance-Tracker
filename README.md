# Attract — Face-Based Attendance Tracker

Attract is a native Android attendance tracker for a teacher’s personal phone. It is intentionally local-first and minimal:

- student self-check-in with on-device face recognition;
- progressive enrollment from a teacher-managed class roster;
- immediate offline attendance persistence;
- teacher-only operations protected during a pinned attendance session; and
- encrypted biometric templates, never permanently retained raw face photos.

## Current phase

**Active Biometric Production Release.** The native Android application is fully implemented and running on Android API 35 (offline-first, Jetpack Compose, Room v4, TFLite).

The biometric pipeline is powered by:
- **Detection & Landmarks**: YOLOv8n-Face (`yolov8n_face.tflite`, SHA-256: `85a19457127249bb7f2a0875ff344b9dc6021a2a371e14c77d4c0e5f22f7ed54`) with 5-point facial landmark similarity alignment (Umeyama 2D least squares).
- **Recognition & Similarity**: ArcFace MobileFaceNet 512-D (`arcface_mobilefacenet.tflite`, SHA-256: `dfac9cfe6517a9c4c3969b6ff0c2a0ac112cdf67a287d8218b60636810f0b576`), unit-norm normalized, cosine distance with grouped-maximum identity scoring.
- **Decision Policy**: `acceptThreshold = 0.25f`, `ambiguousMargin = 0.05f`, `duplicateThreshold = 0.22f`.
- **Database & Security**: Room Schema v4 with all-roster forward migration (`MIGRATION_3_4`), explicit `REENROLL_REQUIRED` repair status, non-null Keystore AES-GCM envelope encryption, and fail-closed D-001 teacher authorization grants.

## Key Biometric Safeguards & Invariants

1. **Roster Protection & D-001 Confirmation**: The student self-check-in candidate list shows only `NOT_ENROLLED` students. Selecting a student identity requires teacher confirmation before capture.
2. **Authenticated Teacher Fallback**: Enrolled students who cannot be verified via camera are marked PRESENT or repaired exclusively through authenticated teacher assistance with single-action grants.
3. **Continuous Same-Person Enrollment**: Guided enrollment requires exactly 3 valid angles (Straight, Left profile, Right profile) with pairwise same-person continuity verification and duplicate detection across the class gallery.
4. **Pinned Attendance Safety**: Lock-task pinning begins before capture and releases strictly upon authenticated session exit or discard.

## Architecture & Model Profile

| Component | Asset / Target | Contract |
|---|---|---|
| Detector | `yolov8n_face.tflite` | float32 `[1, 3, 640, 640]` -> `[1, 20, 8400]` |
| Recognizer | `arcface_mobilefacenet.tflite` | float32 `[1, 112, 112, 3]` -> `[1, 512]` |
| Alignment | 5-point Umeyama similarity | Canonical 112x112 ArcFace crop |
| Preprocessing | RGB, `(pixel - 127.5) / 128.0` | Range: `[-1.0, 1.0]` |
| Schema Version | Room v4 | `face_templates` + `students.enrollment_status` |

## Documentation map

| Document | Purpose | Status |
|---|---|---|
| [00 Main SDD](docs/00-main-sdd.md) | Canonical HLD / SDD baseline | Active |
| [00 Document control](docs/00-document-control.md) | Change control & release history | Active |
| [00 Risks and gates](docs/00-architecture-risks-and-gates.md) | Decision gates and evaluation criteria | Active |
| [00 Architecture decisions](docs/00-architecture-decisions.md) | Approved resolutions for identity proof, scope, session, and security | Approved |
| [00 Implementation runbook](docs/00-implementation-runbook.md) | Build, test, and verification instructions | Active |
