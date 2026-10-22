@echo off
cd /d "%~dp0"
chcp 65001 > nul
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -cp bin client.ConsoleClient localhost 5000
pause
