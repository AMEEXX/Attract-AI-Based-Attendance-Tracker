$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Manifest = Join-Path $Root 'source_manifest.csv'
$DestRoot = Join-Path $Root 'real-faces'

if (-not (Test-Path $Manifest)) { throw "Missing source_manifest.csv" }
New-Item -ItemType Directory -Force -Path $DestRoot | Out-Null

$rows = Import-Csv $Manifest
foreach ($row in $rows) {
    $destDir = Join-Path $DestRoot $row.person
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    $dest = Join-Path $destDir $row.slot
    Write-Host "Downloading $($row.person)/$($row.slot) ..."
    Invoke-WebRequest -Uri $row.url -OutFile $dest
    if ((Get-Item $dest).Length -lt 1000) { throw "Downloaded file looks invalid: $dest" }
}
Write-Host "Done. Real-face test images are in: $DestRoot"
