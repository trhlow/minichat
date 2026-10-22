@echo off
cd /d "%~dp0"
call "%~dp0find-java.bat" || (pause & exit /b 1)
if not exist bin\server\ChatServer.class (echo Chua build, hay chay build.bat truoc & pause & exit /b 1)
rem Chay server tai port 5000, du lieu luu trong thu muc data\
"%JAVA_BIN%\java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp bin server.ChatServer 5000 data
pause
