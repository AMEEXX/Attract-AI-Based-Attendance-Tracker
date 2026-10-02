# LLD-11 — Face Recognition & Decision Engine

**Status:** Draft — ready for review and freeze after Gate B  
**Requirements source:** SDD §§21–26, 29–31, 71–73  
**Depends on:** LLD-02, LLD-06, LLD-08–10, LLD-12, Decision D-004

## Goal

Recognize an enrolled student only when quality, liveness, similarity, and separation from the next candidate all satisfy calibrated evidence. Uncertainty fails closed to retry or teacher assistance.

## Interfaces and cache

```kotlin
interface EmbeddingEngine { suspend fun embed(faceInput: ModelInput): EmbeddingResult }
interface TemplateMatcher { fun rank(query: FloatArray, templates: ClassTemplateCache): RankedCandidates }
interface RecognitionDecisionEngine { fun decide(input: DecisionInput): RecognitionOutcome }
```

At session start decrypt only active templates for the selected class into `ClassTemplateCache`, grouped by student. Clear cache on ending/error. Query embedding preprocessing (alignment/crop/resize/color/normalization) is one versioned `ModelProfile` shared with enrollment; reject output with wrong dimension, NaN, or failed L2 normalization.

## Matching and decision algorithm

For each student in the active class roster, calculate cosine similarity against each stored multi-angle template vector and retain their maximum similarity score. Students arrive in random order (e.g. Roll #10 before Roll #1). Rank all roster students by maximum score. Let best score = B, second score = S, margin = B−S.

Decision input contains valid multi-angle quality result, Passed liveness, B, S, margin, model/config version, and active cache version. Outcomes:
- **Accepted(student):** quality/liveness pass; B >= accept threshold (0.25); margin >= margin threshold (0.05) [calibrated 2026-10-02 for ArcFace 512-D; min genuine=0.25, max impostor=0.16].
- **Ambiguous:** B may be high but margin too small; never choose a student. Increments 2-attempt retry counter.
- **Unknown:** B below threshold/no candidate. Increments 2-attempt retry counter; on 2nd attempt, triggers enrollment path.
- **Unavailable:** model asset missing, TFLite interpreter initialization fault, or inference exception. `EmbeddingEngine` throws explicit `IllegalStateException` without silent synthetic fallbacks, immediately displaying `"Face verification temporarily unavailable."`.

Retry Policy: Recognition tracks a 2-attempt retry counter per student interaction. The first `Ambiguous` or `Unknown` result prompts `"Couldn't verify clearly, please try again."` and returns to `READY` state. The second consecutive failure resets the counter and displays the un-enrolled roster selection sheet. Successful match or explicit session reset clears the counter.

## Performance and privacy

Compute on a bounded CPU dispatcher. For typical class sizes/multi-template profiles brute-force in-memory cosine matching is simpler and safer than a vector database; profile performance before optimizing. Store only permitted match confidence/config identifier if LLD-02 retention policy needs it; no query embedding, ranking vector, or face crop is logged/persisted.

## Tests

| Scenario | Expected result |
|---|---|
| normalized known vectors | expected cosine/ranking |
| multiple templates/student | strongest template represents student |
| best threshold pass but small margin | Ambiguous |
| score below threshold | Unknown |
| no second candidate | correct configured margin handling |
| NaN/wrong dimension/cache mismatch | Unavailable; no PRESENT |
| stale attempt/cache version | discarded by coordinator |
| only another class templates | never considered |
| Gate B genuine/impostor dataset | FAR/FRR/margin evidence recorded |
| class-size load test | p95 matching fits session latency budget |

## Adaptive multi-frame verification (LLD-16 amendment, 2026-08)

The mandatory STRAIGHT→LEFT→RIGHT transaction is superseded by the adaptive 1→2→3 frame
engine ([LLD-16](16-lld-adaptive-multiframe-verification.md)). Per-frame decisions still use
THIS document's `RecognitionDecisionEngine` with UNCHANGED accept=0.45 / margin=0.10.
Evidence fusion across frames is a separate pluggable layer (`EvidenceFusion`); production
currently uses `conservativeBestFrame`. Switching to `averageIdentityScore` is pending
calibration decision D-16a. All LLD-11 single-frame semantics (fail-closed NaN handling,
ambiguity, 2-attempt retry, Unavailable-on-model-fault) remain in force per frame.

## Definition of done

Recognition is deterministic, class-scoped, calibrated from evidence, and incapable of automatic acceptance when any required safety signal is uncertain.
