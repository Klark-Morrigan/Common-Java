@echo off
rem The body every scripts\*.bat launcher shares: finds Git Bash through Common-Automation's _find-bash.bat, then
rem runs the .sh the launcher names in bash_script with the launcher's arguments.
rem
rem A launcher is double-clicked from Explorer, which closes the window the moment it exits. So every exit below
rem pauses first, or a failure would vanish unread. The .sh's own exit pause is switched off, since this one
rem already holds the window.
setlocal

if not defined bash_script (
    echo No script to run: set bash_script before calling "%~f0".
    pause
    exit /b 1
)

if defined COMMON_AUTOMATION_ROOT (
    set "automation_root=%COMMON_AUTOMATION_ROOT%"
) else (
    set "automation_root=%~dp0..\..\..\Common-Automation"
)
set "find_bash=%automation_root%\scripts\_find-bash.bat"

if not exist "%find_bash%" (
    echo Common-Automation not found: "%find_bash%" is missing.
    echo Clone it beside Common-Java, or set COMMON_AUTOMATION_ROOT to its folder.
    pause
    exit /b 1
)

rem _find-bash.bat prints its own error before it fails.
call "%find_bash%"
if errorlevel 1 (
    pause
    exit /b 1
)

set COMMON_AUTOMATION_NO_PAUSE=1
"%BASH%" "%bash_script%" %*
set rc=%errorlevel%
pause
exit /b %rc%
