import os
import glob
import numpy as np
from PIL import Image
from ai_edge_litert.interpreter import Interpreter

YOLO_PATH = "app/src/main/assets/yolov8n_face.tflite"
ARCFACE_PATH = "app/src/main/assets/arcface_mobilefacenet.tflite"

def nms(boxes, scores, iou_threshold=0.45):
    x1 = boxes[:, 0]
    y1 = boxes[:, 1]
    x2 = boxes[:, 2]
    y2 = boxes[:, 3]
    areas = (x2 - x1) * (y2 - y1)
    order = scores.argsort()[::-1]
    keep = []
    while order.size > 0:
        i = order[0]
        keep.append(i)
        xx1 = np.maximum(x1[i], x1[order[1:]])
        yy1 = np.maximum(y1[i], y1[order[1:]])
        xx2 = np.minimum(x2[i], x2[order[1:]])
        yy2 = np.minimum(y2[i], y2[order[1:]])
        w = np.maximum(0.0, xx2 - xx1)
        h = np.maximum(0.0, yy2 - yy1)
        inter = w * h
        ovr = inter / np.maximum(areas[i] + areas[order[1:]] - inter, 1e-6)
        inds = np.where(ovr <= iou_threshold)[0]
        order = order[inds + 1]
    return keep

def detect_faces(yolo_interp, img_pil, conf_threshold=0.5):
    orig_w, orig_h = img_pil.size
    img_resized = img_pil.resize((640, 640), Image.Resampling.BILINEAR)
    img_np = np.array(img_resized, dtype=np.float32) / 255.0  # [640, 640, 3]
    input_data = np.expand_dims(np.transpose(img_np, (2, 0, 1)), axis=0)

    input_details = yolo_interp.get_input_details()
    output_details = yolo_interp.get_output_details()
    
    yolo_interp.set_tensor(input_details[0]['index'], input_data)
    yolo_interp.invoke()
    
    raw_out = yolo_interp.get_tensor(output_details[0]['index'])[0]  # [20, 8400]
    preds = np.transpose(raw_out, (1, 0))  # [8400, 20]

    # channels:
    # 0: cx, 1: cy, 2: w, 3: h (already normalized 0..1)
    # 4: score
    # 5..19: 5 landmarks * 3 coords (x, y, conf) (already normalized 0..1)
    scores = preds[:, 4]
    mask = scores > conf_threshold
    valid_preds = preds[mask]
    if len(valid_preds) == 0:
        return []

    valid_scores = valid_preds[:, 4]
    
    cx = valid_preds[:, 0]
    cy = valid_preds[:, 1]
    w = valid_preds[:, 2]
    h = valid_preds[:, 3]
    
    x1 = cx - w / 2
    y1 = cy - h / 2
    x2 = cx + w / 2
    y2 = cy + h / 2
    boxes_norm = np.stack([x1, y1, x2, y2], axis=1)

    keep = nms(boxes_norm, valid_scores, iou_threshold=0.45)

    detections = []
    for idx in keep:
        det = valid_preds[idx]
        score = float(det[4])
        bx1 = float(np.clip(boxes_norm[idx, 0] * orig_w, 0, orig_w))
        by1 = float(np.clip(boxes_norm[idx, 1] * orig_h, 0, orig_h))
        bx2 = float(np.clip(boxes_norm[idx, 2] * orig_w, 0, orig_w))
        by2 = float(np.clip(boxes_norm[idx, 3] * orig_h, 0, orig_h))

        landmarks = []
        lm_confs = []
        for l_idx in range(5):
            lx = float(det[5 + l_idx * 3] * orig_w)
            ly = float(det[6 + l_idx * 3] * orig_h)
            lconf = float(det[7 + l_idx * 3])
            landmarks.append((lx, ly, lconf))
            lm_confs.append(lconf)

        # Quality check: all 5 landmarks must have reasonable confidence
        min_lm_conf = min(lm_confs)
        mean_lm_conf = float(np.mean(lm_confs))
        if min_lm_conf < 0.6:
            continue

        detections.append({
            "box": (bx1, by1, bx2, by2),
            "score": score,
            "min_lm_conf": min_lm_conf,
            "mean_lm_conf": mean_lm_conf,
            "landmarks": landmarks
        })
    # Sort detections by score * mean_lm_conf descending
    detections.sort(key=lambda d: d["score"] * d["mean_lm_conf"], reverse=True)
    return detections

