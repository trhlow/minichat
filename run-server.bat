@echo off
cd /d "%~dp0"
java -cp bin server.ChatServer 5000
pause
