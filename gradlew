#!/bin/sh
# Gradle wrapper starter. The real wrapper jar is fetched by Android Studio /
# by running `gradle wrapper` once; this script delegates to a local Gradle
# installation if present, otherwise prints the setup hint.
# NOTE: for a full offline checkout, copy gradle-wrapper.jar from any
# Android Studio project into gradle/wrapper/ and use the standard script.
set -e
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
elif command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
else
  echo "未找到 gradle-wrapper.jar，也未找到系统 gradle。"
  echo "请在 Android Studio 中打开本工程，IDE 会自动下载 Gradle 8.7 并生成 wrapper；"
  echo "或先安装 Gradle 8.7，再运行 ./gradlew assembleDebug。"
  exit 1
fi
