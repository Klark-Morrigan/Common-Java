@echo off
rem Explorer double-click launcher for run-tests-bash.sh beside it.
rem imports\_run-bash-script.bat holds the body every launcher shares.
setlocal
set "bash_script=%~dp0run-tests-bash.sh"
call "%~dp0imports\_run-bash-script.bat" %*
exit /b %errorlevel%
