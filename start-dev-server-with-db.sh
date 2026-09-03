#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$project_dir"

if [[ -z "${JAVA_HOME:-}" ]]; then
  android_studio_jdk="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -d "$android_studio_jdk" ]]; then
    export JAVA_HOME="$android_studio_jdk"
  elif /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  else
    echo "[FastToWin] Không tìm thấy JDK 17. Hãy cài Android Studio hoặc OpenJDK 17." >&2
    exit 1
  fi
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "[FastToWin] Không tìm thấy Docker. Hãy cài và mở Docker Desktop." >&2
  exit 1
fi

docker compose up -d --wait database

export FASTTOWIN_ENV=dev
export DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/fasttowin
export DATABASE_USER=fasttowin
export DATABASE_PASSWORD=fasttowin
export FASTTOWIN_WEB_BASE_URL=http://localhost:8081

./gradlew :server:installDist
exec ./server/build/install/server/bin/server
