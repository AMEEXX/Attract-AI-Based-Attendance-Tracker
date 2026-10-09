# Attract — Face-Based Attendance Tracker

<p align="center">
  <img src="docs/art/app_logo.png" alt="Attract App Logo" width="120" />
  <h3 align="center">Offline, Class-Scoped Biometric Attendance for Native Android</h3>
  <p align="center">
    High-assurance student self-check-in with on-device face recognition, progressive enrollment, hardware-pinned kiosk safety, and zero cloud dependency.
  </p>
</p>

<p align="center">
  <!-- Core Platform -->
  <img src="https://img.shields.io/badge/Android_15_(API_35)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 15" />
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Material_Design_3-757575?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material Design 3" />
</p>

<p align="center">
  <!-- AI / ML & Vision -->
  <img src="https://img.shields.io/badge/TensorFlow_Lite-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white" alt="TFLite" />
  <img src="https://img.shields.io/badge/YOLOv8n--Face-00FFFF?style=for-the-badge&logo=target&logoColor=black" alt="YOLOv8" />
  <img src="https://img.shields.io/badge/ArcFace_512--D-8A2BE2?style=for-the-badge&logo=thealgorithms&logoColor=white" alt="ArcFace" />
  <img src="https://img.shields.io/badge/Umeyama_2D_Similarity-FF4081?style=for-the-badge&logo=opencv&logoColor=white" alt="Umeyama" />
  <img src="https://img.shields.io/badge/CameraX_API-00C853?style=for-the-badge&logo=googlecamera&logoColor=white" alt="CameraX" />
</p>

<p align="center">
  <!-- Security & Storage -->
  <img src="https://img.shields.io/badge/Room_v4_SQLite-00599C?style=for-the-badge&logo=sqlite&logoColor=white" alt="Room SQLite" />
  <img src="https://img.shields.io/badge/Keystore_AES--256--GCM-D32F2F?style=for-the-badge&logo=securityscorecard&logoColor=white" alt="Keystore AES-GCM" />
  <img src="https://img.shields.io/badge/Android_Screen_Pinning-E65100?style=for-the-badge&logo=pinboard&logoColor=white" alt="Screen Pinning" />
  <img src="https://img.shields.io/badge/100%25_Offline_/_Zero_Cloud-004D40?style=for-the-badge&logo=shield&logoColor=white" alt="Offline First" />
</p>

---

## 1. Executive Summary

**Attract** is a native Android attendance application built for a teacher’s personal smartphone. It is engineered from first principles to be **100% offline-first**, **class-scoped**, and **fail-closed**:

- 🔒 **Zero Cloud & Zero Telemetry**: Face detection, facial landmark alignment, 512-D embedding extraction, cosine matching, and database persistence occur strictly on-device. No images, vectors, or student records ever leave the phone.
- 📱 **Student Self Check-In with Pinned Kiosk Lockdown**: When taking attendance, the teacher's phone enters hardware-backed Android Screen Pinning (Lock Task Mode), restricting student interaction to the attendance UI.
- 👥 **Progressive Multi-Angle Enrollment**: Students enroll directly during routine roll call without needing dedicated setup sessions.
- 🛡️ **Hardware Keystore Cryptography**: Raw facial photos are never saved to disk. Face embeddings are encrypted using AES-256-GCM via the hardware-backed Android Keystore before storing in Room database.

> [!NOTE]
> For the canonical high-level design and engineering specifications, refer to the [Master Software Design Document (docs/00-main-sdd.md)](docs/00-main-sdd.md) and [Architecture Decision Records (docs/00-architecture-decisions.md)](docs/00-architecture-decisions.md).

---

## 2. Technology Stack & Ecosystem

Attract leverages modern Android architecture, high-performance edge machine learning, and hardware cryptography:

