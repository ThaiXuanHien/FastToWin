#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$project_dir"

action=${1:-}
value=${2:-}
env_file=deploy/.env.production
compose_file=compose.production.yaml
backup_dir=deploy/backups
backup_retention_days=${FASTTOWIN_BACKUP_RETENTION_DAYS:-14}

[ -n "$action" ] || {
  echo "Cách dùng: ./production-ops.sh status|health|maintenance-on|maintenance-off|backup|verify-backup|backup-cycle|restore-drill|prune-backups|rollback" >&2
  exit 1
}
[ -f "$env_file" ] || { echo "Thiếu $env_file." >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "Không tìm thấy Docker CLI." >&2; exit 1; }
case "$backup_retention_days" in
  ''|*[!0-9]*) echo "FASTTOWIN_BACKUP_RETENTION_DAYS phải là số ngày hợp lệ." >&2; exit 1 ;;
esac
[ "$backup_retention_days" -ge 1 ] && [ "$backup_retention_days" -le 3650 ] || {
  echo "FASTTOWIN_BACKUP_RETENTION_DAYS phải từ 1 đến 3650." >&2
  exit 1
}

if [ -z "${FASTTOWIN_RELEASE_TAG:-}" ] && [ -f deploy/state/active-release.txt ]; then
  FASTTOWIN_RELEASE_TAG=$(cat deploy/state/active-release.txt)
  export FASTTOWIN_RELEASE_TAG
fi

compose() {
  docker compose --env-file "$env_file" -f "$compose_file" "$@"
}

env_value() {
  sed -n "s/^$1=//p" "$env_file" | tail -n 1
}

health() {
  domain=$(env_value FASTTOWIN_DOMAIN)
  case "$domain" in ''|*your-domain*) echo "FASTTOWIN_DOMAIN chưa được cấu hình." >&2; exit 1 ;; esac
  curl --fail --silent --show-error "https://$domain/health" | grep -qx 'OK'
  curl --fail --silent --show-error "https://$domain/status"
  echo
  echo "[FastToWin] Health check đạt."
}

database_container_id() {
  container_id=$(compose ps -q database)
  [ -n "$container_id" ] || { echo "Database container chưa chạy." >&2; exit 1; }
}

backup() {
  mkdir -p "$backup_dir"
  timestamp=$(date -u +%Y%m%d-%H%M%S)
  output="$backup_dir/fasttowin-$timestamp.dump"
  database_container_id
  remote="/tmp/fasttowin-$timestamp.dump"
  docker exec "$container_id" sh -c "pg_dump -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -Fc -f '$remote'"
  docker cp "$container_id:$remote" "$output"
  docker exec "$container_id" rm -f "$remote"
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "$backup_dir" && sha256sum "$(basename "$output")" > "$(basename "$output").sha256")
  else
    echo "Thiếu sha256sum; không thể xác minh backup." >&2
    exit 1
  fi
  last_backup=$output
  echo "[FastToWin] Backup: $output"
}

