"""Confusion analysis over the on-device LFW benchmark result CSVs."""
import csv
from collections import defaultdict
from pathlib import Path

RES = Path(r'C:\tmp\lfw-results\lfw_bench_results')


def rows(name):
    with (RES / name).open(newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


g = rows('genuine_comparisons.csv')
imp = rows('impostor_comparisons.csv')
img = rows('image_metrics.csv')
seq = rows('sequence_results.csv')

print('=' * 70)
print('CONFUSION ANALYSIS — LFW benchmark, production thresholds UNCHANGED')
print('=' * 70)

# --- per-identity FRR ---
per_id = defaultdict(lambda: [0, 0])
for r in g:
    per_id[r['trueIdentity']][0] += 1
    if r['outcome'] != 'TA':
        per_id[r['trueIdentity']][1] += 1
frr = [(n, fr, t) for n, (t, fr) in per_id.items()]
frr.sort(key=lambda x: (-x[1], -x[2]))
print('\nWORST IDENTITIES BY FALSE REJECTS (rejects/queries):')
for n, fr, t in frr[:10]:
    print(f'  {n}: {fr}/{t}')

# --- outcome breakdown by pose bin ---
by_bin = defaultdict(lambda: defaultdict(int))
for r in g:
    by_bin[r['poseBin']][r['outcome']] += 1
print('\nGENUINE OUTCOME BY MEASURED POSE BIN:')
for b, d in sorted(by_bin.items()):
    total = sum(d.values())
    ta = d.get('TA', 0)
    print(f'  {b}: TA {ta}/{total} ({100 * ta / total:.1f}%)  {dict(d)}')

# --- quality reject reasons ---
qreasons = defaultdict(int)
for r in img:
    if r['qualityAccepted'].lower() == 'false':
        qreasons[r['qualityReason']] += 1
print(f'\nQUALITY REJECT REASONS (of {len(img)} images): {dict(qreasons)}')

# --- liveness ---
lv = sum(1 for r in img if r['livenessPassed'].lower() == 'false')
print(f'LIVENESS REJECTS: {lv}')

# --- impostor FA pairs ---
fa_pairs = defaultdict(int)
fa_ids = defaultdict(int)
for r in imp:
    if r['outcome'] == 'FALSE_ACCEPT':
        fa_pairs[(r['idA'], r['idB'])] += 1
        fa_ids[r['idB']] += 1
print(f'\nFALSE ACCEPTS: {sum(fa_pairs.values())} / {len(imp)} pairs')
print('WORST IMPOSTOR PAIRS (target identity most often accepted):')
for (a, b), c in sorted(fa_pairs.items(), key=lambda x: -x[1])[:10]:
    sim = max((float(r['sim']) for r in imp if r['idA'] == a and r['idB'] == b), default=0)
    print(f'  {a} -> {b}: {c} FAs (max sim {sim:.3f})')

# --- similarity distributions ---
sims_g = sorted(float(r['simToTrue']) for r in g)
sims_i = sorted(float(r['sim']) for r in imp)


def pct(a, p):
    return a[min(len(a) - 1, int(p * len(a)))]


print(f'\nGENUINE SIM DISTRIBUTION: min={sims_g[0]:.3f} p1={pct(sims_g, .01):.3f} p5={pct(sims_g, .05):.3f} '
      f'p50={pct(sims_g, .5):.3f} max={sims_g[-1]:.3f}')
print(f'IMPOSTOR SIM DISTRIBUTION: min={sims_i[0]:.3f} p50={pct(sims_i, .5):.3f} p95={pct(sims_i, .95):.3f} '
      f'p99={pct(sims_i, .99):.3f} max={sims_i[-1]:.3f}')
for thr in (0.35, 0.40, 0.45, 0.50):
    far = sum(1 for s in sims_i if s >= thr) / len(sims_i)
    frr_t = sum(1 for s in sims_g if s < thr) / len(sims_g)
    print(f'  threshold {thr}: FAR={far * 100:.2f}%  FRR(sim-only)={frr_t * 100:.2f}%')

# --- smallest margins ---
gm = sorted(g, key=lambda r: float(r['margin']) if r['margin'] not in ('', 'NaN') else 9)
print('\nSMALLEST MARGINS:')
for r in gm[:8]:
    print(f"  {r['trueIdentity']}/{r['file']}: margin={float(r['margin']):.4f} top1={r['top1Id']}({float(r['top1Sim']):.3f}) "
          f"top2={r['top2Id']}({float(r['top2Sim']):.3f}) outcome={r['outcome']}")

# --- ambiguous analysis: was top1 correct? ---
amb = [r for r in g if r['outcome'] == 'AMBIGUOUS']
amb_correct = sum(1 for r in amb if r['top1Id'] == r['trueIdentity'])
print(f'\nAMBIGUOUS CASES: {len(amb)}, top1 was CORRECT identity in {amb_correct} ({100 * amb_correct / max(1, len(amb)):.0f}%)')

# --- unknown analysis by bin ---
unk = defaultdict(int)
for r in g:
    if r['outcome'] == 'UNKNOWN':
        unk[r['poseBin']] += 1
print(f'UNKNOWN cases by pose bin: {dict(unk)}')

# --- sequences ---
v = [r for r in seq if r['expected_class'] == 'VALID']
i_ = [r for r in seq if r['expected_class'] == 'INVALID']
print(f'\nSEQUENCES: valid={len(v)} accepted={sum(1 for r in v if "CORRECT_ACCEPT" in r["verdict"])} '
      f'missed={sum(1 for r in v if r["verdict"] == "MISSED_ACCEPT")}')
print('VALID SEQUENCE FAILURES:')
for r in v[:10]:
    print(f"  {r['case_id']} {r['identity']} -> {r['outcome']} ({r['verdict']})")
inv_ok = sum(1 for r in i_ if r['verdict'] == 'CORRECT_REJECT')
missed_rej = [r for r in i_ if r['verdict'] == 'MISSED_REJECT']
print(f'INVALID: rejected correctly {inv_ok}/{len(i_)}, MISSED REJECTS: {len(missed_rej)}')
for r in missed_rej[:8]:
    print(f"  !! {r['case_id']} {r['identity']} {r['frame_labels']} -> ACCEPTED (should reject)")
