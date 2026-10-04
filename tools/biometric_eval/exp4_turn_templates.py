"""EXP-4: Do LEFT/RIGHT templates help genuine matching, and do they inflate impostor scores?
P04: gallery from series-1, probes = series-2 near-frontal (|pan|<=15, |tilt|<=15).
Gallery variants: F (frontal only), F+15 (frontal + ±15° turns), F+30 (frontal + ±30°)."""
import pickle, json, os, numpy as np
C = pickle.load(open(os.path.join(os.path.dirname(__file__), "..", "..", ".datasets", "feature_cache.pkl"), "rb"))
E = "emb_bgr"
def get(s, ser, p, t=0):
    for tt in (t, 15, -15):
        k = f"p4/p{s:02d}_s{ser}_t{tt:+03d}_p{p:+03d}.jpg"
        if k in C and E in C[k]: return C[k][E]
subs = [s for s in range(1, 16) if get(s, 1, 0) is not None]
variants = {"F": [0], "F+15": [0, -15, 15], "F+30": [0, -30, 30], "F+15+30": [0, -15, 15, -30, 30]}
res = {}
for name, pans in variants.items():
    gal = {s: [v for p in pans if (v := get(s, 1, p)) is not None] for s in subs}
    gen, imp, top1 = [], [], 0; n = 0
    for s in subs:
        for p in (0, -15, 15):
            for t in (0, 15, -15):
                k = f"p4/p{s:02d}_s2_t{t:+03d}_p{p:+03d}.jpg"
                if k not in C or E not in C[k]: continue
                q = C[k][E]; sc = {g: max(float(q @ x) for x in ts) for g, ts in gal.items()}
                gen.append(sc[s]); imp.append(max(v for g, v in sc.items() if g != s))
                top1 += max(sc, key=sc.get) == s; n += 1
    gen, imp = np.array(gen), np.array(imp)
    res[name] = dict(probes=n, top1_acc=round(top1 / n, 3), genuine_median=round(float(np.median(gen)), 3),
                     genuine_p10=round(float(np.quantile(gen, .1)), 3), best_impostor_p90=round(float(np.quantile(imp, .9)), 3),
                     best_impostor_max=round(float(imp.max()), 3), genuine_ge_0_50=round(float((gen >= .5).mean()), 3))
    print(name, res[name])
json.dump(res, open(os.path.join(os.path.dirname(__file__), "results", "exp4_turn_templates.json"), "w"), indent=1)
