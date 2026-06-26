@echo off
REM Double-click this file (or run "run.cmd") to launch ticket-triage.
REM It just calls run.ps1, bypassing PowerShell's script-execution policy.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
pause
