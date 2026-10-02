import os
import urllib.request
import hashlib
import sys
from inspect_tflite import inspect_model

MODELS = {
    "yolov8n_face.tflite": {
        "url": "https://github.com/sanjaysharmajw/flutter_face_liveness/releases/download/v3.2.0-models/yolov8n-face.tflite",
        "target": "app/src/main/assets/yolov8n_face.tflite",
        "sha256": "85a19457127249bb7f2a0875ff344b9dc6021a2a371e14c77d4c0e5f22f7ed54"
    },
    "arcface_mobilefacenet.tflite": {
        "url": "https://github.com/sanjaysharmajw/flutter_face_liveness/releases/download/v3.2.0-models/arcface_mobilefacenet_v1.tflite",
        "target": "app/src/main/assets/arcface_mobilefacenet.tflite",
        "sha256": "dfac9cfe6517a9c4c3969b6ff0c2a0ac112cdf67a287d8218b60636810f0b576"
    }
}

def download_file(url, target_path):
    print(f"Downloading {url} -> {target_path}...")
    os.makedirs(os.path.dirname(target_path), exist_ok=True)
    temp_path = target_path + ".tmp"
    
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as response, open(temp_path, 'wb') as out_file:
        total = int(response.headers.get('content-length', 0))
        downloaded = 0
        block_size = 64 * 1024
        while True:
            buffer = response.read(block_size)
            if not buffer:
                break
            downloaded += len(buffer)
            out_file.write(buffer)
            if total > 0:
                percent = downloaded * 100 // total
                sys.stdout.write(f"\r  {downloaded}/{total} bytes ({percent}%)")
                sys.stdout.flush()
    print()
    if os.path.exists(target_path):
        os.remove(target_path)
    os.rename(temp_path, target_path)

    sha256 = hashlib.sha256()
    with open(target_path, 'rb') as f:
        while chunk := f.read(64 * 1024):
            sha256.update(chunk)
    hash_str = sha256.hexdigest()
    print(f"Downloaded: {target_path} (Size: {os.path.getsize(target_path)} bytes, SHA256: {hash_str})")
    return hash_str

def main():
    for name, info in MODELS.items():
        target = info["target"]
        if not os.path.exists(target):
            download_file(info["url"], target)
        else:
            print(f"File exists: {target} (Size: {os.path.getsize(target)} bytes)")
        inspect_model(target)
        print("\n" + "="*50 + "\n")

if __name__ == '__main__':
    main()
