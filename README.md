# Attract — Face-Based Attendance Tracker

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_15_(API_35)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android API 35" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Storage-Room_v4_SQLite-00599C?style=for-the-badge&logo=sqlite&logoColor=white" alt="Room SQLite v4" />
  <img src="https://img.shields.io/badge/AI_Engine-TFLite_(YOLO_+_ArcFace)-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white" alt="TFLite" />
  <img src="https://img.shields.io/badge/Privacy-100%25_Offline_/_Zero_Cloud-darkgreen?style=for-the-badge" alt="100% Offline" />
  <img src="https://img.shields.io/badge/Security-Keystore_AES--GCM_Envelope-red?style=for-the-badge" alt="Keystore AES-GCM" />
</p>

---

## 1. Overview

**Attract** is a high-assurance, native Android application engineered for classroom attendance tracking on a teacher’s personal smartphone. It is built from first principles to be **100% offline-first**, **class-scoped**, and **privacy-preserving**:

- **Zero Cloud & Zero External Servers**: Face detection, facial alignment, feature extraction, matching, and database storage occur strictly on-device. No telemetry, no external APIs, and no images ever leave the phone.
- **Student Self Check-In with Pinned Kiosk Safety**: Teachers hand their phone to students with hardware-backed Android Screen Pinning (Lock Task Mode) enabled, completely locking down the device to the attendance interface.
- **Progressive Multi-Angle Enrollment**: Students enroll directly during routine roll call without needing dedicated setup sessions.
- **Hardware-Backed Cryptographic Security**: Raw facial photographs are never permanently stored. Extracted embeddings are encrypted with AES-256-GCM via the Android Keystore before writing to Room database.

> [!NOTE]
> For the comprehensive, formal engineering specification, read the canonical [Software Design Document (docs/00-main-sdd.md)](docs/00-main-sdd.md) and [Architecture Decision Records (docs/00-architecture-decisions.md)](docs/00-architecture-decisions.md).

---

## 2. System Architecture

The following diagram illustrates the complete end-to-end biometric and attendance pipeline:

```mermaid
flowchart TD
    subgraph Camera ["Hardware & Frame Acquisition"]
        C[CameraX Preview] -->|YUV Stream| FB[FrameBundle: Timestamp, Lens, Detections]
    end

    subgraph Biometrics ["On-Device Biometric Pipeline"]
        FB --> YD[YOLOv8n-Face Detector\n640x640 RGB float32]
        YD -->|Bounding Box + 5 Landmarks| FA[Umeyama 2D Similarity Aligner\nCanonical 112x112 Crop]
        FA --> QE[Quality & Liveness Engine\nPose, Sharpness, Eye Signals]
        FA --> EE[ArcFace MobileFaceNet 512-D\nL2-Normalized Embedding]
    end

    subgraph Decision ["Decision & Verification Engine"]
        EE --> GS[IdentityScorer\nGrouped Maximum Cosine Similarity]
        QE -.->|Quality Gated| GS
        GS --> DE[RecognitionDecisionEngine\nAccept: >= 0.25 | Margin: >= 0.05]
        DE -->|Score >= 0.25 & Margin >= 0.05| Match[MATCH: Verified Student]
        DE -->|Margin < 0.05| Ambiguous[AMBIGUOUS: Retry / Teacher Assist]
        DE -->|Score < 0.25| Unknown[UNKNOWN: Progressive Enrollment]
    end

    subgraph Persistence ["Secure Storage & Kiosk Control"]
        Match --> TX1[Room Database Transaction\nAtomic Attendance Write: PRESENT]
        TX1 --> RoomDB[(Room SQLite v4\nAES-GCM Encrypted Templates)]
        Unknown --> PE[Progressive 3-Angle Enrollment\nStraight, Left Turn, Right Turn]
        PE --> D001{Teacher Auth\nGrant D-001}
        D001 -->|Approved| DC[DuplicateCheckService\nClass-Wide Duplicate Gate]
        DC -->|Clear| TX2[Room Transaction\nSave Templates + Mark PRESENT]
        TX2 --> RoomDB
    end

    style Biometrics fill:#f0f7ff,stroke:#0066cc,stroke-width:2px
    style Decision fill:#f6ffed,stroke:#52c41a,stroke-width:2px
    style Persistence fill:#fff7e6,stroke:#fa8c16,stroke-width:2px
```

---

## 3. Core Product Workflows

