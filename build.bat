@echo off
cd /d "%~dp0"
if exist bin rmdir /s /q bin
mkdir bin
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d bin @sources.txt
set ERR=%ERRORLEVEL%
del sources.txt
if not "%ERR%"=="0" (echo Build LOI & pause & exit /b 1)
echo Build xong.
pause
