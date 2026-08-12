@echo off
setlocal EnableExtensions

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "DEVICE_FOUND="

if not exist "%ADB%" (
    echo [FastToWin] Khong tim thay adb: %ADB%
    exit /b 1
)

for /f "skip=1 tokens=1,2" %%D in ('"%ADB%" devices 2^>nul') do (
    if "%%E"=="device" (
        "%ADB%" -s %%D reverse tcp:8080 tcp:8080 >nul
        if not errorlevel 1 (
            echo [FastToWin] Da ket noi %%D: tcp:8080 -^> tcp:8080
            set "DEVICE_FOUND=1"
        )
    )
)

if not defined DEVICE_FOUND (
    echo [FastToWin] Khong co emulator hoac thiet bi online.
    echo [FastToWin] Hay mo emulator, sau do chay lai connect-dev-device.cmd.
    exit /b 1
)
