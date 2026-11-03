#!/bin/sh
# One monitor-only process. No arguments or caller-selected executable are accepted.
set +x
set -eu
umask 077
fail() { printf '%s\n' 'Invalid collector launch configuration' >&2; exit 1; }
[ "$#" -eq 0 ] || fail
env -i PATH=/usr/local/bin:/usr/bin:/bin /usr/local/bin/node /opt/redis-monitor-cloud/runtime-directory.mjs collector start || fail
cleanup() { env -i PATH=/usr/local/bin:/usr/bin:/bin /usr/local/bin/node /opt/redis-monitor-cloud/runtime-directory.mjs collector cleanup || exit 1; }
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
secret=${FASTTOWIN_REDIS_MONITOR_PASSWORD-}
[ "${#secret}" -eq 96 ] || fail
case "$secret" in *[!a-f0-9]*) fail ;; esac
[ -n "${FASTTOWIN_REDIS_MONITOR_ADDRESS-}" ] || fail
case "${FASTTOWIN_REDIS_MONITOR_TLS-false}" in true|false|'') ;; *) fail ;; esac

printf '%s' "$secret" > /run/secrets/redis-monitor || fail
chmod 600 /run/secrets/redis-monitor || fail
unset secret FASTTOWIN_REDIS_MONITOR_PASSWORD GRAFANA_CLOUD_WRITE_TOKEN
exec env -i PATH=/usr/local/bin:/usr/bin:/bin HOME=/nonexistent SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt \
  FASTTOWIN_REDIS_MONITOR_ADDRESS="$FASTTOWIN_REDIS_MONITOR_ADDRESS" \
  FASTTOWIN_REDIS_MONITOR_TLS="${FASTTOWIN_REDIS_MONITOR_TLS-false}" \
  FASTTOWIN_REDIS_MONITOR_LISTEN='[::]:19121' \
  FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE=/run/secrets/redis-monitor \
  /usr/local/bin/redis-monitor
