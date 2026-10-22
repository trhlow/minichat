@echo off
rem Bien dich va chay kiem thu tich hop server (socket that, khong can thu vien ngoai)
cd /d "%~dp0"
call "%~dp0find-java.bat" || (pause & exit /b 1)
if exist out-test rmdir /s /q out-test
mkdir out-test
"%JAVA_BIN%\javac" -encoding UTF-8 -d out-test src\common\*.java src\server\*.java src\client\*.java test\*.java test\client\*.java
if errorlevel 1 (echo Build LOI & pause & exit /b 1)
xcopy /e /i /q /y src\client\fonts out-test\client\fonts > nul
"%JAVA_BIN%\java" -Dstdout.encoding=UTF-8 -cp out-test MiniChatServerTest
rem Ve giao dien ra anh PNG trong thu muc snapshots\ (khong mo cua so)
"%JAVA_BIN%\java" -cp out-test client.GuiSnapshot snapshots
pause
