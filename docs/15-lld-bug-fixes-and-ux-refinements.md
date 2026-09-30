# Attract — LLD-15: Bug Fixes & UX Refinements (Round 1)

**Document type:** SDD v4 addendum — new LLD, sits alongside LLD-01 through LLD-14  
**Status:** IMPLEMENTED & VERIFIED  

---

## Overview & Implementation Summary

This document records the exact resolution of the 16 bug fixes and UX refinements identified during real-device testing.

| Issue ID | Priority | Module | Summary of Implementation | Status |
|---|---|---|---|---|
| **BF-06** | P0 | Biometric | Fixed active template cache retrieval in `AttractRepository.getActiveTemplatesForClass()`. Replaced stub matching in `AttendanceScreen` with real `RecognitionDecisionEngine` threshold matching (`acceptThreshold = 0.45f`, `ambiguousMargin = 0.10f`). | VERIFIED |
| **BF-04** | P0 | Navigation | Integrated `BackHandler(enabled = canNavigateBack)` in `AttractApp.kt`. Tab switches inside `ClassWorkspace` do not push back stack; AI Attendance back press remains locked. | VERIFIED |
| **BF-05** | P0 | Lockdown | Audited `LockTaskControllerImpl.kt`. Confirmed zero KeyguardManager or DeviceAdmin APIs. Unpin exit returns directly to `ClassWorkspace` without OS lock screen interaction. | VERIFIED |
| **BF-01** | P0 | Dashboard | Confirmed `observeClasses()` in `AttractRepository` returns Room `Flow<List<ClassRow>>`. Class creation updates UI state dynamically. | VERIFIED |
| **BF-02** | P0 | Theme | Updated `ThemeRepository.kt` to use `.commit()` for SharedPreferences writes, ensuring synchronous disk persistence before screen transitions. | VERIFIED |
| **BF-03** | P0 | Roster | Created `AddStudentBottomSheet.kt`. Handles manual student entry with name, roll number, optional serial number, and inline `DuplicateRollNumber` validation error handling. | VERIFIED |
| **BF-09** | P1 | Export | Updated `AttendanceExporter.kt` to include per-session date P/A columns matrix in CSV output alongside student summary roster block. | VERIFIED |
| **BF-10** | P1 | Dashboard | Updated `DashboardScreen.kt` to compute dynamic time-of-day greeting from `java.time.LocalTime.now()` (Good morning/afternoon/evening/night). | VERIFIED |
| **BF-07** | P2 | Biometric | Added secondary "↺ Retake Poses" TextButton in `AttendanceScreen.kt` during enrollment frame collection (`collectedFrames > 0`). Hidden on submission. | VERIFIED |
| **BF-12** | P2 | History | Updated Room query in `Daos.kt` for `observeEndedForClass` to `ORDER BY ses.session_date DESC, ses.started_at DESC`. Created `HistoryFilterBottomSheet.kt` for sort and month filter. | VERIFIED |
| **BF-13** | P2 | Attendance | Added zero-attendance confirmation dialog ("No students marked present. Save this session anyway?") to `AttendanceScreen.kt` and `ManualAttendanceScreen.kt`. | VERIFIED |
| **BF-14** | P2 | Enrollment | Implemented `AppScreen.StandaloneEnrollment` reusing `AttendanceScreen` in `isStandaloneMode = true`. Added delete warning dialog in `StudentDetailScreen.kt`. | VERIFIED |
| **BF-08** | P3 | Toolbar | Replaced `Icons.Default.Lock` with `Icons.AutoMirrored.Filled.ExitToApp` on `AttendanceScreen.kt` top toolbar. | VERIFIED |
| **BF-11** | P3 | Navigation | Wrapped `TeacherNavHost` screen transitions in `AttractApp.kt` with `AnimatedContent` horizontal push/pop animations. | VERIFIED |
| **BF-15** | P3 | Design | Added reusable `Modifier.tapScale(targetScale = 0.96f)` press modifier in `Motion.kt` using snappy spring specs. | VERIFIED |
| **Docs-Sync** | Docs | Docs | Created `docs/15-lld-bug-fixes-and-ux-refinements.md` and updated `00-main-sdd.md`, `01-lld-...`, `10-lld-...`, `13-lld-...`. | COMPLETED |

---

## Round 2 — Master Bug Fixes (v3 Plan)

| Issue ID | Priority | Module | Summary of Implementation | Status |
|---|---|---|---|---|
| **Issue 1** | P0 | Session | Updated 3-way Zero-Student Exit Confirmation Dialog in `AttendanceScreen.kt` to show a back arrow in the title, and only two buttons (`Save` and `Discard`) in the action button column. | VERIFIED |
| **Issue 2** | P0 | Dashboard | Added direct `getClassSummary()` fallback query in `AttractViewModel.kt` to resolve Flow-emission race condition on class creation. | VERIFIED |
| **Issue 3** | P1 | Roster | Added "Ask Teacher" fallback option to roster enrollment bottom sheet. Authenticating with teacher PIN allows manual selection and marking of any student present under `TEACHER_ASSISTED`. | VERIFIED |
| **Issue 4a** | P0 | Security | Safe navigation sequencing returns activity to Calendar tab of the Class Workspace on exit/discard without dropping to system lock screen. | VERIFIED |
| **Issue 4b** | P0 | Biometric | Fully wired ML Kit Face Detection, `FaceQualityEngine`, and `LivenessEngine` checks to perform quality & passive anti-spoof checks on capture and Submit. | VERIFIED |
| **Issue 4c** | P0 | Biometric | Confirmed `saveFaceAttendance()` writes `AttendanceSource.AI_RECOGNITION` in Room database. Threshold calibrated at `0.45` L2 distance (`ambiguousMargin = 0.10f`). | VERIFIED |
| **Issue 5** | P0 | Theme | Passed `currentThemeMode` to `OnboardingScreen` and skipped appearance selection step, starting directly at profile setup. | VERIFIED |

---

