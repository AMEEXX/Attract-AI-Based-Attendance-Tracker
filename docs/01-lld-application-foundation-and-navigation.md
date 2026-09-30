# LLD-01 — Application Foundation & Navigation

**Status:** Draft — ready for review and freeze
**Approval order:** 1 of 14
**Requirements source:** SDD §§5–8, 57–63, 71–72
**Depends on:** 00 Main SDD, [Approved Decisions](00-architecture-decisions.md), [Implementation Runbook](00-implementation-runbook.md)

## 1. Goal and non-goal

Build one Android application shell that always opens into exactly one safe world:

- **Teacher world:** onboarding, dashboard, class setup, class workspace, students, history, and manual attendance.
- **Student attendance world:** the only UI reachable while a FACE attendance session is active. It contains camera/check-in, result, enrollment request, and teacher-assistance states.

This LLD prevents a student from returning to teacher data through the Compose back stack. It does not implement Room, camera, face recognition, pinning, authentication, or attendance arithmetic; later LLDs supply those capabilities.

## 2. Concrete structure

```text
app/
  AttractApplication                 Creates DI graph
  MainActivity                        Hosts one Compose root
  AppRoot                             Observes startup state and renders one graph
  StartupViewModel                    Resolves the first destination once
  TeacherNavHost                      Teacher destinations only
  AttendanceNavHost                   Student-session destinations only
  AppNavigator                        Small interface for route changes
core/ui/
  AttractTheme, spacing, type scale, colors, reusable buttons/cards/dialogs
feature/*/
  XxxScreen, XxxViewModel, XxxUiState, XxxUiAction, XxxUiEffect
```

Use a single activity. Nested navigation graphs are allowed, but a student graph must never be placed under the teacher graph where normal back navigation could reveal it.

## 3. Startup routing

`StartupCoordinator.resolve()` runs once after the local database and security-preference adapter are ready. It returns one sealed value:

```kotlin
sealed interface StartupDestination {
  data object Onboarding : StartupDestination
  data class RecoverSession(val sessionId: Long) : StartupDestination
  data object TeacherDashboard : StartupDestination
  data class BlockingSetup(val failedChecks: Set<SetupCheck>) : StartupDestination
}
```

Decision order:

1. If first-run security/setup is incomplete, route to onboarding or the blocking setup checklist.
2. Otherwise ask the session repository for the one ACTIVE FACE session.
3. If an active session exists, route to recovery. Do **not** open a teacher screen first.
4. Otherwise open the teacher dashboard.

The resolver has no camera work, model loading, or decryption. It uses only lightweight persisted state. A database read failure shows a recoverable startup error with Retry; it never guesses that no active session exists.

## 4. Routes and navigation rules

Routes carry stable IDs only. Never put student names, face data, JSON objects, or credentials in a route or saved state.

| Graph      | Route                                     | Required argument | Who can open it               |
| ---------- | ----------------------------------------- | ----------------- | ----------------------------- |
| Root       | `onboarding`                            | none              | Startup only                  |
| Root       | `setup-blocked`                         | failed-check IDs  | Startup or prerequisite check |
| Root       | `recovery/{sessionId}`                  | session ID        | Startup only                  |
| Teacher    | `dashboard`                             | none              | Teacher/root                  |
| Teacher    | `class/{classId}` (Defaults to `calendar` tab) | class ID | Teacher |
| Teacher    | `class/{classId}/calendar`               | class ID          | Teacher (Default Tab 0)       |
| Teacher    | `class/{classId}/students`              | class ID          | Teacher (Tab 1)               |
| Teacher    | `class/{classId}/history`               | class ID          | Teacher (Tab 2)               |
| Teacher    | `manual-session/{classId}`              | class ID          | Teacher                       |
| Attendance | `attendance/{sessionId}`                | session ID        | Session coordinator           |
| Attendance | `attendance/{sessionId}/enrollment`     | session ID        | Session coordinator only      |
| Attendance | `attendance/{sessionId}/teacher-assist` | session ID        | Session coordinator only      |

Use typed route factories such as `TeacherRoute.ClassWorkspace(classId)`, even if Compose Navigation receives a string internally. Route parsing validates IDs and displays a safe error if an ID is absent or invalid.

### Entering a face attendance session

1. Teacher chooses Take Attendance.
2. LLD-06 validates prerequisites and creates the session.
3. Root navigation replaces its graph with `AttendanceNavHost(sessionId)`.
4. Clear the entire teacher navigation back stack inclusively.
5. LLD-13 starts/validates Screen Pinning.
6. Attendance graph starts at the session coordinator’s current state.

If any step fails before the pinned screen is usable, LLD-06 changes the session to an appropriate recoverable state and root returns to the safe teacher destination. It must not leave a hidden teacher stack behind.

### Leaving a face attendance session

Only the authenticated End Session / recovery finalization commands may request the root to exit attendance mode. On success:

1. stop camera and face processing;
2. clear sensitive in-memory session state;
3. LLD-13 stops lock task;
4. replace attendance graph with the class workspace route; and
5. do not restore the old teacher back stack.

Android Back in attendance mode delegates to the session coordinator. Its default result is no navigation. Back on a teacher screen has normal, explicit behavior.

