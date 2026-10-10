# Machine Learning Models Catalog

This document catalogs the on-device ML models used by the Attract Face Based Attendance Tracker application.

| Model | File Name | Purpose | Upstream Source / Training | License |
|---|---|---|---|---|
| **YOLOv8n-Face** | `yolov8n_face.tflite` | Face detection and bounding box extraction | Ultralytics / Derghazarian | **AGPL-3.0** (Open-source distribution only; Commercial use requires enterprise license from Ultralytics) |
| **ArcFace MobileFaceNet** | `arcface_mobilefacenet.tflite` | Face feature extraction (128d embeddings) | InsightFace | **Non-Commercial / Research Use Only** |

## License Compliance & Commercialization
The current weights included in this repository (`app/src/main/assets/*.tflite`) are suitable for **academic, non-commercial, and open-source pilot deployments**. 

**If this application is commercialized or distributed as proprietary closed-source software**, the following model replacements are mandatory:
1. **YOLOv8n-Face** must be replaced with **Google ML Kit Face Detection** or **MediaPipe BlazeFace** (Apache-2.0).
2. **ArcFace** must be replaced with an open-source face recognition model (e.g., FaceNet under MIT) or a model trained from scratch on legally cleared commercial datasets (e.g., not WebFace).

## Download Script
Models are verified against their SHA-256 hashes by `scripts/download_models.py` during build and setup to prevent supply-chain poisoning.
