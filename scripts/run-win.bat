@echo off
setlocal
set JAR="%~dp0..\target\webcam-test-0.0.1-SNAPSHOT-all.jar"
if not exist %JAR% (
  echo Fat JAR not found: %JAR%
  echo 먼저 Windows에서 다음을 실행해 주세요: mvn clean package
  exit /b 1
)
java -jar %JAR%
endlocal

