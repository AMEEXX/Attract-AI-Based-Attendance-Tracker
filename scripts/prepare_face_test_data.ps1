# prepare_face_test_data.ps1
# PowerShell Test Data Preparation Script for Attract Face Verification System

$RootDir = Resolve-Path "$PSScriptRoot\.."
$TestDataDir = "$RootDir\test-data"
$SyntheticDir = "$TestDataDir\synthetic-faces"
$RealDir = "$TestDataDir\real-faces"
$AndroidAssetsDir = "$RootDir\app\src\androidTest\assets\test-data"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Attract Face System — Test Data Preparation (PowerShell)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Run python preparation script if python is available
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
    Write-Host "[1/1] Running python script..." -ForegroundColor Green
    python "$PSScriptRoot\prepare_face_test_data.py"
} else {
    Write-Host "[1/2] Creating directory structure..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $SyntheticDir | Out-Null
    New-Item -ItemType Directory -Force -Path $RealDir | Out-Null
    New-Item -ItemType Directory -Force -Path $AndroidAssetsDir | Out-Null

    Write-Host "[2/2] Setup complete. Please run python scripts/prepare_face_test_data.py for dataset generation." -ForegroundColor Yellow
}
