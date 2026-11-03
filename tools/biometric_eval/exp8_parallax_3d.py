"""exp8 - Can the MediaPipe 478-landmark mesh tell a real 3D head turn from a flat photo/screen?

REAL 3D  : Pointing'04 (15 subjects x 2 series), same person photographed at pan 0 and pan +-15/+-30
           (tilt 0), and tilt 0 vs tilt +-15 (pan 0). MediaPipe FaceLandmarker run on each photo.
PLANAR   : the pan-0 photo itself rotated as a rigid flat sheet (a photo on a laptop/phone screen being
           tilted or turned in front of the camera): exact perspective homography of a plane rotated by
           theta about its vertical (yaw) or horizontal (pitch) axis, + random in-plane shift/scale/roll
           (hand motion), + sensor noise, then MediaPipe re-run on the warped pixels.
           Also STATIC planar pairs (no rotation, only hand jitter) = the user's laptop attack.

Metrics per pair (A,B):
  dev_residual   : exact port of ParallaxDepthTest (4-anchor homography 10/152/234/454, median residual of
                   33 STABLE_INDICES / IOD(33,263))
  lsq_residual   : proposed - least-squares (normalised DLT) homography over all 33 stable points, median
                   residual / IOD
  yaw/pitch delta: MediaPipe transformation-matrix Euler angles (port of HeadPose.eulerFromMatrix)
  z_nose_rel     : MediaPipe relative z of nose tip vs cheeks (shows whether z is hallucinated on photos)
"""
import os, sys, csv, json, random
import numpy as np, cv2

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
P04 = os.path.join(REPO, ".datasets", "pointing04")
RES = os.path.join(os.path.dirname(__file__), "results")
STABLE = [1, 4, 6, 168, 197, 195, 5, 33, 133, 362, 263, 234, 454, 93, 323, 132,
          361, 58, 288, 172, 397, 136, 365, 152, 10, 109, 338, 67, 297, 21, 251, 127, 356]
ANCHORS = [10, 152, 234, 454]


class Mesh:
    def __init__(self):
        from mediapipe.tasks.python import vision, BaseOptions
        import mediapipe as mp
        self.mp = mp
        o = vision.FaceLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=os.path.join(REPO, "app/src/main/assets/face_landmarker.task")),
            output_face_blendshapes=True, output_facial_transformation_matrixes=True, num_faces=1)
        self.lm = vision.FaceLandmarker.create_from_options(o)

    def run(self, rgb):
        img = self.mp.Image(image_format=self.mp.ImageFormat.SRGB, data=np.ascontiguousarray(rgb))
        r = self.lm.detect(img)
        if not r.face_landmarks: return None
        pts = np.array([[p.x, p.y, p.z] for p in r.face_landmarks[0]], np.float64)
        yaw = pitch = 0.0
        if r.facial_transformation_matrixes:
            m = np.array(r.facial_transformation_matrixes[0]).reshape(4, 4)
            # Kotlin reads a column-major FloatArray; MediaPipe python gives row-major 4x4 -> same rotation
            r00, r10, r20, r21, r22 = m[0, 0], m[1, 0], m[2, 0], m[2, 1], m[2, 2]
            pitch = np.degrees(np.arctan2(r21, r22))
            yaw = -np.degrees(np.arctan2(-r20, np.sqrt(r00 ** 2 + r10 ** 2)))
        return dict(p=pts, yaw=float(yaw), pitch=float(pitch), w=rgb.shape[1], h=rgb.shape[0])


def iod(p): return float(np.hypot(*(p[33, :2] - p[263, :2])))


def h4(src, dst):
    return cv2.getPerspectiveTransform(src.astype(np.float32), dst.astype(np.float32)).astype(np.float64)


def hlsq(src, dst):
    H, _ = cv2.findHomography(src, dst, 0)        # plain least squares (normalised DLT)
    return H


def apply_h(H, pts):
    q = np.c_[pts, np.ones(len(pts))] @ H.T
    return q[:, :2] / q[:, 2:3]


