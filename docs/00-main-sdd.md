# Attendance Tracker — Software Design Document

**Version:** SDD v3.1 — HLD / Implementation Baseline
**Status:** HLD FROZEN — LLD Phase Starting
**Platform:** Native Android
**Primary User:** Teacher
**Core Mode:** Offline-first, on-device attendance

---

# 1. Project Overview

Attendance Tracker is a native Android application that enables teachers to take
classroom attendance using student self-check-in through face recognition.

The teacher creates a class and imports a student roster from CSV/XLSX.

Students initially exist only as roster records:

- Name
- Roll number / Student ID
- Optional serial number
- No face profile

Students progressively enroll their faces.

The first time an unenrolled student attends:

1. Student attempts face check-in.
2. If no enrolled identity matches, the app offers first-time enrollment.
3. Student selects their identity from the remaining unenrolled roster.
4. The app validates face quality.
5. Liveness / anti-spoof verification runs.
6. Multiple high-quality face observations are captured automatically.
7. Face embeddings are generated.
8. The new identity is checked against existing enrolled identities.
9. Face templates are encrypted and stored.
10. Student becomes enrolled.
11. Student is marked PRESENT in the same atomic operation.

During future classes, the student normally only needs to face the camera.

The system recognizes the student and automatically marks attendance.

During an attendance session, the teacher's phone enters Android Screen Pinning.
Only the dedicated student-facing attendance interface is available.

---

# 2. Product Principles

## 2.1 Student Self Check-In

```text
Teacher selects class
        ↓
Take Attendance
        ↓
Attendance session created
        ↓
Screen Pinning starts
        ↓
Phone passed to students
        ↓
Student checks in
        ↓
Recognition
        ↓
Attendance stored immediately
        ↓
Success feedback
        ↓
Next student
```

---

## 2.2 Progressive Enrollment

Enrollment does not need to happen during a special enrollment day.

Example:

```text
Roster = 60

Day 1
45 students attend
↓
45 become ENROLLED
15 remain NOT_ENROLLED

Day 2
Existing enrolled students
→ automatic recognition

Previously absent student arrives
→ no recognition match
→ Create Profile
→ selects unenrolled ID
→ enrolls
→ marked PRESENT
```

Students can therefore enroll whenever they first attend.

---

## 2.3 Local-First

Core attendance does not depend on internet connectivity.

```text
Application
     ↓
Room Database
     ↓
Local source of truth
```

Camera processing, face detection, liveness, embedding generation and recognition
run on-device.

---

## 2.4 Biometric Data Minimization

Raw face photographs are not permanently stored.

```text
Camera frame
     ↓
Face processing
     ↓
Embedding generated
     ↓
Embedding encrypted
     ↓
Stored as FaceTemplate
     ↓
Raw image discarded
```

---

# 3. Functional Requirements

The system shall allow the teacher to:

- Create classes.
- Edit classes.
- Archive classes.
- Import student rosters.
- Add/edit/archive students.
- Start attendance sessions.
- Take attendance through face recognition.
- Progressively enroll students.
- Re-enroll student face profiles.
- Take attendance manually.
- Correct PRESENT/ABSENT status manually.
- View attendance through a calendar.
- View student attendance percentages.
- View total classes held.
- Delete accidental attendance sessions.
- Resume interrupted attendance sessions.
- Export attendance.
- Securely end pinned attendance sessions.

The system shall prevent:

- Duplicate attendance for the same student/session.
- Students accessing teacher-facing screens during attendance.
- Already-enrolled students appearing in the new-profile selection list.
- Enrollment under another student's identity when biometric duplication is detected.
- Poor-quality face data from being accepted for enrollment.

---

# 4. Technology Stack

| Layer                     | Technology                                              |
| ------------------------- | ------------------------------------------------------- |
| Language                  | Kotlin                                                  |
| UI                        | Jetpack Compose                                         |
| Architecture              | MVVM + Repository + Domain/Use Cases where required     |
| Reactive State            | StateFlow                                               |
| Concurrency               | Kotlin Coroutines                                       |
| Navigation                | Compose Navigation                                      |
| Camera                    | CameraX                                                 |
| Face Detection            | ML Kit Face Detection                                   |
| Recognition               | MobileFaceNet, ArcFace-trained, TFLite                  |
| Matching                  | Cosine similarity                                       |
| Liveness                  | Passive-first anti-spoof + active fallback              |
| Passive candidate         | Silent-Face-Anti-Spoofing                               |
| Database                  | Room                                                    |
| Encryption                | Android Keystore-backed                                 |
| Kiosk                     | `startLockTask()` / `stopLockTask()` Screen Pinning |
| DI                        | Hilt if justified                                       |
| Core Internet Requirement | None                                                    |

---

# 5. High-Level Architecture

```text
┌───────────────────────────────────────┐
│              UI LAYER                 │
│                                       │
│ Jetpack Compose                       │
│                                       │
│ Onboarding                            │
│ Dashboard                             │
│ Class Setup                           │
│ Class Workspace                       │
│ Students                              │
│ Calendar                              │
│ Session                               │
│ Enrollment                            │
│ History                               │
└───────────────────┬───────────────────┘
                    │
                StateFlow
                    │
                    ↓
┌───────────────────────────────────────┐
│            VIEWMODEL LAYER            │
└───────────────────┬───────────────────┘
                    │
                    ↓
┌───────────────────────────────────────┐
│             DOMAIN LAYER              │
│                                       │
│ Attendance Rules                      │
│ Enrollment Rules                      │
│ Session Rules                         │
│ Recognition Decisions                 │
│ Eligibility                           │
│ Percentage Calculation                │
└───────────────────┬───────────────────┘
                    │
                    ↓
┌───────────────────────────────────────┐
│           REPOSITORY LAYER            │
│                                       │
│ ClassRepository                       │
│ StudentRepository                     │
│ AttendanceRepository                  │
│ FaceRepository                        │
│ BackupRepository                      │
└────────────┬──────────────┬───────────┘
             │              │
             ↓              ↓
        ROOM DATABASE    FACE ENGINE
```

