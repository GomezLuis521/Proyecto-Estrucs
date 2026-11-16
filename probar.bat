@echo off
rem Compila y corre los casos de prueba (sin dependencias externas).
cd /d "%~dp0"
javac -encoding UTF-8 -d out -sourcepath "src;test" test\billeteras\Pruebas.java || exit /b 1
java -Dstdout.encoding=UTF-8 -cp out billeteras.Pruebas
