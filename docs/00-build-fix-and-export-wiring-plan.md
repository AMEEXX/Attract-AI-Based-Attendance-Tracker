# Build Fix & Export Wiring Plan

## Background & Problem Summary

The Attract app — a face-based attendance tracker for Android — had a non-compiling codebase due to several layered issues:

1. **CameraX Compatibility:** CameraX 1.6.1 required `compileSdk 36` and AGP 8.9.1+, but the project pinned `compileSdk 35` and AGP 8.8.2. Downgraded CameraX to 1.4.2 to resolve this.
2. **MainActivity Wiring:** `MainActivity.kt` had broken constructor calls and dead code which were aligned with the actual ViewModel and App Compose signatures.
3. **Kotlin Compilation Errors:** Two mechanical compilation errors were found:
   - **CsvRosterImporter.kt:** `RosterStudent` was used but never imported.
   - **AttractRepository.kt:** `item.serialNumber.cleanOptional()` called a non-null receiver extension on a nullable `String?` field.
4. **Architectural Gaps:** Designed features like CSV attendance report export and JSON backup export had backend code written but were not wired into the UI or ViewModel.

---

## Architectural Changes & Feature Wiring

The following files have been modified to correct compilation errors and wire up the export/backup features:

### 1. Compile Error Fixes
* **[CsvRosterImporter.kt](file:///c:/Users/amitk/OneDrive/Documents/ChatGPT/Attract%20-%20Face%20Based%20Attendance%20Tracker/app/src/main/java/com/attract/attendance/data/importexport/CsvRosterImporter.kt):** Added missing import `com.attract.attendance.core.model.RosterStudent`.
* **[AttractRepository.kt](file:///c:/Users/amitk/OneDrive/Documents/ChatGPT/Attract%20-%20Face%20Based%20Attendance%20Tracker/app/src/main/java/com/attract/attendance/data/repository/AttractRepository.kt):** Fixed nullable safe call to `item.serialNumber?.cleanOptional()`.

### 2. Build Settings & Warnings
* **[build.gradle.kts (app)](file:///c:/Users/amitk/OneDrive/Documents/ChatGPT/Attract%20-%20Face%20Based%20Attendance%20Tracker/app/build.gradle.kts):** 
  - Added KSP schema location setting (`room.schemaLocation`) to clean Room database build warnings.
  - Added code documentation notes for the CameraX 1.4.2 downgrade decision.

### 3. Exporter Wiring (LLD-14)
* **[AttractViewModel.kt](file:///c:/Users/amitk/OneDrive/Documents/ChatGPT/Attract%20-%20Face%20Based%20Attendance%20Tracker/app/src/main/java/com/attract/attendance/feature/app/AttractViewModel.kt):** 
  - Wired `AttendanceExporter` and `BackupExporter` into constructor parameters.
  - Added ViewModel commands `exportClassReport(classId, uri)` and `exportBackup(uri)`.
* **[MainActivity.kt](file:///c:/Users/amitk/OneDrive/Documents/ChatGPT/Attract%20-%20Face%20Based%20Attendance%20Tracker/app/src/main/java/com/attract/attendance/app/MainActivity.kt):** Passed context exporters inside the ViewModel Provider Factory.
* **[AttractApp.kt](file:///c:/Users/amitk/OneDrive/Documents/ChatGPT/Attract%20-%20Face%20Based%20Attendance%20Tracker/app/src/main/java/com/attract/attendance/feature/app/AttractApp.kt):** 
  - Wired Android Storage Access Framework (SAF) `CreateDocument` launchers for picking files.
  - Added **"Export attendance report"** button to the `WorkspaceScreen` (history list footer).
  - Added **"Export full backup"** button to the `DashboardScreen`.

### 4. Test Configuration Fix
* **[FaceQualityEngineTest.kt](file:///c:/Users/amitk/OneDrive/Documents/ChatGPT/Attract%20-%20Face%20Based%20Attendance%20Tracker/app/src/test/java/com/attract/attendance/domain/face/FaceQualityEngineTest.kt):** Corrected a pre-existing test bug where `minBlurVariance = 900f` was passed while testing config version propagation, violating the `targetBlurVariance > minBlurVariance` rule validation (target defaults to 500). Lowered it to `100f`.

---

## Verification Results

* **Local Compile:** Verified via `./gradlew assembleDebug` (Build Successful).
* **Unit Tests:** Verified via `./gradlew testDebugUnitTest` (24/24 tests pass).