---

# 6. Navigation Architecture

The application contains two logically isolated navigation environments.

## 6.1 Teacher World

```text
Dashboard
    │
    ├── Create Class
    ├── Archived Classes
    │
    └── Class Workspace
            │
            ├── Calendar
            ├── Students
            ├── History
            ├── Manual Attendance
            └── Take Attendance
```

## 6.2 Student Attendance World

After Take Attendance:

```text
Teacher World
      ↓
Create ACTIVE session
      ↓
startLockTask()
      ↓
══════════════════════════
 PINNED ATTENDANCE WORLD
══════════════════════════
      ↓
Attendance Camera
      ↓
Recognition / Enrollment
      ↓
Result
      ↓
Next Student
```

Teacher navigation is inaccessible until authenticated session termination.

---

# 7. Pinned Session Isolation

While an attendance session is ACTIVE, students MUST NOT access:

- Dashboard
- Class workspace
- Calendar
- Attendance history
- Manual attendance
- Full student database
- Analytics
- Settings
- Class creation
- Roster import
- Archived classes

Back navigation must not expose the Teacher navigation stack.

Only attendance-related states are permitted.

---

# 8. Attendance Session State Model

Conceptual states:

```text
IDLE
 ↓
INITIALIZING
 ↓
READY
 ↓
FACE_ACQUIRING
 ↓
QUALITY_CHECKING
 ↓
LIVENESS_CHECKING
 ↓
RECOGNIZING
 ↓
┌──────────────┬──────────────┬───────────────┐
│              │              │
SUCCESS       RETRY        UNKNOWN
│                             │
↓                             ↓
READY                     ENROLLMENT
                              │
                              ↓
                         ENROLLMENT_SUCCESS
                              │
                              ↓
                            READY
```

Additional states include:

```text
ALREADY_PRESENT
TEACHER_ASSISTANCE
ENDING
ENDED
ERROR
```

Exact transitions belong to the Session Engine LLD.

---

# 9. Dashboard

The main screen displays active classes.

Example:

```text
Attendance Tracker

Your Classes

┌─────────────────────────────┐
│ Operating Systems           │
│ CSE • 7th A                 │
│                             │
│ 18 Classes • 64 Students    │
└─────────────────────────────┘

┌─────────────────────────────┐
│ DBMS                        │
│ CSE • 7th A                 │
│                             │
│ 16 Classes • 64 Students    │
└─────────────────────────────┘

        + CREATE CLASS
```

Archived classes are separated from active classes.

---

# 10. Class Creation

Teacher provides:

- Subject/class name
- Section
- Semester/batch
- Required attendance percentage
- Student roster

Class creation should use a short, polished flow rather than a large complex form.

---

# 11. Student Roster Import

Supported:

```text
CSV
XLSX
Manual Addition
```

Example:

```text
Sl No | Roll Number | Name
1     | B124001     | Aman Singh
2     | B124002     | Rahul Das
3     | B124003     | Amit Kumar
```

Import flow:

```text
Select File
    ↓
Parse
    ↓
Detect/Map Columns
    ↓
Validate
    ↓
Preview
    ↓
Teacher Confirms
    ↓
Transactional Import
```

Possible recognized column names may include:

```text
Roll
Roll Number
Student ID
ID

Name
Student Name

Sl No
S.No
Serial
```

Constraint:

```text
UNIQUE(classId, rollNumber)
```

---

# 12. Student Enrollment State

Imported students initially have:

```text
enrollmentStatus = NOT_ENROLLED
```

After successful enrollment:

```text
enrollmentStatus = ENROLLED
enrolledAt = timestamp
```

An ENROLLED student must have at least one valid FaceTemplate.

---

# 13. First-Time Student Flow

Student presses:

```text
CHECK IN
```

Recognition is attempted first.

For an enrollment-heavy initial class, the product may use one recognition attempt.

For normal subsequent sessions:

```text
Maximum normal recognition attempts = 2
```

If no enrolled identity is recognized:

```text
We don't recognize you yet.

[ CREATE MY PROFILE ]

Already registered?
[ ASK TEACHER ]
```

---

# 14. New Profile Selection

`CREATE MY PROFILE` displays only:

```text
enrollmentStatus == NOT_ENROLLED
```

Example:

```text
SELECT YOUR PROFILE

B124017   Aman Singh
B124021   Rahul Das
B124034   Priya Sharma
```

Already-enrolled identities are never exposed in this list.

---

# 15. Enrollment Pipeline

```text
Camera
   ↓
Face Detection
   ↓
Tracking
   ↓
Frame Selection
   ↓
Face Quality
   ↓
Liveness
   ↓
Collect 3–5 Good Observations
   ↓
Generate Embeddings
   ↓
Duplicate Identity Check
   ↓
Encrypt Face Templates
   ↓
Atomic Enrollment + PRESENT
```

Enrollment does not persist arbitrary selfies.

---

# 16. Face Quality

Signals:

