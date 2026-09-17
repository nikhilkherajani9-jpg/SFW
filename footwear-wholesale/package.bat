@echo off
REM ============================================================
REM  package.bat — Creates a self-contained Windows app bundle
REM  using jpackage (with your current Eclipse Temurin JDK).
REM
REM  Output: target\SFW-Wholesale\  (copy this entire folder to USB HDD)
REM ============================================================

setlocal

REM ── Configure paths ────────────────────────────────────────────────
REM By default, we use the Java currently on your PATH.
REM If it fails, uncomment and set JAVA_HOME to your Eclipse Temurin path:
REM set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot

where jpackage >nul 2>&1
if %ERRORLEVEL% equ 0 (
    set JPACKAGE=jpackage
) else (
    if defined JAVA_HOME (
        set JPACKAGE="%JAVA_HOME%\bin\jpackage.exe"
    ) else (
        echo ERROR: jpackage not found on PATH. Please set JAVA_HOME.
        pause & exit /b 1
    )
)

REM ── Step 1: Build the project and collect JARs ─────────────────────
REM Fallback to the Maven downloaded earlier if mvn is not in PATH
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    if exist "C:\tools\apache-maven-3.9.16\bin\mvn.cmd" (
        set "PATH=C:\tools\apache-maven-3.9.16\bin;%PATH%"
    )
)

echo.
echo [1/3] Building project with Maven (skipping tests)...
call mvn -DskipTests clean package -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven build failed. Check the output above.
    pause & exit /b 1
)
echo     Done.

REM ── Step 2: Package with jpackage ─────────────────────────────────
echo.
echo [2/3] Packaging with jpackage (app-image)...
if exist target\SFW-Wholesale rmdir /s /q target\SFW-Wholesale

%JPACKAGE% ^
  --type app-image ^
  --input target\libs ^
  --main-jar footwear-wholesale-1.0.0.jar ^
  --main-class com.sfw.wholesale.Main ^
  --name "SFW-Wholesale" ^
  --app-version 1.0.0 ^
  --icon src\main\resources\icons\sfw-logo.ico ^
  --dest target ^
  --java-options "-Xmx256m" ^
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED"

if %ERRORLEVEL% neq 0 (
    echo ERROR: jpackage failed.
    pause & exit /b 1
)
echo     Done.

REM ── Step 3: Summary ───────────────────────────────────────────────
echo.
echo [3/3] Complete!
echo.
echo Self-contained app bundle created at:
echo   %CD%\target\SFW-Wholesale\
echo.
echo To deploy to USB HDD:
echo   1. Copy the entire "SFW-Wholesale" folder to your USB HDD.
echo   2. Launch by running "SFW-Wholesale\SFW-Wholesale.exe" from the USB.
echo   3. footwear.db will be created in the same folder on first launch.
echo.
pause
