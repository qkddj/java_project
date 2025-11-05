@echo off
setlocal
set PROJECT_ROOT=%~dp0..\
set TARGET=%PROJECT_ROOT%target\
set OUT=%PROJECT_ROOT%dist\win\
if not exist "%OUT%" mkdir "%OUT%"

set APP_NAME=RandomVideoApp
set MAIN_JAR=%TARGET%webcam-test-0.0.1-SNAPSHOT-all.jar
if not exist "%MAIN_JAR%" (
  echo Fat JAR not found: %MAIN_JAR%
  echo 먼저 Windows에서 다음을 실행해 주세요: mvn clean package
  exit /b 1
)

for /f "tokens=*" %%i in ('where java') do set JAVA_EXE=%%i
if "%JAVA_EXE%"=="" (
  echo JAVA_HOME 또는 PATH 에 java 가 필요합니다.
  exit /b 1
)

set JP= jpackage
where jpackage >nul 2>&1
if errorlevel 1 (
  echo jpackage 가 필요합니다. JDK 14+ 를 설치해 주세요.
  exit /b 1
)

jpackage ^
  --name %APP_NAME% ^
  --type exe ^
  --dest "%OUT%" ^
  --input "%TARGET%" ^
  --main-jar "webcam-test-0.0.1-SNAPSHOT-all.jar" ^
  --main-class com.test.video.swing.SwingMain ^
  --app-version 0.0.1 ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut

echo 완료: %OUT%%APP_NAME%-*.exe
endlocal