| Requirement | Signal                           |
| ----------- | -------------------------------- |
| Pose        | ML Kit Euler angles              |
| Eyes        | ML Kit eye-open probabilities    |
| Face size   | Bounding-box/frame ratio         |
| Framing     | Bounding-box position            |
| Blur        | Laplacian variance               |
| Lighting    | Face-region brightness/histogram |
| Face count  | ML Kit detections                |

Starting pose experimentation may begin around approximately ±15–20°, but exact
cutoffs are calibration parameters.

Poor-quality enrollment must be rejected before templates are stored.

---

# 17. Multi-Template Face Storage

Each student stores multiple face embeddings.

Recommended initial enrollment:

```text
3–5 high-quality templates
```

Do not collapse the architecture to a mandatory single embedding.

Recognition compares the query against all templates belonging to each student.

---

# 18. FaceTemplate Schema

Conceptual Room entity:

```text
FaceTemplate

id
studentId
encryptedEmbedding
qualityScore
capturedAt
source
```

Possible source:

```text
ENROLLMENT
REENROLLMENT
APPENDED
```

`APPENDED` is reserved for future adaptive enrollment and is disabled for MVP.

Relationship:

```text
Student
   │
   │ 1:N
   ↓
FaceTemplate
```

---

# 19. Opportunistic Template Learning

Post-MVP architecture may support adding high-confidence observations over time.

MVP MUST NOT automatically append recognized faces.

Reason:

```text
False recognition
       ↓
Bad template appended
       ↓
Identity profile poisoned
       ↓
Future recognition worsens
```

The schema supports the feature without enabling it.

---

# 20. Duplicate Identity Protection

Before enrollment:

```text
New Face Templates
       ↓
Compare against templates
of every ENROLLED student
       ↓
Suspiciously close match?
```

If yes:

```text
Enrollment blocked
       ↓
Teacher verification required
```

This reduces the risk of one student selecting another unenrolled identity.

---

# 21. Face Recognition Model

MVP model direction:

```text
MobileFaceNet
ArcFace-trained
TFLite
```

Reasons:

- Mobile optimized
- Small model
- Established Android/TFLite deployment history
- Appropriate MVP integration complexity

Potential future upgrade:

```text
GhostFaceNets
```

Newer alternatives may be revisited after MVP benchmarking.

Published benchmark accuracy is not considered sufficient evidence for classroom
deployment. The selected model must be validated against real captured pilot data.

---

# 22. Recognition Pipeline

```text
Camera
   ↓
Face Detection / Tracking
   ↓
Best Frame Selection
   ↓
Quality Gate
   ↓
Liveness
   ↓
MobileFaceNet
   ↓
Embedding
   ↓
Normalization
   ↓
Template Matching
   ↓
Candidate Ranking
   ↓
Decision Engine
```

---

# 23. Recognition Matching

For each student:

```text
Student A
 ├── Template A1
 ├── Template A2
 ├── Template A3
 └── Template A4
```

Calculate cosine similarity between the query embedding and each template.

The student's identity score is based on their strongest relevant template match
for the MVP design.

Then rank students:

```text
Best Student
Best Score

Second-Best Student
Second-Best Score

Margin =
Best Score - Second-Best Score
```

---

# 24. Recognition Decision

Decision must not use only:

```text
highest similarity > threshold
```

It should incorporate:

```text
Face Quality
+
Liveness
+
Best Similarity
+
Second-Best Similarity
+
Best-vs-Second Margin
```

Possible outcomes:

```text
ACCEPTED
AMBIGUOUS
UNKNOWN
```

Thresholds remain calibrated configuration values.

---

# 25. Threshold Calibration

Production thresholds must be derived from real pilot data.

Method:

```text
Collect genuine pairs
+
Collect impostor pairs
       ↓
Calculate similarity distributions
       ↓
Measure FAR / FRR
       ↓
Find EER reference
       ↓
Bias threshold toward lower false accepts
       ↓
Validate real classroom performance
```

Attendance has asymmetric costs:

```text
False Reject
→ retry / teacher assistance

False Accept
→ wrong student marked PRESENT
```

Therefore false accepts should generally be treated as the more serious recognition error.

Any example threshold range used during development is an experimental sweep point,
not a production guarantee.

Best-vs-second margin is calibrated alongside similarity.

---

# 26. Fairness Validation

Recognition must be tested against the actual pilot population.

Validation should examine performance across relevant appearance/lighting variation
rather than relying only on published benchmark numbers.

One global threshold must not be assumed fair simply because aggregate accuracy is high.

---

# 27. Liveness / Anti-Spoof

Target architecture:

```text
Camera
   ↓
Passive RGB Anti-Spoof
   ↓
High confidence?
   │
   ├── YES → Continue
   │
   └── AMBIGUOUS
           ↓
     Active Challenge
           ↓
        PASS/FAIL
```

MVP candidate:

```text
Silent-Face-Anti-Spoofing
```

The Face Spike must validate its Android/TFLite feasibility.

If passive liveness proves unreliable or too expensive for MVP:

```text
Active challenge
```

becomes the temporary MVP fallback rather than delaying the entire product.

Test attacks:

- Printed photo
- Photo displayed on phone
- Tablet display
- Replay/video attacks where practical

The product must never claim equivalence to hardware depth-sensing Face Unlock.

---

# 28. Camera Performance Architecture

Heavy ML inference must not run on every camera frame.

Wrong:

```text
30 camera frames/sec
        ↓
30 MobileFaceNet inferences/sec
```

Correct:

```text
Camera Preview
      ↓
Lightweight detection/tracking
      ↓
Discard redundant frames
      ↓
Discard poor frames
      ↓
Select useful observation
      ↓
Run expensive pipeline
```

Goals:

