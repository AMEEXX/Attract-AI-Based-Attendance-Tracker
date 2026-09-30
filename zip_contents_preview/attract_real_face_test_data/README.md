# Attract real-face test dataset bootstrap

This package creates the exact directory structure expected by the Attract real-image tests:

real-faces/
  person_01/ straight_01.jpg left_01.jpg right_01.jpg extra_01.jpg extra_02.jpg
  person_02/ straight_01.jpg left_01.jpg right_01.jpg extra_01.jpg extra_02.jpg
  person_03/ straight_01.jpg left_01.jpg right_01.jpg extra_01.jpg extra_02.jpg

IMPORTANT:
- These are REAL PHOTOGRAPHS from a public LFW-derived research dataset mirror; they are not generated faces.
- The files are public benchmark images of public figures, not consenting student photos.
- The names straight_01/left_01/right_01 are compatibility slots for the Attract test harness. They are NOT guaranteed to be true pose labels. Do not use the filenames as ground truth for yaw.
- For final biometric accuracy validation, replace this dataset with consenting test subjects or a benchmark with explicit pose annotations.

Run the downloader from this package or from the repo root after copying the real-faces directory:

  powershell -ExecutionPolicy Bypass -File scripts/download_real_faces.ps1

The script downloads the source JPEGs from the public GitHub mirror and renames them into the exact Attract test layout.
