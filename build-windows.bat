@echo off
setlocal
cd /d "%~dp0.."
if not exist out mkdir out
javac -encoding UTF-8 -d out src\EdmsPrototype.java src\EdmsGui.java test\EdmsTests.java
if errorlevel 1 (
  echo Build failed. Make sure JDK 17 or newer is installed and on PATH.
  exit /b 1
)
echo EDMS Group 27 GUI build successful.
endlocal