- Low latency
- Low heat
- Low battery usage
- Smooth camera preview
- Stable full-class operation

---

# 29. Active-Class Template Cache

At session start:

```text
Room
 ↓
Load ENROLLED students
 ↓
Load FaceTemplates
 ↓
Decrypt
 ↓
Keep active-class templates in memory
```

Recognition operates primarily against the in-memory cache.

Do not query/decrypt the database separately for every recognition candidate.

At session end:

```text
Clear sensitive template cache
```

---

# 30. Successful Check-In

Once recognized:

```text
Identity Accepted
      ↓
Check duplicate
      ↓
Persist PRESENT immediately
      ↓
Show success
```

Example:

```text
            ✓

    ATTENDANCE MARKED

       AMIT KUMAR
        B124003

        PRESENT
        10:07 AM
```

Desired feedback:

- Smooth success animation
- Large student name
- Roll number
- Timestamp
- Short sound
- Subtle haptic feedback

After approximately 1.5–2 seconds:

```text
READY FOR NEXT STUDENT
```

---

# 31. Duplicate Attendance

Constraint:

```text
UNIQUE(sessionId, studentId)
```

If the student is already PRESENT:

```text
       ALREADY CHECKED IN

          AMIT KUMAR
           B124003

       Checked in 10:07 AM
```

No duplicate record is created.

---

# 32. Atomic Enrollment

Enrollment and first attendance are one transaction.

```text
BEGIN TRANSACTION

Verify NOT_ENROLLED
       ↓
Insert FaceTemplates
       ↓
Set enrollmentStatus = ENROLLED
       ↓
Set enrolledAt
       ↓
Create PRESENT AttendanceRecord
       ↓
COMMIT
```

Failure at any point:

```text
ROLLBACK
```

Partial enrollment must not exist.

---

# 33. Failed Recognition of Existing Student

After normal retry attempts:

```text
Recognition failed
       ↓

Not registered yet?
[ CREATE PROFILE ]

Already registered?
[ ASK TEACHER ]
```

An already-enrolled student cannot select themselves or another enrolled identity
through Create Profile.

---

# 34. Teacher Assistance

```text
ASK TEACHER
     ↓
Teacher Assistance Required
     ↓
Teacher Authentication
     ↓
Teacher selects student
     ↓
┌────────────────────────────┐
│ Mark Present               │
│ Re-enroll Face             │
│ Cancel                     │
└────────────────────────────┘
```

Teacher-facing information remains protected behind authentication.

---

# 35. Face Re-Enrollment

Teacher may initiate:

```text
Student
  ↓
Manage Face Profile
  ↓
RE-ENROLL
  ↓
Teacher Authentication
  ↓
Quality Capture
  ↓
Liveness
  ↓
Generate New Templates
  ↓
Replace Previous Templates
```

Historical attendance remains attached to the same Student ID.

---

# 36. Attendance Persistence

Every successful attendance event is written immediately.

Never wait until End Session to save PRESENT students.

```text
Student recognized
      ↓
Room transaction
      ↓
Successful DB write
      ↓
Success animation
```

This allows process/session recovery.

---

# 37. Attendance Sessions

Session status:

```text
ACTIVE
ENDED
ABORTED
```

Mode:

```text
FACE
MANUAL
```

Multiple sessions on the same date are supported.

Example:

```text
06 Aug
10:00 → Session 31
14:00 → Session 32
```

Both count independently.

---

# 38. Session Recovery

If the process dies with an ACTIVE session:

```text
Application starts
      ↓
Check Room
      ↓
ACTIVE session found
      ↓

Attendance session interrupted

Operating Systems
37 students already recorded

[ RESUME ]

[ END SESSION ]
```

Already persisted attendance remains intact.

---

# 39. Session Finalization

During ACTIVE attendance:

```text
Recognized student
→ PRESENT

Student who has not checked in
→ no final ABSENT yet
```

When End Session succeeds:

```text
Find all eligible students
without PRESENT
       ↓
Write ABSENT
       ↓
Session status = ENDED
```

A finalized session therefore has deterministic PRESENT/ABSENT state for all eligible students.

---

# 40. Ending Session

```text
END SESSION 🔒
      ↓
Teacher Authentication
      ↓
Finalize attendance
      ↓
Session = ENDED
      ↓
stopLockTask()
      ↓
Clear sensitive memory
      ↓
Return to Class Workspace
```

Teacher authentication is separate from ordinary student interaction.

---

# 41. Manual Attendance

Teacher can create a normal attendance session manually.

```text
Class
 ↓
Calendar
 ↓
Add Attendance
 ↓
Choose date/time
 ↓

[x] Aman
[x] Rahul
[ ] Amit
[x] Priya

SAVE
```

The session counts exactly like a face-recognition session for attendance calculations.

---

# 42. Manual Corrections

Teacher can open an existing session:

```text
06 AUG

[x] Aman
[x] Rahul
[ ] Amit
[x] Priya
```

Teacher may directly change:

```text
PRESENT ↔ ABSENT
```

No mandatory:

- Reason
- Audit workflow
- Approval
- Complex override system

MVP intentionally keeps manual correction simple.

---

# 43. Classes Held

A valid finalized attendance session counts as one class.

```text
Classes Held =
number of valid ENDED sessions
```

Deleting an accidental session removes it from this count and recalculates dependent statistics.

---

# 44. Attendance Percentage

```text
Attendance Percentage =

Eligible PRESENT Sessions
───────────────────────── × 100
Total Eligible Sessions
```

Historical sessions before a student's eligibility start do not automatically count as absences.

---

# 45. Late-Joining Students

