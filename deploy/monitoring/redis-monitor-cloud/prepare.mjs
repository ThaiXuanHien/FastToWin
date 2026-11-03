import {isIP} from 'node:net';

export const metricNames = Object.freeze([
  'fasttowin_redis_monitor_up',
  'fasttowin_redis_monitor_collection_duration_seconds',
  'fasttowin_redis_monitor_last_success_timestamp_seconds',
  'fasttowin_redis_monitor_collection_errors_total',
  'fasttowin_redis_monitor_memory_used_bytes',
  'fasttowin_redis_monitor_memory_max_bytes',
  'fasttowin_redis_monitor_evicted_keys_total',
  'fasttowin_redis_monitor_aof_enabled',
  'fasttowin_redis_monitor_aof_current_size_bytes',
  'fasttowin_redis_monitor_aof_base_size_bytes',
  'fasttowin_redis_monitor_aof_last_write_ok',
  'fasttowin_redis_monitor_aof_last_rewrite_ok',
  'fasttowin_redis_monitor_aof_rewrite_in_progress',
  'fasttowin_redis_monitor_aof_pending_fsync',
  'fasttowin_redis_monitor_aof_delayed_fsync_total',
  'up',
]);

export function validateDestination(input) {
  try {
    const {url, expectedHost, tenantId} = input;
    if (typeof url !== 'string' || typeof expectedHost !== 'string' ||
        typeof tenantId !== 'string' || !/^[0-9]+$/.test(tenantId) ||
        !/^prometheus-[a-z0-9]+(?:-[a-z0-9]+)*(?:\.[a-z0-9]+(?:-[a-z0-9]+)*)*\.grafana\.net$/.test(expectedHost) ||
        /[\x00-\x20\x7f]/.test(url)) throw 0;
    const parsed = new URL(url);
    // Compare the raw spelling too: URL normalization must not hide escaped paths,
    // dot segments, empty queries/fragments, backslashes or alternate authorities.
    if (url !== `https://${expectedHost}/api/prom/push` &&
        url !== `https://${expectedHost}:443/api/prom/push`) throw 0;
    if (parsed.protocol !== 'https:' || parsed.hostname !== expectedHost ||
        parsed.port || parsed.username || parsed.password || parsed.search || parsed.hash ||
        parsed.pathname !== '/api/prom/push') throw 0;
    return {url: parsed.href, expectedHost, tenantId};
  } catch { throw new Error('Invalid cloud destination'); }
}

function privateTarget(address) {
  if (typeof address !== 'string') return false;
  const match = /^(\[[0-9a-fA-F:]+\]|[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?):([0-9]+)$/.exec(address);
  if (!match || Number(match[2]) < 1 || Number(match[2]) > 65535) return false;
  const host = match[1].replace(/^\[|\]$/g, '');
  if (isIP(host) === 4) {
    const [a,b] = host.split('.').map(Number);
    return a === 10 || a === 127 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
  }
  if (isIP(host) === 6) return host === '::1' || /^f[cd]/i.test(host);
  if (host.includes(':') || /^[0-9.]+$/.test(host)) return false;
  return /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(host) ||
    /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.railway\.internal$/.test(host);
}

export function renderAlloy(input) {
  const destination = validateDestination(input);
  if (!privateTarget(input.collectorAddress)) throw new Error('Invalid collector target');
  const errorName = 'fasttowin_redis_monitor_collection_errors_total';
  const others = metricNames.filter(name => name !== errorName).join('|');
  return `// Prepared locally; secrets are runtime files only.
prometheus.scrape "redis_monitor" {
  targets = [{ "__address__" = "${input.collectorAddress}" }]
  scrape_interval = "15s"
  scrape_timeout = "5s"
  follow_redirects = false
  proxy_from_environment = false
  honor_labels = false
  honor_timestamps = true
  forward_to = [prometheus.relabel.redis_monitor.receiver]
}

prometheus.relabel "redis_monitor" {
  forward_to = [prometheus.remote_write.grafana.receiver]
  rule {
    source_labels = ["__name__"]
    regex = "${metricNames.join('|')}"
    action = "keep"
  }
  rule {
    source_labels = ["__name__", "reason"]
    regex = "${errorName};(connect|timeout|auth|protocol|oversized|missing|invalid)|(${others});.*"
    action = "keep"
  }
  rule {
    source_labels = ["__name__"]
    regex = "${others}"
    target_label = "reason"
    replacement = ""
  }
  rule {
    target_label = "job"
    replacement = "fasttowin-redis-monitor"
  }
  rule {
    target_label = "instance"
    replacement = "redis-primary"
  }
  rule {
    regex = "__name__|job|instance|reason"
    action = "labelkeep"
  }
}

prometheus.remote_write "grafana" {
  endpoint {
    url = "${destination.url}"
    remote_timeout = "10s"
    follow_redirects = false
    proxy_from_environment = false
    send_exemplars = false
    send_native_histograms = false
    basic_auth {
      username = "${destination.tenantId}"
      password_file = "/run/secrets/grafana-write"
    }
    metadata_config {
      send = false
    }
    queue_config {
      min_shards = 1
      max_shards = 1
      capacity = 1000
      max_samples_per_send = 250
      min_backoff = "1s"
      max_backoff = "30s"
    }
  }
  wal {
    truncate_frequency = "5m"
    min_keepalive_time = "5m"
    max_keepalive_time = "2h"
  }
}
`;
}
