#!/usr/bin/env sh
set -eu

action=${1:-install}
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
state_dir="$project_dir/deploy/state"
begin_marker='# BEGIN FASTTOWIN DATABASE MAINTENANCE'
end_marker='# END FASTTOWIN DATABASE MAINTENANCE'

case "$project_dir" in
  *"'"*|*"
"*) echo "Đường dẫn project chứa ký tự không được hỗ trợ bởi cron." >&2; exit 1 ;;
esac
case "$action" in
  install|remove) ;;
  *) echo "Cách dùng: $0 [install|remove]" >&2; exit 1 ;;
esac

command -v crontab >/dev/null 2>&1 || { echo "Không tìm thấy crontab." >&2; exit 1; }
command -v flock >/dev/null 2>&1 || { echo "Không tìm thấy flock (util-linux)." >&2; exit 1; }
[ -f "$project_dir/deploy/.env.production" ] || {
  echo "Thiếu deploy/.env.production; chưa cài lịch backup." >&2
  exit 1
}
[ -x "$project_dir/production-ops.sh" ] || {
  echo "Hãy chạy: chmod +x production-ops.sh" >&2
  exit 1
}

mkdir -p "$state_dir"
current_file=$(mktemp)
next_file=$(mktemp)
trap 'rm -f "$current_file" "$next_file"' EXIT INT TERM

crontab -l >"$current_file" 2>/dev/null || true
awk -v begin="$begin_marker" -v end="$end_marker" '
  $0 == begin { skipping = 1; next }
  $0 == end { skipping = 0; next }
  !skipping { print }
' "$current_file" >"$next_file"

if [ "$action" = install ]; then
  {
    printf '%s\n' "$begin_marker"
    printf "17 2 * * * cd '%s' && flock -n deploy/state/backup.lock ./production-ops.sh backup-cycle >> deploy/state/backup.log 2>&1\n" "$project_dir"
    printf "43 3 * * 0 cd '%s' && flock -n deploy/state/restore-drill.lock ./production-ops.sh restore-drill >> deploy/state/restore-drill.log 2>&1\n" "$project_dir"
    printf '%s\n' "$end_marker"
  } >>"$next_file"
fi

crontab "$next_file"
if [ "$action" = install ]; then
  echo "[FastToWin] Đã cài backup hằng ngày lúc 02:17 và restore drill Chủ nhật lúc 03:43."
else
  echo "[FastToWin] Đã gỡ lịch backup/restore drill khỏi crontab."
fi
