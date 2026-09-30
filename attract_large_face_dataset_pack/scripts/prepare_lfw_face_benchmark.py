from __future__ import annotations
import argparse, csv, hashlib, json, os, random, shutil, tarfile, urllib.request
from pathlib import Path

URL = "https://ndownloader.figshare.com/files/5976018"
PAIRS_URL = "http://vis-www.cs.umass.edu/lfw/pairs.txt"
EXPECTED_SHA256 = "055f7d9c632d7370e6fb4afc7468d40f970c34a80d4c6f50ffec63f5a8d536c0"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    print(f"Downloading {url}")
    urllib.request.urlretrieve(url, dest)


def collect_identities(image_root: Path):
    out = {}
    for person_dir in sorted(image_root.iterdir()):
        if not person_dir.is_dir():
            continue
        imgs = sorted(person_dir.glob('*.jpg'))
        if imgs:
            out[person_dir.name] = imgs
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--root', default=None, help='dataset working directory')
    ap.add_argument('--min-images', type=int, default=3)
    ap.add_argument('--max-identities', type=int, default=0, help='0 = all eligible identities')
    ap.add_argument('--triplets', type=int, default=10000)
    ap.add_argument('--impostor-pairs', type=int, default=20000)
    args = ap.parse_args()

    script_root = Path(__file__).resolve().parents[1]
    root = Path(args.root).resolve() if args.root else script_root / 'data'
    archive = root / 'raw' / 'lfw.tgz'
    extract_root = root / 'extracted'
    lfw_root = extract_root / 'lfw'
    manifest_root = root / 'manifests'
    manifest_root.mkdir(parents=True, exist_ok=True)

    if not archive.exists():
        download(URL, archive)
    digest = sha256(archive)
    if digest != EXPECTED_SHA256:
        raise RuntimeError(f"LFW archive checksum mismatch: {digest}")
    print('LFW checksum OK')

    marker = extract_root / '.extracted'
    if not marker.exists():
        extract_root.mkdir(parents=True, exist_ok=True)
        print('Extracting LFW...')
        with tarfile.open(archive, 'r:gz') as tf:
            tf.extractall(extract_root)
        marker.write_text('ok', encoding='utf-8')

    identities = collect_identities(lfw_root)
    eligible = {k: v for k, v in identities.items() if len(v) >= args.min_images}
    names = sorted(eligible)
    if args.max_identities:
        names = names[:args.max_identities]

    print(f'Identities total: {len(identities)}')
    print(f'Identities with >= {args.min_images} images: {len(names)}')
    print(f'Images across eligible identities: {sum(len(eligible[n]) for n in names)}')

    # Image catalog
    with (manifest_root / 'images.csv').open('w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['identity', 'image_path', 'image_index'])
        for name in names:
            for idx, img in enumerate(eligible[name], 1):
                w.writerow([name, str(img.relative_to(root)).replace('\\', '/'), idx])

    rng = random.Random(20260825)
    triplet_rows = []
    for _ in range(args.triplets):
        identity = rng.choice(names)
        choices = eligible[identity]
        if len(choices) >= 3:
            a, b, c = rng.sample(choices, 3)
        else:
            continue
        triplet_rows.append([identity, str(a.relative_to(root)).replace('\\', '/'), str(b.relative_to(root)).replace('\\', '/'), str(c.relative_to(root)).replace('\\', '/')])
    with (manifest_root / 'genuine_triplets.csv').open('w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['identity', 'frame1', 'frame2', 'frame3'])
        w.writerows(triplet_rows)

    imp_rows = []
    for _ in range(args.impostor_pairs):
        a, b = rng.sample(names, 2)
        ia = rng.choice(eligible[a]); ib = rng.choice(eligible[b])
        imp_rows.append([a, str(ia.relative_to(root)).replace('\\', '/'), b, str(ib.relative_to(root)).replace('\\', '/')])
    with (manifest_root / 'impostor_pairs.csv').open('w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['identity_a', 'image_a', 'identity_b', 'image_b'])
        w.writerows(imp_rows)

    # Download official LFW verification pairs file if available.
    pairs = root / 'raw' / 'pairs.txt'
    if not pairs.exists():
        try:
            download(PAIRS_URL, pairs)
        except Exception as exc:
            print(f'WARNING: could not download pairs.txt: {exc}')

    summary = {
        'dataset': 'LFW',
        'source_url': URL,
        'archive_sha256': digest,
        'eligible_identities': len(names),
        'eligible_images': sum(len(eligible[n]) for n in names),
        'genuine_triplets_generated': len(triplet_rows),
        'impostor_pairs_generated': len(imp_rows),
        'seed': 20260825,
    }
    (manifest_root / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2))
    print('\nREADY. Feed manifests into the Attract test generator; do not copy the full LFW tree into the APK.')

if __name__ == '__main__':
    main()
