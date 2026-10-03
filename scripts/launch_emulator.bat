@echo off
title Launching Android Emulator - Attract App
echo ===================================================
echo   Starting Android Phone Emulator (Pixel 6 API 35)
echo ===================================================
echo.

start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd Pixel_6_API_35

echo Waiting for emulator to initialize...
timeout /t 12 /nobreak >nul

echo Waiting for Android device connection...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" wait-for-device

echo Launching Attract Attendance App...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell am start -n com.attract.attendance/.app.MainActivity

echo.
echo ===================================================
echo   SUCCESS! The Attract App is running on the phone.
echo ===================================================
echo.
pause
