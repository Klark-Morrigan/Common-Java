@echo off
setlocal
rem Explorer-double-click launcher for scripts/run-ci-yaml-and-bash.sh. Resolves Git
rem Bash via Common-Automation's _find-bash.bat, then runs the shim with
rem the engine pause suppressed (this .bat self-pauses below).
rem Common-Automation is expected as a sibling checkout under the same
rem parent directory,
rem or wherever COMMON_AUTOMATION_ROOT names.

if defined COMMON_AUTOMATION_ROOT (set "automation_root=%COMMON_AUTOMATION_ROOT%") else (set "automation_root=%~dp0..\..\Common-Automation")
call "%automation_root%\scripts\_find-bash.bat" || exit /b 1

set COMMON_AUTOMATION_NO_PAUSE=1
"%BASH%" "%~dp0run-ci-yaml-and-bash.sh" %*
set rc=%errorlevel%
pause
exit /b %rc%
