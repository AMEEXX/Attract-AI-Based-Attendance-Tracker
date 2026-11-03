"""exp9 - REAL screen-replay attacks (AxonData/Display_replay_attacks, CC-BY-4.0): a phone camera filming a
face video/photo shown on a monitor. Plus the 5 'Real' (live) photos of the same set.

Per video: sample every 6th frame (~5 fps), downscale to 540x960, YOLO face detect (app port), 2.7x wide crop ->
  * dev pixel PAD verdict (exact port)               -> does dev catch it?
  * MiniFASNetV2 live score (silentface.tflite)      -> does a PAD CNN catch it?
  * MediaPipe mesh -> |dyaw| span and ParallaxDepthTest residual on the max-|dyaw| pair inside sliding 2 s windows
    (this is what the proposed "mandatory 3D check" would see if an attacker films a replay of a person turning).
Usage: python tools/biometric_eval/exp9_real_replay_videos.py
"""
import os, sys, glob, json
import numpy as np, cv2
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap
import exp7_pad_backlight as e7
import exp8_parallax_3d as e8

AX = os.path.join(ap.REPO, ".datasets", "axon")


def main():
    fas = e7.Fas(); mesh = e8.Mesh()
    out = {"videos": {}, "real_photos": []}
    for f in sorted(glob.glob(os.path.join(AX, "Real", "*.jpg"))):
        im = np.asarray(Image.open(f).convert("RGB"))
        s = 960 / max(im.shape[:2]); im = cv2.resize(im, (int(im.shape[1] * s), int(im.shape[0] * s)))
        d = ap.detect(Image.fromarray(im))
        if not d: out["real_photos"].append(dict(file=os.path.basename(f), face=False)); continue
        wc = e7.wide_crop(im, d[0].box)
        out["real_photos"].append(dict(file=os.path.basename(f), face=True, fas=fas.live(wc, scale255=True),
                                       pix=e7.dev_pixel_pad(e7.analyzer(wc))))
    print("real photos", out["real_photos"], flush=True)

    for v in sorted(glob.glob(os.path.join(AX, "Screen", "*"))):
        cap = cv2.VideoCapture(v); i = 0; rows = []
        while True:
            ok, fr = cap.read()
            if not ok: break
            if i % 6 == 0:
                fr = cv2.cvtColor(cv2.resize(fr, (540, 960)), cv2.COLOR_BGR2RGB)
                d = ap.detect(Image.fromarray(fr))
                if d:
                    wc = e7.wide_crop(fr, d[0].box)
                    m = mesh.run(fr)
                    rows.append(dict(t=i / 30.0, fas=fas.live(wc, scale255=True), pix=e7.dev_pixel_pad(e7.analyzer(wc)),
                                     m=m))
            i += 1
        fasv = np.array([r["fas"] for r in rows]) if rows else np.array([0.0])
        # sliding 2 s windows: best pair by |dyaw|
        win = []
        ms = [r for r in rows if r["m"] is not None]
        for a in range(len(ms)):
            grp = [r for r in ms[a:] if r["t"] - ms[a]["t"] <= 2.0]
            if len(grp) < 4: continue
            yaws = [g["m"]["yaw"] for g in grp]
            ia, ib = int(np.argmin(yaws)), int(np.argmax(yaws))
            res = e8.residuals(grp[ia]["m"], grp[ib]["m"])
            win.append(dict(dyaw=float(max(yaws) - min(yaws)), dev=res["dev"], lsq=res["lsq"]))
        trig = [w for w in win if w["dyaw"] >= 8.0]
        name = os.path.basename(v)
        out["videos"][name] = dict(
            frames_with_face=len(rows),
            fas_p50=float(np.median(fasv)), fas_frac_lt_0_5=float(np.mean(fasv < 0.5)), fas_frac_lt_0_3=float(np.mean(fasv < 0.3)),
            fas_frac_lt_0_2=float(np.mean(fasv < 0.2)),
            dev_pixel_reject_frac=float(np.mean([r["pix"] is not None for r in rows])) if rows else 0.0,
            dev_pixel_reasons={str(k): int(sum(1 for r in rows if str(r["pix"]) == str(k))) for k in set(str(r["pix"]) for r in rows)},
            windows=len(win), windows_dyaw_ge8=len(trig),
            max_dyaw_2s=float(max([w["dyaw"] for w in win], default=0.0)),
            dev_residual_when_dyaw_ge8=e8.stats([w["dev"] for w in trig]),
            dev_residual_all_windows=e8.stats([w["dev"] for w in win]),
        )
        print(name, json.dumps({k: v2 for k, v2 in out["videos"][name].items() if not isinstance(v2, dict) or k == "dev_pixel_reasons"}), flush=True)
        os.makedirs("/tmp/exp9", exist_ok=True)
        if rows:
            fr0 = rows[len(rows) // 2]
    json.dump(out, open(os.path.join(os.path.dirname(__file__), "results", "exp9_real_replay_videos.json"), "w"), indent=1, default=str)


if __name__ == "__main__":
    main()
