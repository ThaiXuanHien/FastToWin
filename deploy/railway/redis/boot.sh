#!/bin/sh
set -eu
umask 077
fail() { printf 'Invalid Redis configuration\n' >&2; exit 1; }
valid_secret() { case "$1" in ''|*[!0-9a-f]*) return 1;; esac; [ "${#1}" -eq 96 ]; }
namespace=${FASTTOWIN_RATE_LIMIT_NAMESPACE:-}
case "$namespace" in ''|[!a-z0-9]*|*[!a-z0-9_-]*) fail;; esac
[ "${#namespace}" -le 48 ] || fail
app=${FASTTOWIN_REDIS_APP_PASSWORD:-}; monitor=${FASTTOWIN_REDIS_MONITOR_PASSWORD:-}; backup=${FASTTOWIN_REDIS_BACKUP_PASSWORD:-}; previous=${FASTTOWIN_REDIS_APP_PREVIOUS_PASSWORD:-}
presence=${FASTTOWIN_REDIS_PRESENCE_PASSWORD:-}
valid_secret "$app" && valid_secret "$monitor" && valid_secret "$backup" || fail
[ "$app" != "$monitor" ] && [ "$app" != "$backup" ] && [ "$monitor" != "$backup" ] || fail
if [ -n "$previous" ]; then
 valid_secret "$previous" && [ "$previous" != "$app" ] && [ "$previous" != "$monitor" ] && [ "$previous" != "$backup" ] || fail
fi
if [ -n "$presence" ]; then
 valid_secret "$presence" && [ "$presence" != "$app" ] && [ "$presence" != "$monitor" ] && [ "$presence" != "$backup" ] && [ "$presence" != "$previous" ] || fail
fi
mkdir -p /run/ftw-redis
chown root:redis /run/ftw-redis
chmod 750 /run/ftw-redis
cat > /run/ftw-redis/redis.conf <<'CONFIG'
bind 0.0.0.0 ::
protected-mode yes
port 6379
dir /data
maxmemory 64mb
maxmemory-policy noeviction
appendonly yes
appendfsync everysec
save ""
daemonize no
loglevel notice
aclfile /run/ftw-redis/users.acl
CONFIG
{
 printf 'user default off\n'
 printf 'user ftw_app on >%s' "$app"
 [ -z "$previous" ] || printf ' >%s' "$previous"
 printf ' ~ftw:%s:rl:v1:* +hello +client|setinfo +ping +select +eval +type +hlen +hmget +hset +pexpire +time\n' "$namespace"
 printf 'user ftw_monitor on >%s +ping +info\n' "$monitor"
 printf 'user ftw_backup on >%s ~ftw:%s:ops:backup-fence +ping +set +waitaof +client|pause +client|unpause +info\n' "$backup" "$namespace"
 [ -z "$presence" ] || printf 'user ftw_presence on >%s ~ftw:%s:presence:v1:* +hello +client|setinfo +ping +select +eval +time +zadd +zrem +zremrangebyscore +zcount +pexpire\n' "$presence" "$namespace"
} > /run/ftw-redis/users.acl
chown redis:redis /run/ftw-redis/redis.conf /run/ftw-redis/users.acl
chmod 600 /run/ftw-redis/redis.conf /run/ftw-redis/users.acl
chown -R redis:redis /data
cd /data
exec /usr/local/bin/docker-entrypoint.sh redis-server /run/ftw-redis/redis.conf
