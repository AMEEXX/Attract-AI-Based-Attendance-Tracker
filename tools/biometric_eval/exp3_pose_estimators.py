"""EXP-3: Which yaw estimator built from the SAME 5 YOLO landmarks tracks real head turn?

Candidates (all computable on-device in Kotlin, no new model):
  A  app ratio            (0.5 - dL/(dL+dR))*60           - current production
  B  nose offset          (nose.x - eyeMid.x) / interEye   - normalised, sign-stable
  C  nose-vs-face-centre  (nose.x - mean(eyes,mouth).x) / interEye
  D  solvePnP (5 pts, generic 3D head model, pinhole f=image width) -> Euler yaw degrees
Fit a monotone linear map gt_pan ~ k*feature on half the subjects, test on the other half.
"""
import csv, os, sys, json, collections, math
import numpy as np, cv2
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap

ROOT = os.path.join(ap.REPO, ".datasets", "pointing04")
rows = [r for r in csv.DictReader(open(os.path.join(ROOT, "labels.csv")))
        if abs(int(r["tilt"])) <= 15 and abs(int(r["pan"])) <= 60]

# Generic 3D model (mm) for [leftEye(img-left), rightEye, nose, mouthL, mouthR]
MODEL3D = np.array([[-32.0, 35.0, -30.0], [32.0, 35.0, -30.0], [0.0, 0.0, 0.0],
                    [-24.0, -32.0, -25.0], [24.0, -32.0, -25.0]], dtype=np.float64)


def pnp_yaw(lm, w, h):
    f = float(w); K = np.array([[f, 0, w / 2], [0, f, h / 2], [0, 0, 1]], dtype=np.float64)
    pts = lm.astype(np.float64).copy(); pts[:, 1] = -pts[:, 1] + h    # y-up to match model
    ok, rvec, tvec = cv2.solvePnP(MODEL3D, pts, K, None, flags=cv2.SOLVEPNP_EPNP)
    if not ok:
        return float("nan"), float("nan")
    ok, rvec, tvec = cv2.solvePnP(MODEL3D, pts, K, None, rvec, tvec, True, cv2.SOLVEPNP_ITERATIVE)
    R, _ = cv2.Rodrigues(rvec)
    yaw = math.degrees(math.asin(max(-1, min(1, -R[2, 0]))))
    pitch = math.degrees(math.atan2(R[2, 1], R[2, 2]))
    return yaw, pitch


data = []
for r in rows:
    img = Image.open(os.path.join(ROOT, r["file"])).convert("RGB")
    dets = ap.detect(img)
    if len(dets) != 1:
        continue
    lm = dets[0].landmarks; w, h = img.size
    eye_mid = (lm[0] + lm[1]) / 2; ie = max(np.linalg.norm(lm[1] - lm[0]), 1e-3)
    centre = lm[[0, 1, 3, 4]].mean(0)
    d = dict(subject=int(r["subject"]), pan=int(r["pan"]), tilt=int(r["tilt"]),
             A=ap.yaw_ratio(lm), B=float((lm[2, 0] - eye_mid[0]) / ie), C=float((lm[2, 0] - centre[0]) / ie))
    d["D"], d["D_pitch"] = pnp_yaw(lm, w, h)
    data.append(d)

train = [d for d in data if d["subject"] <= 8]; test = [d for d in data if d["subject"] > 8]
res = {}
for k in "ABCD":
    x = np.array([d[k] for d in train]); y = np.array([d["pan"] for d in train])
    ok = np.isfinite(x); kfit = float((x[ok] @ y[ok]) / (x[ok] @ x[ok]))       # through-origin LS
    xt = np.array([d[k] for d in test]); yt = np.array([d["pan"] for d in test]); okt = np.isfinite(xt)
    pred = kfit * xt[okt]; err = np.abs(pred - yt[okt])
    corr = float(np.corrcoef(xt[okt], yt[okt])[0, 1])
    per_pan = {}
    for p in sorted(set(yt.tolist())):
        sel = (yt[okt] == p)
        per_pan[p] = [round(float(np.mean(pred[sel])), 1), round(float(np.std(pred[sel])), 1)]
    # Can it separate "straight" (|pan|<=15) from "turned >=30"?
    straight = np.abs(yt[okt]) <= 15; turned = np.abs(yt[okt]) >= 30
    res[k] = dict(scale=kfit, corr=round(corr, 3), mae_deg=round(float(err.mean()), 1),
                  p90_err=round(float(np.quantile(err, .9)), 1), per_pan_mean_sd=per_pan,
                  sign_correct_turned=round(float((np.sign(pred[turned]) == np.sign(yt[okt][turned])).mean()), 3))
    print(k, json.dumps({kk: v for kk, v in res[k].items() if kk != "per_pan_mean_sd"}))
    print("   per-pan predicted mean±sd:", per_pan)
json.dump(dict(n=len(data), results=res), open(os.path.join(os.path.dirname(__file__), "results", "exp3_pose_estimators.json"), "w"), indent=1)