### 3.1 Live Attendance Self Check-In
1. **Teacher Launch**: The teacher selects a class section, taps **Take Attendance**, and the app automatically engages **Android Screen Pinning**.
2. **Student Recognition**: The student faces the camera. CameraX delivers high-rate frames, the detector isolates the primary face, generates a 512-D ArcFace embedding, and compares it against all active enrolled templates for that class.
3. **Idempotent Commit**: Upon confident recognition (`score >= 0.25`, `margin >= 0.05`), the app atomically writes a `PRESENT` record to Room with source `FACE`. Repeated check-ins display *"Already Checked In"* without corrupting timestamps.

### 3.2 Progressive First-Time Enrollment
1. **Unrecognized Student**: When an unenrolled student faces the camera, the system reports unrecognized status and presents the **Create Profile** option.
2. **Strict Roster Eligibility**: The candidate picker exposes **strictly `NOT_ENROLLED` students** from the roster (enrolled identities are hidden to prevent accidental overwrites).
3. **D-001 Teacher Confirmation**: The student selects their name, and a modal prompts the teacher for immediate PIN authorization to verify physical identity ownership.
4. **Guided 3-Angle Capture**: The student captures exactly **3 validated angles** (Frontal/Straight, Slight Left ~15°, Slight Right ~15°) ensuring same-person embedding continuity (`similarity >= 0.35f` to frontal anchor).
5. **Class-Wide Duplicate Protection**: Before committing, `DuplicateCheckService` compares all 3 samples against every existing template in the class (`threshold = 0.22f`). If another student has a matching face, enrollment is blocked.
6. **Atomic Persistence**: Templates are encrypted via Keystore AES-GCM, the student's status updates to `ENROLLED`, and an attendance record with source `ENROLLMENT` is persisted in a single Room transaction.

### 3.3 Authenticated Teacher Assistance & Profile Repair
- If an enrolled student cannot be verified due to lighting, camera occlusion, or temporary injury, the student cannot be marked via unauthenticated UI clicks.
- The teacher taps **Teacher Assist**, enters their secure PIN, and can mark the student with source `TEACHER_ASSISTED`.
- If an older template is obsolete or corrupted, the teacher initiates an authorized re-enrollment, transitioning the student status to `REENROLL_REQUIRED`.

---

## 4. Biometric Specification & Model Profile

| Pipeline Stage | Model Asset / Algorithm | Input Contract | Output Contract | Verification Threshold |
|---|---|---|---|---|
| **Face Detection** | `yolov8n_face.tflite` | `[1, 3, 640, 640]` float32 RGB | `[1, 20, 8400]` float32 | IoU: `0.45f`, Confidence: `0.50f` |
| **Facial Alignment** | 2D Umeyama Similarity Fit | 5 landmarks (eyes, nose, mouth corners) | `[112, 112, 3]` canonical crop | Residual & geometry check |
| **Feature Extraction** | `arcface_mobilefacenet.tflite` | `[1, 112, 112, 3]` float32 `[-1.0, 1.0]` | `[1, 512]` float32 L2-norm | Unit vector: norm $\in [0.8, 1.2]$ |
| **Identity Scoring** | Grouped-Maximum Cosine | Query vector vs class gallery | Top score ($B$), Runner-up ($S$) | Accept: $\ge 0.25$, Margin: $\ge 0.05$ |
| **Duplicate Gate** | Class-wide pairwise check | 3 enrollment candidate vectors | Max score against gallery | Block if $\ge 0.22$ |
| **Encryption Boundary** | Android Keystore AES-GCM | Plaintext float bytes | IV + Ciphertext blob (v2) | Non-null hardware boundary |

---

## 5. Repository Structure

```text
Attract/
├── app/
│   ├── schemas/                          # Room database schema exports (v1, v2, v3, v4)
│   └── src/
│       ├── main/
│       │   ├── assets/                   # Bundled TFLite models (YOLOv8n + ArcFace)
│       │   └── java/com/attract/attendance/
│       │       ├── app/                  # MainActivity, Navigation & Application bootstrap
│       │       ├── core/model/           # Domain models, Status enums & CommandResult
│       │       ├── data/
│       │       │   ├── local/            # Room Database, DAOs, Entities & Migrations
│       │       │   ├── repository/       # AttractRepository & atomic transaction commands
│       │       │   └── security/         # Keystore cipher & TemplateEnvelopeCodec
│       │       ├── domain/
│       │       │   ├── face/             # YOLO detector, Umeyama aligner, ArcFace embedding,
│       │       │   │                     # IdentityScorer, Liveness, Quality & Duplicate check
│       │       │   └── session/          # SessionCoordinator, SessionState & Teacher Grants
│       │       ├── feature/attendance/   # AttendanceScreen, CameraPreview & ViewModels
│       │       ├── lockdown/             # Android Screen Pinning (Lock Task Controller)
│       │       └── ui/                   # Jetpack Compose theme, components & class screens
│       ├── test/                         # Unit tests (Coordinator, Math, Duplicate, Reproductions)
│       └── androidTest/                  # Instrumented Room migration & camera integration tests
├── docs/                                 # Complete Software Design Documents & LLDs
├── sample-data/                          # Reference student rosters and CSV import templates
├── scripts/                              # Model downloaders, TFLite inspectors, ADB automation
├── build.gradle.kts                      # Root Gradle build script
└── README.md                             # Project overview and architecture guide
```

