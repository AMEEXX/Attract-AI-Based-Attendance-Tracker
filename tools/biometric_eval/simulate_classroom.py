"""Multi-day classroom simulation of the attendance pipeline on mock data.

MOCK CLASS (mirrors the user's scenario)
  Pose-labelled subjects (Pointing'04, 15 people, 2 capture sessions on different days)
    - enrollment uses session-1 STRAIGHT / LEFT / RIGHT turns
    - later check-ins use session-2 images (different day, lighting, clothes)
  LFW subjects (in-the-wild, 1 photo per "day")
    - enrolled from their first photo(s); check in with later, unseen photos
  Strangers: LFW people who are NOT on the roster - must never be marked present.

DAYS
  Day 1: clean DB. Everyone present (except 'absentees') clicks -> not found ->
         enrollment list -> picks own name -> 3-pose enrollment -> ENROLLED & PRESENT.
  Day 2: everyone clicks; enrolled must be recognised; day-1 absentees must be
         routed to enrollment; strangers must NOT match.
  Day 3: repeat with yet another photo.

Two pipelines are compared:
  CURRENT  - exact constants/gates from origin/main (app_pipeline.py)
  PROPOSED - nose-offset yaw with relative turn gating, BGR input, calibrated
             accept/margin/duplicate/continuity thresholds (see PROPOSED dict)
"""
import os, sys, json, random, pickle, collections, csv
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
import app_pipeline as ap

DS = os.path.join(ap.REPO, ".datasets")
C = pickle.load(open(os.path.join(DS, "feature_cache.pkl"), "rb"))
OUTDIR = os.path.join(os.path.dirname(__file__), "results")

CURRENT = dict(name="CURRENT", emb="emb_rgb", yaw="app",
               accept=0.25, margin=0.05, dup=0.22, cont=0.35,
               straight_max=15, turn_min=20, turn_max=45, quality_blur=120.0, rel_turn=False)

# Proposed values are calibrated in calibrate.py on DISJOINT identities and written here.
PROPOSED_PATH = os.path.join(OUTDIR, "proposed_config.json")


def proposed():
    return json.load(open(PROPOSED_PATH))


# ------------------------------------------------------------------ yaw providers
NOSE_SCALE = -60.8    # degrees per inter-eye unit, fitted in EXP-3 on subjects 1-8 (sign: +ve = subject's right in image)


def yaw_of(rec, cfg):
    if cfg["yaw"] == "app":
        return rec["app_yaw"]
    return NOSE_SCALE * rec["nose_off"]


def pose_ok(rec, slot, cfg, anchor_yaw=None):
    """slot: STRAIGHT / LEFT / RIGHT -> (ok, reason)."""
    y = yaw_of(rec, cfg)
    if slot == "STRAIGHT":
        return (abs(y) <= cfg["straight_max"], "POSE_NOT_STRAIGHT")
    if cfg.get("rel_turn") and anchor_yaw is not None:
        y = y - anchor_yaw                       # relative to the user's own frontal
    lo, hi = cfg["turn_min"], cfg["turn_max"]
    if slot == "LEFT":
        return (-hi <= y <= -lo, "POSE_NOT_LEFT")
    return (lo <= y <= hi, "POSE_NOT_RIGHT")


FAIR = True   # LFW = 250px web photos with bystanders: neutralise those artefacts for BOTH pipelines


def quality_ok(rec, cfg, is_lfw=False):
    if rec.get("n_faces", 0) == 0:
        return False, "NO_FACE"
    if rec["n_faces"] > 1 and not (FAIR and is_lfw):
        return False, "MULTIPLE_FACES"
    if rec["blur"] < cfg["quality_blur"] and not (FAIR and is_lfw):
        return False, "BLUR"
    if not (40 <= rec["brightness"] <= 220):
        return False, "LIGHT"
    return True, "OK"


# ------------------------------------------------------------------ roster construction
def p4_key(s, ser, tilt, pan):
    return f"p4/p{s:02d}_s{ser}_t{tilt:+03d}_p{pan:+03d}.jpg"


def p4_frames(s, ser, pans, tilts=(0, 15, -15)):
    out = []
    for p in pans:
        for t in tilts:
            k = p4_key(s, ser, t, p)
            if k in C and C[k].get("n_faces", 0) >= 1 and "emb_rgb" in C[k]:
                out.append(k)
    return out


