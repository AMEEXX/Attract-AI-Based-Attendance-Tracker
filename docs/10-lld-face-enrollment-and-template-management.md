# LLD-10  —  Face Enrollment & Template Management

**Status:** Draft  —  ready for review and freeze  
**Requirements source:** SDD §§12–20, 32–35, 54, 64, 69  
**Depends on:** LLD-02, LLD-05, LLD-06, LLD-09, LLD-12, Decisions D-001/D-002

## Goal

Create a class-scoped, encrypted multi-template profile and first PRESENT record in one safe operation. It owns first-time identity confirmation and re-enrollment. It never silently learns new templates after recognition.

## First-time & First-Day Inline Enrollment Flow

1. Student captures 3 quality pose photos in fixed order  —  **Straight  →  Left profile  →  Right profile**  —  during the live AI attendance session. Each frame is validated against its per-step pose window plus the full anti-spoof gate set (LLD-09 step-aware pose gating; LLD-12 presentation-attack detection) BEFORE the flow advances. A rejected frame must be retaken for the same step; three identical frontal frames are invalid and never accepted as enrollment input.
2. System evaluates vector similarity against active class templates. If no match is found ($\ge 0.75$), the system identifies the student as **NOT_ENROLLED**.
3. UI transitions button from SUBMIT to **ENROLL** and automatically presents a Modal Bottom Sheet listing all un-enrolled students in the class section.
4. Student selects their Roll Number / Name from the list and taps **ENROLL**.
5. System validates 3 multi-pose quality frames & liveness, generates normalized feature vectors, AEAD-encrypts templates, updates student's status to `ENROLLED`, and executes an atomic Room transaction creating the student's **PRESENT** record (`AttendanceSource.ENROLLMENT`).
6. System displays `ENROLLED & PRESENT: {Name}` feedback and resets for the next student. On all subsequent days, recognition occurs automatically without enrollment.

## Re-enrollment

Teacher opens a profile, authenticates, captures/liveness-checks new templates, and runs the same class-scoped duplicate check excluding the target student's active templates. In one transaction insert replacement templates, deactivate/delete old templates, keep the same student ID, and preserve attendance. A failure leaves old templates active.

## Components and contracts

```text
EnrollmentCoordinator  →  ApprovalGateway  →  ObservationCollector
                      →  LivenessEngine  →  EmbeddingEngine  →  DuplicateChecker
                      →  TemplateCipher  →  EnrollmentRepository transaction
```

`DuplicateChecker` returns Clear, Suspicious(studentId), or Unavailable. Unavailable blocks enrollment; do not downgrade to allow. The duplicate threshold and model/config version are calibration values distinct from recognition acceptance threshold.

## Tests

| Scenario | Expected result |
|---|---|
| student selects other/unknown roll | no approval/capture |
| teacher auth cancel/approval expiry | no template or record |
| 3–5 identical frames | insufficient diversity, no commit |
| liveness/embedding/duplicate failure | all temporary data cleared; no partial profile |
| duplicate enrolled face in same class | blocked with teacher path |
| same real person in another class | allowed under D-002 |
| transaction/template insert failure | NOT_ENROLLED and no PRESENT |
| re-enrollment success | old templates replaced, history unchanged |
| re-enrollment failure | old templates remain active |
| adaptive append command | rejected in MVP |

## Definition of done

Every stored template has teacher-confirmed ownership, liveness/quality evidence, class-scoped duplicate protection, encryption, and a matching atomic attendance write.

## Migration amendment (2026-08-25): embedding-dimension compatibility gate

**Production incident:** a phone upgraded from an earlier build retained templates enrolled
by a prototype model emitting **32-D** embeddings. The current MobileFaceNet emits **192-D**.
Both builds stamped modelVersion="v1", so version strings alone could not detect the
mismatch; the 32-D template reached TemplateMatcher.cosineSimilarity and crashed
(IllegalArgumentException: Embedding dimensions must match (A: 192, B: 32)).

**Fixes (all fail-closed):**
1. TemplateCompatibility (domain/face) is the single authority for biometric format:
   CURRENT_EMBEDDING_DIM = 192, CURRENT_MODEL_ID = "mobilefacenet_192d_v2".
2. Room v1→v2 migration adds face_templates.embedding_dim; enrollment stamps it and
   REJECTS wrong-dimension observations before any DB write.
3. AttractRepository.getActiveTemplatesForClass returns ONLY current-format templates;
   incompatible rows can never reach TemplateMatcher.
4. AttractRepository.retireIncompatibleTemplates(classId) deactivates incompatible rows
   (active=0, never deleted) and flips affected students back to NOT_ENROLLED so the
   existing enrollment sheet offers re-enrollment. Invoked once per AttendanceScreen start.
5. Defense-in-depth: RecognitionDecisionEngine and AdaptiveVerificationEngine each skip
   dimension-mismatched templates BEFORE cosine similarity. Never crash, never accept.
6. NO truncation/padding/conversion of stale vectors — different models produce
   non-equivalent biometric spaces; re-enrollment is the only recovery path.

## Model Upgrade amendment (2026-10-02): ArcFace 512-D & Room v3 Migration

**Upgrade to ArcFace MobileFaceNet:**
1. Upgraded biometric pipeline to ArcFace MobileFaceNet (`arcface_mobilefacenet.tflite`) emitting **512-D** L2-normalized embeddings from 112×112 canonical aligned crops.
2. Updated `TemplateCompatibility`: `CURRENT_EMBEDDING_DIM = 512`, `CURRENT_MODEL_ID = "arcface_512d_v3"`.
3. Room database version bumped from 2 to 3 with `MIGRATION_2_3`: automatically deactivates legacy templates with `embedding_dim != 512`.
4. Re-enrollment workflow: students with legacy 32-D or 192-D templates are transitioned back to `NOT_ENROLLED` and seamlessly re-enrolled with high-accuracy 512-D ArcFace templates.

