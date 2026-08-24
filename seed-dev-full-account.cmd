@echo off
setlocal EnableExtensions

set "DOCKER="
if exist "%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin\docker.exe" set "DOCKER=%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin\docker.exe"
if not defined DOCKER for %%D in (docker.exe) do set "DOCKER=%%~$PATH:D"

if not defined DOCKER (
    echo [FastToWin] Khong tim thay Docker. Hay cai va mo Docker Desktop.
    exit /b 1
)

"%DOCKER%" compose up -d --wait database
if errorlevel 1 exit /b 1

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not defined FASTTOWIN_DEV_ACCOUNT_EMAIL set "FASTTOWIN_DEV_ACCOUNT_EMAIL=fulltest@fasttowin.dev"
if not defined FASTTOWIN_DEV_ACCOUNT_PASSWORD set "FASTTOWIN_DEV_ACCOUNT_PASSWORD=12345678"
if not defined FASTTOWIN_DEV_ACCOUNT_NAME set "FASTTOWIN_DEV_ACCOUNT_NAME=Full Test"
set "FASTTOWIN_ENV=dev"
set "DATABASE_URL=jdbc:postgresql://localhost:5432/fasttowin"
set "DATABASE_USER=fasttowin"
set "DATABASE_PASSWORD=fasttowin"

echo [FastToWin] Dang tao tai khoan dev day du du lieu...
call gradlew.bat :server:seedDevFullAccount
exit /b %errorlevel%