def residuals(a, b):
    """a,b: Mesh results. Coordinates in PIXELS (anisotropy-free) - Kotlin uses normalised [0,1] coords;
    a homography is invariant to that per-axis scaling so the residual/IOD ratio is identical when the
    image is square-pixel-scaled consistently; we use pixels to keep IOD physically meaningful."""
    A = a["p"][:, :2] * [a["w"], a["h"]]; B = b["p"][:, :2] * [b["w"], b["h"]]
    d = iod(B)
    out = {}
    try:
        H = h4(A[ANCHORS], B[ANCHORS]); r = np.linalg.norm(apply_h(H, A[STABLE]) - B[STABLE], axis=1) / d
        out["dev"] = float(np.median(r))
    except Exception:
        out["dev"] = float("nan")
    H = hlsq(A[STABLE], B[STABLE])
    r = np.linalg.norm(apply_h(H, A[STABLE]) - B[STABLE], axis=1) / d
    out["lsq"] = float(np.median(r))
    out["lsq_p90"] = float(np.percentile(r, 90))
    # nose-tip specific parallax: residual of nose tip (1) after LSQ fit on the face OUTLINE only
    outline = [10, 109, 338, 67, 297, 234, 454, 93, 323, 132, 361, 58, 288, 172, 397, 136, 365, 152, 21, 251, 127, 356]
    Ho = hlsq(A[outline], B[outline])
    out["nose_vs_outline"] = float(np.linalg.norm(apply_h(Ho, A[[1, 4]]) - B[[1, 4]], axis=1).mean() / d)
    out["dyaw"] = abs(a["yaw"] - b["yaw"]); out["dpitch"] = abs(a["pitch"] - b["pitch"])
    return out


def z_nose_rel(m):
    p = m["p"]; return float((np.mean(p[[234, 454], 2]) - p[1, 2]) / (iod(p[:, :2] * 1.0) + 1e-9))


