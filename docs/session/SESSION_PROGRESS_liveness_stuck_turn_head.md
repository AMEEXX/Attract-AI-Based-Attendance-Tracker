# Session progress — "Turn your head slightly" stuck forever (branch genspark_ai_developer_backlight_spoof)

## Status log
- [x] Checked out origin/genspark_ai_developer_backlight_spoof (HEAD 3068d31)
- [x] Located message source: AttendanceStrings.LIVENESS_TURN_HEAD_* shown when
      LivenessOrchestrator.evaluate() -> RiskFusion MEDIUM -> Rejected(ACTIVE_CHECK_REQUIRED) and no challenge running
      (= depthLive false). Files: domain/face/liveness/{LivenessOrchestrator,RiskFusion,ParallaxDepthTest,Homography}.kt,
      feature/attendance/{AttendanceScreen.kt ~L1079, ~L1718 (onFrame feed), CameraPreview.kt ~L150-430}
- [ ] Root-cause analysis (in progress)
- [ ] Write docs/23 implementation plan