def align_face_5point(img_pil, landmarks):
    # ArcFace standard 112x112 canonical 5-point landmarks
    src_pts = np.array([[lm[0], lm[1]] for lm in landmarks[:5]], dtype=np.float32)
    dst_pts = np.array([
        [38.2946, 51.6963],  # left eye
        [73.5318, 51.5014],  # right eye
        [56.0252, 71.7366],  # nose
        [41.5493, 92.3655],  # left mouth
        [70.7299, 92.2041]   # right mouth
    ], dtype=np.float32)

    # Estimate similarity transform: src_pts -> dst_pts
    # Let's use 2 eye points for direct similarity (rotation + scale + translation)
    src_left = src_pts[0]
    src_right = src_pts[1]
    dst_left = dst_pts[0]
    dst_right = dst_pts[1]

    src_dx = src_right[0] - src_left[0]
    src_dy = src_right[1] - src_left[1]
    src_dist = np.hypot(src_dx, src_dy)
    src_angle = np.degrees(np.arctan2(src_dy, src_dx))

    dst_dx = dst_right[0] - dst_left[0]
    dst_dy = dst_right[1] - dst_left[1]
    dst_dist = np.hypot(dst_dx, dst_dy)
    dst_angle = np.degrees(np.arctan2(dst_dy, dst_dx))

    scale = dst_dist / max(src_dist, 1e-6)
    rot_angle = dst_angle - src_angle

    src_center = (src_left + src_right) / 2.0
    dst_center = (dst_left + dst_right) / 2.0

    # PIL affine transform (maps output coords -> input coords)
    inv_scale = 1.0 / scale
    theta = np.radians(-rot_angle)
    cos_t = np.cos(theta) * inv_scale
    sin_t = np.sin(theta) * inv_scale

    tx = src_center[0] - (dst_center[0] * cos_t - dst_center[1] * sin_t)
    ty = src_center[1] - (dst_center[0] * sin_t + dst_center[1] * cos_t)

    aligned = img_pil.transform(
        (112, 112),
        Image.Transform.AFFINE,
        (cos_t, -sin_t, tx, sin_t, cos_t, ty),
        resample=Image.Resampling.BILINEAR
    )
    return aligned

def extract_embedding(arcface_interp, aligned_pil):
    # ArcFace input: [1, 112, 112, 3], normalized to [-1, 1]
    img_np = np.array(aligned_pil, dtype=np.float32)
    img_norm = (img_np - 127.5) / 128.0
    input_data = np.expand_dims(img_norm, axis=0)

    input_details = arcface_interp.get_input_details()
    output_details = arcface_interp.get_output_details()

    arcface_interp.set_tensor(input_details[0]['index'], input_data)
    arcface_interp.invoke()

    raw_emb = arcface_interp.get_tensor(output_details[0]['index'])[0]
    norm = np.linalg.norm(raw_emb)
    if norm > 1e-6:
        raw_emb = raw_emb / norm
    return raw_emb

