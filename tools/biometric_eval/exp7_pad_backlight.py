"""exp7 - Why genuine backlit faces get "Screen replay detected" while a laptop photo passes.

Faithful Python port of the dev (f85e7a8) pixel PAD path:
  CameraPreview: PadCrop.box(scale=2.7) on the YOLO face box -> crop -> resize 160x160 (wideCrop)
  PresentationAttackAnalyzer.analyze: resize 96x96 bilinear -> hfe, specular(R,G,B>235), centerStd, noiseSigma
  PresentationAttackDetector: hfeCompensated > 0.22 | specular > 0.25 | centerStd < 3.0  -> reject
and evaluates MiniFASNetV2 (silentface.tflite, Apache-2.0) on the same wide crop.

Scenes (LFW identities, portrait 480x640 phone frame, face ~40 % of width):
  genuine_normal_bg      : original (blurred) background
  genuine_white_wall     : person in front of a sun-lit white wall, face-metered AE (face OK, wall clipped 255)
  genuine_window_backlit : window behind, face under-exposed (x0.55) + ISO noise + ISP sharpening, wall clipped
  attack_laptop_dark     : photo shown full-screen on a 16:9 laptop (black side bars), re-captured by phone
  attack_phone_dark      : photo on a phone screen held in front of the camera (dark bezel, glare stripe)
  attack_print           : A4 colour print (low contrast, warm paper, halftone)
Real attacks: CelebA-Spoof test shard 0 (nguyenkhoa/celeba-spoof-for-face-antispoofing-test, 6,717 crops).

Usage: python tools/biometric_eval/exp7_pad_backlight.py [n_identities=150] [celeba_max=6717]
"""
import io, os, sys, json, random
import numpy as np, cv2
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap

REPO = ap.REPO
DS = os.path.join(REPO, ".datasets")
LFW = os.path.join(DS, "lfw")
RES = os.path.join(os.path.dirname(__file__), "results")
W, H = 480, 640

# ------------------------------------------------------------------ exact ports
def pad_crop_box(srcW, srcH, box, scale=2.7):
    l, t, r, b = box
    bw, bh = r - l, b - t
    msh = (srcH - 1) / bh if bh > 0 else scale
    msw = (srcW - 1) / bw if bw > 0 else scale
    s = min(msh, msw, scale)
    nw, nh = bw * s, bh * s
    cx, cy = (l + r) / 2, (t + b) / 2
    L, T, R, B = cx - nw / 2, cy - nh / 2, cx + nw / 2, cy + nh / 2
    if L < 0: R -= L; L = 0
    if T < 0: B -= T; T = 0
    if R > srcW: L = max(0, L - (R - srcW)); R = srcW
    if B > srcH: T = max(0, T - (B - srcH)); B = srcH
    return int(np.clip(L, 0, srcW)), int(np.clip(T, 0, srcH)), int(np.clip(R, 0, srcW)), int(np.clip(B, 0, srcH))


def wide_crop(frame, box, scale=2.7, size=160):
    L, T, R, B = pad_crop_box(frame.shape[1], frame.shape[0], box, scale)
    c = frame[T:B, L:R]
    return cv2.resize(c, (size, size), interpolation=cv2.INTER_LINEAR)


def analyzer(crop_rgb):
    s = cv2.resize(crop_rgb, (96, 96), interpolation=cv2.INTER_LINEAR).astype(np.float32)
    g = 0.299 * s[..., 0] + 0.587 * s[..., 1] + 0.114 * s[..., 2]
    hl = float(np.mean((s[..., 0] > 235) & (s[..., 1] > 235) & (s[..., 2] > 235)))
    lap = np.abs(4 * g[1:-1, 1:-1] - g[1:-1, :-2] - g[1:-1, 2:] - g[:-2, 1:-1] - g[2:, 1:-1])
    hfe = float(lap.mean() / 255.0)
    c = g[24:72, 24:72]
    k = np.array([[1, -2, 1], [-2, 4, -2], [1, -2, 1]], np.float32)
    r = cv2.filter2D(g, -1, k)[1:-1, 1:-1]
    ns = float(np.mean(np.abs(r)) * np.sqrt(np.pi / 2) / 6)
    return dict(hfe=hfe, hfec=max(0.0, hfe * 255 - 3.6 * ns) / 255, spec=hl, cstd=float(c.std()), ns=ns)


