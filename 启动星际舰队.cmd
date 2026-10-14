@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0extensions\StellarFleet\start.ps1" -GameDirectory "%~dp0."
if errorlevel 1 pause
