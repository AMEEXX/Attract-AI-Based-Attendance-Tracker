"""EXP-1b: Full FaceQualityEngine pass-rate (all gates, not only pose) on frontal-ish frames,
and STRETCH (production) vs LETTERBOX resize effect on landmark-based yaw."""
import csv, os, sys, json, collections
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap
ROOT = os.path.join(ap.REPO, ".datasets", "pointing04")
rows = [r for r in csv.DictReader(open(os.path.join(ROOT, "labels.csv"))) if abs(int(r["tilt"])) <= 15 and abs(int(r["pan"])) <= 45]
rej = collections.Counter(); blur = []; ratio = []; n = 0
yaw_s = collections.defaultdict(list); yaw_l = collections.defaultdict(list)
for r in rows:
    img = Image.open(os.path.join(ROOT, r["file"])).convert("RGB"); pan = int(r["pan"])
    dets = ap.detect(img); sig = ap.signals_for(img, dets)
    if pan == 0 and int(r["tilt"]) == 0:
        n += 1; ok, why = ap.quality(sig, "STRAIGHT"); rej[why] += 1; blur.append(sig.blur); ratio.append(sig.face_ratio)
    if len(dets) == 1: yaw_s[pan].append(dets[0].yaw)
    dl = ap.detect(img, letterbox=True)
    if len(dl) == 1: yaw_l[pan].append(dl[0].yaw)
print("frontal (pan=0,tilt=0) STRAIGHT verdicts:", dict(rej), "n=", n)
print("blur var quantiles", np.quantile(blur, [.1, .5, .9]).round(1), " faceRatio quantiles", np.quantile(ratio, [.1, .5, .9]).round(3))
print("pan: stretch-yaw vs letterbox-yaw (mean)")
for p in sorted(yaw_s): print(f"  {p:>4}: {np.mean(yaw_s[p]):6.1f}  {np.mean(yaw_l[p]):6.1f}")
json.dump(dict(frontal_verdicts=dict(rej), blur_q=np.quantile(blur, [.1, .5, .9]).tolist(),
               stretch={p: float(np.mean(v)) for p, v in yaw_s.items()}, letterbox={p: float(np.mean(v)) for p, v in yaw_l.items()}),
          open(os.path.join(os.path.dirname(__file__), "results", "exp1b_quality_gates.json"), "w"), indent=1)
