@echo off
setlocal
rem Explorer-double-click launcher for scripts/run-ci-gradle.sh. With no
rem COMMON_JAVA_TARGET_REPO set it builds Common-Java's ci-smoke project (the
rem local mirror of ci-gradle.yml's gate). Resolves Git Bash via
rem Common-Automation's _find-bash.bat, then runs the script with the engine
rem pause suppressed (this .bat self-pauses below). Common-Automation is
rem expected as a sibling checkout under the same parent directory,
rem or wherever COMMON_AUTOMATION_ROOT names.

if defined COMMON_AUTOMATION_ROOT (set "automation_root=%COMMON_AUTOMATION_ROOT%") else (set "automation_root=%~dp0..\..\Common-Automation")
call "%automation_root%\scripts\_find-bash.bat" || exit /b 1

set COMMON_AUTOMATION_NO_PAUSE=1
"%BASH%" "%~dp0run-ci-gradle.sh" %*
set rc=%errorlevel%
pause
exit /b %rc%
