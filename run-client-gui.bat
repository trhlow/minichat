@echo off
cd /d "%~dp0"
call "%~dp0find-java.bat" || (pause & exit /b 1)
if not exist bin\client\ChatClientGUI.class (echo Chua build, hay chay build.bat truoc & pause & exit /b 1)
start "" "%JAVA_BIN%\javaw" -Dfile.encoding=UTF-8 -cp bin client.ChatClientGUI
