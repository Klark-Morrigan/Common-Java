@echo off
rem Explorer double-click launcher for run-ci-yaml-and-bash.sh beside it.
rem imports\_run-bash-script.bat holds the body every launcher shares.
setlocal
set "bash_script=%~dp0run-ci-yaml-and-bash.sh"
call "%~dp0imports\_run-bash-script.bat" %*
exit /b %errorlevel%
