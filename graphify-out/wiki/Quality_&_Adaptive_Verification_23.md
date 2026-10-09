# Quality & Adaptive Verification

> 17 nodes

## Key Concepts

- **sys** (16 connections)
- **build_feature_cache.py** (11 connections) — `tools/biometric_eval/build_feature_cache.py`
- **download_models.py** (8 connections) — `scripts/download_models.py`
- **feat()** (6 connections) — `tools/biometric_eval/build_feature_cache.py`
- **inspect_tflite.py** (4 connections) — `scripts/inspect_tflite.py`
- **report_phone_db.py** (4 connections) — `scripts/report_phone_db.py`
- **main()** (3 connections) — `scripts/download_models.py`
- **inspect_model()** (3 connections) — `scripts/inspect_tflite.py`
- **download_file()** (2 connections) — `scripts/download_models.py`
- **rows()** (2 connections) — `scripts/report_phone_db.py`
- **e()** (2 connections) — `tools/biometric_eval/build_feature_cache.py`
- **main()** (2 connections) — `tools/biometric_eval/build_feature_cache.py`
- **Read-only Room proof report for the phone acceptance DB pull.** (1 connections) — `scripts/report_phone_db.py`
- **Runs detector + aligner + recogniser ONCE per image and caches everything the…** (1 connections) — `tools/biometric_eval/build_feature_cache.py`
- **hashlib** (1 connections)
- **sqlite3** (1 connections)
- **tflite** (1 connections)

## Relationships

- [Biometrics & Enrollment](Biometrics_&_Enrollment.md) (18 shared connections)
- [Quality & Adaptive Verification](Quality_&_Adaptive_Verification.md) (4 shared connections)
- [Lockdownmodelskt Blocked](Lockdownmodelskt_Blocked.md) (1 shared connections)
- [Settingsgradlekts](Settingsgradlekts.md) (1 shared connections)

## Source Files

- `scripts/download_models.py`
- `scripts/inspect_tflite.py`
- `scripts/report_phone_db.py`
- `tools/biometric_eval/build_feature_cache.py`

## Audit Trail

- EXTRACTED: 45 (98%)
- INFERRED: 1 (2%)
- AMBIGUOUS: 0 (0%)

---

*Part of the graphify knowledge wiki. See [index](index.md) to navigate.*