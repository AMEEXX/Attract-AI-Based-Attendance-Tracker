// Attract Web Showcase & Biometric Pipeline Simulator
document.addEventListener('DOMContentLoaded', () => {
  const stepsData = [
    {
      title: "Stage 1: YOLOv8n-Face Detection",
      badge: "Input: 640×640 CameraX Stream",
      content: `// YOLOv8n-Face model performs non-maximum suppression (IoU 0.45, Conf 0.50):<br>
Bounding Box: Rect(x=120, y=95, w=280, h=340)<br>
Landmarks (5-pt): Left Eye, Right Eye, Nose Tip, Left Mouth, Right Mouth<br>
<span class="sim-highlight">Confidence: 0.948 | Landmark Min Confidence: 0.887 (PASS)</span>`
    },
    {
      title: "Stage 2: 2D Umeyama Similarity Alignment",
      badge: "Target: 112×112 Canonical Face Crop",
      content: `// Umeyama analytical least-squares similarity transformation matrix:<br>
Scale factor: 1.042 | Rotation (Roll): -2.18°<br>
Face de-rotated around eye midpoint to prevent roll tilt leaking into yaw.<br>
<span class="sim-highlight">Output: Normalised 112×112 canonical facial alignment crop</span>`
    },
    {
      title: "Stage 3: Calibrated Nose-Offset Pose Evaluation",
      badge: "Formula: K_YAW = -60.8f * (nose.x - eyeMid.x) / interEye",
      content: `// Measured against student's own straight anchor frame (relative delta):<br>
Straight Anchor Yaw: +2.4° | Current Measured Yaw: -16.8°<br>
Relative Delta: -19.2° (Target LEFT window: [-40.0°, -10.0°])<br>
<span class="sim-highlight">Quality Status: ACCEPTED (Natural Left Profile Pose Verified)</span>`
    },
    {
      title: "Stage 4: ArcFace 512-D Embedding Extraction (BGR)",
      badge: "Model: arcface_mobilefacenet.tflite (BGR)",
      content: `// Tensor channel order: BGR [b, g, r] normalised to [-1, 1]:<br>
Embedding dimensions: 512 float32 values<br>
L2-Norm Check: sqrt(sum(x²)) = 1.000000<br>
<span class="sim-highlight">Output: 512-D L2-normalised biometric vector</span>`
    },
    {
      title: "Stage 5: Identity Decision & Room v5 Persistence",
      badge: "Profile: arcface512_bgr_noseyaw_v5",
      content: `// Cosine matching against class gallery (N=60 students):<br>
Top Match: Student #1042 ("Aarav Sharma") | Cosine Score: 0.742<br>
Runner-up Score: 0.381 | Margin: 0.361 (>= 0.08 margin PASS)<br>
Score 0.742 >= 0.60 (Fast-path Immediate Verification PASS)<br>
<span class="sim-highlight">Result: Atomic Room Transaction committed → Status: PRESENT</span>`
    }
  ];

  let currentStep = 1;
  const totalSteps = stepsData.length;

  const stepperItems = document.querySelectorAll('.step-item');
  const titleEl = document.getElementById('sim-step-title');
  const badgeEl = document.getElementById('sim-status-badge');
  const codeEl = document.getElementById('sim-code-content');
  const btnPrev = document.getElementById('btn-prev-step');
  const btnNext = document.getElementById('btn-next-step');

  function updateStepper(step) {
    currentStep = step;
    stepperItems.forEach(item => {
      const itemStep = parseInt(item.getAttribute('data-step'), 10);
      item.classList.remove('active', 'done');
      if (itemStep === currentStep) {
        item.classList.add('active');
      } else if (itemStep < currentStep) {
        item.classList.add('done');
      }
    });

    const data = stepsData[currentStep - 1];
    titleEl.textContent = data.title;
    badgeEl.textContent = data.badge;
    codeEl.innerHTML = data.content;

    btnPrev.disabled = currentStep === 1;
    btnNext.textContent = currentStep === totalSteps ? '↺ Restart Pipeline' : 'Next Stage →';
  }

  stepperItems.forEach(item => {
    item.addEventListener('click', () => {
      const step = parseInt(item.getAttribute('data-step'), 10);
      updateStepper(step);
    });
  });

  btnPrev.addEventListener('click', () => {
    if (currentStep > 1) updateStepper(currentStep - 1);
  });

  btnNext.addEventListener('click', () => {
    if (currentStep < totalSteps) {
      updateStepper(currentStep + 1);
    } else {
      updateStepper(1);
    }
  });

  // Smooth scroll for internal links
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function(e) {
      const targetId = this.getAttribute('href');
      if (targetId === '#') return;
      const targetEl = document.querySelector(targetId);
      if (targetEl) {
        e.preventDefault();
        targetEl.scrollIntoView({ behavior: 'smooth' });
      }
    });
  });

  // Fallback check for APK download
  const downloadBtn = document.getElementById('download-apk-action');
  if (downloadBtn) {
    downloadBtn.addEventListener('click', () => {
      console.log('Initiating APK download...');
    });
  }
});
