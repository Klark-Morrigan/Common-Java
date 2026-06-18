@echo off
setlocal
rem Explorer-double-click launcher for scripts/run-ci-gradle.sh. With no
rem COMMON_JAVA_TARGET_REPO set it builds Common-Java's ci-smoke project (the
rem local mirror of ci.yml's conventions gate). Resolves Git Bash via
rem Common-Automation's _find-bash.bat, then runs the script with the engine
rem pause suppressed (this .bat self-pauses below). Common-Automation is
rem expected as a sibling checkout under the same parent directory.

call "%~dp0..\..\Common-Automation\scripts\_find-bash.bat" || exit /b 1

set COMMON_AUTOMATION_NO_PAUSE=1
"%BASH%" "%~dp0run-ci-gradle.sh" %*
set rc=%errorlevel%
pause
exit /b %rc%
