# 00 — Implementation Runbook

**Purpose:** This is the common “how to build” guide for every LLD. Read it before writing code. The individual LLDs define product rules; this document defines the shared working style so an implementation stays consistent.

## 1. Build in the approved order

Implement one frozen LLD at a time in the order in [00 LLD Plan](00-lld-plan.md). For each LLD:

1. Read its purpose, non-scope, invariants, and test list.
2. Create interfaces and pure domain tests first.
3. Implement database adapters or Android adapters behind those interfaces.
4. Connect a ViewModel and Compose screen only after the use case works.
5. Run its unit and integration tests.
6. Review error paths, cancellation, privacy, and logging.
7. Mark the LLD implemented only after its definition of done is met.

Do not start a dependent LLD merely because its screen is easy to draw.

## 2. Package and dependency direction

```text
com.attract.attendance/
  app/                 Application, root activity, navigation wiring, DI
  core/
    common/            Result types, clock, dispatcher providers, identifiers
    model/             Small shared value types; never Room entities
    ui/                Theme, components, accessibility helpers
    testing/           Fakes, fixtures, test clocks, coroutine test rules
  domain/
    classstudent/      Pure use cases and repository interfaces
    attendance/
    session/
    face/
    security/
  data/
    local/             Room database, entities, DAOs, migrations
    repository/        Repository implementations and mappers
    crypto/            Encrypted embedding envelope implementation
    importexport/
  feature/
    onboarding/
    dashboard/
    classworkspace/
    students/
    importroster/
    attendance/
    enrollment/
    history/
  platform/
    camera/            CameraX and image conversion adapters
    face/              ML Kit, TFLite, model adapters
    lockdown/          Screen Pinning and device prerequisite adapters
```

Rules:

- A Compose screen calls only its ViewModel; it never calls Room, CameraX, ML Kit, or TFLite.
- A ViewModel calls use cases; it does not contain SQL, bitmap conversion, or cryptographic code.
- Domain code is Kotlin/JVM-testable and imports no Android UI framework.
- Platform APIs sit behind narrow interfaces so they can be faked in tests.
- Room entities, encrypted blobs, and Android `Context` do not escape their data/platform layer.

## 3. Standard contracts

Use these conventions in all LLDs:

- IDs are immutable typed values or `Long`/UUID values wrapped at boundaries. Never use display names as identifiers.
- Time comes from an injected `Clock`. Store an `Instant` for events and a captured local date/timezone for session reporting.
- Expose long-lived screen state as `StateFlow<UiState>`. Emit navigation, toast, haptic, and one-time results as `SharedFlow<UiEffect>`.
- Use a sealed `AppError`/feature error hierarchy. Convert technical errors to short, kind user messages at the ViewModel boundary.
- Repository commands return explicit success/failure values. Do not use exceptions as normal control flow.
- Database writes run on the injected IO dispatcher. CPU ML work runs on a bounded CPU executor/dispatcher. UI work stays on Main.
- Every command that can be retried must be idempotent or carry an idempotency key.

## 4. Error and logging rules

Every error path must answer four questions: What happened? What does the user see? Can they retry? What is safely logged?

Never log:

- face crops, image bytes, embeddings, decrypted templates, raw similarity vectors;
- teacher PINs, authentication tokens, or encryption key material; or
- more student identity data than is needed for an operational event.

Log stable event names, non-sensitive IDs when justified, model/config version, elapsed time, error category, and retry outcome. Debug face diagnostics are off in production and must not persist biometric content.

## 5. Testing ladder

| Level               | Runs against                             | Goal                                               |
| ------------------- | ---------------------------------------- | -------------------------------------------------- |
| Unit                | Pure Kotlin with fakes                   | Business rule and state-transition correctness     |
| DAO / migration     | Instrumented Room database               | Constraints, SQL queries, transactions, migrations |
| Repository          | Room + fake crypto/platform adapter      | Mapping and error translation                      |
| ViewModel           | Coroutine test dispatcher + fakes        | UI state and one-off effects                       |
| Android integration | Device/emulator                          | Permissions, CameraX, pinning, keystore, lifecycle |
| Spike / pilot       | Approved real devices and consented data | Accuracy, liveness, latency, thermal, OEM behavior |

Each test name should read like a requirement: `finalizeSession_writesAbsentOnlyForEligibleStudentsWithoutPresentRecord`.

## 6. Definition of implementation done

An LLD is implemented only when:

- every interface and invariant in the frozen LLD is represented in code;
- all listed unit, DAO/integration, and ViewModel tests pass;
- static analysis and formatting pass;
- errors, empty states, retries, and configuration changes are verified;
- sensitive data is absent from logs and UI outside its permitted boundary;
- performance checks are within the LLD target; and
- documentation is updated if implementation uncovers an approved change.

## 7. Safe defaults

- Fail closed for attendance recognition: uncertainty goes to retry or teacher assistance, never automatic PRESENT.
- Persist a successful PRESENT before showing the success animation.
- Cancel ongoing face work when its session or capture token becomes invalid.
- Clear the active class template cache, face crops, and challenge state on session end and process destruction.
- Keep manual teacher attendance available as a recoverable fallback.
