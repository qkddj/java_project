#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."
TARGET="$PROJECT_ROOT/target"
OUT="$PROJECT_ROOT/dist/mac"
mkdir -p "$OUT"

APP_NAME="RandomVideoApp"
MAIN_JAR="$TARGET/webcam-test-0.0.1-SNAPSHOT-all.jar"
if [[ ! -f "$MAIN_JAR" ]]; then
  echo "Fat JAR not found: $MAIN_JAR"
  echo "먼저 macOS에서 다음을 실행해 주세요: mvn clean package"
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage 가 필요합니다. JDK 14+ 를 설치해 주세요."
  exit 1
fi

jpackage \
  --name "$APP_NAME" \
  --type app-image \
  --dest "$OUT" \
  --input "$TARGET" \
  --main-jar "webcam-test-0.0.1-SNAPSHOT-all.jar" \
  --main-class com.test.video.swing.SwingMain \
  --app-version 0.0.1

echo "완료: $OUT/$APP_NAME"

