@echo off
cd /d "%~dp0"
java -Dfile.encoding=UTF-8 -cp bin client.ConsoleClient localhost 5000
pause
