"""Runs detector + aligner + recogniser ONCE per image and caches everything the
simulations need (so threshold sweeps are pure numpy).

Per image: detection count, landmarks, landmark conf, app yaw/pitch (ratio heuristic),
proposed yaw (nose-offset), quality signals, RGB & BGR embeddings, flip embedding.
"""
import csv, os, sys, json, pickle
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap

DS = os.path.join(ap.REPO, ".datasets")
OUT = os.path.join(DS, "feature_cache.pkl")


def feat(path):
    img = Image.open(path).convert("RGB")
    dets = ap.detect(img)
    rec = dict(n_faces=len(dets))
    if not dets:
        return rec
    # LFW images may contain bystanders: keep the face nearest the centre for embedding,
    # but n_faces preserves the multi-face signal the app would see.
    d = min(dets, key=lambda d: (d.cx - .5) ** 2 + (d.cy - .5) ** 2)
    lm = d.landmarks
    sig = ap.signals_for(img, [d])
    crop = ap.align(img, lm)
    a = np.asarray(crop, dtype=np.float32)
    m = ap.Models.get()

    def e(arr):
        m.arc.set_tensor(m.ai, arr[None].astype(np.float32)); m.arc.invoke()
        v = m.arc.get_tensor(m.ao)[0].astype(np.float32); return v / np.linalg.norm(v)
    eye_mid = (lm[0] + lm[1]) / 2; ie = max(float(np.linalg.norm(lm[1] - lm[0])), 1e-3)
    rec.update(
        lm=lm, lm_conf=d.lm_conf, score=d.score,
        app_yaw=d.yaw, app_pitch=d.pitch, roll=d.roll,
        nose_off=float((lm[2, 0] - eye_mid[0]) / ie),
        nose_v=float((lm[2, 1] - eye_mid[1]) / ie),
        face_ratio=sig.face_ratio, cx=sig.cx, cy=sig.cy, blur=sig.blur, brightness=sig.brightness,
        emb_rgb=e((a - 127.5) / 128), emb_bgr=e((a[..., ::-1] - 127.5) / 128),
    )
    return rec


def main():
    cache = pickle.load(open(OUT, "rb")) if os.path.exists(OUT) else {}
    jobs = []
    p4 = os.path.join(DS, "pointing04")
    for r in csv.DictReader(open(os.path.join(p4, "labels.csv"))):
        if abs(int(r["tilt"])) <= 30 and abs(int(r["pan"])) <= 60:
            jobs.append(("p4/" + r["file"], os.path.join(p4, r["file"])))
    lfw = os.path.join(DS, "lfw")
    ids = sorted(os.listdir(lfw))
    multi = [i for i in ids if len(os.listdir(os.path.join(lfw, i))) >= 4]
    singles = [i for i in ids if len(os.listdir(os.path.join(lfw, i))) == 1]
    for i in multi[:420]:                                     # calibration + class members
        for f in sorted(os.listdir(os.path.join(lfw, i)))[:6]:
            jobs.append((f"lfw/{i}/{f}", os.path.join(lfw, i, f)))
    for i in singles[:400]:                                   # strangers (never enrolled)
        f = os.listdir(os.path.join(lfw, i))[0]
        jobs.append((f"lfw/{i}/{f}", os.path.join(lfw, i, f)))
    todo = [j for j in jobs if j[0] not in cache]
    print("jobs", len(jobs), "todo", len(todo), flush=True)
    for k, (key, path) in enumerate(todo):
        cache[key] = feat(path)
        if k % 500 == 499:
            pickle.dump(cache, open(OUT, "wb")); print(k + 1, flush=True)
    pickle.dump(cache, open(OUT, "wb"))
    print("cached", len(cache))


if __name__ == "__main__":
    main()
