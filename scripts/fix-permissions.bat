@echo off
rem Explorer double-click launcher for fix-permissions.sh beside it.
rem imports\_run-bash-script.bat holds the body every launcher shares.
setlocal
set "bash_script=%~dp0fix-permissions.sh"
call "%~dp0imports\_run-bash-script.bat" %*
exit /b %errorlevel%
