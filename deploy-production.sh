#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$project_dir"

build_only=false
if [ "${1:-}" = "--build-only" ]; then
  build_only=true
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Không tìm thấy Java. Hãy cài JDK 17 và đặt JAVA_HOME." >&2
  exit 1
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "Không tìm thấy Docker CLI." >&2
  exit 1
fi

echo "[FastToWin] Kiểm thử và đóng gói backend + Web production..."
./gradlew \
  :server:clean \
  :server:test \
  :server:installDist \
  :webApp:composeCompatibilityBrowserDistribution \
  --no-daemon

test -f server/build/install/server/bin/server
test -f webApp/build/dist/composeWebCompatibility/productionExecutable/index.html

if [ "$build_only" = true ]; then
  echo "[FastToWin] Build production đã hoàn tất."
  exit 0
fi

test -f deploy/.env.production || {
  echo "Thiếu deploy/.env.production; hãy sao chép từ file .example." >&2
  exit 1
}
for secret_file in \
  deploy/secrets/database_password.txt \
  deploy/secrets/smtp_password.txt \
  deploy/secrets/firebase-service-account.json
do
  test -f "$secret_file" || {
    echo "Thiếu production secret: $secret_file" >&2
    exit 1
  }
done

docker compose --env-file deploy/.env.production -f compose.production.yaml config --quiet
docker compose --env-file deploy/.env.production -f compose.production.yaml up -d --build --wait
echo "[FastToWin] Production đã khởi động. Kiểm tra HTTPS /health trước khi phát hành."