def build_class(seed=0, test_ids=None):
    rnd = random.Random(seed)
    lfw_multi = sorted({k.split("/")[1] for k in C if k.startswith("lfw/")
                        and sum(1 for kk in C if kk.startswith(f"lfw/{k.split('/')[1]}/")) >= 4})
    calib_ids = set(json.load(open(PROPOSED_PATH))["calibration_identities"]) if os.path.exists(PROPOSED_PATH) else set()
    pool = [i for i in lfw_multi if i not in calib_ids]
    rnd.shuffle(pool)
    students = []
    for s in range(1, 16):                                   # 15 pose-labelled students
        students.append(dict(id=f"P04_{s:02d}", kind="p4", subj=s))
    for i in pool[:45]:                                      # 45 in-the-wild students -> class of 60
        files = sorted(k for k in C if k.startswith(f"lfw/{i}/") and "emb_rgb" in C[k])
        if len(files) >= 4:
            students.append(dict(id=f"LFW_{i}", kind="lfw", files=files))
    strangers = sorted(k for k in C if k.startswith("lfw/") and "emb_rgb" in C[k]
                       and k.split("/")[1] not in lfw_multi)
    rnd.shuffle(strangers)
    absent_day1 = set(rnd.sample([s["id"] for s in students], 8))
    return students, strangers[:120], absent_day1


# ------------------------------------------------------------------ enrollment attempt
def enrollment_captures(st, cfg, rnd):
    """Simulate the user turning for each slot. Returns (samples, log)."""
    log = []
    if st["kind"] == "p4":
        cands = {"STRAIGHT": p4_frames(st["subj"], 1, [0]),
                 "LEFT": p4_frames(st["subj"], 1, [-15, -30, -45]),        # natural "turn left" range
                 "RIGHT": p4_frames(st["subj"], 1, [15, 30, 45])}
        # Image-left/right vs subject-left/right: app yaw sign -> LEFT = negative.
        # Ground truth pan<0 gives app yaw<0 (EXP-1), so LEFT slot <- pan<0 frames.
    else:
        f = sorted(st["files"][:3], key=lambda k: abs(C[k]["nose_off"]))
        cands = {"STRAIGHT": [f[0]], "LEFT": [f[1]], "RIGHT": [f[2]]}       # in-the-wild: no real turns available
    samples = {}
    anchor = None
    for slot in ("STRAIGHT", "LEFT", "RIGHT"):
        accepted = None
        for k in cands[slot]:                                 # user keeps trying (each = one CLICK)
            rec = C[k]
            ok, why = quality_ok(rec, cfg, st["kind"] == "lfw")
            if ok and st["kind"] == "lfw" and slot != "STRAIGHT" and cfg["name"] == "PROPOSED":
                ok, why = True, "OK"                         # LFW lacks turns: pose gate N/A, measured on P04 only
            elif ok:
                ok, why = pose_ok(rec, slot, cfg, anchor)
            log.append((slot, k, ok, why))
            if ok:
                accepted = k; break
        if accepted is None:
            if st["kind"] == "lfw" and cfg["name"] == "CURRENT" and slot != "STRAIGHT":
                pass
            return None, log
        samples[slot] = accepted
        if slot == "STRAIGHT":
            anchor = yaw_of(C[accepted], cfg)
    return samples, log


def validate(samples, cfg):
    e = {k: C[v][cfg["emb"]] for k, v in samples.items()}
    y = {k: yaw_of(C[v], cfg) for k, v in samples.items()}
    if cfg["name"] == "CURRENT":
        ok, why = ap.validate_batch((e["STRAIGHT"], y["STRAIGHT"]), (e["LEFT"], y["LEFT"]), (e["RIGHT"], y["RIGHT"]))
        if not ok:
            return False, why
    sl = float(e["STRAIGHT"] @ e["LEFT"]); sr = float(e["STRAIGHT"] @ e["RIGHT"])
    if min(sl, sr) < cfg["cont"]:
        return False, f"CONTINUITY({min(sl, sr):.2f})"
    return True, "OK"


