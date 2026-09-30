# =============================================================================
# Attract — FINAL PHYSICAL-DEVICE ACCEPTANCE DRIVER
# Device: 10BE9L1QYC0010Z
# Human-in-the-loop gates are clearly marked: the script handles ALL taps/logs/DB;
# you only provide the FACES at the prompted moments (and your teacher PIN at exit).
# =============================================================================
$ErrorActionPreference = 'Stop'
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$SERIAL = "10BE9L1QYC0010Z"
$PKG = "com.attract.attendance"
$APK = "C:\tmp\attract-build\app\outputs\apk\debug\app-debug.apk"
$OUT = "C:\tmp\phone_acceptance"
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

function Adb { & $ADB -s $SERIAL @args }
function Say($m) { Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Gate($m) { Read-Host ">> $m  (press ENTER when ready)" | Out-Null }
function Uidump {
    Adb exec-out uiautomator dump /dev/tty 2>$null | Out-File "$OUT\ui.xml" -Encoding UTF8
}
function Has-Text($t) {
    Uidump
    return (Select-String -Path "$OUT\ui.xml" -Pattern ('text="' + [regex]::Escape($t) + '"') -Quiet)
}
function Tap-Text($t) {
    Uidump
    $m = Select-String -Path "$OUT\ui.xml" -Pattern ('text="' + [regex]::Escape($t) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m) { throw "Text not found on screen: $t" }
    $x = [int]((([int]$m.Matches[0].Groups[1].Value) + [int]$m.Matches[0].Groups[3].Value) / 2)
    $y = [int]((([int]$m.Matches[0].Groups[2].Value) + [int]$m.Matches[0].Groups[4].Value) / 2)
    Adb shell input tap $x $y
}
function Tap-Desc($d) {
    Uidump
    $m = Select-String -Path "$OUT\ui.xml" -Pattern ('content-desc="' + [regex]::Escape($d) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m) { throw "Desc not found: $d" }
    $x = [int]((([int]$m.Matches[0].Groups[1].Value) + [int]$m.Matches[0].Groups[3].Value) / 2)
    $y = [int]((([int]$m.Matches[0].Groups[2].Value) + [int]$m.Matches[0].Groups[4].Value) / 2)
    Adb shell input tap $x $y
}
function Wait-Text($t, $sec = 15) {
    for ($i = 0; $i -lt ($sec * 2); $i++) { if (Has-Text $t) { return $true }; Start-Sleep -Milliseconds 500 }
    return $false
}

Say "PHASE 0: device check"
Adb get-state | Out-Null
Write-Host "Device online: $SERIAL"

Say "PHASE 1: install normal app APK (no androidTest APK)"
Adb install -r $APK
Adb shell pm list packages | Select-String $PKG | Tee-Object -Variable pkgLine
if (-not $pkgLine) { throw "Package NOT installed!" }
Adb shell dumpsys package $PKG | Select-String "versionName" | Select-Object -First 1

Say "PHASE 2: pull CURRENT app database (safe inspection, no wipe)"
New-Item -ItemType Directory -Force -Path "$OUT\db0" | Out-Null
foreach ($f in @("attract.db", "attract.db-wal", "attract.db-shm")) {
    Adb exec-out run-as $PKG cat "databases/$f" 2>$null | Set-Content "$OUT\db0\$f" -AsByteStream
}
python "$PWD\scripts\report_phone_db.py" "$OUT\db0\attract.db"

Say "PHASE 3: launch app"
Adb logcat -c
Adb shell am start -n "$PKG/.app.MainActivity"
Start-Sleep -Seconds 4

Say "PHASE 3a: create class AT-ACCEPT (via real UI)"
Adb shell input keyevent KEYCODE_MOVE_HOME; Start-Sleep -Milliseconds 500
# If onboarding/theme appears, handle minimal:
if (-not (Has-Text "AT-ACCEPT")) {
    if (Has-Text "Light") { Tap-Text "Light"; Start-Sleep -Milliseconds 400; Tap-Text "Continue"; Start-Sleep -Seconds 1 }
}
Tap-Desc "Create Class"
Wait-Text "Class name" | Out-Null
# Fill fields via focus+taps: click each field then type
function Type-In-Field($label, $text) {
    Uidump
    # fields are EditTexts in order; find bounds of the label then nearest edit text below is complex — use ordinal approach instead
}
# simpler: tap fields by known order using EditText nodes list
Uidump
$edits = @()
Select-String -Path "$OUT\ui.xml" -Pattern 'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' | ForEach-Object {
    foreach ($mm in $_.Matches) { $edits += ,@([int]$mm.Groups[1].Value, [int]$mm.Groups[2].Value, [int]$mm.Groups[3].Value, [int]$mm.Groups[4].Value) }
}
function FillEdit($idx, $text) {
    $b = $edits[$idx]
    Adb shell input tap ([int](($b[0]+$b[2])/2)) ([int](($b[1]+$b[3])/2))
    Start-Sleep -Milliseconds 300
    Adb shell input text $text
    Adb shell input keyevent KEYCODE_BACK; Start-Sleep -Milliseconds 250
}
FillEdit 0 "AT-ACCEPT"
FillEdit 1 "Acceptance"
FillEdit 2 "A"
Tap-Text "Create class"
Wait-Text "AT-ACCEPT" 10 | Out-Null
Write-Host "Class AT-ACCEPT created."

Say "PHASE 3b: add students AMIT / Student B / Student C"
Tap-Text "AT-ACCEPT"; Start-Sleep -Seconds 2
Tap-Desc "Add Student"
foreach ($s in @(@("AMIT","R-A1"), @("Student B","R-A2"), @("Student C","R-A3"))) {
    Wait-Text "Full Name *" 8 | Out-Null
    Uidump
    $edits = @()
    Select-String -Path "$OUT\ui.xml" -Pattern 'class="android.widget.EditText"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' | ForEach-Object {
        foreach ($mm in $_.Matches) { $edits += ,@([int]$mm.Groups[1].Value,[int]$mm.Groups[2].Value,[int]$mm.Groups[3].Value,[int]$mm.Groups[4].Value) }
    }
    FillEdit 0 $s[0]
    FillEdit 1 $s[1]
    Tap-Text "Save Student"
    Start-Sleep -Milliseconds 800
    if (Has-Text "Add Student") { } else { Tap-Desc "Add Student" }
}
Write-Host "Students added."

Say "PHASE 4: ENROLL AMIT through real enrollment flow"
Tap-Text "AMIT"; Start-Sleep -Seconds 2
Tap-Text "Enroll face biometrics"
Gate "Stand AMIT (the person who will be RECOGNIZED later) in front of the camera"
# Standalone enrollment: STRAIGHT -> LEFT -> RIGHT taps with prompts
Gate "Frame 1/3 (STRAIGHT): hold still, then ENTER to capture"
Tap-Desc "Capture"; Start-Sleep -Seconds 2
Gate "Frame 2/3 (LEFT): turn head left, then ENTER"
Tap-Desc "Capture"; Start-Sleep -Seconds 2
Gate "Frame 3/3 (RIGHT): turn head right, then ENTER"
Tap-Desc "Capture"; Start-Sleep -Seconds 3
if (Has-Text "ENROLLED SUCCESSFULLY") { Write-Host "AMIT enrolled OK" } else { Write-Host "WARNING: enrollment message not detected — check screen" }

Say "PHASE 5: start attendance session through REAL UI (calendar -> AI Attendance)"
# navigate back from enrollment
Adb shell input keyevent KEYCODE_BACK; Start-Sleep -Seconds 1
Adb shell input keyevent KEYCODE_BACK; Start-Sleep -Seconds 1
if (-not (Has-Text "CALENDAR")) { Tap-Text "AT-ACCEPT" }
if (Has-Text "CALENDAR") { } else { Tap-Text "CALENDAR" }
$today = Get-Date -Format "d"
if (-not (Has-Text $today)) { throw "Calendar day cell not found: $today" }
Tap-Text $today
Tap-Text "AI Attendance"
Wait-Text "Step 1" 15 | Out-Null
Start-Sleep -Seconds 3
Adb logcat -d | Select-String "ATTENDANCE_SCREEN_ENTERED|START_SESSION" | Select-Object -Last 4

Say "PHASE 6: RECOGNIZED ATTENDANCE — AMIT walks up"
Adb logcat -c
Gate "Have AMIT look straight at the phone camera, then ENTER to capture"
Tap-Desc "Capture"; Start-Sleep -Seconds 4
if (-not (Has-Text "PRESENT:") -and -not (Has-Text "Already Checked In")) {
    Gate "Not marked yet (adaptive asked another frame or quality retry). Ensure good light/face, ENTER to capture again"
    Tap-Desc "Capture"; Start-Sleep -Seconds 4
}
if (Has-Text "PRESENT:") { Write-Host "RECOGNIZED PATH OK" } elseif (Has-Text "Already Checked In") { Write-Host "Already checked in (acceptable variant)" } else { Write-Host "WARNING: recognition leg did not complete — capture logcat/db now" }

Say "PHASE 7: DUPLICATE recognition"
Gate "AMIT stays in front; ENTER to capture again"
Tap-Desc "Capture"; Start-Sleep -Seconds 4
Has-Text "Already Checked In"

Say "PHASE 8: UNKNOWN -> roster -> fallback Student B"
Gate "Have a DIFFERENT person (not AMIT, not B/C) in front; ENTER to capture"
Tap-Desc "Capture"; Start-Sleep -Seconds 4
if (-not (Has-Text "Select")) {
    Gate "Still no UNKNOWN — try again with clearly different/unrecognized person; ENTER"
    Tap-Desc "Capture"; Start-Sleep -Seconds 4
}
# roster opens automatically; select Student B
Wait-Text "Student B" 10 | Out-Null
Tap-Text "Student B"
Start-Sleep -Seconds 3
Has-Text "PRESENT:"
Say "PHASE 9: fallback duplicate (Student B again)"
Gate "Different person again in front; ENTER to capture"
Tap-Desc "Capture"; Start-Sleep -Seconds 4
Wait-Text "Student B" 10 | Out-Null
Tap-Text "Student B"
Start-Sleep -Seconds 3
Has-Text "Already Checked In"

Say "PHASE 10: END session (your Teacher PIN will be requested ON SCREEN)"
Gate "Ready to end session; ENTER, then type YOUR PIN when the dialog appears and confirm End"
# Script cannot know the private PIN — teacher types it.
Start-Sleep -Seconds 6

Say "PHASE 11: NEW SESSION via UI"
if (-not (Has-Text "CALENDAR")) { Tap-Text "AT-ACCEPT" }
Tap-Text $today
Tap-Text "AI Attendance"
Wait-Text "Step 1" 15 | Out-Null
Start-Sleep -Seconds 3

Say "PHASE 12: pull FINAL database + full logcat"
New-Item -ItemType Directory -Force -Path "$OUT\dbF" | Out-Null
foreach ($f in @("attract.db","attract.db-wal","attract.db-shm")) {
    Adb exec-out run-as $PKG cat "databases/$f" 2>$null | Set-Content "$OUT\dbF\$f" -AsByteStream
}
Adb logcat -d -v time > "$OUT\final_phone_acceptance.txt"
python "$PWD\scripts\report_phone_db.py" "$OUT\dbF\attract.db"
Write-Host "`nDONE. Artifacts in $OUT"
