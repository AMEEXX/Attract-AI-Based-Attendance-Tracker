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

foreach ($stu in @(@("AMIT","R-A1"), @("Student B","R-A2"), @("Student C","R-A3"))) {
    Dump
    # close leftover sheet
    if (Select-String -Path C:\tmp\phone_ui.xml -Pattern 'content-desc="Close sheet"' -Quiet) {
        TapDescR "Close sheet" | Out-Null; Start-Sleep -Seconds 2; Dump
    }
    if (Select-String -Path C:\tmp\phone_ui.xml -Pattern ('text="' + [regex]::Escape($stu[0]) + '"') -Quiet) {
        Write-Host "skip existing $($stu[0])"; continue
    }
    if (-not (TapDescR "Add Student")) { Write-Host "Add Student FAB not found"; continue }
    Start-Sleep -Seconds 2

    $f1 = Bounds 'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    if (-not $f1) { Write-Host "sheet did not open"; continue }

    TapB $f1; Start-Sleep -Milliseconds 500          # focuses name field, keyboard opens
    & $ADB -s $s shell input text $stu[0]
    Start-Sleep -Milliseconds 400
    & $ADB -s $s shell input keyevent KEYCODE_BACK   # close KEYBOARD only
    Start-Sleep -Milliseconds 600

    $f2 = $null
    foreach ($tryN in 1..4) {
        Dump
        $all = @(); Select-String -Path C:\tmp\phone_ui.xml -Pattern 'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' | ForEach-Object { foreach ($mm in $_.Matches) { $all += @{x1=[int]$mm.Groups[1].Value;y1=[int]$mm.Groups[2].Value;x2=[int]$mm.Groups[3].Value;y2=[int]$mm.Groups[4].Value} } }
        $f2 = $all | Where-Object { $_.y1 -gt ($f1.y1 + 50) } | Sort-Object y1 | Select-Object -First 1
        if ($f2) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $f2) { Write-Host "roll field still missing for $($stu[0])"; continue }

    TapB $f2; Start-Sleep -Milliseconds 500
    & $ADB -s $s shell input text $stu[1]
    Start-Sleep -Milliseconds 400
    & $ADB -s $s shell input keyevent KEYCODE_BACK   # close keyboard again
    Start-Sleep -Milliseconds 500

    if (-not (TapTextR "Save Student")) { Write-Host "Save button missing"; continue }
    Start-Sleep -Seconds 2
}

Dump
$found = Select-String -Path C:\tmp\phone_ui.xml -Pattern 'text="(AMIT|Student B|Student C)"' -AllMatches
Write-Host ("ROSTER: " + (($found.Matches | ForEach-Object Value | Sort-Object -Unique) -join ", "))
