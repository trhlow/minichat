@echo off
cd /d "%~dp0"
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d bin @sources.txt
del sources.txt
echo Build xong.
pause
