"""
exp5 - Low-light / backlight / resolution stress test of the attendance pipeline.

Runs the SHIPPED TFLite models (YOLOv8n-face + ArcFace MobileFaceNet, BGR input as in
EmbeddingEngine.kt) on LFW identities rendered into synthetic phone-camera frames under
classroom lighting conditions, and measures for each condition:

  det      : face detected (exactly one face, all 5 landmarks >= 0.5)
  gateMain : passes the CURRENT main quality gate (blur on raw frame, minBlur=60, minBright=40)
  gate720  : same gate when the frame is 1280x720 (doc-20 WP2.1)  -> the "Too blurry" regression
  gateNew  : passes the PROPOSED v4 gate (sharpness on normalised 112 crop, see below)
  padFP    : genuine face wrongly rejected by PresentationAttackDetector (current thresholds)
  padFPNew : genuine face wrongly rejected by the PROPOSED noise-aware PAD gate
  gen@0.50 : genuine cosine >= acceptThreshold (0.50) for each enhancement variant
  imp      : max impostor cosine >= 0.50 (false accept) for each variant

Usage:  python tools/biometric_eval/exp5_lowlight.py [n_identities]
Writes: tools/biometric_eval/results/exp5_lowlight.json
"""
from __future__ import annotations

import json, os, random, sys, time
import numpy as np
import cv2
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap  # noqa: E402

ROOT = os.path.join(ap.REPO, ".datasets", "lfw")
OUT = os.path.join(os.path.dirname(__file__), "results", "exp5_lowlight.json")
ACCEPT = 0.50
rng = np.random.default_rng(7)

# ------------------------------------------------------------------ frame synthesis
def to_frame(img: np.ndarray, h: int, w: int) -> np.ndarray:
    """Portrait phone frame (h>w). LFW face placed centrally, face box ~35-40% of width;
    background = heavily blurred copy of the photo (no duplicate faces)."""
    bg = cv2.GaussianBlur(cv2.resize(img, (w, h), interpolation=cv2.INTER_LINEAR), (0, 0), max(h, w) * 0.05)
    s = (w * 0.95) / img.shape[1]
    face = cv2.resize(img, (int(img.shape[1] * s), int(img.shape[0] * s)), interpolation=cv2.INTER_CUBIC)
    fh, fw = face.shape[:2]
    y0, x0 = (h - fh) // 2, (w - fw) // 2
    yy, xx = np.mgrid[0:fh, 0:fw]
    m = np.clip(1.0 - np.maximum(np.abs(xx - fw / 2) / (fw / 2), np.abs(yy - fh / 2) / (fh / 2)), 0, 1)
    m = np.clip(m * 8, 0, 1)[..., None]
    out = bg.astype(np.float32)
    out[y0:y0 + fh, x0:x0 + fw] = face * m + out[y0:y0 + fh, x0:x0 + fw] * (1 - m)
    return out.astype(np.uint8)


def face_mask(h, w):
    yy, xx = np.mgrid[0:h, 0:w]
    m = (((xx - w / 2) / (w * 0.22)) ** 2 + ((yy - h * 0.52) / (h * 0.30)) ** 2) <= 1.0
    return cv2.GaussianBlur(m.astype(np.float32), (0, 0), h * 0.03)