Preferred eligibility boundary:

```text
eligibleFromSessionId
```

Example:

```text
Sessions 1–10
student was not eligible

Student joins
      ↓
eligibleFromSessionId = Session 11
```

Percentage begins from the eligible boundary.

Teacher should eventually be able to adjust this boundary if the student actually belonged
to the class earlier but was entered into the application late.

Exact edge-case behavior belongs to Attendance Domain LLD.

---

# 46. Calendar

Calendar indicates dates containing attendance.

Example:

```text
August 2026

Mo Tu We Th Fr Sa Su

             1  2
 3  4  5 [6] 7  8  9
10 11 12 13 14 15 16
```

Selecting a date displays its sessions.

```text
6 August

10:00 AM
Operating Systems

Present 52
Absent 12

[ OPEN ATTENDANCE ]
```

Multiple sessions on the same day appear separately.

---

# 47. Student Statistics

Example:

```text
64 Students
18 Classes Held

Aman Singh
B124001
16 / 18
88.9%

Rahul Das
B124002
14 / 18
77.8%

Amit Kumar
B124003
12 / 18
66.7%
Below required 75%
```

---

# 48. Class Lifecycle

Normal removal:

```text
ARCHIVE
```

rather than destructive deletion.

Archived classes:

- disappear from normal Dashboard
- retain students
- retain attendance
- retain history
- retain statistics
- can be viewed separately

---

# 49. Student Lifecycle

Students are normally archived/deactivated rather than deleting historical records.

Changing:

```text
Name
Roll Number
```

does not change internal Student ID.

Historical attendance therefore remains valid.

---

# 50. Logical Database Schema

Core entities:

```text
Teacher
ClassSection
Student
FaceTemplate
AttendanceSession
AttendanceRecord
```

Relationships:

```text
TEACHER
   │
   └── CLASS
        │
        ├── STUDENT
        │     │
        │     └── FACE TEMPLATE
        │
        └── ATTENDANCE SESSION
                    │
                    └── ATTENDANCE RECORD
                              │
                              └── STUDENT
```

---

# 51. Teacher Entity

Conceptually:

```text
Teacher

id
name
pinHash
createdAt
```

---

# 52. ClassSection Entity

```text
ClassSection

id
teacherId

name
subject
section
semesterBatch

requiredAttendancePercent

archived

createdAt
updatedAt
```

---

# 53. Student Entity

```text
Student

id
classId

name
rollNumber
serialNumber

enrollmentStatus

enrolledAt

eligibleFromSessionId

archived

createdAt
updatedAt
```

Student no longer contains a single face embedding.

Constraint:

```text
UNIQUE(classId, rollNumber)
```

---

# 54. FaceTemplate Entity

```text
FaceTemplate

id
studentId

encryptedEmbedding

qualityScore

capturedAt

source
```

Relationship:

```text
Student 1:N FaceTemplate
```

---

# 55. AttendanceSession Entity

```text
AttendanceSession

id
classId

date
startTime
endTime

status

mode

createdAt
updatedAt
```

---

# 56. AttendanceRecord Entity

```text
AttendanceRecord

id

sessionId
studentId

status

checkInTime

matchConfidence
recognitionMetadata

createdAt
updatedAt
```

Status:

```text
PRESENT
ABSENT
```

Constraint:

```text
UNIQUE(sessionId, studentId)
```

Recognition confidence is internal and should not be shown to students.

---

# 57. Screen Pinning Strategy

MVP uses Android Screen Pinning.

It does NOT use Device Owner / fully managed kiosk mode because the application is designed
for the teacher's existing personal phone and must not require device reset/reprovisioning.

Architecture:

```text
Screen Pinning
+
Secure unpin configuration
+
In-app teacher authenticated exit
```

---

# 58. Starting Attendance

Teacher should not manually use Android Recents to pin the application every time.

```text
TAKE ATTENDANCE
      ↓
Verify prerequisites
      ↓
Create ACTIVE session
      ↓
startLockTask()
      ↓
Android pinning confirmation if required
      ↓
Pinned Session UI
```

---

# 59. Pinning Prerequisites

Before starting:

```text
✓ Secure device lock
✓ Screen Pinning enabled
✓ Authentication required for unpinning
✓ Camera permission
✓ Teacher app authentication configured
✓ Required model/resources available
```

Attendance start is blocked if critical prerequisites are missing.

---

# 60. First-Run Onboarding

Goal: eliminate permission/setup interruptions during class.

```text
Welcome
 ↓
Camera Permission
 ↓
Teacher PIN
 ↓
Biometric Setup/Check
 ↓
Device Lock Check
 ↓
Screen Pinning Guidance
 ↓
Secure Unpin Guidance
 ↓
Battery / OEM Guidance
 ↓
Model Initialization
 ↓
READY
```

The app guides the teacher into Android settings where automatic configuration is not permitted.

---

# 61. Tamper Detection

A brittle mandatory `onPause()` alarm is NOT part of MVP.

Primary security boundary:

```text
Screen Pinning
+
Secure unpin authentication
```

Tamper detection may be reconsidered after OEM testing.

---

# 62. OEM Pinning Validation

Test at least representative:

```text
Pixel / Stock Android
Samsung / One UI
Xiaomi / HyperOS
Vivo / Funtouch OS
Oppo / ColorOS where available
```

Test:

```text
Home
Back
Recents

Gesture navigation
Button navigation

Notification shade

System unpin gesture

Authentication barrier

BiometricPrompt

stopLockTask()

Process interruption
```

---

# 63. Teacher Authentication

Teacher-only operations include:

