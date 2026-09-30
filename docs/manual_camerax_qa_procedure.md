# Manual CameraX & On-Device Face Biometrics QA Protocol

**Document Version:** 1.0  
**Target:** Native Android Hardware (CameraX + ML Kit + MobileFaceNet)

---

## 🎯 Objective

Validate that a real student standing in front of an actual phone running Attract can successfully complete the entire pipeline:
`Front Camera Frame → CameraX → ML Kit Detection → Quality Gate → Liveness Check → Face Crop → MobileFaceNet Embeddings → Template Match → PRESENT Attendance Record`.

---

## 📋 On-Device Manual Test Checklist

### 1. Enrollment Flow Verification
- [ ] **Step 1 (STRAIGHT):** Position face frontal in normal indoor lighting. Tap CLICK. Verify prompt advances to Step 2.
- [ ] **Step 2 (LEFT PROFILE):** Turn head ~30° LEFT. Tap CLICK. Verify prompt advances to Step 3.
- [ ] **Step 3 (RIGHT PROFILE):** Turn head ~30° RIGHT. Tap CLICK. Verify notification "ENROLLED & PRESENT: [Student Name]".

### 2. Multi-Angle Attendance Recognition
- [ ] **Straight Check-in:** Face camera straight. Verify immediate match ("PRESENT: [Student Name]").
- [ ] **Left Angle Check-in:** Face camera at ~20° left. Verify match against multi-angle templates.
- [ ] **Right Angle Check-in:** Face camera at ~20° right. Verify match against multi-angle templates.
- [ ] **Repeat Check-in:** Face camera again within same session. Verify status "Already Checked In: [Student Name]" (no duplicate attendance record).

### 3. Edge Conditions & Production Gating
- [ ] **Low Light / Dark Environment:** Cover main light source or face camera in dark room. Verify status "Lighting too low for biometric liveness verification" or "Dark face".
- [ ] **Distance / Small Face:** Stand 3+ meters away so face occupies < 10% of frame. Verify status "Move closer to camera" (`TOO_SMALL`).
- [ ] **Off-Center Framing:** Position face at extreme left or right edge of camera view. Verify status "Center face in box" (`OFF_CENTER`).
- [ ] **Multiple Faces:** Have two people stand in camera view simultaneously. Verify status "Multiple faces detected — only one student permitted" (`MULTIPLE_FACES`).
- [ ] **No Face:** Point camera at empty background. Verify "No face detected" (`NO_FACE`).

### 4. Spoof & Anti-Attack Verification
- [ ] **Printed Photo Replay:** Hold a high-resolution printed color photograph of an enrolled student in front of camera. Verify rejection ("Photo detected" or "Spoofing detected — live natural movement required").
- [ ] **Phone Screen Replay:** Hold a smartphone/tablet playing video/photo of an enrolled student. Verify rejection ("Screen glare detected" or "Screen detected").

---

## 📊 Pass / Fail Criteria

| Protocol Step | Required Behavior | Result |
|---|---|---|
| Enroll + Recognize | Correct Student ID marked PRESENT | PASS / FAIL |
| Duplicate Check-in | "Already Checked In" shown, exactly 1 DB record | PASS / FAIL |
| Photo/Screen Replay | Rejected by LivenessEngine, 0 attendance records | PASS / FAIL |
| Unenrolled Face | Routed to enrollment sheet or UNKNOWN | PASS / FAIL |