def dev_pixel_pad(sig):
    if sig["hfec"] > 0.22: return "SCREEN_HFE"
    if sig["spec"] > 0.25: return "SCREEN_GLARE"
    if sig["cstd"] < 3.0: return "PRINT_FLAT"
    return None

def analyzer_luma(c):
    c = c.astype(np.float32); return 0.299 * c[..., 0] + 0.587 * c[..., 1] + 0.114 * c[..., 2]


def face_gamma(crop, face_luma, target=110.0):
    """Proposed PAD pre-normalisation: gamma so that the FACE (not the frame) has mean luma ~target.
    Only brightens (backlit / dim); never darkens."""
    if face_luma >= target * 0.85 or face_luma < 3: return crop
    g = np.log(target / 255.0) / np.log(max(face_luma, 3) / 255.0)
    lut = np.clip(255.0 * (np.arange(256) / 255.0) ** g, 0, 255).astype(np.uint8)
    return lut[crop]

# ------------------------------------------------------------------ MiniFASNet
class Fas:
    def __init__(self):
        from ai_edge_litert.interpreter import Interpreter
        m_path = os.path.join(DS, "models", "silentface.tflite")
        if not os.path.exists(m_path):
            m_path = os.path.join(REPO, "app", "src", "main", "assets", "silentface.tflite")
        self.it = Interpreter(model_path=m_path)
        self.it.allocate_tensors()
        self.i = self.it.get_input_details()[0]["index"]; self.o = self.it.get_output_details()[0]["index"]

    def live(self, crop_rgb, scale255=False):
        x = cv2.resize(crop_rgb, (80, 80), interpolation=cv2.INTER_LINEAR)[..., ::-1].astype(np.float32)
        if not scale255: x = x / 255.0
        self.it.set_tensor(self.i, x.transpose(2, 0, 1)[None].copy())
        self.it.invoke()
        p = self.it.get_tensor(self.o)[0].astype(np.float64)
        if p.min() < 0 or abs(p.sum() - 1) > 1e-3:          # logits -> softmax
            p = np.exp(p - p.max()); p /= p.sum()
        return float(p[1])

# ------------------------------------------------------------------ scene synthesis
def person_mask(h, w, box):
    """Soft head+shoulders mask from the face box (pixel coords in the frame)."""
    l, t, r, b = box; fw, fh = r - l, b - t; cx, cy = (l + r) / 2, (t + b) / 2
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    head = (((xx - cx) / (fw * 0.72)) ** 2 + ((yy - (cy - 0.10 * fh)) / (fh * 0.82)) ** 2) <= 1
    sh_top = b + 0.15 * fh
    shoulders = (yy > sh_top) & (np.abs(xx - cx) < (fw * 0.55 + (yy - sh_top) * 1.4))
    neck = (yy > cy) & (yy <= sh_top + 2) & (np.abs(xx - cx) < fw * 0.30)
    m = (head | shoulders | neck).astype(np.float32)
    return cv2.GaussianBlur(m, (0, 0), 2.5)


def isp(f, noise, sharpen, seed):
    r = np.random.default_rng(seed)
    f = f.astype(np.float32)
    sigma = np.sqrt(noise ** 2 + 0.15 * np.maximum(f, 0))
    f = f + r.normal(0, 1, f.shape).astype(np.float32) * sigma
    f = cv2.GaussianBlur(f, (0, 0), 0.6)                         # ISP denoise
    if sharpen: f = cv2.addWeighted(f, 1 + sharpen, cv2.GaussianBlur(f, (0, 0), 1.2), -sharpen, 0)
    return np.clip(f, 0, 255).astype(np.uint8)


