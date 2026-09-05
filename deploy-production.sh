#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$project_dir"

build_only=false
release_tag=${FASTTOWIN_RELEASE_TAG:-}
while [ "$#" -gt 0 ]; do
  case "$1" in
    --build-only) build_only=true ;;
    --release-tag)
      shift
      [ "$#" -gt 0 ] || { echo "Thiếu giá trị --release-tag." >&2; exit 1; }
      release_tag=$1
      ;;
    *) echo "Tham số không hỗ trợ: $1" >&2; exit 1 ;;
  esac
  shift
done

if [ -z "$release_tag" ]; then
  release_tag=$(git rev-parse --short=12 HEAD 2>/dev/null || printf 'local')
fi
case "$release_tag" in
  ''|*[!A-Za-z0-9._-]*|[!A-Za-z0-9]*)
    echo "Release tag không hợp lệ: $release_tag" >&2
    exit 1
    ;;
esac
[ "${#release_tag}" -le 64 ] || { echo "Release tag tối đa 64 ký tự." >&2; exit 1; }
export FASTTOWIN_RELEASE_TAG=$release_tag
echo "[FastToWin] Release: $release_tag"

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
  deploy/secrets/firebase-service-account.json \
  deploy/secrets/grafana_admin_password.txt
do
  test -f "$secret_file" || {
    echo "Thiếu production secret: $secret_file" >&2
    exit 1
  }
done

docker compose --env-file deploy/.env.production -f compose.production.yaml config --quiet
docker compose --env-file deploy/.env.production -f compose.production.yaml up -d --build --wait

mkdir -p deploy/state
previous_release=$(cat deploy/state/active-release.txt 2>/dev/null || true)
if [ -n "$previous_release" ] && [ "$previous_release" != "$release_tag" ]; then
  printf '%s' "$previous_release" > deploy/state/previous-release.txt
fi
printf '%s' "$release_tag" > deploy/state/active-release.txt
echo "[FastToWin] Production release $release_tag đã khởi động. Chạy ./production-ops.sh health."
