# Biometric pipeline evaluation (offline, mock data)

Runs the app's **shipped TFLite models** through a line-faithful Python port of the
Kotlin pipeline, on public face datasets, to measure what the phone actually does.
Findings and the rebuild plan: [`docs/18-attendance-pipeline-root-cause-and-rebuild-plan.md`](../../docs/18-attendance-pipeline-root-cause-and-rebuild-plan.md).

```bash
pip install ai-edge-litert pyarrow opencv-python pillow numpy
python tools/biometric_eval/extract_datasets.py   # ~1.5 GB download into .datasets/ (git-ignored)
python tools/biometric_eval/build_feature_cache.py  # detector+aligner+ArcFace once per image (~6 min)
python tools/biometric_eval/exp1_pose_sweep.py      # why LEFT/RIGHT never pass
python tools/biometric_eval/exp2b_center_face.py    # recogniser health, RGB vs BGR
python tools/biometric_eval/exp3_pose_estimators.py # replacement yaw estimator
python tools/biometric_eval/exp4_turn_templates.py  # value of turn templates
python tools/biometric_eval/calibrate.py            # thresholds on disjoint identities
python tools/biometric_eval/simulate_classroom.py   # Day1/2/3 class: CURRENT vs PROPOSED
```

| File | Purpose |
|---|---|
| `app_pipeline.py` | Port of YoloFaceDetector / FaceAligner / EmbeddingEngine / FaceQualityEngine / EnrollmentBatchValidator / IdentityScorer with `main` constants |
| `extract_datasets.py` | Pointing'04 head-pose (HF `StevenLe456/head-pose`) + LFW (HF `bitmind/lfw`) → JPEG folders |
| `simulate_classroom.py` | 60-student mock class, 8 Day-1 absentees, 120 strangers, 3 days, 5 random classes |
| `results/*.json`, `results/final_simulation.txt` | Raw outputs quoted in doc 18 |

Datasets are research data, used for testing only. They are never committed and never
placed in the APK or a production database.
