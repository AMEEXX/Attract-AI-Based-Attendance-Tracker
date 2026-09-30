"""Builds a compact LFW benchmark subset for on-device Attract three-frame validation.

Reads the full LFW manifests produced by prepare_lfw_face_benchmark.py, selects a
deterministic representative subset, writes bench manifests, and (with --push)
copies the subset images to a device path readable by instrumentation tests.

The full dataset stays OUT of the APK: images go to /data/local/tmp/lfw_bench.
"""
from __future__ import annotations
import argparse, csv, os, random, subprocess, sys
from pathlib import Path

SEED = 20260825


def read_csv(path: Path):
    with path.open(newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


ADB = str(Path(os.environ.get('LOCALAPPDATA', '')) / 'Android' / 'Sdk' / 'platform-tools' / 'adb.exe')


def adb(*argv, check=True):
    return subprocess.run([ADB, *argv], check=check, capture_output=True, text=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--root', default=r'C:\tmp\lfw-benchmark')
    ap.add_argument('--max-identities', type=int, default=100)
    ap.add_argument('--min-images-per-id', type=int, default=8)
    ap.add_argument('--max-images-per-id', type=int, default=10)
    ap.add_argument('--genuine-triplets', type=int, default=2000)
    ap.add_argument('--impostor-pairs', type=int, default=3000)
    ap.add_argument('--assets', action='store_true', help='package subset into androidTest assets')
    args = ap.parse_args()

    root = Path(args.root)
    manifests = root / 'manifests'
    out = manifests / 'bench'
    out.mkdir(parents=True, exist_ok=True)

    images = read_csv(manifests / 'images.csv')
    by_id = {}
    for row in images:
        by_id.setdefault(row['identity'], []).append(row['image_path'])

    eligible = sorted(
        (n for n, p in by_id.items() if len(p) >= args.min_images_per_id),
        key=lambda n: (-len(by_id[n]), n),
    )
    chosen = eligible[:args.max_identities]
    subset = {}
    total_imgs = 0
    for name in chosen:
        paths = sorted(by_id[name])[:args.max_images_per_id]
        subset[name] = paths
        total_imgs += len(paths)

    print(f'Eligible identities (>= {args.min_images_per_id} imgs): {len(eligible)}')
    print(f'Selected identities: {len(chosen)}  total images: {total_imgs}')

    # bench image manifest
    with (out / 'bench_images.csv').open('w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['identity', 'image_path'])
        for name in chosen:
            for p in subset[name]:
                w.writerow([name, p])

    subset_names = set(subset)

    rng = random.Random(SEED)

    # genuine triplets restricted to subset
    triplets = [r for r in read_csv(manifests / 'genuine_triplets.csv')
                if r['identity'] in subset_names]
    rng.shuffle(triplets)
    triplets = triplets[:args.genuine_triplets]
    with (out / 'bench_genuine_triplets.csv').open('w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['case_id', 'identity', 'frame1', 'frame2', 'frame3', 'expected'])
        for i, r in enumerate(triplets):
            w.writerow([f'G{i:06d}', r['identity'], r['frame1'], r['frame2'], r['frame3'], 'SAME_PERSON'])

    # impostor pairs restricted to subset
    pairs = [r for r in read_csv(manifests / 'impostor_pairs.csv')
             if r['identity_a'] in subset_names and r['identity_b'] in subset_names]
    rng.shuffle(pairs)
    pairs = pairs[:args.impostor_pairs]

    # Synthesize additional deterministic pairs when pack pairs don't cover the subset.
    all_imgs = [(n, p) for n in chosen for p in subset[n]]
    seen = {(r['image_a'], r['image_b']) for r in pairs}
    guard = 0
    while len(pairs) < args.impostor_pairs and guard < args.impostor_pairs * 50:
        guard += 1
        (na, pa), (nb, pb) = rng.sample(all_imgs, 2)
        if na == nb or (pa, pb) in seen:
            continue
        seen.add((pa, pb))
        pairs.append({'identity_a': na, 'image_a': pa,
                      'identity_b': nb, 'image_b': pb})
    with (out / 'bench_impostor_pairs.csv').open('w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['case_id', 'identity_a', 'image_a', 'identity_b', 'image_b', 'expected'])
        for i, r in enumerate(pairs):
            w.writerow([f'I{i:06d}', r['identity_a'], r['image_a'], r['identity_b'], r['image_b'], 'DIFFERENT_PERSON'])

    summary = {
        'seed': SEED,
        'selected_identities': len(chosen),
        'total_images': total_imgs,
        'genuine_triplets': len(triplets),
        'impostor_pairs': len(pairs),
    }
    import json
    (out / 'bench_summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2))

    if args.assets:
        dataset_root = root
        asset_root = Path(__file__).resolve().parents[1] / 'app' / 'src' / 'androidTest' / 'assets' / 'test-data' / 'lfw-bench'
        if asset_root.exists():
            import shutil as _shutil
            _shutil.rmtree(asset_root)
        copied = 0
        for name in chosen:
            dst_dir = asset_root / name
            dst_dir.mkdir(parents=True, exist_ok=True)
            for p in subset[name]:
                src = dataset_root / p.replace('/', '\\')
                (dst_dir / Path(p).name).write_bytes(src.read_bytes())
                copied += 1
        print(f'Copied {copied} images into androidTest assets: {asset_root}')
        for fname in ['bench_images.csv', 'bench_genuine_triplets.csv', 'bench_impostor_pairs.csv']:
            (asset_root / fname).write_text((out / fname).read_text(encoding='utf-8'), encoding='utf-8')
        print('Manifests written into androidTest assets.')


if __name__ == '__main__':
    main()

