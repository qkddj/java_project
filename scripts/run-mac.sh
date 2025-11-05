#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/../target/webcam-test-0.0.1-SNAPSHOT-all.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Fat JAR not found: $JAR"
  echo "먼저 macOS에서 다음을 실행해 주세요: mvn clean package"
  exit 1
fi
exec java -jar "$JAR"