def camera(frame: np.ndarray, cond: str, seed: int) -> np.ndarray:
    """Simulate sensor + ISP for one capture. frame: uint8 RGB. Returns uint8 RGB."""
    r = np.random.default_rng(seed)
    f = frame.astype(np.float32)
    h, w = f.shape[:2]
    if cond == "normal":
        g, noise, nr = 1.0, 2.0, 0.0
    elif cond == "dim":            # ~20-30 lux, AE at max gain: dark + noisy + ISP NR smear
        g, noise, nr = 0.38, 9.0, 0.8
    elif cond == "very_dim":       # ~5 lux
        g, noise, nr = 0.20, 13.0, 1.1
    elif cond == "backlit":        # window behind student, AE metered on window
        m = face_mask(h, w)[..., None]
        bg = np.clip(f * 1.6 + 70, 0, 255)
        fg = f * 0.30
        f = bg * (1 - m) + fg * m
        g, noise, nr = 1.0, 7.0, 0.6
    elif cond == "backlit_hard":
        m = face_mask(h, w)[..., None]
        bg = np.full_like(f, 250.0)
        fg = f * 0.20
        f = bg * (1 - m) + fg * m
        g, noise, nr = 1.0, 9.0, 0.8
    elif cond == "dim_motion":     # dim + long exposure hand shake
        g, noise, nr = 0.38, 9.0, 0.8
        k = np.zeros((7, 7), np.float32); k[3, :] = 1 / 7
        f = cv2.filter2D(f, -1, k)
    else:
        raise ValueError(cond)
    f = f * g
    # signal-dependent (shot) + read noise, per channel
    sigma = np.sqrt(noise ** 2 + 0.15 * np.maximum(f, 0))
    f = f + r.normal(0, 1, f.shape).astype(np.float32) * sigma
    if nr > 0:
        f = cv2.GaussianBlur(f, (0, 0), nr)
    return np.clip(f, 0, 255).astype(np.uint8)

# ------------------------------------------------------------------ enhancement variants
def luma(a):
    return 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]


def gamma_for(mean):
    m = min(max(mean / 255.0, 0.02), 0.95)
    return float(np.clip(np.log(0.45) / np.log(m), 0.35, 1.0))


def apply_gamma(a, mean):
    g = gamma_for(mean)
    lut = np.clip(255.0 * (np.arange(256) / 255.0) ** g + 0.5, 0, 255).astype(np.uint8)
    return lut[a]


def clahe_luma(a, clip=2.0, tiles=4):
    y = luma(a.astype(np.float32))
    ye = cv2.createCLAHE(clipLimit=clip, tileGridSize=(tiles, tiles)).apply(np.clip(y, 0, 255).astype(np.uint8)).astype(np.float32)
    ratio = (ye + 1) / (y + 1)
    return np.clip(a.astype(np.float32) * ratio[..., None], 0, 255).astype(np.uint8)


def normalize_luma(a, target_mean=128.0, target_std=None):
    """Proposed 'auto-levels' for the 112 crop: robust gain to put face mean at ~120."""
    y = luma(a.astype(np.float32))
    c = y[16:96, 16:96]
    lo, hi = np.percentile(c, 2), np.percentile(c, 98)
    if hi - lo < 8:
        return a
    gain = 200.0 / (hi - lo)
    gain = min(gain, 4.0)
    out = (a.astype(np.float32) - lo) * gain + 28
    return np.clip(out, 0, 255).astype(np.uint8)

# ------------------------------------------------------------------ quality metrics
def main_blur_bright(frame, box):
    return ap.brightness_and_blur(Image.fromarray(frame), box)


def crop_sharpness(crop112: np.ndarray) -> float:
    """PROPOSED: contrast-normalised Laplacian energy on the aligned 112 crop.
    Resolution invariant (fixed face scale) and illumination invariant (divide by
    the crop's own contrast). Computed on the central face region only."""
    y = luma(crop112.astype(np.float32))[20:100, 16:96]
    y = cv2.GaussianBlur(y, (0, 0), 0.6)            # suppress pure sensor noise
    lap = cv2.Laplacian(y, cv2.CV_32F, ksize=1)
    sd = float(y.std()) + 1e-3
    return float(lap.std() / sd)


def noise_sigma(crop112: np.ndarray) -> float:
    y = luma(crop112.astype(np.float32))[20:100, 16:96]
    k = np.array([[1, -2, 1], [-2, 4, -2], [1, -2, 1]], np.float32)
    r = cv2.filter2D(y, -1, k)
    return float(np.sqrt(np.pi / 2) * np.mean(np.abs(r)) / 6.0)   # Immerkaer estimator

# ------------------------------------------------------------------ PAD (port of PresentationAttackAnalyzer / Detector)
def pad_signals(crop: np.ndarray):
    s = cv2.resize(crop, (96, 96), interpolation=cv2.INTER_LINEAR).astype(np.float32)
    g = luma(s)
    hl = float(np.mean((s[..., 0] > 235) & (s[..., 1] > 235) & (s[..., 2] > 235)))
    lap = np.abs(4 * g[1:-1, 1:-1] - g[1:-1, :-2] - g[1:-1, 2:] - g[:-2, 1:-1] - g[2:, 1:-1])
    hfe = float(lap.mean() / 255.0)
    c = g[24:72, 24:72]
    return hfe, hl, float(c.std())


