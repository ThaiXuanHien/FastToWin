@echo off
setlocal

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

call "%~dp0connect-dev-device.cmd"

set "FASTTOWIN_ENV=dev"
set "FASTTOWIN_WEB_BASE_URL=http://localhost:8081"
echo [FastToWin] Dang dong goi server va protocol...
call gradlew.bat :server:installDist
if errorlevel 1 exit /b 1

call "%~dp0run-packaged-server.cmd"
