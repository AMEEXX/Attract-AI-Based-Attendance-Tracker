"""
Bit-faithful Python port of the CURRENT Android biometric pipeline (origin/main e458c9f).

Every constant and formula below is copied from the Kotlin sources so that offline
measurements reflect what the phone actually does:

  YoloFaceDetector.kt      - 640x640 STRETCH resize, NCHW RGB/255, conf>=0.5,
                             ALL 5 landmark confs >=0.5, NMS 0.45, ratio "yaw"/"pitch"
  FaceAligner.kt           - 5-pt Umeyama similarity, MSE>450 -> 2-eye fallback
  EmbeddingEngine.kt       - 112x112 RGB (x-127.5)/128, L2 normalised 512-D
  FaceQualityConfig.kt     - calibrationDefaults()
  FaceQualityEngine.kt     - evaluate(signals, cfg, expectedPose)
  EnrollmentBatch.kt       - EnrollmentBatchValidator + DuplicateCheckService
  IdentityScorer.kt        - grouped max, accept 0.25, margin 0.05

Nothing here is "improved" - see proposed_pipeline.py for the candidate fixes.
"""
from __future__ import annotations

import math
import os
from dataclasses import dataclass, field
from typing import List, Optional, Sequence, Tuple

import numpy as np
from PIL import Image

try:
    from ai_edge_litert.interpreter import Interpreter
except ImportError:  # pragma: no cover
    from tflite_runtime.interpreter import Interpreter  # type: ignore

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(REPO, "app", "src", "main", "assets")

# ---------------------------------------------------------------- constants (Kotlin)
YOLO_INPUT = 640
YOLO_CONF = 0.5
YOLO_MIN_LM_CONF = 0.5
YOLO_IOU = 0.45

ACCEPT_THRESHOLD = 0.25          # BiometricModelProfile / RecognitionDecisionEngine
AMBIGUOUS_MARGIN = 0.05
DUPLICATE_THRESHOLD = 0.22       # DuplicateCheckService.DUPLICATE_DETECTION_THRESHOLD
CONTINUITY_THRESHOLD = 0.35      # EnrollmentBatchValidator.SAME_PERSON_CONTINUITY_THRESHOLD
MIN_YAW_SEPARATION = 15.0

# FaceQualityConfig.calibrationDefaults()
Q = dict(
    minFaceRatio=0.10, maxPoseDegrees=20.0, maxOffCenterFraction=0.35,
    minBlurVariance=120.0, targetBlurVariance=500.0, minBrightness=40.0,
    maxBrightness=220.0, idealBrightness=128.0, maxBrightnessDeviation=90.0,
    frontalThresholdDegrees=10.0, straightMaxYawDegrees=15.0,
    profileMinYawDegrees=20.0, profileMaxYawDegrees=45.0,
)

CANONICAL = np.array([
    [38.2946, 51.6963], [73.5318, 51.5014], [56.0252, 71.7366],
    [41.5493, 92.3655], [70.7299, 92.2041]], dtype=np.float32)


@dataclass
class Detection:
    box: Tuple[float, float, float, float]       # pixel x1,y1,x2,y2
    score: float
    landmarks: np.ndarray                        # (5,2) pixel
    lm_conf: np.ndarray                          # (5,)
    yaw: float
    pitch: float
    roll: float
    face_ratio: float
    cx: float
    cy: float


@dataclass
class RawCandidate:
    """Pre-landmark-filter candidate - used to show WHY faces vanish when turned."""
    score: float
    min_lm_conf: float
    lm_conf: np.ndarray


class Models:
    _inst: Optional["Models"] = None

    def __init__(self):
        self.yolo = Interpreter(model_path=os.path.join(ASSETS, "yolov8n_face.tflite"), num_threads=4)
        self.yolo.allocate_tensors()
        self.arc = Interpreter(model_path=os.path.join(ASSETS, "arcface_mobilefacenet.tflite"), num_threads=4)
        self.arc.allocate_tensors()
        self.yi = self.yolo.get_input_details()[0]["index"]
        self.yo = self.yolo.get_output_details()[0]["index"]
        self.ai = self.arc.get_input_details()[0]["index"]
        self.ao = self.arc.get_output_details()[0]["index"]

    @classmethod
    def get(cls) -> "Models":
        if cls._inst is None:
            cls._inst = Models()
        return cls._inst


