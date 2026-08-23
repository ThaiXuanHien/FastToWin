@echo off
setlocal EnableExtensions

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"

set "SERVER_LIB=%~dp0server\build\install\server\lib\*"
"%JAVA_HOME%\bin\java.exe" -cp "%SERVER_LIB%" com.hienthai.fastowin.server.MainKt
