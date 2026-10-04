"""EXP-1: Why LEFT/RIGHT enrollment captures are never accepted.

For every Pointing'04 image with |tilt| <= 15 we run the EXACT app detector and record:
  - raw YOLO face found (score>=0.5) ?
  - survives the app's "all 5 landmark conf >= 0.5" filter ?
  - app "yaw" (landmark ratio) vs ground-truth pan
  - would FaceQualityEngine accept it for STRAIGHT / LEFT / RIGHT (pose gates only)?
"""
import csv, os, sys, json, collections
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap

ROOT = os.path.join(ap.REPO, ".datasets", "pointing04")
rows = list(csv.DictReader(open(os.path.join(ROOT, "labels.csv"))))
rows = [r for r in rows if abs(int(r["tilt"])) <= 15]

by_pan = collections.defaultdict(lambda: collections.Counter())
yaws = collections.defaultdict(list)
lmmin = collections.defaultdict(list)
records = []
for r in rows:
    pan = int(r["pan"]); img = Image.open(os.path.join(ROOT, r["file"])).convert("RGB")
    dets, raw = ap.detect(img, return_raw=True)
    c = by_pan[pan]; c["n"] += 1
    if raw:
        c["raw_face"] += 1
        lmmin[pan].append(max(x.min_lm_conf for x in raw))
    if len(dets) == 1:
        d = dets[0]; c["app_face"] += 1; yaws[pan].append(d.yaw)
        pose_sig = ap.Signals(1, d.yaw, d.pitch, 0.5, 0.5, 0.5, 999, 128)   # isolate POSE gate
        for exp in ("STRAIGHT", "LEFT", "RIGHT"):
            if ap.quality(pose_sig, exp)[0]:
                c["ok_" + exp] += 1
        records.append(dict(file=r["file"], pan=pan, tilt=int(r["tilt"]), yaw=d.yaw, pitch=d.pitch,
                            lm=d.lm_conf.round(3).tolist()))
    elif len(dets) > 1:
        c["multi"] += 1

print(f"{'pan':>5} {'n':>4} {'rawFace':>8} {'appFace':>8} {'maxMinLmConf':>13} {'appYaw(mean±sd)':>17} {'STRAIGHT':>9} {'LEFT':>6} {'RIGHT':>6}")
summary = []
for pan in sorted(by_pan):
    c = by_pan[pan]; y = np.array(yaws[pan]) if yaws[pan] else np.array([np.nan])
    lm = np.mean(lmmin[pan]) if lmmin[pan] else float("nan")
    print(f"{pan:>5} {c['n']:>4} {c['raw_face']:>8} {c['app_face']:>8} {lm:>13.2f} {np.nanmean(y):>8.1f}±{np.nanstd(y):<7.1f} "
          f"{c['ok_STRAIGHT']:>9} {c['ok_LEFT']:>6} {c['ok_RIGHT']:>6}")
    summary.append(dict(pan=pan, n=c["n"], raw_face=c["raw_face"], app_face=c["app_face"],
                        mean_min_lm_conf=lm, app_yaw_mean=float(np.nanmean(y)), app_yaw_sd=float(np.nanstd(y)),
                        ok_straight=c["ok_STRAIGHT"], ok_left=c["ok_LEFT"], ok_right=c["ok_RIGHT"]))
os.makedirs(os.path.join(os.path.dirname(__file__), "results"), exist_ok=True)
json.dump(dict(summary=summary, records=records),
          open(os.path.join(os.path.dirname(__file__), "results", "exp1_pose_sweep.json"), "w"), indent=1)
