# LLD-07 — Session Recovery

**Status:** Draft — ready for review and freeze  
**Requirements source:** SDD §§36–40, 61–62, 71–72  
**Depends on:** LLD-02, LLD-05, LLD-06, LLD-13

## Goal

Recover safely after process death, system interruption, camera failure, or relaunch. Persisted PRESENT records are authoritative. Transient frames, caches, attempts, and liveness state are discarded.

## Algorithm

At startup LLD-01 asks for the one ACTIVE FACE session, then shows a teacher-authenticated recovery screen with class name and recorded-present count only.

| Teacher action | Exact action |
|---|---|
| Resume | Authenticate → recheck prerequisites → enter attendance graph → pin → rebuild/decrypt active class cache → bind camera → LLD-06 Ready |
| End session | Authenticate → LLD-05 finalization → mark ENDED → clear cache → stop lock task if active → class workspace |
| Dependency unavailable | Show check-specific remediation; allow Retry or End; never delete records |

If an ACTIVE session has missing/archived class data, corrupt storage, invalidated templates, or unavailable dependencies, recovery remains blocking. It never creates another session, silently marks absences, or opens Dashboard over unresolved session state.

## Components and lifecycle

`RecoveryViewModel` depends on FindActiveSession, GetSessionSummary, TeacherAuthenticator, DeviceSecurityChecker, and SessionCoordinator commands. Saved state retains only session ID. Every Resume invalidates stale attempt tokens before rebuilding resources. Repeated startup/recovery reads data but performs no attendance writes until a deliberate authenticated Resume/End action.

## Tests

| Scenario | Expected result |
|---|---|
| process dies after one PRESENT | record remains and summary count is one |
| recovery resume | cache is rebuilt from encrypted storage, never stale memory |
| failed camera/model/pinning | remediation and retry, not a new session |
| authenticated End | remaining eligible students finalized once |
| auth cancel | no state/data change |
| corrupt/missing database state | safe blocking error, no guessed absence |
| repeated relaunch | no duplicate records or effects |
| lock task already inactive | end/recovery still completes correctly |

## Definition of done

Every ACTIVE session has a deterministic teacher-controlled Resume or End route, and no successful check-in is lost or duplicated.
