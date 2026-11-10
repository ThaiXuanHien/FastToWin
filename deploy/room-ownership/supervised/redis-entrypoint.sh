#!/bin/sh
set -eu
test "${#FTW_FIXTURE_REDIS_PASSWORD}" -eq 64
case "$FTW_FIXTURE_REDIS_PASSWORD" in *[!0-9a-f]*) exit 1;; esac
umask 077
config=$(mktemp)
printf 'bind 0.0.0.0 ::\nprotected-mode yes\nrequirepass %s\nsave ""\nappendonly no\n' "$FTW_FIXTURE_REDIS_PASSWORD" > "$config"
unset FTW_FIXTURE_REDIS_PASSWORD
exec /usr/local/bin/docker-entrypoint.sh redis-server "$config"
