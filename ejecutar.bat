@echo off
rem Compila y arranca
cd /d "%~dp0"
set PUERTO=%1
if "%PUERTO%"=="" set PUERTO=8085
javac -encoding UTF-8 -d out -sourcepath src src\billeteras\Main.java || exit /b 1
rem
start "" /b cmd /c "timeout /t 2 >nul & start http://localhost:%PUERTO%"
java -cp out billeteras.Main %PUERTO%