def pad_main_reject(crop, blur):
    hfe, hl, csd = pad_signals(crop)
    if hfe > 0.18: return "SCREEN"
    if hl > 0.10: return "GLARE"
    if csd < 6.0 and blur < 200: return "PRINT"
    return None


def pad_new_reject(crop):
    """PROPOSED: noise-compensated moire energy, skin-only glare clusters, contrast-normalised print test."""
    hfe, hl, csd = pad_signals(crop)
    ns = noise_sigma(crop)
    # expected |Laplacian| contribution of white noise of sigma ns is ~ 0.8*sqrt(20)*ns
    hfe_comp = max(0.0, hfe * 255.0 - 3.6 * ns) / 255.0
    if hfe_comp > 0.22: return "SCREEN"
    if hl > 0.25: return "GLARE"
    if csd < 3.0: return "PRINT"
    return None

# ------------------------------------------------------------------ gates
def gate_main(sig, blur, bright, min_blur=60.0, min_bright=40.0):
    if sig.face_count != 1: return "NO_FACE" if sig.face_count == 0 else "MULTI"
    if sig.face_ratio < 0.10: return "TOO_SMALL"
    if abs(sig.pitch) > 15: return "POSE"
    if abs(sig.yaw) > 12: return "POSE"
    if blur < min_blur: return "BLUR"
    if bright < min_bright: return "DARK"
    if bright > 220: return "OVER"
    return None


def gate_new(sig, crop, raw_face_luma):
    if sig.face_count != 1: return "NO_FACE" if sig.face_count == 0 else "MULTI"
    if sig.face_ratio < 0.06: return "TOO_SMALL"
    if abs(sig.pitch) > 25: return "POSE"
    if abs(sig.yaw) > 25: return "POSE"
    if raw_face_luma < 12: return "DARK"
    if raw_face_luma > 235: return "OVER"
    if crop_sharpness(crop) < SHARP_MIN: return "BLUR"
    return None

SHARP_MIN = float(os.environ.get("SHARP_MIN", "0.0"))   # calibrated below; 0 = report only

# ------------------------------------------------------------------ data
def load_pairs(n):
    ids = sorted(d for d in os.listdir(ROOT) if len(os.listdir(os.path.join(ROOT, d))) >= 2)
    random.Random(3).shuffle(ids)
    out = []
    for d in ids[:n]:
        fs = sorted(os.listdir(os.path.join(ROOT, d)))
        out.append((d, os.path.join(ROOT, d, fs[0]), os.path.join(ROOT, d, fs[1])))
    return out


def detect_and_crop(frame):
    img = Image.fromarray(frame)
    dets = ap.detect(img)
    sig = ap.signals_for(img, dets)
    crop = np.asarray(ap.align(img, dets[0].landmarks)) if len(dets) == 1 else None
    return dets, sig, crop


def emb(crop):
    return ap.embed(Image.fromarray(np.ascontiguousarray(crop[..., ::-1])))   # BGR like EmbeddingEngine.kt