def lfw_frame(img):
    """LFW photo -> 480x640 portrait frame (face ~40 % width), background = blurred photo."""
    bg = cv2.GaussianBlur(cv2.resize(img, (W, H)), (0, 0), 25)
    s = (W * 0.95) / img.shape[1]
    face = cv2.resize(img, (int(img.shape[1] * s), int(img.shape[0] * s)), interpolation=cv2.INTER_CUBIC)
    fh, fw = face.shape[:2]; y0, x0 = (H - fh) // 2, (W - fw) // 2
    yy, xx = np.mgrid[0:fh, 0:fw]
    m = np.clip(np.clip(1 - np.maximum(np.abs(xx - fw / 2) / (fw / 2), np.abs(yy - fh / 2) / (fh / 2)), 0, 1) * 8, 0, 1)[..., None]
    out = bg.astype(np.float32); out[y0:y0 + fh, x0:x0 + fw] = face * m + out[y0:y0 + fh, x0:x0 + fw] * (1 - m)
    return out.astype(np.uint8)


def detect_box(frame):
    d = ap.detect(Image.fromarray(frame))
    return d[0].box if d else None


def genuine(frame, box, kind, seed):
    f = frame.astype(np.float32)
    if kind == "normal_bg":
        return isp(f, 2.5, 0.6, seed)
    m = person_mask(H, W, box)[..., None]
    r = np.random.default_rng(seed)
    if kind == "white_wall":           # face-metered AE: face exposed normally, wall clipped
        wall = np.full_like(f, 255.0) - r.uniform(0, 12) * np.linspace(0, 1, H)[:, None, None]
        out = f * m + wall * (1 - m)
        bloom = cv2.GaussianBlur(np.clip(wall * (1 - m) - 200, 0, None), (0, 0), 6) * 0.5   # glow into hair edge
        return isp(np.clip(out + bloom, 0, 255), 3.0, 0.8, seed)
    if kind in ("window_backlit", "window_backlit_hard"):   # AE metered on window: face dark, noisy, sharpened
        wall = np.full_like(f, 255.0)
        out = f * (0.55 if kind == "window_backlit" else 0.32) * m + wall * (1 - m)
        bloom = cv2.GaussianBlur(np.clip(wall * (1 - m) - 180, 0, None), (0, 0), 9) * 0.7
        return isp(np.clip(out + bloom, 0, 255), 7.0, 1.2, seed)
    raise ValueError(kind)


def screen_recapture(content, seed, bezel_frac, glare):
    """content = what the screen displays (uint8 RGB, already framed). Simulates display + phone camera."""
    r = np.random.default_rng(seed)
    c = content.astype(np.float32)
    c = 12 + c * (225 / 255.0)                                    # display black level / peak
    c = c * np.array([0.96, 1.0, 1.06], np.float32)               # cold white point
    hh, ww = c.shape[:2]
    yy, xx = np.mgrid[0:hh, 0:ww].astype(np.float32)
    ang = r.uniform(0, np.pi); per = r.uniform(2.6, 6.0)
    moire = 1 - r.uniform(0.04, 0.12) * (0.5 + 0.5 * np.cos(2 * np.pi * (xx * np.cos(ang) + yy * np.sin(ang)) / per))
    c = c * moire[..., None]
    if glare:
        band = np.exp(-((xx * 0.7 + yy * 0.3 - r.uniform(0.2, 0.8) * ww) / (ww * 0.06)) ** 2)
        c = c + band[..., None] * r.uniform(40, 110)
    c = cv2.GaussianBlur(c, (0, 0), 0.7)
    if bezel_frac > 0:                                            # dark bezel / room around screen
        bz = int(min(hh, ww) * bezel_frac)
        big = np.full((hh + 2 * bz, ww + 2 * bz, 3), 18.0, np.float32); big[bz:bz + hh, bz:bz + ww] = c
        c = cv2.resize(big, (ww, hh))
    return isp(c, 4.0, 0.9, seed)