resolve_backup() {
  requested=${1:-}
  mkdir -p "$backup_dir"
  backup_root=$(realpath "$backup_dir")
  if [ -z "$requested" ]; then
    set -- "$backup_dir"/fasttowin-*.dump
    [ -e "$1" ] || { echo "Chưa có backup PostgreSQL để kiểm tra." >&2; exit 1; }
    requested=$(ls -1t "$backup_dir"/fasttowin-*.dump | head -n 1)
  fi
  resolved=$(realpath "$requested")
  case "$resolved" in
    "$backup_root"/*) ;;
    *) echo "Chỉ cho phép dùng backup trong $backup_dir." >&2; exit 1 ;;
  esac
  [ -f "$resolved" ] || { echo "Không tìm thấy backup: $resolved" >&2; exit 1; }
  case "$resolved" in
    *.dump) ;;
    *) echo "Backup phải có đuôi .dump." >&2; exit 1 ;;
  esac
  printf '%s\n' "$resolved"
}

verify_backup() {
  verified_backup=$(resolve_backup "${1:-}")
  checksum="$verified_backup.sha256"
  [ -f "$checksum" ] || { echo "Thiếu checksum: $checksum" >&2; exit 1; }
  (cd "$(dirname "$verified_backup")" && sha256sum -c "$(basename "$checksum")")

  database_container_id
  remote="/tmp/verify-$(basename "$verified_backup")"
  docker cp "$verified_backup" "$container_id:$remote" >/dev/null
  if ! docker exec "$container_id" pg_restore --list "$remote" >/dev/null; then
    docker exec "$container_id" rm -f "$remote" >/dev/null 2>&1 || true
    echo "pg_restore không đọc được backup." >&2
    exit 1
  fi
  docker exec "$container_id" rm -f "$remote" >/dev/null
  echo "[FastToWin] Backup hợp lệ: $verified_backup"
}

prune_backups() {
  mkdir -p "$backup_dir"
  find "$backup_dir" -maxdepth 1 -type f -name 'fasttowin-*.dump' -mtime +"$backup_retention_days" -print |
    while IFS= read -r dump; do
      rm -f -- "$dump" "$dump.sha256"
      echo "[FastToWin] Đã xóa backup quá hạn: $dump"
    done
  echo "[FastToWin] Đã áp dụng thời hạn lưu backup: $backup_retention_days ngày."
}

restore_drill() {
  verify_backup "${1:-}"
  backup_file=$verified_backup
  timestamp=$(date -u +%Y%m%d%H%M%S)
  drill_container="fasttowin-restore-drill-$timestamp-$$"
  drill_database=fasttowin_restore_drill
  drill_user=fasttowin_drill
  drill_password=$(date +%s)-$$-restore-drill

  cleanup_restore_drill() {
    docker rm -f "$drill_container" >/dev/null 2>&1 || true
  }
  trap cleanup_restore_drill EXIT INT TERM

  docker run --detach --rm \
    --name "$drill_container" \
    --tmpfs /var/lib/postgresql/data:rw,noexec,nosuid,size=1g \
    --env "POSTGRES_DB=$drill_database" \
    --env "POSTGRES_USER=$drill_user" \
    --env "POSTGRES_PASSWORD=$drill_password" \
    postgres:17-alpine >/dev/null

  ready=false
  attempt=0
  while [ "$attempt" -lt 30 ]; do
    if docker exec "$drill_container" pg_isready -U "$drill_user" -d "$drill_database" >/dev/null 2>&1; then
      ready=true
      break
    fi
    attempt=$((attempt + 1))
    sleep 1
  done
  [ "$ready" = true ] || { echo "PostgreSQL phục vụ restore drill không sẵn sàng." >&2; exit 1; }

  docker cp "$backup_file" "$drill_container:/tmp/restore.dump" >/dev/null
  docker exec --env "PGPASSWORD=$drill_password" "$drill_container" \
    pg_restore --exit-on-error --no-owner --no-privileges \
    -U "$drill_user" -d "$drill_database" /tmp/restore.dump >/dev/null

  table_count=$(docker exec --env "PGPASSWORD=$drill_password" "$drill_container" \
    psql -U "$drill_user" -d "$drill_database" -Atc \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';")
  core_count=$(docker exec --env "PGPASSWORD=$drill_password" "$drill_container" \
    psql -U "$drill_user" -d "$drill_database" -Atc \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('users','sessions','matches');")
  [ "$table_count" -gt 0 ] || { echo "Restore drill không tìm thấy bảng dữ liệu." >&2; exit 1; }
  [ "$core_count" -eq 3 ] || { echo "Restore drill thiếu bảng users, sessions hoặc matches." >&2; exit 1; }

  cleanup_restore_drill
  trap - EXIT INT TERM
  echo "[FastToWin] Restore drill đạt: $table_count bảng, đủ 3 bảng lõi."
}

maintenance() {
  enabled=$1
  message=${2:-}
  FASTTOWIN_MAINTENANCE=$enabled FASTTOWIN_MAINTENANCE_MESSAGE=$message \
    docker compose --env-file "$env_file" -f "$compose_file" up -d --no-deps --force-recreate --wait server
  echo "[FastToWin] Maintenance: $enabled"
}

case "$action" in
  status)
    compose ps
    [ ! -f deploy/state/active-release.txt ] || echo "[FastToWin] Active release: $(cat deploy/state/active-release.txt)"
    ;;
  health) health ;;
  maintenance-on)
    maintenance true "${value:-Máy chủ đang nâng cấp. Vui lòng quay lại sau.}"
    ;;
  maintenance-off) maintenance false '' ;;
  backup) backup ;;
  verify-backup) verify_backup "${value:-}" ;;
  backup-cycle)
    backup
    verify_backup "$last_backup"
    prune_backups
    ;;
  restore-drill) restore_drill "${value:-}" ;;
  prune-backups) prune_backups ;;
  rollback)
    [ "${3:-}" = "--confirm-schema-compatible" ] || {
      echo "Hãy kiểm tra schema/backup rồi thêm --confirm-schema-compatible." >&2
      exit 1
    }
    target=$value
    [ -n "$target" ] || target=$(cat deploy/state/previous-release.txt 2>/dev/null || true)
    case "$target" in ''|*[!A-Za-z0-9._-]*|[!A-Za-z0-9]*) echo "Release rollback không hợp lệ." >&2; exit 1 ;; esac
    server_image=${FASTTOWIN_SERVER_IMAGE:-$(env_value FASTTOWIN_SERVER_IMAGE)}
    web_image=${FASTTOWIN_WEB_IMAGE:-$(env_value FASTTOWIN_WEB_IMAGE)}
    server_image=${server_image:-fasttowin-server}
    web_image=${web_image:-fasttowin-web}
    docker image inspect "$server_image:$target" >/dev/null
    docker image inspect "$web_image:$target" >/dev/null
    backup
    maintenance true 'Máy chủ đang khôi phục phiên bản ổn định.'
    FASTTOWIN_RELEASE_TAG=$target FASTTOWIN_MAINTENANCE=false \
      docker compose --env-file "$env_file" -f "$compose_file" up -d --no-build --wait server web
    health
    mkdir -p deploy/state
    old_release=${FASTTOWIN_RELEASE_TAG:-}
    if [ -n "$old_release" ] && [ "$old_release" != "$target" ]; then
      printf '%s' "$old_release" > deploy/state/previous-release.txt
    fi
    printf '%s' "$target" > deploy/state/active-release.txt
    echo "[FastToWin] Đã rollback ứng dụng về $target."
    ;;
  *) echo "Thao tác không hỗ trợ: $action" >&2; exit 1 ;;
esac
