"""EXP-2: Is the ArcFace recogniser healthy, and are the shipped thresholds sane?

LFW identities with >=4 images (first 150 such identities, 4 imgs each = 600 faces), run
through the EXACT app detector+aligner. Compares RGB (shipped) vs BGR input order, and
alignment variants, by genuine/impostor score separation.
Reports: genuine/impostor quantiles, EER, TAR@FAR, and what the shipped constants mean.
"""
import os, sys, json, itertools, random
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap

random.seed(7)
LFW = os.path.join(ap.REPO, ".datasets", "lfw")
ids = sorted(d for d in os.listdir(LFW) if len(os.listdir(os.path.join(LFW, d))) >= 4)[:150]


def emb_variants(img):
    dets = ap.detect(img)
    if not dets:
        return None
    d = dets[0]                                   # LFW: centre face is highest scoring
    crop = ap.align(img, d.landmarks)
    rgb = ap.embed(crop)
    bgr = ap.embed(Image.fromarray(np.asarray(crop)[..., ::-1].copy()))
    # 2-eye only (pre-fix aligner) for comparison
    M = ap.eye_fallback_matrix(d.landmarks); inv = np.linalg.inv(np.vstack([M, [0, 0, 1]]))
    eye = ap.embed(img.transform((112, 112), Image.AFFINE, data=tuple(inv[:2].flatten()), resample=Image.BILINEAR))
    flip = ap.embed(crop.transpose(Image.FLIP_LEFT_RIGHT))
    return dict(rgb=rgb, bgr=bgr, eye=eye, rgb_flipsum=(rgb + flip) / np.linalg.norm(rgb + flip))


E = {k: {} for k in ("rgb", "bgr", "eye", "rgb_flipsum")}
miss = 0
for i in ids:
    files = sorted(os.listdir(os.path.join(LFW, i)))[:4]
    for f in files:
        v = emb_variants(Image.open(os.path.join(LFW, i, f)).convert("RGB"))
        if v is None:
            miss += 1; continue
        for k in E:
            E[k].setdefault(i, []).append(v[k])


def metrics(emb):
    gen, imp = [], []
    keys = list(emb)
    for k in keys:
        for a, b in itertools.combinations(emb[k], 2):
            gen.append(float(a @ b))
    for _ in range(20000):
        a, b = random.sample(keys, 2)
        imp.append(float(random.choice(emb[a]) @ random.choice(emb[b])))
    gen, imp = np.array(gen), np.array(imp)
    ths = np.linspace(-0.2, 1, 1201)
    far = np.array([(imp >= t).mean() for t in ths]); frr = np.array([(gen < t).mean() for t in ths])
    e = np.argmin(np.abs(far - frr))
    def tar_at(f):
        t = np.quantile(imp, 1 - f); return float((gen >= t).mean()), float(t)
    return dict(
        n_gen=len(gen), n_imp=len(imp),
        gen_q=np.quantile(gen, [0.01, 0.05, 0.5]).round(3).tolist(),
        imp_q=np.quantile(imp, [0.5, 0.99, 0.999]).round(3).tolist(), imp_max=round(float(imp.max()), 3),
        eer=round(float((far[e] + frr[e]) / 2), 4), eer_thr=round(float(ths[e]), 3),
        tar_far1e2=tar_at(1e-2), tar_far1e3=tar_at(1e-3),
        at_accept_0_25=dict(FAR=round(float((imp >= 0.25).mean()), 4), FRR=round(float((gen < 0.25).mean()), 4)),
        at_dup_0_22=dict(impostor_flag_rate=round(float((imp >= 0.22).mean()), 4)),
        at_continuity_0_35=dict(genuine_fail_rate=round(float((gen < 0.35).mean()), 4)),
    )


res = {k: metrics(v) for k, v in E.items()}
res["_meta"] = dict(identities=len(ids), faces=sum(len(v) for v in E["rgb"].values()), detector_miss=miss)
print(json.dumps(res, indent=1))
json.dump(res, open(os.path.join(os.path.dirname(__file__), "results", "exp2_embedding_sanity.json"), "w"), indent=1)