# ---------------------------------------------------------------- YOLO
def yolo_raw(img: Image.Image, letterbox: bool = False):
    """Returns ([20,8400] output, mapping fn norm->pixel)."""
    m = Models.get()
    w, h = img.size
    if letterbox:
        s = YOLO_INPUT / max(w, h)
        nw, nh = int(round(w * s)), int(round(h * s))
        canvas = Image.new("RGB", (YOLO_INPUT, YOLO_INPUT), (114, 114, 114))
        px, py = (YOLO_INPUT - nw) // 2, (YOLO_INPUT - nh) // 2
        canvas.paste(img.resize((nw, nh), Image.BILINEAR), (px, py))
        src = canvas

        def to_px(nx, ny):
            return (nx * YOLO_INPUT - px) / s, (ny * YOLO_INPUT - py) / s
    else:
        src = img.resize((YOLO_INPUT, YOLO_INPUT), Image.BILINEAR)   # Kotlin: createScaledBitmap (stretch)

        def to_px(nx, ny):
            return nx * w, ny * h
    arr = np.asarray(src, dtype=np.float32) / 255.0
    inp = np.transpose(arr, (2, 0, 1))[None]
    m.yolo.set_tensor(m.yi, inp)
    m.yolo.invoke()
    return m.yolo.get_tensor(m.yo)[0], to_px


def _nms(boxes, scores, thr):
    order = np.argsort(-scores)
    keep = []
    while order.size:
        i = order[0]
        keep.append(i)
        xx1 = np.maximum(boxes[i, 0], boxes[order[1:], 0]); yy1 = np.maximum(boxes[i, 1], boxes[order[1:], 1])
        xx2 = np.minimum(boxes[i, 2], boxes[order[1:], 2]); yy2 = np.minimum(boxes[i, 3], boxes[order[1:], 3])
        inter = np.maximum(0, xx2 - xx1) * np.maximum(0, yy2 - yy1)
        a = (boxes[i, 2] - boxes[i, 0]) * (boxes[i, 3] - boxes[i, 1])
        b = (boxes[order[1:], 2] - boxes[order[1:], 0]) * (boxes[order[1:], 3] - boxes[order[1:], 1])
        iou = inter / np.maximum(a + b - inter, 1e-6)
        order = order[1:][iou <= thr]
    return keep


def detect(img: Image.Image, letterbox: bool = False, min_lm_conf: float = YOLO_MIN_LM_CONF,
           return_raw: bool = False):
    out, to_px = yolo_raw(img, letterbox)
    w, h = img.size
    scores = out[4]
    idx = np.where(scores >= YOLO_CONF)[0]
    raw: List[RawCandidate] = []
    cands = []
    for a in idx:
        lmc = np.array([out[7 + l * 3][a] for l in range(5)])
        raw.append(RawCandidate(float(scores[a]), float(lmc.min()), lmc))
        if lmc.min() < min_lm_conf:
            continue                                   # Kotlin: whole face DISCARDED
        cx, cy, bw, bh = out[0][a], out[1][a], out[2][a], out[3][a]
        box = np.clip([cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2], 0, 1)
        lms = np.array([[out[5 + l * 3][a], out[6 + l * 3][a]] for l in range(5)])
        cands.append((box, float(scores[a]), lms, lmc))
    dets: List[Detection] = []
    if cands:
        boxes = np.array([c[0] for c in cands]); sc = np.array([c[1] for c in cands])
        for k in _nms(boxes, sc, YOLO_IOU):
            box, s, lms, lmc = cands[k]
            x1, y1 = to_px(box[0], box[1]); x2, y2 = to_px(box[2], box[3])
            x1, y1, x2, y2 = max(0, x1), max(0, y1), min(w, x2), min(h, y2)
            pl = np.array([to_px(x, y) for x, y in lms], dtype=np.float32)
            dets.append(Detection(
                box=(x1, y1, x2, y2), score=s, landmarks=pl, lm_conf=lmc,
                yaw=yaw_ratio(pl), pitch=pitch_ratio(pl), roll=roll_deg(pl),
                face_ratio=(x2 - x1) * (y2 - y1) / float(w * h),
                cx=(x1 + x2) / 2 / w, cy=(y1 + y2) / 2 / h))
        dets.sort(key=lambda d: d.score * float(d.lm_conf.mean()), reverse=True)
    return (dets, raw) if return_raw else dets


# ---------------------------------------------------------------- pose heuristics (Kotlin)
def yaw_ratio(lm: np.ndarray) -> float:
    dl = np.linalg.norm(lm[2] - lm[0]); dr = np.linalg.norm(lm[2] - lm[1])
    t = dl + dr
    return 0.0 if t < 1e-6 else float((0.5 - dl / t) * 60.0)


