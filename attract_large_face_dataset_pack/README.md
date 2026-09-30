# Attract Large Face Validation Dataset Pack

This pack is designed to prepare a much larger real-photo face benchmark for Attract without putting a huge dataset inside the APK.

## Source
Labeled Faces in the Wild (LFW): 13,233 images from 5,749 identities. The original archive is distributed as `lfw.tgz` and is available through the public Figshare mirror used by scikit-learn.

Source archive URL:
https://ndownloader.figshare.com/files/5976018

LFW verification pairs:
http://vis-www.cs.umass.edu/lfw/pairs.txt

## What this pack does
1. Downloads the LFW archive once.
2. Extracts it outside `app/src/androidTest/assets`.
3. Finds identities with at least 3 photographs.
4. Generates labeled 3-image sequences and genuine/impostor pairs.
5. Produces a compact manifest that your Android tests can consume without packaging all 13k images into the APK.

## Windows
Run:

powershell -ExecutionPolicy Bypass -File scripts\prepare_lfw_face_benchmark.ps1

Or:

python scripts\prepare_lfw_face_benchmark.py

## Important
This dataset is for research/testing. Do not treat it as consenting Attract student data, and do not put the whole dataset into a production APK or production biometric database.
