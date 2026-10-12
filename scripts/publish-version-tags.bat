@echo off
setlocal
rem Explorer-double-click launcher for scripts/publish-version-tags.sh.
rem Resolves Git Bash via Common-Automation's _find-bash.bat, then runs
rem the shim with the engine pause suppressed (this .bat self-pauses
rem below). Forwards %* so a version like v1.2.3 can be passed; with no
rem argument the engine prompts. Common-Automation is expected as a
rem sibling checkout under the same parent directory,
rem or wherever COMMON_AUTOMATION_ROOT names.

if defined COMMON_AUTOMATION_ROOT (set "automation_root=%COMMON_AUTOMATION_ROOT%") else (set "automation_root=%~dp0..\..\Common-Automation")
call "%automation_root%\scripts\_find-bash.bat" || exit /b 1

set COMMON_AUTOMATION_NO_PAUSE=1
"%BASH%" "%~dp0publish-version-tags.sh" %*
set rc=%errorlevel%
pause
exit /b %rc%