def dup_check(samples, gallery, cfg):
    best = (None, -1.0)
    for sid, ts in gallery.items():
        for k in samples.values():
            for t in ts:
                s = float(C[k][cfg["emb"]] @ t)
                if s > best[1]:
                    best = (sid, s)
    if cfg["name"] == "CURRENT":                       # first-hit >= thr (same result set)
        return ("SUSPICIOUS", best[0], best[1]) if best[1] >= cfg["dup"] else ("CLEAR", None, best[1])
    return ("SUSPICIOUS", best[0], best[1]) if best[1] >= cfg["dup"] else ("CLEAR", None, best[1])


def recognise(key, gallery, cfg):
    rec = C[key]
    ok, why = quality_ok(rec, cfg, key.startswith("lfw/"))
    if not ok:
        return ("QUALITY_RETRY", why, None)
    if key.startswith("lfw/"):
        pass
    elif cfg["name"] == "CURRENT" and abs(rec["app_yaw"]) > 15:
        return ("QUALITY_RETRY", "POSE", None)
    elif cfg["name"] != "CURRENT" and abs(yaw_of(rec, cfg)) > cfg["straight_max"] + 8:
        return ("QUALITY_RETRY", "POSE", None)
    q = rec[cfg["emb"]]
    r = sorted(((sid, max(float(q @ t) for t in ts)) for sid, ts in gallery.items()), key=lambda x: -x[1])
    if not r:
        return ("NOT_FOUND", None, None)
    top = r[0]; second = r[1][1] if len(r) > 1 else -1.0
    if top[1] < cfg["accept"]:
        return ("NOT_FOUND", top[0], top[1])
    if top[1] - second < cfg["margin"]:
        return ("AMBIGUOUS", top[0], top[1])
    return ("MATCH", top[0], top[1])


def checkin_image(st, day):
    """A previously unseen image of the student for day N (N>=2)."""
    if st["kind"] == "p4":
        fr = p4_frames(st["subj"], 2, [0, -15, 15], tilts=(0, 15, -15))
        return fr[(day - 2) % len(fr)] if fr else None
    f = st["files"]
    idx = 3 + (day - 2)
    return f[idx] if idx < len(f) else f[-1]


def run(cfg, seed=0):
    rnd = random.Random(seed)
    students, strangers, absent = build_class(seed)
    gallery = {}           # sid -> [embeddings]
    T = collections.Counter(); events = []
    enroll_fail = collections.Counter()

    def enroll(st, day):
        samples, log = enrollment_captures(st, cfg, rnd)
        T["enroll_clicks"] += len(log)
        if samples is None:
            last = log[-1] if log else ("STRAIGHT", None, False, "NO_USABLE_FRAME")
            enroll_fail[f"{last[0]}:{last[3]}"] += 1
            events.append((day, st["id"], "ENROLL_FAILED", f"{last[0]} {last[3]} after {len(log)} clicks"))
            return False
        ok, why = validate(samples, cfg)
        if not ok:
            enroll_fail[f"VALIDATOR:{why.split('(')[0]}"] += 1
            events.append((day, st["id"], "ENROLL_REJECTED", why)); return False
        d = dup_check(samples, gallery, cfg)
        if d[0] == "SUSPICIOUS":
            enroll_fail["DUPLICATE_FALSE_BLOCK"] += 1
            events.append((day, st["id"], "DUPLICATE_BLOCKED", f"vs {d[1]} {d[2]:.2f}")); return False
        gallery[st["id"]] = [C[k][cfg["emb"]] for k in samples.values()]
        events.append((day, st["id"], "ENROLLED_PRESENT", "")); return True

    # ---------------- Day 1: clean DB
    for st in students:
        if st["id"] in absent:
            continue
        T["d1_attempts"] += 1
        # first click: recognition on a straight frame; DB empty or person not enrolled
        k = sorted(st["files"][:3], key=lambda k: abs(C[k]["nose_off"]))[0] if st["kind"] == "lfw" else (p4_frames(st["subj"], 1, [0]) or [None])[0]
        outcome = recognise(k, gallery, cfg) if k else ("QUALITY_RETRY", "NO_FACE", None)
        if outcome[0] == "MATCH":                     # wrongly matched someone already enrolled
            T["d1_false_match_before_enroll"] += 1
            events.append((1, st["id"], "WRONG_MATCH", f"{outcome[1]} {outcome[2]:.2f}")); continue
        if enroll(st, 1):
            T["d1_enrolled"] += 1

    # ---------------- Day 2..3
    for day in (2, 3):
        for st in students:
            T[f"d{day}_attempts"] += 1
            k = checkin_image(st, day)
            res = None
            for attempt in range(2):                  # SDD: max 2 recognition attempts
                res = recognise(k, gallery, cfg)
                band = cfg.get("confirm_below")
                if res[0] == "MATCH" and band and res[2] < band:
                    # borderline: require a 2nd independent frame agreeing on the SAME identity
                    k2 = checkin_image(st, day + 1) or k
                    r2 = recognise(k2, gallery, cfg) if k2 != k else res
                    if not (r2[0] == "MATCH" and r2[1] == res[1]):
                        res = ("AMBIGUOUS", res[1], res[2])
                    break
                if res[0] in ("MATCH", "NOT_FOUND"):
                    break
                # retry with the alternate image (student repositions)
                alt = checkin_image(st, day + 1)
                k = alt or k
            enrolled = st["id"] in gallery
            if res[0] == "MATCH":
                if res[1] == st["id"]:
                    T[f"d{day}_correct_present"] += 1
                else:
                    T[f"d{day}_WRONG_IDENTITY"] += 1
                    events.append((day, st["id"], "WRONG_IDENTITY", f"marked as {res[1]} ({res[2]:.2f})"))
            elif enrolled:
                T[f"d{day}_enrolled_but_routed_to_enroll" if res[0] == "NOT_FOUND" else f"d{day}_enrolled_{res[0]}"] += 1
                events.append((day, st["id"], "ENROLLED_NOT_RECOGNISED", f"{res[0]} top={res[1]} {res[2]}"))
            else:
                T[f"d{day}_unenrolled_routed_to_enroll"] += 1
                if enroll(st, day):
                    T[f"d{day}_late_enrolled"] += 1
        for k in strangers[(day - 2) * 60:(day - 1) * 60]:
            res = recognise(k, gallery, cfg)
            T[f"d{day}_stranger_{'MARKED_PRESENT' if res[0] == 'MATCH' else 'rejected'}"] += 1
    return dict(config=cfg["name"], class_size=len(students), absent_day1=len(absent),
                totals=dict(T), enroll_failures=dict(enroll_fail), events=events, gallery_size=len(gallery))


