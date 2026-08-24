#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$project_dir"

if [[ -z "${JAVA_HOME:-}" ]]; then
  android_studio_jdk="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -d "$android_studio_jdk" ]]; then
    export JAVA_HOME="$android_studio_jdk"
  else
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  fi
fi

docker compose up -d --wait database

export FASTTOWIN_ENV=dev
export DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/fasttowin
export DATABASE_USER=fasttowin
export DATABASE_PASSWORD=fasttowin
export FASTTOWIN_DEV_ACCOUNT_EMAIL="${FASTTOWIN_DEV_ACCOUNT_EMAIL:-fulltest@fasttowin.dev}"
export FASTTOWIN_DEV_ACCOUNT_PASSWORD="${FASTTOWIN_DEV_ACCOUNT_PASSWORD:-12345678}"
export FASTTOWIN_DEV_ACCOUNT_NAME="${FASTTOWIN_DEV_ACCOUNT_NAME:-Full Test}"

./gradlew :server:seedDevFullAccount
