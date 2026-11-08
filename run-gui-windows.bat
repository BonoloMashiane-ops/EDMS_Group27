@echo off
setlocal
cd /d "%~dp0.."
java -cp out EdmsGui --data demo-data
endlocal
