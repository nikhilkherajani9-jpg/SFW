@echo off
REM ============================================================
REM  package.bat — Creates a self-contained Windows app bundle
REM  using jlink + jpackage.
REM
REM  Requirements:
REM    - BellSoft Liberica JDK 21 Full (includes JavaFX jmods)
REM      Download: https://bell-sw.com/pages/downloads/#jdk-21-lts
REM      Install to: C:\Program Files\BellSoft\LibericaJDK-21-Full\
REM      OR set JAVA_HOME below to your installation path.
REM    - Maven 3.9+ on PATH
REM
REM  Output: target\SFW-Wholesale\  (copy this entire folder to USB HDD)
REM ============================================================

setlocal

REM ── Configure paths ────────────────────────────────────────────────
REM If you installed Liberica JDK to a different path, change this:
set JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-21-Full

set JPACKAGE=%JAVA_HOME%\bin\jpackage.exe
set JLINK=%JAVA_HOME%\bin\jlink.exe
set JAVAFX_MODS=%JAVA_HOME%\jmods

REM ── Step 1: Build the project and collect JARs ─────────────────────
REM Fallback to the Maven downloaded earlier if mvn is not in PATH
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    if exist "C:\tools\apache-maven-3.9.16\bin\mvn.cmd" (
        set "PATH=C:\tools\apache-maven-3.9.16\bin;%PATH%"
    )
)

echo.
echo [1/4] Building project with Maven (skipping tests)...
call mvn -DskipTests clean package -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven build failed. Check the output above.
    pause & exit /b 1
)
echo     Done.

REM ── Step 2: Create custom JRE with jlink ──────────────────────────
echo.
echo [2/4] Creating custom JRE with jlink...
if exist target\runtime rmdir /s /q target\runtime

"%JLINK%" ^
  --module-path "%JAVAFX_MODS%" ^
  --add-modules java.base,java.sql,java.logging,java.desktop,java.naming,java.net.http,jdk.httpserver,javafx.controls,javafx.graphics ^
  --output target\runtime ^
  --strip-debug ^
  --no-man-pages ^
  --no-header-files ^
  --compress=2

if %ERRORLEVEL% neq 0 (
    echo ERROR: jlink failed. Make sure JAVA_HOME points to Liberica JDK Full.
    pause & exit /b 1
)
echo     Done.

REM ── Step 3: Package with jpackage ─────────────────────────────────
echo.
echo [3/4] Packaging with jpackage (app-image)...
if exist target\SFW-Wholesale rmdir /s /q target\SFW-Wholesale

"%JPACKAGE%" ^
  --type app-image ^
  --input target\libs ^
  --main-jar footwear-wholesale-1.0.0.jar ^
  --main-class com.sfw.wholesale.App ^
  --runtime-image target\runtime ^
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

REM ── Step 4: Summary ───────────────────────────────────────────────
echo.
echo [4/4] Complete!
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
