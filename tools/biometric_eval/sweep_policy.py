"""Sweep decision policy on the classroom simulator (5 seeds) to find a safe operating point.
Policies: single-frame (accept, margin) and two-frame consensus (both frames must agree on
the same identity, each >= accept2, plus best margin)."""
import json, collections, sys, os
sys.path.insert(0, os.path.dirname(__file__))
import simulate_classroom as sc
base = sc.proposed()
rows = []
for acc in (0.43, 0.47, 0.50, 0.53):
    for mg in (0.05, 0.08, 0.12):
        cfg = dict(base, accept=acc, margin=mg)
        agg = collections.Counter(); wrong_kind = collections.Counter()
        for seed in range(5):
            r = sc.run(cfg, seed); agg.update(r["totals"])
            for ev in r["events"]:
                if ev[2] == "WRONG_IDENTITY": wrong_kind["wrong"] += 1
        enrolled_q = agg["d2_correct_present"] + agg["d3_correct_present"] + agg["d2_WRONG_IDENTITY"] + agg["d3_WRONG_IDENTITY"] \
            + sum(v for k, v in agg.items() if "enrolled_but" in k or k.startswith(("d2_enrolled_", "d3_enrolled_")))
        strangers = agg["d2_stranger_MARKED_PRESENT"] + agg["d3_stranger_MARKED_PRESENT"]
        rows.append(dict(accept=acc, margin=mg, d1_enrolled=agg["d1_enrolled"], d1_false_match=agg["d1_false_match_before_enroll"],
                         correct=agg["d2_correct_present"] + agg["d3_correct_present"],
                         wrong_id=agg["d2_WRONG_IDENTITY"] + agg["d3_WRONG_IDENTITY"],
                         not_recognised=sum(v for k, v in agg.items() if k.startswith(("d2_enrolled_", "d3_enrolled_"))),
                         stranger_fp=strangers))
        print(rows[-1], flush=True)
json.dump(rows, open(os.path.join(sc.OUTDIR, "policy_sweep.json"), "w"), indent=1)
