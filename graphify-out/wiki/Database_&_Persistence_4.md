# Database & Persistence

> 19 nodes

## Key Concepts

- **YoloFaceDetector** (19 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **.detect()** (13 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **.parseDetections()** (10 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **.isAvailable()** (6 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **.getOrInitInterpreter()** (5 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **PointF** (5 connections)
- **.setUp()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt`
- **.setUp()** (4 connections) — `app/src/androidTest/java/com/attract/attendance/domain/face/LfwThreeFrameBenchmarkTest.kt`
- **.estimatePitch()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **.estimateRoll()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **.estimateYaw()** (4 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **Context** (4 connections)
- **Candidate** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **DetectedFace** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **.nms()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **.rotateLandmarks()** (3 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **Interpreter** (3 connections)
- **.calculateRoll()** (2 connections) — `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`
- **FloatArray** (1 connections)

## Relationships

- [Integration & Unit Tests](Integration_&_Unit_Tests.md) (5 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (4 shared connections)
- [Roster Import & OCR](Roster_Import_&_OCR.md) (4 shared connections)
- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (3 shared connections)
- [UI Theme & Components](UI_Theme_&_Components.md) (3 shared connections)
- [Full Biometric Pipeline Integration Test](Full_Biometric_Pipeline_Integration_Test.md) (2 shared connections)
- [Attendance Session Engine](Attendance_Session_Engine.md) (2 shared connections)
- [Attendance Camera UI](Attendance_Camera_UI.md) (2 shared connections)
- [Glowbuttonstate Error](Glowbuttonstate_Error.md) (1 shared connections)

## Source Files

- `app/src/androidTest/java/com/attract/attendance/domain/face/FullBiometricPipelineIntegrationTest.kt`
- `app/src/androidTest/java/com/attract/attendance/domain/face/LfwThreeFrameBenchmarkTest.kt`
- `app/src/main/java/com/attract/attendance/domain/face/YoloFaceDetector.kt`

## Audit Trail

- EXTRACTED: 61 (97%)
- INFERRED: 2 (3%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*