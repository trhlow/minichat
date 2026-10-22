@echo off
rem Bien dich va chay kiem thu tich hop server (socket that, khong can thu vien ngoai)
cd /d "%~dp0"
if exist out-test rmdir /s /q out-test
mkdir out-test
dir /s /b src\*.java test\*.java > sources.txt
javac -encoding UTF-8 -d out-test @sources.txt
set ERR=%ERRORLEVEL%
del sources.txt
if not "%ERR%"=="0" (echo Build LOI & pause & exit /b 1)
java -Dstdout.encoding=UTF-8 -cp out-test MiniChatServerTest
rem Ve giao dien ra anh PNG trong thu muc snapshots\ (khong mo cua so)
java -cp out-test client.GuiSnapshot snapshots
pause