def main():
    print("Initializing YOLOv8n-face interpreter...")
    yolo = Interpreter(model_path=YOLO_PATH)
    yolo.allocate_tensors()

    print("Initializing ArcFace-MobileFaceNet interpreter...")
    arcface = Interpreter(model_path=ARCFACE_PATH)
    arcface.allocate_tensors()

    test_files = sorted(glob.glob("test-data/real-faces/*/*.jpg"))
    print(f"Found {len(test_files)} test images.\n")
    
    os.makedirs("test-data/aligned_debug", exist_ok=True)
    embeddings = {}
    for path in test_files:
        person = os.path.basename(os.path.dirname(path))
        img_name = os.path.basename(path)
        img = Image.open(path).convert('RGB')
        dets = detect_faces(yolo, img, conf_threshold=0.5)
        if not dets:
            print(f"  [FAIL] No face detected in {person}/{img_name}")
            continue
        top = dets[0]
        aligned = align_face_5point(img, top['landmarks'])
        # Save debug crop
        aligned.save(f"test-data/aligned_debug/{person}_{img_name}")
        emb = extract_embedding(arcface, aligned)
        embeddings[f"{person}/{img_name}"] = {
            "emb": emb,
            "person": person,
            "det": top
        }
        b = top['box']
        print(f"  [OK] {person}/{img_name}: score={top['score']:.3f}, box=({b[0]:.1f}, {b[1]:.1f}, {b[2]:.1f}, {b[3]:.1f})")

    print("\n" + "="*50)
    print("Testing Cosine Similarities across pairs:")
    
    genuine_scores = []
    impostor_scores = []

    keys = list(embeddings.keys())
    for i in range(len(keys)):
        for j in range(i + 1, len(keys)):
            k1, k2 = keys[i], keys[j]
            p1 = embeddings[k1]["person"]
            p2 = embeddings[k2]["person"]
            e1 = embeddings[k1]["emb"]
            e2 = embeddings[k2]["emb"]
            sim = float(np.dot(e1, e2))
            
            if p1 == p2:
                genuine_scores.append((sim, k1, k2))
            else:
                impostor_scores.append((sim, k1, k2))

    print(f"\nGenuine pairs ({len(genuine_scores)} comparisons):")
    for sim, k1, k2 in sorted(genuine_scores, reverse=True)[:10]:
        print(f"  GENUINE:  {k1} <-> {k2} => {sim:.4f}")
    for sim, k1, k2 in sorted(genuine_scores)[:3]:
        print(f"  GENUINE (Lowest):  {k1} <-> {k2} => {sim:.4f}")

    print(f"\nImpostor pairs ({len(impostor_scores)} comparisons):")
    for sim, k1, k2 in sorted(impostor_scores, reverse=True)[:5]:
        print(f"  IMPOSTOR (Highest): {k1} <-> {k2} => {sim:.4f}")
    for sim, k1, k2 in sorted(impostor_scores)[:5]:
        print(f"  IMPOSTOR (Lowest):  {k1} <-> {k2} => {sim:.4f}")

    gen_vals = [s[0] for s in genuine_scores]
    imp_vals = [s[0] for s in impostor_scores]
    
    print("\nSummary Statistics:")
    print(f"  Genuine  : min={np.min(gen_vals):.4f}, mean={np.mean(gen_vals):.4f}, max={np.max(gen_vals):.4f}")
    print(f"  Impostor : min={np.min(imp_vals):.4f}, mean={np.mean(imp_vals):.4f}, max={np.max(imp_vals):.4f}")
    margin = np.min(gen_vals) - np.max(imp_vals)
    print(f"  Separation Gap (Min Genuine - Max Impostor): {margin:.4f}")
    
    # Production Policy Evaluation at threshold=0.25f
    ACCEPT_THRESHOLD = 0.25
    false_accepts = sum(1 for s in imp_vals if s >= ACCEPT_THRESHOLD)
    false_rejects = sum(1 for s in gen_vals if s < ACCEPT_THRESHOLD)
    far = (false_accepts / len(imp_vals) * 100) if imp_vals else 0.0
    frr = (false_rejects / len(gen_vals) * 100) if gen_vals else 0.0
    print(f"\nEvaluation at Production Threshold ({ACCEPT_THRESHOLD}):")
    print(f"  FAR (False Accept Rate): {far:.2f}% ({false_accepts}/{len(imp_vals)})")
    print(f"  FRR (False Reject Rate): {frr:.2f}% ({false_rejects}/{len(gen_vals)})")

if __name__ == '__main__':
    main()
