@echo off
setlocal EnableExtensions

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

set "SERVER_HOME=%FASTTOWIN_SERVER_HOME%"
if not defined SERVER_HOME if exist "%~dp0.artifacts\dev-server\server\lib" set "SERVER_HOME=%~dp0.artifacts\dev-server\server"
if not defined SERVER_HOME set "SERVER_HOME=%~dp0server\build\install\server"

if not exist "%SERVER_HOME%\lib" (
    echo [FastToWin] Khong tim thay server da dong goi tai %SERVER_HOME%.
    echo [FastToWin] Hay chay scripts\prepare-dev-server.ps1 truoc.
    exit /b 1
)

set "SERVER_LIB=%SERVER_HOME%\lib\*"
"%JAVA_HOME%\bin\java.exe" -cp "%SERVER_LIB%" com.hienthai.fastowin.server.MainKt
