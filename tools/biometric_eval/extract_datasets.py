"""Stream the HuggingFace parquet mirrors to plain JPEG folders (low memory).

Pointing'04 (StevenLe456/head-pose): 15 subjects x 2 series x 93 poses x 5 colour
jitters = 13,950. Rows are ordered by series (465 rows each); subject = block // 2.
Labels y = [tilt, pan] in degrees. We keep only jitter #0 of each pose (2,790 images).

LFW (bitmind/lfw): 13,233 images, identity parsed from filename.
"""
import io, os, sys, csv
import pyarrow.parquet as pq

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".datasets"))


def pointing04():
    out = os.path.join(ROOT, "pointing04"); os.makedirs(out, exist_ok=True)
    meta = open(os.path.join(out, "labels.csv"), "w", newline=""); w = csv.writer(meta)
    w.writerow(["file", "subject", "series", "tilt", "pan"])
    gidx = 0; prev = None; seen = set()
    for f in ["hp0.parquet", "hp1.parquet", "hp2.parquet"]:
        pf = pq.ParquetFile(os.path.join(ROOT, f))
        for batch in pf.iter_batches(batch_size=200):
            xs = batch.column("x").to_pylist(); ys = batch.column("y").to_pylist()
            for x, y in zip(xs, ys):
                block = gidx // 465; subj = block // 2 + 1; series = block % 2 + 1
                key = (block, y[0], y[1])
                if key not in seen:              # first jitter of this pose only
                    seen.add(key)
                    name = f"p{subj:02d}_s{series}_t{y[0]:+03d}_p{y[1]:+03d}.jpg"
                    open(os.path.join(out, name), "wb").write(x["bytes"])
                    w.writerow([name, subj, series, y[0], y[1]])
                gidx += 1
    meta.close(); print("pointing04 rows", gidx, "kept", len(seen))


def lfw():
    out = os.path.join(ROOT, "lfw"); os.makedirs(out, exist_ok=True)
    pf = pq.ParquetFile(os.path.join(ROOT, "lfw.parquet")); n = 0
    for batch in pf.iter_batches(batch_size=500):
        imgs = batch.column("image").to_pylist(); names = batch.column("filename").to_pylist()
        for im, fn in zip(imgs, names):
            base = os.path.basename(fn)
            ident = base.rsplit("_", 1)[0]
            d = os.path.join(out, ident); os.makedirs(d, exist_ok=True)
            open(os.path.join(d, base), "wb").write(im["bytes"]); n += 1
    print("lfw images", n, "identities", len(os.listdir(out)))


if __name__ == "__main__":
    which = sys.argv[1:] or ["pointing04", "lfw"]
    if "pointing04" in which: pointing04()
    if "lfw" in which: lfw()
