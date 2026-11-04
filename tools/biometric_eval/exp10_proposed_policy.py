"""exp10 - Validate the PROPOSED Doc-22 liveness policy (WP-A + WP-B + WP-C + WP-E) on the exp7 scenes.

Per identity and scene, 3 consecutive "preview frames" are synthesised (independent ISP noise seeds and
+-3 % face-box jitter, like a hand-held phone at ~6 fps). For each frame:
  * WP-A  pixel PAD on the INNER 1.0x face crop (112x112 -> analyzer 96x96), exact port of the dev gates
          (hfec > 0.22 | spec > 0.25 | cstd < 3.0); veto only if the SAME reason trips on >= 2 of 3 frames (WP-C).
  * WP-B  MiniFASNetV2 (silentface.tflite) on the 2.7x wide crop, raw 0..255 BGR NCHW 80x80; score = median of 3.
Outputs reject rates of the proposed pixel veto, and BPCER/APCER of the PAD median for candidate thresholds.
The 3D-depth stage (WP-E) is taken from exp8 (real yaw turn passes 97 %, planar 0 % at residual 0.035).
Usage: python tools/biometric_eval/exp10_proposed_policy.py [n_ids=150]
"""
import os, sys, json, random
import numpy as np, cv2
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import exp7_pad_backlight as e7

SCENES = ["G:normal_bg", "G:white_wall", "G:window_backlit", "G:window_backlit_hard",
          "A:laptop_dark", "A:phone_dark", "A:print"]
THRS = [0.02, 0.05, 0.08, 0.10, 0.12, 0.15, 0.20, 0.25]


def jitter(box, rng, frac=0.03):
    l, t, r, b = box; w, h = r - l, b - t
    dx, dy = rng.uniform(-frac, frac) * w, rng.uniform(-frac, frac) * h
    s = 1 + rng.uniform(-frac, frac)
    cx, cy = (l + r) / 2 + dx, (t + b) / 2 + dy
    return (cx - w * s / 2, cy - h * s / 2, cx + w * s / 2, cy + h * s / 2)


def main():
    n_ids = int(sys.argv[1]) if len(sys.argv) > 1 else 150
    fas = e7.Fas()
    ids = sorted(os.listdir(e7.LFW)); random.Random(11).shuffle(ids)
    rec = {s: dict(veto={}, pad_med=[], pad_single=[], inner_cstd=[], inner_spec=[], inner_hfec=[], face_luma=[], noface=0)
           for s in SCENES}
    used = 0
    for ident in ids:
        if used >= n_ids: break
        fn = sorted(os.listdir(os.path.join(e7.LFW, ident)))[0]
        img = np.asarray(Image.open(os.path.join(e7.LFW, ident, fn)).convert("RGB"))
        base = e7.lfw_frame(img); box0 = e7.detect_box(base)
        if box0 is None: continue
        used += 1
        for s in SCENES:
            kind = s[2:]
            seed0 = used * 100
            fr0 = e7.genuine(base, box0, kind, seed0) if s[0] == "G" else e7.attack(img, kind, seed0)
            box = e7.detect_box(fr0)
            if box is None: rec[s]["noface"] += 1; continue
            rng = np.random.default_rng(seed0)
            reasons, pads = [], []
            for k in range(3):
                fr = fr0 if k == 0 else (e7.genuine(base, box0, kind, seed0 + k) if s[0] == "G" else e7.attack(img, kind, seed0 + k))
                bx = box if k == 0 else jitter(box, rng)
                inner = e7.wide_crop(fr, bx, scale=1.0, size=112)
                sig = e7.analyzer(inner)
                reasons.append(e7.dev_pixel_pad(sig))
                pads.append(fas.live(e7.wide_crop(fr, bx), scale255=True))
                if k == 0:
                    rec[s]["inner_cstd"].append(sig["cstd"]); rec[s]["inner_spec"].append(sig["spec"]); rec[s]["inner_hfec"].append(sig["hfec"])
                    rec[s]["face_luma"].append(float(np.mean(e7.analyzer_luma(inner))))
            nn = [r for r in reasons if r]
            veto = next((r for r in set(nn) if nn.count(r) >= 2), None)
            rec[s]["veto"][str(veto)] = rec[s]["veto"].get(str(veto), 0) + 1
            rec[s]["pad_med"].append(float(np.median(pads))); rec[s]["pad_single"].append(pads[0])
        if used % 10 == 0: print("ids", used, flush=True)

    out = dict(n_identities=used, scenes={})
    for s, r in rec.items():
        n = len(r["pad_med"]); pm = np.array(r["pad_med"]); ps = np.array(r["pad_single"])
        vetoed = n - r["veto"].get("None", 0)
        out["scenes"][s] = dict(
            n=n, no_face=r["noface"], inner_pixel_veto=r["veto"], inner_pixel_veto_rate=vetoed / max(n, 1),
            inner_cstd=e7.stats(r["inner_cstd"]), inner_spec=e7.stats(r["inner_spec"]), inner_hfec=e7.stats(r["inner_hfec"]),
            face_luma=e7.stats(r["face_luma"]), pad_median3=e7.stats(pm), pad_single=e7.stats(ps),
            pad_median3_frac_below={str(t): float(np.mean(pm < t)) for t in THRS},
            pad_single_frac_below={str(t): float(np.mean(ps < t)) for t in THRS},
        )
        o = out["scenes"][s]
        print(s, "n=%d veto=%.3f %s padmed p5=%.3f p50=%.3f  below0.05=%.3f below0.10=%.3f below0.15=%.3f" % (
            n, o["inner_pixel_veto_rate"], r["veto"], o["pad_median3"]["p5"], o["pad_median3"]["p50"],
            o["pad_median3_frac_below"]["0.05"], o["pad_median3_frac_below"]["0.1"], o["pad_median3_frac_below"]["0.15"]), flush=True)
    json.dump(out, open(os.path.join(e7.RES, "exp10_proposed_policy.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
