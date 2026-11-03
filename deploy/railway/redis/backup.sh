#!/bin/sh
set -eu
umask 077
fail() { printf 'Backup failed\n' >&2; exit 1; }
[ "$#" = 1 ] || fail
tag=$1
case "$tag" in ''|*[!0-9a-f]*) fail;; esac
[ "${#tag}" -eq 32 ] || fail
namespace=${FASTTOWIN_RATE_LIMIT_NAMESPACE:-}; secret=${FASTTOWIN_REDIS_BACKUP_PASSWORD:-}
case "$namespace" in ''|[!a-z0-9]*|*[!a-z0-9_-]*) fail;; esac
[ "${#namespace}" -le 48 ] || fail
case "$secret" in ''|*[!0-9a-f]*) fail;; esac
[ "${#secret}" -eq 96 ] || fail
export REDISCLI_AUTH="$secret"
tmp="/tmp/ftw-backup-stage-$tag"; archive="/tmp/ftw-backup-$tag.tar.gz"
[ ! -e "$tmp" ] && [ ! -L "$tmp" ] && [ ! -e "$archive" ] && [ ! -L "$archive" ] || fail
mkdir -m 700 "$tmp" || fail
paused=0; success=0
cleanup() {
 if [ "$paused" = 1 ]; then timeout 2 redis-cli -h 127.0.0.1 --user ftw_backup --raw CLIENT UNPAUSE >/dev/null 2>&1 || :; fi
 rm -rf "$tmp"
 [ "$success" = 1 ] || rm -f "$archive"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
manifest() {
 root=$1; dir=$root/appendonlydir
 [ -d "$dir" ] && [ ! -L "$dir" ] || return 1
 find "$dir" -mindepth 1 -maxdepth 1 -print > "$tmp/entries" || return 1
 total=$(wc -l < "$tmp/entries")
 count=0; size=0; seen=0
 for f in "$dir"/*; do
  [ -f "$f" ] && [ ! -L "$f" ] && [ "$(stat -c %h "$f")" = 1 ] || return 1
  name=${f##*/}
  case "$name" in
   appendonly.aof.manifest) seen=1 ;;
   appendonly.aof.*.base.rdb) seq=${name#appendonly.aof.}; seq=${seq%.base.rdb}; case "$seq" in ''|*[!0-9]*) return 1;; esac ;;
   appendonly.aof.*.incr.aof) seq=${name#appendonly.aof.}; seq=${seq%.incr.aof}; case "$seq" in ''|*[!0-9]*) return 1;; esac ;;
   *) return 1 ;;
  esac
  size=$((size + $(stat -c %s "$f"))); count=$((count + 1))
  [ "$size" -le 268435456 ] && [ "$count" -le 1025 ] || return 1
 done
 [ "$count" -eq "$total" ] && [ "$count" -ge 2 ] && [ "$seen" = 1 ] || return 1
 # Redis manifest must name each data file exactly once and contain no path syntax.
 awk '(NF != 6 && !(NF == 8 && $7 == "startoffset" && $8 ~ /^[0-9]+$/)) || $1 != "file" || $3 != "seq" || $5 != "type" || $4 !~ /^[0-9]+$/ || ($6 != "b" && $6 != "i" && $6 != "h") || $2 !~ /^appendonly[.]aof[.][0-9]+[.](base[.]rdb|incr[.]aof)$/ || seen[$2]++ { exit 1 } {print $2}' "$dir/appendonly.aof.manifest" > "$tmp/unsorted" || return 1
 LC_ALL=C sort "$tmp/unsorted" > "$tmp/listed" || return 1
 (cd "$dir" && for f in appendonly.aof.*; do [ "$f" = appendonly.aof.manifest ] || printf '%s\n' "$f"; done | LC_ALL=C sort) > "$tmp/actual" || return 1
 cmp -s "$tmp/listed" "$tmp/actual" || return 1
 (cd "$root" && for f in appendonlydir/*; do sha256sum "$f" || exit 1; done) > "$tmp/hashes" || return 1
 LC_ALL=C sort "$tmp/hashes"
}
paused=1
started=$(cut -d. -f1 /proc/uptime)
answer=$(printf 'SET ftw:%s:ops:backup-fence syncack\nWAITAOF 1 0 5000\nCLIENT PAUSE 10000 WRITE\n' "$namespace" | timeout 7 redis-cli -h 127.0.0.1 --user ftw_backup --raw 2>/dev/null) || fail
[ "$answer" = "$(printf 'OK\n1\n0\nOK')" ] || fail
manifest /data > "$tmp/SHA256SUMS" || fail
mkdir "$tmp/appendonlydir" || fail
cp -p /data/appendonlydir/* "$tmp/appendonlydir/" || fail
manifest "$tmp" > "$tmp/copied" || fail
cmp -s "$tmp/SHA256SUMS" "$tmp/copied" || fail
tar -czf "$archive" -C "$tmp" appendonlydir SHA256SUMS || fail
[ "$(stat -c %s "$archive")" -le 268435456 ] || fail
sync -f "$archive" && sync -f /tmp || fail
manifest /data > "$tmp/after" || fail
cmp -s "$tmp/SHA256SUMS" "$tmp/after" || fail
[ $(( $(cut -d. -f1 /proc/uptime) - started )) -lt 7 ] || fail
timeout 2 redis-cli -h 127.0.0.1 --user ftw_backup --raw CLIENT UNPAUSE | grep -qx OK || fail
paused=0
hash=$(sha256sum "$archive") || fail
hash=${hash%% *}
success=1
printf '{"backup":1,"sha256":"%s","bytes":%s}\n' "$hash" "$(stat -c %s "$archive")"