---

## 6. Quick Start & Building

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1+) or Koala
- **JDK**: Java 17
- **Target Platform**: Android 15 (API level 35), Min SDK 26 (Android 8.0 Oreo)
- **Device / Emulator**: Android device with Camera or Android Emulator with Virtual Scene Camera (e.g. `Pixel 6 API 35`)

### Build Commands

```bash
# 1. Download and verify TFLite models
python scripts/download_models.py

# 2. Run all unit tests (100% green required)
./gradlew testDebugUnitTest

# 3. Assemble debug APK
./gradlew assembleDebug

# 4. Run instrumented migration and database tests on connected device/emulator
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.attract.attendance.data.local.TemplateMigrationAndroidTest
```

### Launching the Development Emulator
A convenient script is provided to start the preconfigured emulator and launch the app:
```cmd
.\Launch_Emulator.bat
```

---

## 7. Architecture Documentation Index

For in-depth design rationale, mathematical models, and subsystem specifications, refer to the documentation in [`docs/`](docs/):

| Document | Topic & Scope |
|---|---|
| 📄 **[00 Main SDD (docs/00-main-sdd.md)](docs/00-main-sdd.md)** | **Canonical Architecture Document**: HLD, system invariants, domain rules, and security baselines. |
| 📄 **[00 Architecture Decisions (docs/00-architecture-decisions.md)](docs/00-architecture-decisions.md)** | **ADRs**: Records D-001 through D-006 (teacher proof, class scope, session recovery, ML Kit boundary). |
| 📄 **[02 Database & Persistence (docs/02-lld-database-and-persistence.md)](docs/02-lld-database-and-persistence.md)** | Room entities, migrations 1→2→3→4, schema integrity, and Keystore envelope encryption. |
| 📄 **[06 Live Session Engine (docs/06-lld-live-attendance-session-engine.md)](docs/06-lld-live-attendance-session-engine.md)** | Session state machine, attempt tokens, and feedback concurrency control. |
| 📄 **[08 Camera & Frames (docs/08-lld-camera-and-frame-processing.md)](docs/08-lld-camera-and-frame-processing.md)** | CameraX frame acquisition pipeline, thread safety, and `FrameBundle` contracts. |
| 📄 **[09 Face Quality Engine (docs/09-lld-face-quality-engine.md)](docs/09-lld-face-quality-engine.md)** | Pose calculation, Laplacian blur detection, and target angle evaluation. |
| 📄 **[10 Enrollment & Templates (docs/10-lld-face-enrollment-and-template-management.md)](docs/10-lld-face-enrollment-and-template-management.md)** | Guided 3-angle progressive enrollment, same-person continuity, and duplicate detection. |
| 📄 **[11 Recognition & Decisions (docs/11-lld-face-recognition-and-decision-engine.md)](docs/11-lld-face-recognition-and-decision-engine.md)** | Cosine distance scoring, grouped-maximum algorithm, and decision boundary thresholds. |
| 📄 **[12 Liveness & Anti-Spoof (docs/12-lld-liveness-and-anti-spoof-engine.md)](docs/12-lld-liveness-and-anti-spoof-engine.md)** | Presentation attack detection, temporal motion analysis, and eye-state safety. |
| 📄 **[13 Security & Lockdown (docs/13-lld-lockdown-authentication-and-security.md)](docs/13-lld-lockdown-authentication-and-security.md)** | Android Screen Pinning (Lock Task), teacher PIN hashing (Argon2/PBKDF2), and action grants. |
| 📄 **[16 Multiframe Verification (docs/16-lld-adaptive-multiframe-verification.md)](docs/16-lld-adaptive-multiframe-verification.md)** | Sequential multiframe evidence fusion and attempt budget policy. |
| 📄 **[Sample Data Guide (sample-data/README.md)](sample-data/README.md)** | CSV roster formatting and import guide. |
| 📄 **[Developer Scripts (scripts/README.md)](scripts/README.md)** | Tool catalog for model testing, database diagnostics, and automation. |

---

## 8. License

This project is licensed under the Apache License, Version 2.0. See the `LICENSE` file for details.
