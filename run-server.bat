@echo off
cd /d "%~dp0"
rem Chay server tai port 5000, du lieu luu trong thu muc data\r
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp bin server.ChatServer 5000 data
pause
