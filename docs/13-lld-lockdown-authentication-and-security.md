# LLD-13 — Lockdown, Authentication & Security

**Status:** Implemented & Verified (2026-08-10)  
**Requirements source:** SDD §§7, 34–35, 57–64, 69, 71–73  
**Depends on:** LLD-01, LLD-02, Decisions D-001–D-005

## Goal and boundaries

This LLD protects teacher-only actions and stored biometric templates. It owns onboarding prerequisites, Screen Pinning adapters, teacher authentication, encryption, sensitive-memory clearing, and security logging. It does not implement recognition or decide attendance.

## Interfaces

```kotlin
interface DeviceSecurityChecker { suspend fun check(): Set<SetupCheck> }
interface LockTaskController { suspend fun start(): LockTaskResult; suspend fun stop(): Result<Unit> }
interface TeacherAuthenticator { suspend fun authenticate(reason: AuthReason): AuthResult }
interface EmbeddingCipher {
  fun encrypt(studentId: Long, modelVersion: String, plaintext: ByteArray): EncryptedEmbedding
  fun decrypt(studentId: Long, modelVersion: String, encrypted: EncryptedEmbedding): ByteArray
  fun clear()
}
```

Android implementations live in `platform/lockdown` and `data/crypto`. Session/domain code depends only on these interfaces.

## Prerequisite and pinning policy

Before a FACE session starts require camera permission, configured teacher PIN, secure device lock, completed Screen Pinning guidance/probe, available model resources with verified version/checksum, and no other ACTIVE FACE session.

**Screen Pinning starts automatically** when the attendance screen appears — no teacher action is required. The implementation follows the SDD §7 flow: Session Created → `startLockTask()` → Pinned Attendance World. Per LLD-06: "validate prerequisites → create ACTIVE session → replace root graph → acquire pinning → load/decrypt class cache → bind camera → Ready."

On Android 11 consumer devices not configured as Device Owner, `startLockTask()` may throw `SecurityException`. This is handled gracefully:
- `isScreenPinned = false` — the "📌 Screen Pinned" badge is NOT shown
- `screenPinningFailed = true` — a visible amber warning badge "⚠️ Screen lock unavailable" is shown
- Session continues; teachers should use device-level security or configure Device Policy

This is Screen Pinning, not Device Owner kiosk security. Onboarding gives exact instructions; unknown or failed configuration is surfaced in the UI, never silently swallowed.

### Asynchronous lock-state confirmation (device parity)

`ActivityManager.lockTaskModeState` does **not** transition synchronously after `startLockTask()` on all devices. Emulators typically report `LOCK_TASK_MODE_LOCKED` immediately; physical devices often lag, and consumer (non-Device-Owner) devices first show the system pinning-confirmation dialog ("Touch and hold Back and Overview to unpin"), so the state remains `LOCK_TASK_MODE_NONE` until the user confirms. Implementation requirements:

1. After `startLockTask()`, **poll** `lockTaskModeState` for up to ~5 s (e.g., 250 ms interval) before declaring failure. Treat `LOCK_TASK_MODE_PINNED` and `LOCK_TASK_MODE_LOCKED` as success.
2. Never classify "state not yet Locked immediately after the call" as a pinning error — only a timeout with state still `NONE` is an error.
3. UI reflects the *observed* state: badge shows pinned only when the OS confirms; a pending/confirmation hint may be shown while polling.
4. OEM variance: some manufacturers alter or suppress lock-task behavior. Failures are surfaced per the policy above and recorded as security events; SDD §"Validate pinning behavior across manufacturers" applies at Gate A.

Start order: LLD-06 creates a recoverable ACTIVE session → root swaps to attendance graph → `startLockTask()` is invoked in `LaunchedEffect(Unit)` → lock-task state is observed/confirmed → camera is enabled. End order: PIN authenticated → `stopLockTask()` → finalize attendance → clear plaintext cache → swap root graph. If stopping lock task fails, log the error and continue — never block session end.

## Authentication

Critical actions are End Session, recovery resume/end, teacher assistance, first-time identity confirmation, re-enrollment, and sensitive profile management. Each calls `authenticate(reason)`; a successful authentication authorizes one command only.

Prefer `BiometricPrompt`; if unavailable or cancelled offer the app PIN. PIN entry is never placed in saved state, logs, or an exception. Convert its buffer, verify, and clear it in `finally`.

Store a random salt plus a slow PBKDF2-HMAC-SHA-256 hash, format version, and iteration metadata. Compare in constant time. Exact work factor is release configuration validated on minimum devices; never hard-code an outdated guess.

## Template encryption and memory

Generate a non-exportable AES-GCM key in Android Keystore with a versioned alias. To persist an embedding: serialize fixed-endian floats → generate unique 96-bit IV → AES/GCM encrypt → bind AAD to `templateFormat|studentId|modelVersion` → store format/key version, IV, ciphertext/tag in the LLD-02 encrypted BLOB.

Plain templates exist only in the active class cache. Minimize copies, clear byte/direct buffers on session end, re-enrollment cancellation/failure, and fatal error, and never give the cache to UI code. If Keystore key invalidation/loss occurs, mark templates unusable and require teacher-authenticated re-enrollment; never use an exportable fallback key.

## Errors, logging, build order

Show generic verification failure, never an attack reason, PIN reason, score, or crypto detail. Logs may include action category, result, OEM/model, and safe error code; they never include PIN, crops, embeddings, ciphertext, IV, or full biometric scores.

1. Implement fakes and unit tests.
2. Build checklist/settings guidance.
3. Integrate biometric/PIN authentication and cancellation.
4. Implement and test AES-GCM envelope.
5. Integrate LockTask and run Gate A OEM validation.

## Tests

| Level | Scenario | Expected result |
|---|---|---|
| Unit | incomplete prerequisite set | Start blocked with remediation |
| Unit | correct/wrong PIN | One-command auth result; constant-time comparison path |
| Crypto | encrypt/decrypt round trip | Exact bytes recovered with matching AAD |
| Crypto | altered ciphertext, IV, student, model | Authentication failure |
| Crypto | invalidated key | Re-enrollment-required error; no crash |
| Instrumented | biometric success, cancel, PIN fallback | Privileged action only after success |
| Instrumented | lock start/stop failure | Safe error, no teacher graph leak |
| OEM Gate A | Home/Back/Recents/unpin/notification/restart | Support matrix evidence |
| Review | security event logs | No secret or biometric material |

## Definition of done

All critical actions are authenticated, templates are AEAD-encrypted at rest, unsupported pinning configuration blocks use, and Gate A results define the supported-device policy.
