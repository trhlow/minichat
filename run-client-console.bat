@echo off
cd /d "%~dp0"
call "%~dp0find-java.bat" || (pause & exit /b 1)
if not exist bin\client\ConsoleClient.class (echo Chua build, hay chay build.bat truoc & pause & exit /b 1)
chcp 65001 > nul
"%JAVA_BIN%\java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -cp bin client.ConsoleClient localhost 5000
pause