## 5. UI state contract

Each feature owns four small types:

```kotlin
data class FeatureUiState(...)
sealed interface FeatureUiAction
sealed interface FeatureUiEffect
class FeatureViewModel(...) : ViewModel
```

Rules:

- `UiState` contains renderable state only: loading, data, validation message IDs, controls enabled/disabled, and stable IDs.
- `UiAction` represents a user intent, e.g. `TakeAttendancePressed`, not a direct database instruction.
- `UiEffect` represents one-time effects: navigation, haptic, focus, document picker, and snackbar.
- The ViewModel reduces an action to a new immutable state. It never exposes mutable lists to Compose.
- `SavedStateHandle` stores only route IDs and non-sensitive draft form fields. It never stores PIN input, templates, crops, decrypted cache, or active liveness state.

The root holds no business data. It reacts only to `StartupDestination` and a `SessionNavigationState` emitted by LLD-06.

## 6. Minimal visual system

Attract should be quiet, legible, and consistent:

- use Material 3 with one semantic color palette and a visible light/dark contrast review;
- one primary action per screen; destructive actions use confirmation;
- teacher screens use normal system bars; student session screens use large text, centered feedback, and no hidden gestures;
- display status with icon + text, not color alone;
- minimum 48dp touch targets, scalable text, content descriptions, and TalkBack labels;
- animate only to explain a state change. Success feedback lasts a bounded 1.5–2 seconds; animation must not delay persistence or the Next state.

No dashboard charts, gamification, social features, or decorative motion are introduced by this LLD.

## 7. Lifecycle, errors, and observability

- Collect UI state with lifecycle-aware Compose collection.
- ViewModels survive configuration change; camera and session engines own their own lifecycle in later LLDs.
- A configuration change must retain the current route and screen state but restart no one-time action.
- Process death restarts through `StartupCoordinator`; an ACTIVE session goes to recovery.
- Unknown route, invalid ID, or missing entity produces a simple “This item is unavailable” screen with Back to Dashboard. It never crashes.
- Emit non-sensitive events: `startup_resolved`, `teacher_graph_entered`, `attendance_graph_entered`, `blocked_navigation_attempt`, route duration, and error category.

## 8. Implementation sequence

1. Create the single-activity app, theme, common result/error types, dispatcher/clock providers, and test modules.
2. Create typed routes plus empty composable destination placeholders.
3. Implement `StartupCoordinator` with fake setup and session repositories; write JVM tests.
4. Wire `AppRoot` to select exactly one graph and clear stacks on graph replacement.
5. Add ViewModel base conventions and lifecycle-aware state collection.
6. Build the minimal reusable UI components and accessibility tests.
7. Integrate the real onboarding/recovery/session contracts only after LLD-02, LLD-06, and LLD-13 are implemented.

## 9. Tests

| Level        | Named test / scenario                                         | Expected result                                    |
| ------------ | ------------------------------------------------------------- | -------------------------------------------------- |
| Unit         | `resolve_incompleteSetup_routesToOnboarding`                | No teacher or attendance graph appears.            |
| Unit         | `resolve_activeFaceSession_routesToRecoveryBeforeDashboard` | Recovery receives the stored session ID.           |
| Unit         | `resolve_noActiveSession_routesToDashboard`                 | Teacher dashboard is selected.                     |
| Unit         | `routeFactory_invalidId_returnsInvalidRoute`                | Safe error state, never an exception.              |
| ViewModel    | `effect_isConsumedOnceAfterConfigurationChange`             | Navigation/snackbar is not replayed.               |
| UI           | `enterAttendance_replacesTeacherBackStack`                  | Back cannot reach dashboard, students, or history. |
| UI           | `exitAttendance_replacesStudentGraphWithClassWorkspace`     | Old student routes are gone.                       |
| UI           | `attendanceBackAction_doesNotNavigateToTeacherWorld`        | Coordinator owns the action.                       |
| UI           | `screen_hasAccessibleLabelsAndMinimumTouchTargets`          | Accessibility checks pass.                         |
| Instrumented | rotate during dashboard / recovery / attendance               | Correct state returns; no duplicate navigation.    |
| Instrumented | kill process with an ACTIVE session, relaunch                 | Root shows recovery, not dashboard.                |

## 10. Definition of done

LLD-01 is done when startup routing is deterministic, no inactive teacher back stack can survive entry to attendance mode, all routes are typed and validated, the minimal UI system is accessible, and every listed test passes. The graph contract is frozen before persistence, session, or security implementation depends on it.

---

### LLD-15 Navigation Addendum
- Added `AppScreen.StandaloneEnrollment(val classId: Long, val studentId: Long)` for face re-enrollment outside of active sessions (runs as a standard, non-pinned route with system back enabled).
- Added `BackHandler(enabled = canNavigateBack)` in `AttractApp.kt` to ensure workspace tab switching does not push backstack entries.
- Added `AnimatedContent` horizontal push/pop page transitions (`slideInHorizontally` + `fadeIn` together with `slideOutHorizontally` + `fadeOut`) for all `TeacherNavHost` screen destinations.
- Updated `openClass()` to fall back to `getClassSummary()` direct repository lookup to prevent Flow emission race conditions on class creation.


