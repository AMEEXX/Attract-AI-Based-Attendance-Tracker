# Session progress — "Turn your head slightly" stuck forever (branch genspark_ai_developer_backlight_spoof)

## Status log
- [x] Checked out origin/genspark_ai_developer_backlight_spoof (base HEAD 3068d31)
- [x] Located message source: AttendanceScreen.captureClick -> LivenessOrchestrator.evaluate -> RiskFusion Rule 9 MEDIUM
      -> guidanceFor(!depthLive) = TURN_HEAD -> LIVENESS_TURN_HEAD_* strings.
- [x] Root-cause analysis DONE -> docs/23-liveness-stuck-turn-head-root-cause-and-fix-plan.md
      RC-1 mesh null (silent catch; likely R8 missing protobuf keep rules) | RC-2 history wiped by track churn (400ms gap)
      & faceCount resets | RC-3 no latch, 3s window prunes the turn before capture | RC-4 8deg gate vs MediaPipe yaw
      under-read (real 15deg -> p50 11.6deg) | RC-5 residual in anisotropic normalised coords, 4-pt contour anchors |
      RC-6 challenge only reachable after depthLive; first timeout = HIGH | RC-7 no escape hatch.
- [x] Implementation plan written (WP-0..WP-9, tests, device acceptance, policy v6 table).
- [ ] NEXT: confirm on device with `adb logcat | grep -E "FaceMeshEngine|\[LIVENESS\]"` (doc 23 §3 table).
- [ ] NEXT: implement WP-0 + WP-1.1 (R8 keep rules), then WP-2 + WP-3 (latch), then WP-4 (ParallaxDepthTest v2).

## Notes for next agent
- No env setup was needed; mediapipe is NOT installed in sandbox (don't install; exp11 must run where .datasets exist).
- Key files: domain/face/liveness/*.kt, domain/face/{LivenessPolicy,FaceTracker,FaceMeshResult,HeadPose}.kt,
  platform/face/FaceMeshEngine.kt, feature/attendance/{AttendanceScreen,CameraPreview}.kt, app/proguard-rules.pro
