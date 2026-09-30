$ErrorActionPreference = 'Stop'
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$s = "10BE9L1QYC0010Z"
function Dump { & $ADB -s $s exec-out uiautomator dump /dev/tty 2>$null | Out-File C:\tmp\phone_ui.xml -Encoding utf8 }
function Bounds($pattern) {
    Dump
    $m = (Select-String -Path C:\tmp\phone_ui.xml -Pattern $pattern | Select-Object -First 1)
    if (-not $m) { return $null }
    $g = $m.Matches[0].Groups
    return @{ x1=[int]$g[1].Value; y1=[int]$g[2].Value; x2=[int]$g[3].Value; y2=[int]$g[4].Value }
}
function TapB($b) { & $ADB -s $s shell input tap ((($b.x1 + $b.x2) / 2) -as [int]) ((($b.y1 + $b.y2) / 2) -as [int]) }
function TapTextR($t) { $b = Bounds ('text="' + [regex]::Escape($t) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'); if ($b) { TapB $b; return $true }; return $false }
function TapDescR($d) { $b = Bounds ('content-desc="' + [regex]::Escape($d) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'); if ($b) { TapB $b; return $true }; return $false }

# Trigger roster import: the workspace exposes it via viewModel.prepareRosterImport â€”
# find its UI trigger (icon/text). Try common labels:
$trigger = $null
foreach ($cand in @("Import CSV", "Import Roster CSV")) {
    if (TapDescR $cand) { $trigger = "desc:$cand"; break }
    if (TapTextR $cand) { $trigger = "text:$cand"; break }
}
if (-not $trigger) {
    # maybe an icon with different desc â€” dump all descs for manual pick
    Dump
    Write-Host "TRIGGER NOT FOUND. descs on screen:"
    Select-String -Path C:\tmp\phone_ui.xml -Pattern 'content-desc="[^"]{2,40}"' -AllMatches | ForEach-Object { $_.Matches } | ForEach-Object { $_.Value } | Sort-Object -Unique
    exit 1
}
Start-Sleep -Seconds 3   # system document picker opens

# In the picker: open Downloads â†’ select our file
if (-not (TapTextR "Downloads")) {
    # drawer/hamburger first?
    TapDescR "Show Roots" | Out-Null; Start-Sleep -Seconds 1
    TapTextR "Downloads" | Out-Null
}
Start-Sleep -Seconds 2
if (-not (TapTextR "at_accept_roster.csv")) {
    # scroll down in picker list
    & $ADB -s $s shell input swipe 600 1800 600 600; Start-Sleep -Seconds 1
    if (-not (TapTextR "at_accept_roster.csv")) { Write-Host "CSV not visible in picker"; exit 1 }
}
Start-Sleep -Seconds 3

# A preview/confirm dialog may appear (pendingRosterImport). Confirm import.
$confirmed = $false
foreach ($label in @("Import", "Confirm", "Yes", "OK")) {
    if (TapTextR $label) { $confirmed = $true; break }
}
Start-Sleep -Seconds 3
Dump
Write-Host "POST-IMPORT screen texts:"
Select-String -Path C:\tmp\phone_ui.xml -Pattern 'text="[^"]{1,40}"' -AllMatches | ForEach-Object { $_.Matches } | ForEach-Object { $_.Value } | Select-Object -First 20

