@echo off
rem Tim JDK de bien dich/chay MiniChat, ket qua dat vao bien JAVA_BIN.
rem Thu tu: JAVA_HOME -> PATH -> cac thu muc cai dat thuong gap (ca JDK di kem IntelliJ / Android Studio).
set "JAVA_BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVA_BIN=%JAVA_HOME%\bin"
if not defined JAVA_BIN for %%i in (javac.exe) do if not "%%~$PATH:i"=="" set "JAVA_BIN=%%~dp$PATH:i"
if not defined JAVA_BIN for /d %%d in ("C:\Program Files\Java\jdk*" "C:\Program Files\Eclipse Adoptium\jdk*" "C:\Program Files\Microsoft\jdk*" "C:\Program Files\Zulu\zulu*" "C:\Program Files\Amazon Corretto\jdk*" "%USERPROFILE%\.jdks\*") do if exist "%%~d\bin\javac.exe" set "JAVA_BIN=%%~d\bin"
if not defined JAVA_BIN for /d %%d in ("C:\Program Files\JetBrains\*" "D:\IntelliJ IDEA*" "D:\JetBrains\*" "C:\Program Files\Android\Android Studio") do if exist "%%~d\jbr\bin\javac.exe" set "JAVA_BIN=%%~d\jbr\bin"
if defined JAVA_BIN exit /b 0
echo.
echo  KHONG TIM THAY JDK (javac) TREN MAY.
echo  Cai JDK 17 tro len (vd: https://adoptium.net), roi dat bien moi truong JAVA_HOME
echo  tro toi thu muc JDK, hoac them thu muc bin cua JDK vao PATH.
echo.
exit /b 1
