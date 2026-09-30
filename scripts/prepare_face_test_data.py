#!/usr/bin/env python3
"""
prepare_face_test_data.py

Test Data Preparation Script for Attract Face Verification System.
Prepares:
 1. Synthetic Face Dataset in test-data/synthetic-faces/ (for ML stress/pipeline testing)
 2. Validates Real Human Test Directory in test-data/real-faces/
 3. Syncs available test assets to app/src/androidTest/assets/test-data/

Stored OUTSIDE production app code/database to ensure zero pollution.
"""

import os
import sys
import shutil

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TEST_DATA_DIR = os.path.join(ROOT_DIR, "test-data")
SYNTHETIC_DIR = os.path.join(TEST_DATA_DIR, "synthetic-faces")
REAL_DIR = os.path.join(TEST_DATA_DIR, "real-faces")
ANDROID_ASSETS_DIR = os.path.join(ROOT_DIR, "app", "src", "androidTest", "assets", "test-data")

def ensure_dirs():
    print("[1/4] Setting up test data directories...")
    os.makedirs(SYNTHETIC_DIR, exist_ok=True)
    os.makedirs(REAL_DIR, exist_ok=True)
    os.makedirs(ANDROID_ASSETS_DIR, exist_ok=True)

def populate_synthetic_dataset():
    print("[2/4] Checking synthetic face dataset (for pipeline/stress testing)...")
    existing = [f for f in os.listdir(SYNTHETIC_DIR) if f.endswith(('.png', '.jpg', '.ppm'))]
    
    # If the synthetic files are actually PPM written with png extensions, we must delete them to regenerate valid PNGs!
    if len(existing) > 0:
        # Check if first file is PPM (doesn't start with PNG signature)
        first_file = os.path.join(SYNTHETIC_DIR, existing[0])
        try:
            with open(first_file, "rb") as f:
                sig = f.read(4)
                if sig != b'\x89PNG':
                    print("      Existing synthetic files are invalid format. Deleting to regenerate...")
                    for f_name in existing:
                        os.remove(os.path.join(SYNTHETIC_DIR, f_name))
                    existing = []
        except Exception:
            pass

    if len(existing) >= 50:
        print(f"      Synthetic face dataset already present: {len(existing)} images.")
        return

    from PIL import Image, ImageDraw
    print("      Generating 50 synthetic baseline photographic images using PIL...")
    for i in range(1, 51):
        filepath = os.path.join(SYNTHETIC_DIR, f"synth_face_{i:04d}.png")
        if not os.path.exists(filepath):
            # Create a 112x112 gray image
            img = Image.new("RGB", (112, 112), color=(180, 180, 180))
            draw = ImageDraw.Draw(img)
            # Draw face-like oval
            draw.ellipse([56 - 35, 56 - 45, 56 + 35, 56 + 45], 
                         fill=((140 + (i * 7) % 80), (100 + (i * 3) % 60), (80 + (i * 5) % 50)))
            # Draw eyes
            draw.rectangle([42 - 3, 45 - 3, 42 + 3, 45 + 3], fill=(20, 20, 20))
            draw.rectangle([70 - 3, 45 - 3, 70 + 3, 45 + 3], fill=(20, 20, 20))
            img.save(filepath, "PNG")
    print(f"      Created synthetic face images in {SYNTHETIC_DIR}")

def validate_real_human_dataset():
    print(f"[3/4] Validating real human test dataset in {REAL_DIR}...")
    people_dirs = [
        d for d in os.listdir(REAL_DIR)
        if os.path.isdir(os.path.join(REAL_DIR, d)) and not d.startswith(".")
    ]

    total_images = 0
    valid_people = 0
    for p in people_dirs:
        p_path = os.path.join(REAL_DIR, p)
        imgs = [f for f in os.listdir(p_path) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        if len(imgs) > 0:
            valid_people += 1
            total_images += len(imgs)

    if valid_people > 0:
        print(f"      Real face dataset found: {valid_people} people / {total_images} images.")
    else:
        print("      [NOTICE] Real face dataset missing.")
        print("      Add consenting test images under: test-data/real-faces/person_XX/")
        # Create placeholder structure README (without inserting fake drawn images into real-faces)
        readme_path = os.path.join(REAL_DIR, "README.md")
        with open(readme_path, "w", encoding="utf-8") as f:
            f.write("""# Real Human Test Dataset Directory

Place real consenting test participant photographs in this directory following the structure:

```
test-data/real-faces/
├── person_01/
│   ├── straight_01.jpg
│   ├── straight_02.jpg
│   ├── left_01.jpg
│   └── right_01.jpg
├── person_02/
│   ├── straight_01.jpg
│   ├── left_01.jpg
│   └── right_01.jpg
```

**Note:** If no real photographs are added, real-human recognition tests will cleanly **SKIP** and print setup instructions. Fake images are never automatically generated in this directory.
""")

def sync_assets_to_android():
    print(f"[4/4] Syncing test assets to Android test asset folder ({ANDROID_ASSETS_DIR})...")
    target_synth = os.path.join(ANDROID_ASSETS_DIR, "synthetic-faces")
    target_real = os.path.join(ANDROID_ASSETS_DIR, "real-faces")

    if os.path.exists(target_synth):
        shutil.rmtree(target_synth)
    if os.path.exists(target_real):
        shutil.rmtree(target_real)

    if os.path.exists(SYNTHETIC_DIR):
        shutil.copytree(SYNTHETIC_DIR, target_synth)
    if os.path.exists(REAL_DIR):
        shutil.copytree(REAL_DIR, target_real)

    print("      Successfully synced test data assets for instrumented test runner.")

def update_gitignore():
    gitignore_path = os.path.join(ROOT_DIR, ".gitignore")
    if os.path.exists(gitignore_path):
        with open(gitignore_path, "r", encoding="utf-8") as f:
            content = f.read()
        if "test-data/" not in content:
            with open(gitignore_path, "a", encoding="utf-8") as f:
                f.write("\n# Test Dataset - kept out of production builds\ntest-data/\napp/src/androidTest/assets/test-data/\n")
            print("[INFO] Updated .gitignore to exclude test datasets from release build.")

def main():
    print("==========================================================")
    print("  Attract Face System — Test Data Preparation")
    print("==========================================================")
    ensure_dirs()
    populate_synthetic_dataset()
    validate_real_human_dataset()
    sync_assets_to_android()
    update_gitignore()
    print("==========================================================")
    print("  Preparation Complete! Dataset is ready for testing.")
    print("==========================================================")

if __name__ == "__main__":
    main()
