#!/bin/sh
# One fixed sender. Configuration is regenerated from validated non-secret fields.
set +x
set -eu
umask 077
fail() { printf '%s\n' 'Invalid sender launch configuration' >&2; exit 1; }
[ "$#" -eq 0 ] || fail
env -i PATH=/usr/local/bin:/usr/bin:/bin /usr/local/bin/node /opt/redis-monitor-cloud/runtime-directory.mjs sender start || fail
cleanup() { env -i PATH=/usr/local/bin:/usr/bin:/bin /usr/local/bin/node /opt/redis-monitor-cloud/runtime-directory.mjs sender cleanup || exit 1; }
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
secret=${GRAFANA_CLOUD_WRITE_TOKEN-}
[ "${#secret}" -ge 16 ] && [ "${#secret}" -le 4096 ] || fail
case "$secret" in *[!a-zA-Z0-9_+=/.-]*) fail ;; esac

printf '%s' "$secret" > /run/secrets/grafana-write || fail
chmod 600 /run/secrets/grafana-write || fail
unset secret GRAFANA_CLOUD_WRITE_TOKEN FASTTOWIN_REDIS_MONITOR_PASSWORD
# env -i also prevents proxy/Node options and unrelated Railway secrets crossing boundaries.
env -i PATH=/usr/local/bin:/usr/bin:/bin \
  GRAFANA_CLOUD_REMOTE_WRITE_URL="${GRAFANA_CLOUD_REMOTE_WRITE_URL-}" \
  GRAFANA_CLOUD_EXPECTED_HOST="${GRAFANA_CLOUD_EXPECTED_HOST-}" \
  GRAFANA_CLOUD_TENANT_ID="${GRAFANA_CLOUD_TENANT_ID-}" \
  FASTTOWIN_REDIS_MONITOR_SCRAPE_ADDRESS="${FASTTOWIN_REDIS_MONITOR_SCRAPE_ADDRESS-}" \
  /usr/local/bin/node /opt/redis-monitor-cloud/prepare-runtime.mjs || fail
env -i PATH=/usr/local/bin:/usr/bin:/bin /bin/alloy validate /run/secrets/config.alloy >/dev/null 2>&1 || fail
exec env -i PATH=/usr/local/bin:/usr/bin:/bin HOME=/nonexistent SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt \
  /bin/alloy run --disable-reporting \
  --server.http.listen-addr=127.0.0.1:12345 --server.http.enable-pprof=false \
  --server.http.disable-support-bundle --storage.path=/var/lib/alloy /run/secrets/config.alloy