def summarise(r):
    t = r["totals"]; n = r["class_size"]
    out = [f"=== {r['config']}  (class={n}, day-1 absentees={r['absent_day1']}, enrolled at end={r['gallery_size']})"]
    out.append(f"Day1: {t.get('d1_enrolled',0)}/{t.get('d1_attempts',0)} enrolled; enrollment clicks={t.get('enroll_clicks',0)}; failures={r['enroll_failures']}")
    for d in (2, 3):
        enrolled_n = t.get(f"d{d}_correct_present", 0) + t.get(f"d{d}_WRONG_IDENTITY", 0) + sum(v for k, v in t.items() if k.startswith(f"d{d}_enrolled_"))
        out.append(f"Day{d}: correct PRESENT={t.get(f'd{d}_correct_present',0)} | WRONG identity={t.get(f'd{d}_WRONG_IDENTITY',0)} | "
                   f"enrolled-but-not-recognised={sum(v for k,v in t.items() if k.startswith(f'd{d}_enrolled_'))} | "
                   f"unenrolled->enroll={t.get(f'd{d}_unenrolled_routed_to_enroll',0)} (late-enrolled ok={t.get(f'd{d}_late_enrolled',0)}) | "
                   f"strangers marked present={t.get(f'd{d}_stranger_MARKED_PRESENT',0)}/{t.get(f'd{d}_stranger_MARKED_PRESENT',0)+t.get(f'd{d}_stranger_rejected',0)}")
    return "\n".join(out)


if __name__ == "__main__":
    which = sys.argv[1:] or ["CURRENT", "PROPOSED"]
    allres = {}
    for w in which:
        cfg = CURRENT if w == "CURRENT" else proposed()
        runs = [run(cfg, seed) for seed in range(5)]
        allres[w] = runs
        print(summarise(runs[0])); print()
        agg = collections.Counter()
        for r in runs:
            agg.update(r["totals"])
        print(f"--- {w}: aggregate over 5 random classes (300 student-classes, 600 stranger probes)")
        print("   ", json.dumps(dict(sorted(agg.items()))))
        print()
    json.dump(allres, open(os.path.join(OUTDIR, "classroom_simulation.json"), "w"), indent=1, default=str)