def attack(img, kind, seed):
    if kind == "laptop_dark":          # 16:9 laptop showing the square photo full-height => black bars
        lap = np.zeros((360, 640, 3), np.uint8)
        ph = cv2.resize(img, (360, 360)); lap[:, 140:500] = ph
        # phone held so the face fills the frame: crop centre portrait region of the screen
        view = cv2.resize(lap[:, 140 - 60:500 + 60], (W, int(W * 360 / 480)))
        canvas = np.full((H, W, 3), 0, np.uint8); y0 = (H - view.shape[0]) // 2; canvas[y0:y0 + view.shape[0]] = view
        return screen_recapture(canvas, seed, 0.0, glare=False)
    if kind == "phone_dark":
        return screen_recapture(lfw_frame(img), seed, 0.12, glare=True)
    if kind == "print":
        f = lfw_frame(img).astype(np.float32)
        f = 35 + f * 0.70; f = f * np.array([1.04, 1.0, 0.92], np.float32)
        yy, xx = np.mgrid[0:H, 0:W]
        f = f * (1 - 0.05 * (0.5 + 0.5 * np.cos(2 * np.pi * (xx + yy) / 3.2)))[..., None]
        return isp(cv2.GaussianBlur(f, (0, 0), 0.9), 3.0, 0.7, seed)
    raise ValueError(kind)

# ------------------------------------------------------------------ main
def stats(xs):
    xs = np.array(xs, np.float64)
    return dict(n=int(len(xs)), p5=float(np.percentile(xs, 5)), p50=float(np.median(xs)), p95=float(np.percentile(xs, 95)))