def pitch_ratio(lm: np.ndarray) -> float:
    eye_y = (lm[0, 1] + lm[1, 1]) / 2; mouth_y = (lm[3, 1] + lm[4, 1]) / 2
    tv = mouth_y - eye_y
    return 0.0 if tv < 1e-6 else float(((lm[2, 1] - eye_y) / tv - 0.45) * 50.0)


def roll_deg(lm: np.ndarray) -> float:
    d = lm[1] - lm[0]
    return float(math.degrees(math.atan2(d[1], d[0])))


# ---------------------------------------------------------------- alignment (Kotlin)
def similarity_matrix(lm: np.ndarray):
    if not np.all(np.isfinite(lm)):
        return None
    if np.linalg.norm(lm[1] - lm[0]) < 8:
        return None
    sm = lm.mean(0); dm = CANONICAL.mean(0)
    s = lm - sm; d = CANONICAL - dm
    sumD = float((s ** 2).sum()); sumA = float((s * d).sum())
    sumB = float((s[:, 0] * d[:, 1] - s[:, 1] * d[:, 0]).sum())
    if sumD < 1e-4:
        return None
    a, b = sumA / sumD, sumB / sumD
    sc = math.hypot(a, b)
    if sc < 0.01 or sc > 50:
        return None
    tx = dm[0] - (a * sm[0] - b * sm[1]); ty = dm[1] - (b * sm[0] + a * sm[1])
    M = np.array([[a, -b, tx], [b, a, ty]], dtype=np.float64)
    proj = (M[:, :2] @ lm.T).T + M[:, 2]
    if float(((proj - CANONICAL) ** 2).sum(1).mean()) > 450:
        return None
    return M


def eye_fallback_matrix(lm: np.ndarray):
    sl, sr = lm[0], lm[1]; dl, dr = CANONICAL[0], CANONICAL[1]
    sd = max(np.linalg.norm(sr - sl), 1e-4); dd = np.linalg.norm(dr - dl)
    ang = math.atan2(*(dr - dl)[::-1]) - math.atan2(*(sr - sl)[::-1])
    sc = dd / sd
    c, s_ = math.cos(ang) * sc, math.sin(ang) * sc
    sc_ = (sl + sr) / 2; dc = (dl + dr) / 2
    M = np.array([[c, -s_, 0], [s_, c, 0]], dtype=np.float64)
    M[:, 2] = dc - M[:, :2] @ sc_
    return M


def align(img: Image.Image, lm: np.ndarray, size: int = 112) -> Image.Image:
    M = similarity_matrix(lm)
    if M is None:
        M = eye_fallback_matrix(lm)
    A = np.vstack([M, [0, 0, 1]])
    inv = np.linalg.inv(A)
    return img.transform((size, size), Image.AFFINE, data=tuple(inv[:2].flatten()), resample=Image.BILINEAR)


# ---------------------------------------------------------------- embedding (Kotlin)
def embed(face112: Image.Image) -> np.ndarray:
    m = Models.get()
    arr = (np.asarray(face112.convert("RGB").resize((112, 112), Image.BILINEAR), dtype=np.float32) - 127.5) / 128.0
    m.arc.set_tensor(m.ai, arr[None])
    m.arc.invoke()
    v = m.arc.get_tensor(m.ao)[0].astype(np.float64)
    return v / max(np.linalg.norm(v), 1e-5)


# ---------------------------------------------------------------- quality signals
def luminance(img: Image.Image) -> np.ndarray:
    a = np.asarray(img.convert("RGB"), dtype=np.float32)
    return 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]


