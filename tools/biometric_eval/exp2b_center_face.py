"""EXP-2b: same as EXP-2 but select the detection nearest the image centre (LFW protocol)
and compare preprocessing variants that could explain weak separation."""
import os, sys, json, itertools, random
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap
random.seed(7)
LFW = os.path.join(ap.REPO, ".datasets", "lfw")
ids = sorted(d for d in os.listdir(LFW) if len(os.listdir(os.path.join(LFW, d))) >= 4)[:150]
m = ap.Models.get()

def raw_embed(arr):
    m.arc.set_tensor(m.ai, arr[None].astype(np.float32)); m.arc.invoke()
    v = m.arc.get_tensor(m.ao)[0].astype(np.float64); return v / np.linalg.norm(v)

E = {k: {} for k in ("rgb_127", "bgr_127", "rgb_01", "rgb_m11_255")}
for i in ids:
    for f in sorted(os.listdir(os.path.join(LFW, i)))[:4]:
        img = Image.open(os.path.join(LFW, i, f)).convert("RGB")
        dets = ap.detect(img)
        if not dets: continue
        d = min(dets, key=lambda d: (d.cx - .5) ** 2 + (d.cy - .5) ** 2)
        a = np.asarray(ap.align(img, d.landmarks), dtype=np.float32)
        E["rgb_127"].setdefault(i, []).append(raw_embed((a - 127.5) / 128))
        E["bgr_127"].setdefault(i, []).append(raw_embed((a[..., ::-1] - 127.5) / 128))
        E["rgb_01"].setdefault(i, []).append(raw_embed(a / 255.0))
        E["rgb_m11_255"].setdefault(i, []).append(raw_embed(a / 127.5 - 1))

def metrics(emb):
    gen = [float(a @ b) for k in emb for a, b in itertools.combinations(emb[k], 2)]
    keys = list(emb); imp = []
    for _ in range(20000):
        a, b = random.sample(keys, 2); imp.append(float(random.choice(emb[a]) @ random.choice(emb[b])))
    gen, imp = np.array(gen), np.array(imp)
    ths = np.linspace(-0.2, 1, 1201)
    far = np.array([(imp >= t).mean() for t in ths]); frr = np.array([(gen < t).mean() for t in ths])
    e = np.argmin(np.abs(far - frr))
    t3 = np.quantile(imp, 0.999)
    return dict(gen_q01_05_50=np.quantile(gen, [.01, .05, .5]).round(3).tolist(),
                imp_q50_99_999=np.quantile(imp, [.5, .99, .999]).round(3).tolist(),
                eer=round(float((far[e] + frr[e]) / 2), 4), eer_thr=round(float(ths[e]), 3),
                tar_at_far1e3=round(float((gen >= t3).mean()), 4), thr_far1e3=round(float(t3), 3))
res = {k: metrics(v) for k, v in E.items()}
print(json.dumps(res, indent=1))
json.dump(res, open(os.path.join(os.path.dirname(__file__), "results", "exp2b_center_face.json"), "w"), indent=1)