def main():
    n_ids = int(sys.argv[1]) if len(sys.argv) > 1 else 150
    cel_max = int(sys.argv[2]) if len(sys.argv) > 2 else 6717
    fas = Fas()
    ids = sorted(os.listdir(LFW)); random.Random(7).shuffle(ids)
    scenes = ["G:normal_bg", "G:white_wall", "G:window_backlit", "G:window_backlit_hard", "A:laptop_dark", "A:phone_dark", "A:print"]
    rec = {s: dict(reasons={}, rows=[]) for s in scenes}
    used = 0
    for ident in ids:
        if used >= n_ids: break
        fn = sorted(os.listdir(os.path.join(LFW, ident)))[0]
        img = np.asarray(Image.open(os.path.join(LFW, ident, fn)).convert("RGB"))
        base = lfw_frame(img); box0 = detect_box(base)
        if box0 is None: continue
        used += 1
        for s in scenes:
            kind = s[2:]
            fr = genuine(base, box0, kind, used) if s[0] == "G" else attack(img, kind, used)
            box = detect_box(fr)
            if box is None:
                rec[s]["reasons"]["NO_FACE"] = rec[s]["reasons"].get("NO_FACE", 0) + 1; continue
            wc = wide_crop(fr, box)
            sig = analyzer(wc); why = dev_pixel_pad(sig)
            rec[s]["reasons"][str(why)] = rec[s]["reasons"].get(str(why), 0) + 1
            # inner-face variant of the same statistics (proposed: measure on 1.0x box, not 2.7x)
            inner = analyzer(wide_crop(fr, box, scale=1.0, size=112))
            sig_live = fas.live(wc); sig_live255 = fas.live(wc, scale255=True)
            fl = float(np.mean(analyzer_luma(wide_crop(fr, box, scale=1.0, size=112))))
            wcn = face_gamma(wc, fl)
            sig_norm = fas.live(wcn, scale255=True)
            rec[s]["rows"].append(dict(**sig, inner_spec=inner["spec"], inner_hfec=inner["hfec"], inner_cstd=inner["cstd"],
                                       fas=sig_live, fas255=sig_live255, fasnorm=sig_norm, face_luma=fl, why=why))
            if used <= 3:
                os.makedirs("/tmp/exp7", exist_ok=True)
                Image.fromarray(wc).save(f"/tmp/exp7/{used}_{kind}.jpg")
        if used % 25 == 0: print("ids", used, flush=True)

    out = {"n_identities": used, "scenes": {}}
    for s in scenes:
        rows = rec[s]["rows"]
        if not rows: continue
        g = lambda k: [r[k] for r in rows]
        out["scenes"][s] = dict(
            dev_pixel_pad_reasons=rec[s]["reasons"],
            dev_pixel_pad_reject_rate=float(np.mean([r["why"] is not None for r in rows])),
            wide_spec=stats(g("spec")), wide_hfec=stats(g("hfec")), wide_cstd=stats(g("cstd")),
            inner_spec=stats(g("inner_spec")), inner_hfec=stats(g("inner_hfec")),
            fas_live_div255=stats(g("fas")), fas_live_raw255=stats(g("fas255")),
            fas_live_lt_0_5=float(np.mean(np.array(g("fas255")) < 0.5)),
            fas_norm=stats(g("fasnorm")), face_luma=stats(g("face_luma")),
            fas255_frac_below={str(t): float(np.mean(np.array(g("fas255")) < t)) for t in [0.1, 0.2, 0.3, 0.5]},
            fasnorm_frac_below={str(t): float(np.mean(np.array(g("fasnorm")) < t)) for t in [0.1, 0.2, 0.3, 0.5]},
        )
        print(s, json.dumps(out["scenes"][s]["dev_pixel_pad_reasons"]), "reject=%.2f" % out["scenes"][s]["dev_pixel_pad_reject_rate"],
              "fas255 p50=%.3f" % out["scenes"][s]["fas_live_raw255"]["p50"], "fasnorm p50=%.3f" % out["scenes"][s]["fas_norm"]["p50"], "fas<0.5=%.2f" % out["scenes"][s]["fas_live_lt_0_5"], flush=True)

    # ---------------- real attacks: CelebA-Spoof test shard
    import pyarrow.parquet as pq
    pf = pq.ParquetFile(os.path.join(DS, "cs0.parquet"))
    cel = dict(live=dict(fas=[], fas255=[], pix=[]), spoof=dict(fas=[], fas255=[], pix=[]))
    n = 0
    for b in pf.iter_batches(batch_size=100):
        for im, lab in zip(b.column("cropped_image").to_pylist(), b.column("labelNames").to_pylist()):
            if n >= cel_max: break
            if im is None or im.get("bytes") is None: continue
            c = np.asarray(Image.open(io.BytesIO(im["bytes"])).convert("RGB"))
            if min(c.shape[:2]) < 24: continue
            cel[lab]["fas"].append(fas.live(c)); cel[lab]["fas255"].append(fas.live(c, scale255=True))
            cel[lab]["pix"].append(dev_pixel_pad(analyzer(cv2.resize(c, (160, 160)))) is not None)
            n += 1
        if n >= cel_max: break

    def auc(pos, neg):
        pos, neg = np.array(pos), np.array(neg)
        allv = np.concatenate([pos, neg]); ranks = allv.argsort().argsort() + 1
        return float((ranks[:len(pos)].sum() - len(pos) * (len(pos) + 1) / 2) / (len(pos) * len(neg)))

    celo = {"n_live": len(cel["live"]["fas"]), "n_spoof": len(cel["spoof"]["fas"])}
    for key in ["fas", "fas255"]:
        L, S = np.array(cel["live"][key]), np.array(cel["spoof"][key])
        celo[key] = dict(auc=auc(L, S), live=stats(L), spoof=stats(S),
                         by_thr={str(t): dict(BPCER=float(np.mean(L < t)), APCER=float(np.mean(S >= t)))
                                 for t in [0.1, 0.2, 0.3, 0.5, 0.7, 0.8, 0.9]})
    celo["dev_pixel_pad"] = dict(BPCER=float(np.mean(cel["live"]["pix"])), APCER=float(1 - np.mean(cel["spoof"]["pix"])))
    out["celeba_spoof_tight_crops"] = celo
    print("CelebA-Spoof", json.dumps(celo, indent=1))
    os.makedirs(RES, exist_ok=True)
    json.dump(out, open(os.path.join(RES, "exp7_pad_backlight.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
