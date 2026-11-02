"""exp6 - calibrate the proposed sharpness gate and the PAD screen-replay gate.
For genuine LFW crops: sharpness vs. synthetic optical/motion blur, and PAD high-frequency
energy under phone-like high-ISO noise + ISP sharpening (the real cause of 'Screen detected'
false positives in dim rooms). Fast (~1-2 min)."""
import os, sys, json, numpy as np, cv2
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import exp5_lowlight as e5, app_pipeline as ap
pairs = e5.load_pairs(int(sys.argv[1]) if len(sys.argv) > 1 else 40)
crops = []
for ident, g, p in pairs:
    fr = e5.camera(e5.to_frame(np.asarray(Image.open(p).convert("RGB")), 640, 480), "normal", 1)
    _, _, c = e5.detect_and_crop(fr)
    if c is not None: crops.append(c)
print("crops", len(crops))
def degrade(c, dark, noise, sharpen, blur, motion, seed):
    r = np.random.default_rng(seed); f = c.astype(np.float32) * dark
    if blur: f = cv2.GaussianBlur(f, (0, 0), blur)
    if motion:
        k = np.zeros((motion, motion), np.float32); k[motion // 2, :] = 1 / motion; f = cv2.filter2D(f, -1, k)
    f = f + r.normal(0, noise, f.shape)
    if sharpen: f = cv2.addWeighted(f, 1 + sharpen, cv2.GaussianBlur(f, (0, 0), 1.0), -sharpen, 0)
    # camera auto-exposure / gain brings mean back up (this is what the phone delivers)
    f = f / dark if dark < 1 else f
    return np.clip(f, 0, 255).astype(np.uint8)
cases = {
 "sharp_good_light": (1.0, 2, 0, 0, 0), "dim_iso_noise": (0.35, 6, 0, 0, 0),
 "dim_iso_noise_sharpened": (0.35, 6, 1.5, 0, 0), "very_dim_sharpened": (0.2, 7, 1.5, 0, 0),
 "out_of_focus_s1.5": (1.0, 2, 0, 1.5, 0), "out_of_focus_s2.5": (1.0, 2, 0, 2.5, 0),
 "motion_9px": (1.0, 2, 0, 0, 9), "motion_15px": (1.0, 2, 0, 0, 15), "dim_motion_9px": (0.35, 6, 1.0, 0, 9),
}
out = {}
for name, (d, n, s, b, m) in cases.items():
    sh, hfe, hfec, scr_main, scr_new = [], [], [], 0, 0
    for i, c in enumerate(crops):
        x = degrade(c, d, n, s, b, m, i)
        lv = e5.normalize_luma(x)
        sh.append(e5.crop_sharpness(lv))
        h, hl, csd = e5.pad_signals(x); ns = e5.noise_sigma(x)
        hfe.append(h); hfec.append(max(0, h * 255 - 3.6 * ns) / 255)
        scr_main += h > 0.18; scr_new += e5.pad_new_reject(x) is not None
    q = lambda a: [round(float(np.percentile(a, p)), 3) for p in (5, 50, 95)]
    out[name] = dict(sharp_p5_50_95=q(sh), hfe=q(hfe), hfe_comp=q(hfec),
                     screenFP_main=f"{scr_main}/{len(crops)}", padReject_new=f"{scr_new}/{len(crops)}")
    print(name.ljust(26), out[name])
json.dump(out, open(os.path.join(os.path.dirname(__file__), "results", "exp6_gates.json"), "w"), indent=1)
