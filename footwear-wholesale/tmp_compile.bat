@echo off
cd /d c:\SFW\footwear-wholesale
setlocal enabledelayedexpansion
set /p CP=<cp.txt
dir /b /s src\main\java\*.java > sources.txt
"C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\javac.exe" -Xlint:all -cp "!CP!" -d target\tmpclasses @sources.txt