def brightness_and_blur(img: Image.Image, box) -> Tuple[float, float]:
    """Port of CameraPreview.computeBitmapBrightness / computeBitmapLaplacianVariance."""
    L = luminance(img); H, W = L.shape
    l = int(np.clip(box[0], 0, W - 1)); r = int(np.clip(box[2], l + 1, W))
    t = int(np.clip(box[1], 0, H - 1)); b = int(np.clip(box[3], t + 1, H))
    step = max((b - t) // 40, 1)
    bright = float(L[t:b:step, l:r:step].mean())
    l2 = int(np.clip(box[0] + 1, 1, W - 2)); r2 = int(np.clip(box[2] - 1, l2 + 1, W - 1))
    t2 = int(np.clip(box[1] + 1, 1, H - 2)); b2 = int(np.clip(box[3] - 1, t2 + 1, H - 1))
    step = max((b2 - t2) // 30, 1)
    ys = np.arange(t2, b2, step); xs = np.arange(l2, r2, step)
    Y, X = np.meshgrid(ys, xs, indexing="ij")
    lap = L[Y - 1, X] + L[Y + 1, X] + L[Y, X - 1] + L[Y, X + 1] - 4 * L[Y, X]
    return bright, float(lap.var())


@dataclass
class Signals:
    face_count: int
    yaw: float = 0.0
    pitch: float = 0.0
    face_ratio: float = 0.0
    cx: float = 0.5
    cy: float = 0.5
    blur: float = 0.0
    brightness: float = 0.0


def signals_for(img: Image.Image, dets: Sequence[Detection]) -> Signals:
    if len(dets) != 1:
        return Signals(face_count=len(dets))
    d = dets[0]
    br, bl = brightness_and_blur(img, d.box)
    return Signals(1, d.yaw, d.pitch, d.face_ratio, d.cx, d.cy, bl, br)


def quality(sig: Signals, expected: str = "STRAIGHT", q=Q) -> Tuple[bool, str]:
    """FaceQualityEngine.evaluate (order of checks preserved)."""
    if sig.face_count == 0:
        return False, "NO_FACE"
    if sig.face_count > 1:
        return False, "MULTIPLE_FACES"
    if sig.face_ratio < q["minFaceRatio"]:
        return False, "TOO_SMALL"
    lim = q["maxOffCenterFraction"]
    if not (lim <= sig.cx <= 1 - lim and lim <= sig.cy <= 1 - lim):
        return False, "OFF_CENTER"
    if abs(sig.pitch) > q["maxPoseDegrees"]:
        return False, "POSE"
    y = sig.yaw
    ok = {"STRAIGHT": abs(y) <= q["straightMaxYawDegrees"],
          "LEFT": -q["profileMaxYawDegrees"] <= y <= -q["profileMinYawDegrees"],
          "RIGHT": q["profileMinYawDegrees"] <= y <= q["profileMaxYawDegrees"]}[expected]
    if not ok:
        return False, f"POSE_NOT_{expected}"
    if sig.blur < q["minBlurVariance"]:
        return False, "BLUR"
    if sig.brightness < q["minBrightness"]:
        return False, "DARK"
    if sig.brightness > q["maxBrightness"]:
        return False, "OVEREXPOSED"
    return True, "OK"


# ---------------------------------------------------------------- recognition / enrollment (Kotlin)
def grouped_max(query: np.ndarray, gallery: dict) -> List[Tuple[int, float]]:
    """gallery: {student_id: [emb,...]} -> ranked [(sid, best)]"""
    res = [(sid, float(max(np.dot(query, t) for t in ts))) for sid, ts in gallery.items() if ts]
    return sorted(res, key=lambda x: (-x[1], x[0]))


def decide(query, gallery, thr=ACCEPT_THRESHOLD, margin=AMBIGUOUS_MARGIN):
    r = grouped_max(query, gallery)
    if not r:
        return ("EMPTY", None, None)
    top = r[0]
    if top[1] < thr:
        return ("UNKNOWN", top[0], top[1])
    if len(r) > 1 and top[1] - r[1][1] < margin:
        return ("AMBIGUOUS", top[0], top[1])
    return ("MATCH", top[0], top[1])


def duplicate_check(samples: Sequence[np.ndarray], gallery: dict, exclude=None, thr=DUPLICATE_THRESHOLD):
    for sid in gallery:                       # Kotlin iterates groupBy order; first hit wins
        if sid == exclude:
            continue
        for c in samples:
            for t in gallery[sid]:
                s = float(np.dot(c, t))
                if s >= thr:
                    return ("SUSPICIOUS", sid, s)
    return ("CLEAR", None, None)


def validate_batch(straight, left, right) -> Tuple[bool, str]:
    """EnrollmentBatchValidator.validate; each arg = (emb, yaw)."""
    (es, ys), (el, yl), (er, yr) = straight, left, right
    if abs(ys) > 15:
        return False, "STRAIGHT_YAW"
    if yl > -15:
        return False, "LEFT_YAW"
    if yr < 15:
        return False, "RIGHT_YAW"
    if min(abs(ys - yl), abs(ys - yr), abs(yl - yr)) < MIN_YAW_SEPARATION:
        return False, "YAW_SEPARATION"
    sl, sr = float(np.dot(es, el)), float(np.dot(es, er))
    if sl < CONTINUITY_THRESHOLD or sr < CONTINUITY_THRESHOLD:
        return False, f"CONTINUITY(sl={sl:.2f},sr={sr:.2f})"
    return True, "OK"


def process(img: Image.Image, letterbox=False):
    """Camera-frame -> (detections, signals, aligned112 or None)."""
    dets = detect(img, letterbox=letterbox)
    sig = signals_for(img, dets)
    crop = align(img, dets[0].landmarks) if len(dets) == 1 else None
    return dets, sig, crop