def plane_warp(img, yaw_deg=0.0, pitch_deg=0.0, roll_deg=0.0, shift=(0, 0), scale=1.0, f_frac=0.75, seed=0):
    """Image of a flat sheet rotated about its own centre by yaw/pitch (perspective), then in-plane
    hand motion. f = focal length in px = f_frac * width (phone front cam ~ 0.7-0.9)."""
    h, w = img.shape[:2]; f = f_frac * w
    ty, tp = np.radians(yaw_deg), np.radians(pitch_deg)
    C = np.array([[1, 0, -w / 2], [0, 1, -h / 2], [0, 0, 1]], np.float64)
    Ci = np.linalg.inv(C)
    Hy = np.array([[np.cos(ty), 0, 0], [0, 1, 0], [np.sin(ty) / f, 0, 1]])
    Hp = np.array([[1, 0, 0], [0, np.cos(tp), 0], [0, np.sin(tp) / f, 1]])
    a = np.radians(roll_deg)
    S = np.array([[scale * np.cos(a), -scale * np.sin(a), shift[0]], [scale * np.sin(a), scale * np.cos(a), shift[1]], [0, 0, 1]])
    H = Ci @ S @ Hp @ Hy @ C
    out = cv2.warpPerspective(img, H, (w, h), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    r = np.random.default_rng(seed)
    out = np.clip(out.astype(np.float32) + r.normal(0, 3.0, out.shape), 0, 255).astype(np.uint8)
    return out


def stats(xs):
    xs = np.array([x for x in xs if np.isfinite(x)])
    if len(xs) == 0: return {}
    return dict(n=int(len(xs)), p5=float(np.percentile(xs, 5)), p10=float(np.percentile(xs, 10)), p50=float(np.median(xs)),
                p90=float(np.percentile(xs, 90)), p95=float(np.percentile(xs, 95)), max=float(xs.max()))


def main():
    mesh = Mesh()
    rows = list(csv.DictReader(open(os.path.join(P04, "labels.csv"))))
    by = {}
    for r in rows:
        by[(int(r["subject"]), int(r["series"]), int(r["tilt"]), int(r["pan"]))] = r["file"]
    load = lambda fn: cv2.cvtColor(cv2.imread(os.path.join(P04, fn)), cv2.COLOR_BGR2RGB)
    groups = {k: [] for k in ["real_pan15", "real_pan30", "real_tilt15", "planar_yaw5", "planar_yaw10", "planar_yaw15",
                              "planar_yaw25", "planar_pitch15", "planar_static_jitter", "real_same_pose_series"]}
    zrel = dict(real=[], planar=[])
    rng = random.Random(1)
    for subj in range(1, 16):
        for ser in (1, 2):
            k0 = (subj, ser, 0, 0)
            if k0 not in by: continue
            img0 = load(by[k0]); m0 = mesh.run(img0)
            if m0 is None: continue
            zrel["real"].append(z_nose_rel(m0))
            for pan, g in [(15, "real_pan15"), (-15, "real_pan15"), (30, "real_pan30"), (-30, "real_pan30")]:
                k = (subj, ser, 0, pan)
                if k in by:
                    m = mesh.run(load(by[k]))
                    if m: groups[g].append(residuals(m0, m))
            for tilt in (15, -15):
                k = (subj, ser, tilt, 0)
                if k in by:
                    m = mesh.run(load(by[k]))
                    if m: groups["real_tilt15"].append(residuals(m0, m))
            k2 = (subj, 3 - ser, 0, 0)
            if k2 in by:
                m = mesh.run(load(by[k2]))
                if m: groups["real_same_pose_series"].append(residuals(m0, m))
            # planar attacks built from this frontal photo (the photo on a screen)
            for rep in range(4):
                sd = subj * 100 + ser * 10 + rep
                jit = dict(shift=(rng.uniform(-12, 12), rng.uniform(-12, 12)), scale=rng.uniform(0.93, 1.07),
                           roll_deg=rng.uniform(-4, 4), f_frac=rng.uniform(0.65, 0.95))
                base = plane_warp(img0, seed=sd, **{**jit, "shift": (0, 0), "scale": 1.0, "roll_deg": 0.0})
                mb = mesh.run(base)
                if mb is None: continue
                if rep == 0: zrel["planar"].append(z_nose_rel(mb))
                for ang, g in [(5, "planar_yaw5"), (10, "planar_yaw10"), (15, "planar_yaw15"), (25, "planar_yaw25")]:
                    sgn = 1 if rep % 2 == 0 else -1
                    m = mesh.run(plane_warp(img0, yaw_deg=sgn * ang, seed=sd + 1, **jit))
                    if m: groups[g].append(residuals(mb, m))
                m = mesh.run(plane_warp(img0, pitch_deg=(15 if rep % 2 else -15), seed=sd + 2, **jit))
                if m: groups["planar_pitch15"].append(residuals(mb, m))
                m = mesh.run(plane_warp(img0, seed=sd + 3, **jit))
                if m: groups["planar_static_jitter"].append(residuals(mb, m))
        print("subject", subj, {k: len(v) for k, v in groups.items()}, flush=True)

    out = {"groups": {}}
    for g, v in groups.items():
        out["groups"][g] = {k: stats([x[k] for x in v]) for k in ["dev", "lsq", "lsq_p90", "nose_vs_outline", "dyaw", "dpitch"]}
    out["z_nose_rel"] = {k: stats(v) for k, v in zrel.items()}

    # separability: real_pan15 vs all planar rotations, per metric
    def sep(metric, real_keys, planar_keys):
        R = np.array([x[metric] for k in real_keys for x in groups[k] if np.isfinite(x[metric])])
        P = np.array([x[metric] for k in planar_keys for x in groups[k] if np.isfinite(x[metric])])
        allv = np.concatenate([R, P]); ranks = allv.argsort().argsort() + 1
        auc = (ranks[:len(R)].sum() - len(R) * (len(R) + 1) / 2) / (len(R) * len(P))
        thr = {}
        for t in np.round(np.arange(0.005, 0.08, 0.0025), 4):
            thr[str(t)] = dict(real_pass=float(np.mean(R >= t)), planar_pass=float(np.mean(P >= t)))
        return dict(auc=float(auc), n_real=int(len(R)), n_planar=int(len(P)), by_threshold=thr)

    planar_rot = ["planar_yaw5", "planar_yaw10", "planar_yaw15", "planar_yaw25", "planar_pitch15"]
    out["separability_pan15_vs_planar"] = {m: sep(m, ["real_pan15"], planar_rot) for m in ["dev", "lsq", "nose_vs_outline"]}
    out["separability_tilt15_vs_planar"] = {m: sep(m, ["real_tilt15"], planar_rot) for m in ["dev", "lsq", "nose_vs_outline"]}
    os.makedirs(RES, exist_ok=True)
    json.dump(out, open(os.path.join(RES, "exp8_parallax_3d.json"), "w"), indent=1)
    for g, v in out["groups"].items():
        print(f"{g:24s} dev p50={v['dev'].get('p50', float('nan')):.4f} lsq p10/p50/p90={v['lsq'].get('p10', 0):.4f}/{v['lsq'].get('p50', 0):.4f}/{v['lsq'].get('p90', 0):.4f}"
              f" nose p10/p50={v['nose_vs_outline'].get('p10', 0):.4f}/{v['nose_vs_outline'].get('p50', 0):.4f} dyaw p50={v['dyaw'].get('p50', 0):.1f} dpitch p50={v['dpitch'].get('p50', 0):.1f}")
    print("z_nose_rel", json.dumps(out["z_nose_rel"]))
    for k in ["separability_pan15_vs_planar", "separability_tilt15_vs_planar"]:
        print(k, {m: round(v["auc"], 3) for m, v in out[k].items()})


if __name__ == "__main__":
    main()