```text
End Session
Teacher Assistance
Face Re-enrollment
```

Authentication:

```text
BiometricPrompt
or
App PIN
```

The app-level PIN is separate from the phone's normal lock-screen flow.

---

# 64. Encryption

Face templates are sensitive biometric data.

At-rest encryption uses Android Keystore-backed cryptography.

Conceptually:

```text
Face Embedding
     ↓
EmbeddingCipher
     ↓
Encrypted BLOB
     ↓
Room
```

Decryption occurs only when needed.

Active-class templates may be held temporarily in memory during attendance and cleared afterwards.

Exact cryptographic implementation belongs to Security LLD.

---

# 65. Reporting / Export

Teacher can export attendance by:

- Class
- Session/date
- Student
- Date range where appropriate

Formats:

```text
CSV
XLSX-compatible export
```

Example:

```text
Roll      Name          06 Aug   08 Aug   10 Aug   %
B124001   Aman Singh       P        P        A     66.7
B124002   Rahul Das        P        P        P    100.0
```

---

# 66. Backup Architecture

MVP remains local-first.

Architecture should allow later:

```text
ROOM
 ↓
Backup Repository
 ↓
Encrypted Backup
```

Future synchronization:

```text
ROOM
 ↓
Sync Engine
 ↓
Encrypted Cloud
```

Potential sync data:

- Classes
- Students
- Face templates
- Attendance sessions
- Attendance records
- Eligibility
- Relevant settings

Never raw face photographs.

---

# 67. Device Migration

Post-MVP:

```text
Old Phone
   ↓
Encrypted Backup
   ↓
Teacher authenticates
   ↓
New Phone
   ↓
Restore
```

Goal:

Students should not need to re-enroll simply because the teacher changes devices.

---

# 68. Backup Encryption

Device encryption and portable backup encryption are different.

```text
Android Keystore
      ↓
Device-specific protection
```

A non-exportable Keystore key cannot simply be moved to another phone.

Future migration therefore requires a separate portable encrypted backup/key-wrapping architecture.

This remains post-MVP.

---

# 69. Privacy Principles

1. Do not retain raw face photographs.
2. Store only required biometric templates.
3. Encrypt templates at rest.
4. Perform recognition on-device for MVP.
5. Re-enrollment replaces previous templates.
6. Do not silently collect additional templates in MVP.
7. Future cloud backup must protect biometric data.
8. Define biometric retention/deletion rules before real deployment.
9. Address applicable notice/consent/institutional requirements before production use.

---

# 70. Error Handling Principles

Examples:

### No Face

```text
Position your face inside the frame
```

### Multiple Faces

```text
Only one person at a time
```

### Poor Lighting

```text
Move to better lighting
```

### Blur

```text
Hold still for a moment
```

### Poor Pose

```text
Look toward the camera
```

### Liveness Failure

```text
Verification failed.
Please try again.
```

Do not reveal detailed anti-spoof internals.

### Recognition Uncertain

```text
We couldn't confidently recognize you.

Please try again.
```

### Face Engine Failure

```text
Face verification is temporarily unavailable.

Ask your teacher for assistance.
```

Manual attendance provides graceful fallback.

---

# 71. Non-Functional Requirements

## Performance

Normal enrolled check-in target:

```text
~2–3 seconds
```

under normal classroom conditions.

## Reliability

Attendance must be persisted immediately.

Process interruption must not erase already recorded attendance.

## Offline

Core attendance works without internet.

## Privacy

Raw images are not retained.

## Security

Biometric templates encrypted at rest.

## Compatibility

Minimum Android:

```text
API 26 / Android 8.0
```

subject to final dependency validation.

## Thermal Efficiency

Heavy inference is throttled/selective rather than frame-by-frame.

## Failure Handling

Camera/model/database failures must produce recoverable states rather than crashes.

---

# 72. Testing Strategy

Testing occurs continuously during implementation.

## Unit Tests

- Attendance percentages
- Eligibility
- Duplicate prevention
- Session finalization
- Manual correction
- Archive behavior
- Recognition decision logic
- State transitions

## Room Tests

- Enrollment transaction
- Re-enrollment
- Attendance insertion
- Unique constraints
- Session deletion
- ABSENT finalization
- Recovery
- Multiple sessions/day

## Face Tests

- Same student
- Different students
- Similar-looking students
- Different lighting
- Glasses
- Pose
- Appearance variation
- Blur
- Exposure

## Anti-Spoof Tests

- Printed photo
- Phone display
- Tablet display
- Replay/video

## Performance Tests

Measure:

```text
Latency
Memory
CPU
Battery
Thermal behavior
Model initialization
Long-session stability
```

## Security Tests

- Unauthorized session exit
- Identity misuse
- Template extraction risk
- Authentication boundaries
- Sensitive-data lifecycle

## OEM Tests

Validate pinning behavior across manufacturers.

## UAT

Start with:

```text
1 teacher
+
small student group
```

Then expand to a full classroom pilot.

---

# 73. Technical Spikes

Two high-risk technical areas must be validated early.

## Spike A — Screen Pinning

Prototype:

```text
startLockTask()
 ↓
Home / Back / Recents
 ↓
Unpin attempts
 ↓
Secure authentication
 ↓
BiometricPrompt
 ↓
stopLockTask()
```

Test on multiple OEMs.

## Spike B — Face Pipeline

Prototype:

```text
CameraX
 ↓
ML Kit
 ↓
Face Quality
 ↓
Liveness
 ↓
MobileFaceNet
 ↓
Embedding
 ↓
Multi-template matching
```

Measure:

```text
Latency
Accuracy
CPU
Memory
Heat
Model compatibility
```

Spike results feed into the corresponding LLDs.

---

# 74. Package Direction

Conceptually:

```text
com.app.attendance/

├── ui/
│   ├── onboarding/
│   ├── dashboard/
│   ├── classsetup/
│   ├── classdetail/
│   ├── students/
│   ├── calendar/
│   ├── session/
│   ├── enrollment/
│   ├── history/
│   └── settings/
│
├── domain/
│   ├── attendance/
│   ├── student/
│   ├── enrollment/
│   └── session/
│
├── data/
│   ├── entity/
│   ├── dao/
│   ├── database/
│   └── repository/
│
├── camera/
│
├── face/
│   ├── detection/
│   ├── quality/
│   ├── liveness/
│   ├── embedding/
│   ├── matching/
│   └── decision/
│
├── lockdown/
│
├── security/
│
├── importexport/
│
├── backup/
│
└── di/
```

Exact package/module boundaries will be frozen during LLD.

---

# 75. LLD Decomposition

The HLD is now decomposed into 14 implementation-level design units.

```text
LLD-01
Application Foundation & Navigation

LLD-02
Database & Persistence

LLD-03
Class & Student Management

LLD-04
CSV/XLSX Roster Import

LLD-05
Attendance Domain Engine

LLD-06
Live Attendance Session Engine

LLD-07
Session Recovery

LLD-08
Camera & Frame Processing

LLD-09
Face Quality Engine

LLD-10
Face Enrollment & Template Management

LLD-11
Face Recognition & Decision Engine

LLD-12
Liveness / Anti-Spoof Engine

LLD-13
Lockdown / Authentication / Security

LLD-14
Reporting / Export / Backup / Data Lifecycle
```

---

# 76. LLD Dependency Direction

```text
                    LLD-01
               App Foundation
                     │
                     ↓
                    LLD-02
                  Database
                     │
          ┌──────────┴──────────┐
          ↓                     ↓
       LLD-03                LLD-05
 Class / Students        Attendance Domain
          │                     │
          ↓                     ↓
       LLD-04                LLD-06
       Import            Session Engine
                                │
             ┌──────────────────┼──────────────────┐
             ↓                  ↓                  ↓
          LLD-07             LLD-08             LLD-13
          Recovery           Camera             Security
                                │
                                ↓
                             LLD-09
                          Face Quality
                                │
                     ┌──────────┴──────────┐
                     ↓                     ↓
                  LLD-10                LLD-12
                 Enrollment             Liveness
                     │                     │
                     └──────────┬──────────┘
                                ↓
                             LLD-11
                          Recognition

LLD-14 Reporting / Export / Backup
primarily depends on LLD-02 + LLD-05.
```

---

# 77. What Every LLD Must Specify

Every LLD should contain:

```text
1. Purpose / Responsibility

2. Scope

3. Dependencies

4. Inputs

5. Outputs

6. Classes

7. Interfaces

8. Data Models

9. Public APIs / Methods

10. Internal Algorithms

11. State Machine

12. Database Interaction

13. Transactions

14. Coroutine / Threading Model

15. Lifecycle Handling

16. Error Types

17. Error Recovery

18. Security Considerations

19. Edge Cases

20. Logging / Observability

21. Unit Tests

22. Integration Tests

23. Performance Considerations

24. Definition of Done
```

Not every section needs equal size for every unit, but each must be considered.

---

# 78. Development SDLC

The project uses:

**Architecture-first + risk-first + iterative implementation + continuous testing.**

```text
Requirements
     │
     │ COMPLETE
     ↓
Product Specification
     │
     │ COMPLETE
     ↓
HLD / SDD
     │
     │ COMPLETE
     ↓
HLD FREEZE
     │
     │ CURRENT STATE
     ↓
System Decomposition
     │
     │ COMPLETE
     ↓
Foundation LLDs
     │
     ├── LLD-01
     ├── LLD-02
     ├── LLD-03
     ├── LLD-04
     ├── LLD-05
     └── LLD-13 foundation
     │
     ├────────────────────────────┐
     ↓                            ↓
Foundation Implementation    Technical Spikes
                                  │
                              ┌───┴────┐
                              ↓        ↓
                           Pinning    Face
                              │        │
                              └───┬────┘
                                  ↓
                            Spike Results
                                  ↓
                           Session/ML LLDs
                                  │
                              LLD-06–12
                                  ↓
                       Attendance Implementation
                                  ↓
                              LLD-14
                                  ↓
                          Full Integration
                                  ↓
                          System Validation
                                  ↓
                           ML Calibration
                                  ↓
                          Classroom Pilot
                                  ↓
                        Release Candidate
                                  ↓
                               RELEASE
```

---

# 79. Continuous Testing Rule

Testing does not wait until the application is complete.

For every unit:

```text
LLD
 ↓
IMPLEMENT
 ↓
UNIT TEST
 ↓
INTEGRATION TEST
 ↓
REVIEW
 ↓
DONE
```

---

# 80. Definition of Done

A normal unit is complete when:

```text
[ ] LLD approved
[ ] Interfaces finalized
[ ] Implementation complete
[ ] Unit tests passing
[ ] Integration tests passing
[ ] Edge cases handled
[ ] Error states implemented
[ ] Security considerations reviewed
[ ] Performance acceptable
[ ] Documentation synchronized
[ ] No known critical defects
```

ML components additionally require:

