@echo off
setlocal EnableExtensions

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-dev-all.ps1" %*
exit /b %ERRORLEVEL%
