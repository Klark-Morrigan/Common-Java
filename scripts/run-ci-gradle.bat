@echo off
rem Explorer double-click launcher for run-ci-gradle.sh beside it.
rem With no CONSUMER_REPO_PATH set, it builds Common-Java's ci-smoke project.
rem imports\_run-bash-script.bat holds the body every launcher shares.
setlocal
set "bash_script=%~dp0run-ci-gradle.sh"
call "%~dp0imports\_run-bash-script.bat" %*
exit /b %errorlevel%
