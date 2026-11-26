@echo off
setlocal
pwsh -NoProfile -File "%~dp0scripts\run-mobile-casms-qa.ps1" %*
exit /b %errorlevel%