| Domain | Technology / Library | Badge Tag | Role in Attract |
|---|---|---|---|
| **Core Platform** | Android 15 (API 35) | ![Android](https://img.shields.io/badge/Android_15-3DDC84?style=flat-square&logo=android&logoColor=white) | Primary target OS with Screen Pinning & CameraX support |
| **Language** | Kotlin 2.0 | ![Kotlin](https://img.shields.io/badge/Kotlin_2.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white) | Type-safe domain logic, coroutines, and sealed state machines |
| **UI Framework** | Jetpack Compose | ![Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white) | Declarative, modern reactive UI with custom biometric overlays |
| **Design System** | Material Design 3 | ![Material 3](https://img.shields.io/badge/Material_3-757575?style=flat-square&logo=materialdesign&logoColor=white) | Unified typography, dark/light themes, and haptic feedback |
| **Camera Feed** | CameraX | ![CameraX](https://img.shields.io/badge/CameraX_1.4-00C853?style=flat-square&logo=googlecamera&logoColor=white) | High-speed preview stream & non-blocking `ImageAnalysis` analyzer |
| **Face Detection** | YOLOv8n-Face | ![YOLOv8](https://img.shields.io/badge/YOLOv8n--Face-00FFFF?style=flat-square&logo=target&logoColor=black) | On-device face detection & 5-point facial landmark regression |
| **Alignment** | Umeyama 2D Transform | ![Umeyama](https://img.shields.io/badge/Umeyama_Similarity-FF4081?style=flat-square&logo=opencv&logoColor=white) | Least-squares similarity alignment to canonical 112×112 crop |
| **Feature Extraction** | ArcFace MobileFaceNet | ![ArcFace](https://img.shields.io/badge/ArcFace_512--D-8A2BE2?style=flat-square&logo=thealgorithms&logoColor=white) | 512-dimensional L2-normalized deep facial identity embeddings |
| **Runtime Engine** | LiteRT / TFLite | ![TFLite](https://img.shields.io/badge/TensorFlow_Lite-FF6F00?style=flat-square&logo=tensorflow&logoColor=white) | Low-latency CPU & GPU inference acceleration |
| **Database** | Room v4 (SQLite) | ![Room](https://img.shields.io/badge/Room_v4-00599C?style=flat-square&logo=sqlite&logoColor=white) | Atomic transactions, foreign-key safety, and forward migrations |
| **Encryption** | Android Keystore | ![Keystore](https://img.shields.io/badge/Keystore_AES--GCM-D32F2F?style=flat-square&logo=securityscorecard&logoColor=white) | Hardware-backed key generation & envelope template encryption |
| **Kiosk Security** | Lock Task Mode | ![LockTask](https://img.shields.io/badge/Screen_Pinning-E65100?style=flat-square&logo=pinboard&logoColor=white) | Device lockdown during attendance sessions |
| **Concurrency** | Coroutines & Flow | ![Coroutines](https://img.shields.io/badge/Coroutines_&_Flow-4A148C?style=flat-square&logo=kotlin&logoColor=white) | Thread-safe Mutex state coordinator & reactive room observations |
| **Build Tooling** | Gradle 8.10 | ![Gradle](https://img.shields.io/badge/Gradle_8.10-02303A?style=flat-square&logo=gradle&logoColor=white) | Kotlin DSL build scripts & multi-variant compilation |

---

## 3. End-to-End System Architecture

The following diagram illustrates the complete biometric and persistence flow:

```mermaid
flowchart TD
    subgraph Camera ["1. Hardware Frame Acquisition"]
        C[CameraX YUV Stream] --> FB[Immutable FrameBundle\nFrameId, Timestamp, Rotation, Mirror]
    end

    subgraph Biometrics ["2. Edge Biometric Pipeline"]
        FB --> YD[YOLOv8n-Face TFLite\n640x640 RGB float32]
        YD -->|Bounding Box + 5 Landmarks| FA[Umeyama 2D Similarity Aligner\nCanonical 112x112 Crop]
        FA --> QE[Quality & Liveness Engine\nPose Bounds, Blur Laplacian, Eye Ratios]
        FA --> EE[ArcFace MobileFaceNet 512-D\nL2-Normalized Embedding]
    end

    subgraph Decision ["3. Identity Scoring & Decision"]
        EE --> GS[IdentityScorer\nGrouped Maximum Cosine Similarity]
        QE -.->|Quality Gate| GS
        GS --> DE[RecognitionDecisionEngine\nAccept: >= 0.25 | Margin: >= 0.05]
        DE -->|Score >= 0.25 & Margin >= 0.05| Match[MATCH: Enrolled Student]
        DE -->|Margin < 0.05| Ambiguous[AMBIGUOUS: Retry / Teacher Assist]
        DE -->|Score < 0.25| Unknown[UNKNOWN: Create Profile]
    end

    subgraph Persistence ["4. Persistence & Security Boundary"]
        Match --> TX1[Room Database Transaction\nAtomic Attendance Write: PRESENT]
        TX1 --> RoomDB[(Room SQLite v4\nAES-GCM Encrypted Templates)]
        Unknown --> PE[Progressive 3-Angle Enrollment\nStraight, Left Turn, Right Turn]
        PE --> D001{Teacher Auth\nGrant D-001}
        D001 -->|PIN Verified| DC[DuplicateCheckService\nClass-Wide Duplicate Gate < 0.22]
        DC -->|Clear| TX2[Room Transaction\nStore Encrypted Templates + Mark PRESENT]
        TX2 --> RoomDB
    end

    style Camera fill:#f9f9f9,stroke:#666,stroke-width:1px
    style Biometrics fill:#f0f7ff,stroke:#0066cc,stroke-width:2px
    style Decision fill:#f6ffed,stroke:#52c41a,stroke-width:2px
    style Persistence fill:#fff7e6,stroke:#fa8c16,stroke-width:2px
```

---

## 4. Core Workflows & Invariants

### 4.1 Live Attendance Self Check-In
1. **Teacher Initialization**: The teacher chooses a class and starts the session. The app engages **Android Screen Pinning (Lock Task Mode)**.
2. **Student Face Verification**: The student faces the camera. CameraX delivers frames to YOLOv8n-Face, which extracts 5 facial landmarks.
3. **Similarity Comparison**: The aligned face is converted into a 512-D unit vector. `IdentityScorer` compares it against all active templates enrolled in that specific class using grouped-maximum cosine scoring.
4. **Idempotent Record**: If score $\ge 0.25$ and margin $\ge 0.05$, a `PRESENT` record is atomically logged with source `FACE`. Repeated scans show *"Already Checked In"* without overwriting check-in times.

### 4.2 Progressive Multi-Angle Enrollment
1. **Unrecognized Student**: An unenrolled student faces the camera and receives an unrecognized result, offering the **Create Profile** button.
2. **Strict Roster Isolation**: The selection list shows **only `NOT_ENROLLED` students** (enrolled students are never shown, preventing identity hijacking).
3. **D-001 Teacher Approval**: The teacher verifies student identity in person and confirms via an action-bound grant dialog.
4. **Guided 3-Angle Capture**: The student captures 3 distinct validated angles:
   - **Angle 1**: Frontal (Straight)
   - **Angle 2**: Slight Left Turn (~15°)
   - **Angle 3**: Slight Right Turn (~15°)
5. **Continuity & Duplicate Verification**: The batch validator requires pairwise embedding continuity ($\ge 0.35$ to frontal anchor) and `DuplicateCheckService` verifies against the class gallery ($\text{max score} < 0.22$).
6. **Atomic Transaction**: Templates are encrypted with Keystore AES-GCM, the student status becomes `ENROLLED`, and an attendance record with source `ENROLLMENT` is saved together in Room.

### 4.3 Teacher-Assisted Fallback & Profile Repair
- **No Unauthenticated Clicks**: Tapping any student row will never mark attendance without teacher authorization.
- **PIN Authorization**: The teacher unlocks assisted attendance using their secure PIN, recording attendance with source `TEACHER_ASSISTED`.
- **Incompatible Profile Repair**: Outdated or damaged templates are marked `REENROLL_REQUIRED` and repaired via teacher-authorized re-enrollment.

---

## 5. Biometric Specification & Model Profile

| Pipeline Stage | Model Asset / Algorithm | Input Dimensions | Output Dimensions | Verification Thresholds |
|---|---|---|---|---|
| **Face Detection** | `yolov8n_face.tflite` | `[1, 3, 640, 640]` float32 RGB | `[1, 20, 8400]` float32 | IoU: `0.45f`, Confidence: `0.50f` |
| **Facial Alignment** | 2D Umeyama Similarity Fit | 5 facial landmarks | `[112, 112, 3]` canonical crop | Geometry and residual check |
| **Feature Extraction** | `arcface_mobilefacenet.tflite` | `[1, 112, 112, 3]` float32 `[-1, 1]` | `[1, 512]` float32 L2-norm | Norm range: $[0.8, 1.2]$ |
| **Identity Scoring** | Grouped-Maximum Cosine | Query vector vs class gallery | Best score ($B$), Runner-up ($S$) | Accept: $\ge 0.25$, Margin: $\ge 0.05$ |
| **Duplicate Gate** | Class-wide pairwise check | 3 candidate sample vectors | Max gallery similarity | Block if $\ge 0.22$ |
| **Envelope Encryption** | Hardware Keystore AES-GCM | Plaintext float bytes | IV + Ciphertext blob (v2) | Non-null hardware boundary |

---

## 6. Repository Layout

```text
Attract/
├── app/
│   ├── schemas/                          # Room schema JSON contracts (v1, v2, v3, v4)
│   └── src/
│       ├── main/
│       │   ├── assets/                   # Committed TFLite neural network assets
│       │   └── java/com/attract/attendance/
│       │       ├── app/                  # MainActivity, Navigation & App bootstrap
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
│       │       ├── lockdown/             # Screen Pinning (Lock Task Controller)
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

## 7. Quick Start & Building

### Prerequisites
- **IDE**: Android Studio Ladybug (2024.2.1+) or Koala
- **JDK**: Java 17
- **Target Platform**: Android 15 (API level 35), Min SDK 26 (Android 8.0 Oreo)
- **Device / Emulator**: Physical Android phone or Android Emulator with Virtual Scene Camera (e.g. `Pixel 6 API 35`)

### Build & Test Commands

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
A convenient script is provided to start the emulator and launch the app:
```cmd
.\Launch_Emulator.bat
```

---

## 8. Architecture Documentation Index

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

## 9. License

This project is licensed under the Apache License, Version 2.0. See the `LICENSE` file for details.