```text
[ ] Latency measured
[ ] Memory measured
[ ] Thermal behavior measured
[ ] Genuine pairs tested
[ ] Impostor pairs tested
[ ] FAR measured
[ ] FRR measured
[ ] Threshold calibrated
[ ] Margin calibrated
[ ] Population/fairness validation
[ ] Spoof scenarios tested
```

---

# 81. Explicitly Out of MVP

Unless requirements change:

- Device Owner / fully managed kiosk
- Factory reset / device reprovisioning
- Mandatory backend
- School-wide admin portal
- QR attendance
- Complex manual-edit audit system
- Manual correction reasons
- Permanent raw face photographs
- Vector database
- Automatic adaptive face-template learning
- Full cloud synchronization
- Device migration implementation
- Multi-device attendance

---

# 82. Remaining Experimental Parameters

These are NOT missing architecture decisions.

They must be measured experimentally.

```text
Exact MobileFaceNet export
Exact preprocessing

Embedding normalization implementation

Recognition accept threshold
Ambiguous boundary
Reject boundary

Best-vs-second margin

Pose threshold
Blur threshold
Brightness threshold
Minimum face size

3 vs 4 vs 5 enrollment templates

Passive liveness threshold
Active fallback trigger

Camera analysis resolution
Inference frequency

Latency
Thermal behavior

FAR
FRR

Population performance
```

These are resolved through:

```text
Technical Spike
+
Pilot Calibration
```

rather than further HLD discussion.

---

# 83. Post-MVP

Potential future development:

- Encrypted cloud synchronization
- Device migration
- Automatic backup
- Multi-device support
- Carefully validated adaptive templates
- Improved recognition models
- Improved anti-spoof models
- School/backend integration

These should not complicate MVP implementation unnecessarily.

---

# 84. Final End-to-End Flow

```text
                        TEACHER
                           │
                           ↓
                       DASHBOARD
                           │
                           ↓
                     SELECT CLASS
                           │
             ┌─────────────┼──────────────┐
             ↓             ↓              ↓
         CALENDAR       STUDENTS     TAKE ATTENDANCE
             │             │              │
             │             │              ↓
             │             │       Prerequisite Check
             │             │              │
             │             │              ↓
             │             │       Create ACTIVE Session
             │             │              │
             │             │              ↓
             │             │        startLockTask()
             │             │              │
══════════════════════════════════════════════════════
                         PINNED MODE
                              │
                              ↓
                            READY
                              │
                              ↓
                         CHECK IN
                              │
                              ↓
                         CAMERA FRAME
                              │
                              ↓
                        FACE DETECTION
                              │
                              ↓
                        QUALITY CHECK
                              │
                              ↓
                         LIVENESS
                              │
                              ↓
                        MOBILEFACENET
                              │
                              ↓
                          EMBEDDING
                              │
                              ↓
                     MULTI-TEMPLATE MATCH
                              │
                              ↓
                      DECISION ENGINE
                              │
             ┌────────────────┴────────────────┐
             ↓                                 ↓
          ACCEPTED                           UNKNOWN
             │                                 │
             ↓                       ┌─────────┴─────────┐
       Already Present?              │                   │
         │         │             New Student?       Registered?
        YES       NO                 │                   │
         │         │                 ↓                   ↓
         │         │           CREATE PROFILE        ASK TEACHER
         │         │                 │                   │
         │         │                 ↓                   ↓
         │         │          Unenrolled List      Authentication
         │         │                 │                   │
         │         │                 ↓              ┌────┴────┐
         │         │            Select Identity     │         │
         │         │                 │           Mark       Re-enroll
         │         │                 ↓          Present       Face
         │         │           Enrollment
         │         │                 │
         │         │                 ↓
         │         │          3–5 Good Templates
         │         │                 │
         │         │                 ↓
         │         │          Duplicate Check
         │         │                 │
         │         │                 ↓
         │         │          Atomic Enrollment
         │         │              + PRESENT
         │         │                 │
         └─────────┴────────────┬────┘
                                ↓
                         SUCCESS FEEDBACK
                                │
                                ↓
                         NEXT STUDENT
                                │
                                └──────────→ READY

══════════════════════════════════════════════════════

                       END SESSION
                            │
                            ↓
                  Teacher Authentication
                            │
                            ↓
             Finalize unchecked eligible
                   students as ABSENT
                            │
                            ↓
                    Session = ENDED
                            │
                            ↓
                      stopLockTask()
                            │
                            ↓
                 Clear Sensitive Cache
                            │
                            ↓
                    CLASS WORKSPACE
```

---

# 85. Project Status

```text
Requirements                 ✅ FROZEN
Product Flow                 ✅ FROZEN
Core Architecture            ✅ FROZEN
HLD / SDD                    ✅ FROZEN
System Decomposition         ✅ FROZEN

LLD                          🔵 STARTING NOW

Implementation               ⏳ Begins incrementally
Technical Spikes             ⏳ Begin alongside early LLD
Integration                  ⏳
Pilot                        ⏳
Release                      ⏳
```

---

# 86. Next Step

The HLD should no longer be continuously redesigned unless:

1. an LLD exposes a genuine architectural contradiction;
2. a technical spike proves an assumption invalid; or
3. a genuine new product requirement appears.

Otherwise new ideas should enter the backlog rather than destabilizing the MVP architecture.

The immediate engineering sequence is:

```text
SDD / HLD FREEZE
        ↓
LLD-01
Application Foundation & Navigation
        ↓
Review / Freeze
        ↓
LLD-02
Database & Persistence
        ↓
Review / Freeze
        ↓
Continue through dependency-ordered LLDs
        ↓
Implementation proceeds incrementally
```

**This SDD is the canonical HLD baseline for the Attendance Tracker project.**
