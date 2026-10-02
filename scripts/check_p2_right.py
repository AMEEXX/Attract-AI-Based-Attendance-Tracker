import sys, os
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from test_models_inference import detect_faces, YOLO_PATH
from ai_edge_litert.interpreter import Interpreter

yolo = Interpreter(YOLO_PATH)
yolo.allocate_tensors()
img = Image.open('test-data/real-faces/person_02/right_01.jpg').convert('RGB')
print('Image size:', img.size)
dets = detect_faces(yolo, img, conf_threshold=0.2)
print('Total detections:', len(dets))
for i, d in enumerate(dets):
    score = d['score']
    box = d['box']
    lm = d['landmarks']
    print(f"Det {i}: score={score:.4f}, box={box}")
    print(f"  landmarks: {lm}")
