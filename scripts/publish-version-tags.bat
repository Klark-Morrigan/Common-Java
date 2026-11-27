@echo off
rem Explorer double-click launcher for publish-version-tags.sh beside it.
rem Pass a version such as v1.2.3, or none to be prompted for one.
rem imports\_run-bash-script.bat holds the body every launcher shares.
setlocal
set "bash_script=%~dp0publish-version-tags.sh"
call "%~dp0imports\_run-bash-script.bat" %*
exit /b %errorlevel%
