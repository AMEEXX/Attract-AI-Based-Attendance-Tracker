$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
python "$PSScriptRoot\prepare_lfw_face_benchmark.py" @args
