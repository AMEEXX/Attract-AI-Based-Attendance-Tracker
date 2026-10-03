# Developer & Diagnostic Utilities (`scripts/`)

This directory contains standalone Python, PowerShell, and batch tools used for model management, device automation, and biometric evaluation.

---

## Tool Catalog

| Tool | Environment | Description |
|---|---|---|
| [`download_models.py`](download_models.py) | Python 3 | Downloads, verifies SHA-256 hashes, and places `yolov8n_face.tflite` and `arcface_mobilefacenet.tflite` into `app/src/main/assets/`. |
| [`inspect_tflite.py`](inspect_tflite.py) | Python 3 + LiteRT | Dumps tensor shapes, input layouts, data types, and quantization metadata for all committed TFLite assets. |
| [`test_models_inference.py`](test_models_inference.py) | Python 3 + Pillow | Runs offline biometric inference benchmarks, evaluates similarity matrices, and measures genuine/impostor separation at production threshold (`0.25f`). |
| [`prepare_face_test_data.py`](prepare_face_test_data.py) | Python 3 | Generates and splits testing datasets across straight, left, and right angular observations for enrollment and verification testing. |
| [`prepare_face_test_data.ps1`](prepare_face_test_data.ps1) | PowerShell | Automates unzipping and staging of test datasets into `test-data/real-faces/`. |
| [`analyze_lfw_benchmark.py`](analyze_lfw_benchmark.py) | Python 3 | Performs metric analysis on LFW benchmarks (ROC, FAR, FRR). |
| [`generate_lfw_benchmark_subset.py`](generate_lfw_benchmark_subset.py) | Python 3 | Generates normalized face subsets from LFW raw assets for regression testing. |
| [`launch_emulator.bat`](launch_emulator.bat) | Windows Batch | Launches the local Android emulator (`Pixel_6_API_35`), waits for device connectivity via ADB, and starts the Attract app. |
| [`phone_import_roster.ps1`](phone_import_roster.ps1) | PowerShell + ADB | Injects students from a CSV file directly into the on-device SQLite database. |
| [`phone_add_students.ps1`](phone_add_students.ps1) | PowerShell + ADB | Helper script for populating mock student records for quick manual testing. |
| [`report_phone_db.py`](report_phone_db.py) | Python 3 + SQLite | Pulls `attract.db` from a connected device and prints an audit report of active sessions, templates, and attendance records. |
| [`run_phone_acceptance.ps1`](run_phone_acceptance.ps1) | PowerShell + ADB | End-to-end automation test suite running on a physical Android phone. |

---

## Typical Workflows

### 1. Download & Verify Models
```bash
python scripts/download_models.py
```

### 2. Inspect Model Tensors
```bash
python scripts/inspect_tflite.py
```

### 3. Launch Development Emulator
Double-click `scripts/launch_emulator.bat` or run:
```cmd
.\scripts\launch_emulator.bat
```
