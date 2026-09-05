@echo off
setlocal EnableExtensions

set "DOCKER="
if exist "%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin\docker.exe" set "DOCKER=%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin\docker.exe"
if not defined DOCKER for %%D in (docker.exe) do set "DOCKER=%%~$PATH:D"

if not defined DOCKER (
    echo [FastToWin] Khong tim thay Docker.
    echo [FastToWin] Hay cai Docker Desktop, hoac dung start-dev-server.cmd de chay bang bo nho.
    exit /b 1
)

"%DOCKER%" compose up -d --wait database
if errorlevel 1 (
    echo [FastToWin] PostgreSQL khong khoi dong duoc.
    exit /b 1
)

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

call "%~dp0connect-dev-device.cmd"
if errorlevel 1 exit /b 1

set "FASTTOWIN_ENV=dev"
set "DATABASE_URL=jdbc:postgresql://localhost:5432/fasttowin"
set "DATABASE_USER=fasttowin"
set "DATABASE_PASSWORD=fasttowin"
set "FASTTOWIN_WEB_BASE_URL=http://localhost:8081"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\prepare-dev-server.ps1"
if errorlevel 1 exit /b 1

call "%~dp0run-packaged-server.cmd"
