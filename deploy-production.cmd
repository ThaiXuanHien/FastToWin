@echo off
setlocal EnableExtensions

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\deploy-production.ps1" %*
exit /b %ERRORLEVEL%
