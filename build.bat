@echo off
cd /d "%~dp0"
call "%~dp0find-java.bat" || (pause & exit /b 1)
echo Dung JDK: %JAVA_BIN%
if exist bin rmdir /s /q bin
mkdir bin
rem Dung duong dan tuong doi: thu muc du an co dau tieng Viet, cmd khong ghi dung vao file danh sach
"%JAVA_BIN%\javac" -encoding UTF-8 -d bin src\common\*.java src\server\*.java src\client\*.java
if errorlevel 1 (echo Build LOI & pause & exit /b 1)
echo Build xong.
pause
