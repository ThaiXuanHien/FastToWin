@echo off
setlocal EnableExtensions

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\production-ops.ps1" %*
exit /b %ERRORLEVEL%
