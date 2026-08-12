@echo off
setlocal

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

call "%~dp0connect-dev-device.cmd"

set "FASTTOWIN_ENV=dev"
call gradlew.bat :server:run