CONDS = ["normal", "dim", "very_dim", "backlit", "backlit_hard", "dim_motion"]
VARIANTS = ["raw", "gamma", "levels", "gamma_clahe", "avg4", "avg4_levels"]


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 120
    pairs = load_pairs(n)
    t0 = time.time()
    # gallery: enrolment image under normal light, 640x480 frame
    gallery, kept = {}, []
    for pid, (ident, g, p) in enumerate(pairs):
        fr = camera(to_frame(np.asarray(Image.open(g).convert("RGB")), 640, 480), "normal", pid)
        dets, sig, crop = detect_and_crop(fr)
        if crop is None: continue
        gallery[ident] = emb(crop); kept.append((ident, p))
    print(f"gallery {len(gallery)} ids  ({time.time()-t0:.0f}s)")
    G = np.stack(list(gallery.values())); names = list(gallery.keys())

    res = {}
    sharp_log = {}
    for cond in CONDS:
        st = {k: 0 for k in ["n", "det", "gateMain", "gate720", "gateNew", "padFP", "padFPNew"]}
        rej = {"main": {}, "720": {}, "pad": {}}
        gen = {v: [] for v in VARIANTS}; imp = {v: [] for v in VARIANTS}
        sharp_log[cond] = []
        for i, (ident, p) in enumerate(kept):
            base = np.asarray(Image.open(p).convert("RGB"))
            seed = 1000 * (CONDS.index(cond) + 1) + i
            fr = camera(to_frame(base, 640, 480), cond, seed)
            fr720 = camera(to_frame(base, 1280, 720), cond, seed)
            st["n"] += 1
            dets, sig, crop = detect_and_crop(fr)
            if crop is None:
                rej["main"]["NO_FACE"] = rej["main"].get("NO_FACE", 0) + 1
                continue
            st["det"] += 1
            bright, blur = main_blur_bright(fr, dets[0].box)
            r = gate_main(sig, blur, bright); st["gateMain"] += r is None
            if r: rej["main"][r] = rej["main"].get(r, 0) + 1
            d7 = ap.detect(Image.fromarray(fr720))
            if len(d7) == 1:
                s7 = ap.signals_for(Image.fromarray(fr720), d7)
                b7, bl7 = main_blur_bright(fr720, d7[0].box)
                r7 = gate_main(s7, bl7, b7); st["gate720"] += r7 is None
                if r7: rej["720"][r7] = rej["720"].get(r7, 0) + 1
            rn = gate_new(sig, normalize_luma(crop), bright); st["gateNew"] += rn is None
            sharp_log[cond].append(crop_sharpness(normalize_luma(crop)))
            pr = pad_main_reject(crop, blur)
            if pr: st["padFP"] += 1; rej["pad"][pr] = rej["pad"].get(pr, 0) + 1
            st["padFPNew"] += pad_new_reject(crop) is not None
            # 4 more noisy captures of the same pose (temporal burst), aligned with SAME landmarks
            burst = [crop]
            for k in range(1, 4):
                frk = camera(to_frame(base, 640, 480), cond, seed + 100000 * k)
                burst.append(np.asarray(ap.align(Image.fromarray(frk), dets[0].landmarks)))
            avg = np.mean(np.stack(burst).astype(np.float32), 0).astype(np.uint8)
            m = float(luma(crop.astype(np.float32))[20:100, 16:96].mean())
            V = {
                "raw": crop,
                "gamma": apply_gamma(crop, m),
                "levels": normalize_luma(crop),
                "gamma_clahe": clahe_luma(apply_gamma(crop, m)),
                "avg4": avg,
                "avg4_levels": normalize_luma(avg),
            }
            for v, c in V.items():
                e = emb(c); s = G @ e
                gi = names.index(ident)
                gen[v].append(float(s[gi])); s[gi] = -1; imp[v].append(float(s.max()))
        out = dict(st)
        out["rejects"] = rej
        for v in VARIANTS:
            g = np.array(gen[v]); im = np.array(imp[v])
            out[v] = dict(gen_mean=round(float(g.mean()), 4) if len(g) else None,
                          gen_acc=round(float((g >= ACCEPT).mean()), 4) if len(g) else None,
                          imp_far=round(float((im >= ACCEPT).mean()), 4) if len(im) else None,
                          imp_max=round(float(im.max()), 4) if len(im) else None)
        sl = np.array(sharp_log[cond]) if sharp_log[cond] else np.array([0])
        out["sharp_p05_p50"] = [round(float(np.percentile(sl, 5)), 3), round(float(np.percentile(sl, 50)), 3)]
        res[cond] = out
        os.makedirs(os.path.dirname(OUT), exist_ok=True)
        json.dump(res, open(OUT, "w"), indent=1)          # incremental: survives timeouts
        print(cond, json.dumps({k: out[k] for k in st}), "| gen_acc",
              {v: out[v]["gen_acc"] for v in VARIANTS}, "| far", {v: out[v]["imp_far"] for v in VARIANTS},
              "| sharp", out["sharp_p05_p50"], "| rej", rej, f"({time.time()-t0:.0f}s)", flush=True)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    json.dump(res, open(OUT, "w"), indent=1)


if __name__ == "__main__":
    main()
