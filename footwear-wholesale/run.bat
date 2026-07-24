@echo off
REM ============================================================
REM  run.bat — Development launcher for SFW Footwear Wholesale
REM  Requires Maven + Java 21 on PATH.
REM  Usage: Double-click or run from project root folder.
REM ============================================================
setlocal
REM Fallback to the Maven downloaded earlier if mvn is not in PATH
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    if exist "C:\tools\apache-maven-3.9.16\bin\mvn.cmd" (
        set "PATH=C:\tools\apache-maven-3.9.16\bin;%PATH%"
    )
)

echo Starting SFW Footwear Wholesale (development mode)...
mvn javafx:run
pause
