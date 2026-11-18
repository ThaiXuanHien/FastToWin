export function parseOptions(args) {
  const options = { pairs: 4, rounds: 1, postgresSettings: null, psql: 'psql' };
  const names = { '--pairs': 'pairs', '--rounds': 'rounds', '--postgres-settings': 'postgresSettings', '--psql': 'psql' };
  const seen = new Set();
  for (let i = 0; i < args.length; i += 2) {
    const name = names[args[i]], value = args[i + 1];
    if (!name || !value || value.startsWith('--') || seen.has(name)) throw new Error('Unsupported or missing load-test option');
    seen.add(name);
    if (name === 'pairs' || name === 'rounds') {
      if (!/^\d+$/.test(value)) throw new Error('Workload counts must be integers');
      const count = Number(value), max = name === 'pairs' ? 16 : 2;
      if (count < 1 || count > max) throw new Error(`Workload ${name} must be 1..${max}`);
      options[name] = count;
    } else options[name] = value;
  }
  return options;
}

export function validateDatabaseSettings(settings) {
  if (settings?.url !== 'jdbc:postgresql://127.0.0.1:15432/admin_test' ||
      typeof settings.user !== 'string' || !settings.user ||
      typeof settings.passwordFile !== 'string' || !settings.passwordFile) {
    throw new Error('Only the known local admin_test database is allowed; no URL/schema overrides');
  }
}

export function summarize(samples, seconds) {
  const sorted = samples.map(s => s.ms).sort((a, b) => a - b);
  const percentile = p => sorted.length ? Math.round(sorted[Math.ceil(p * sorted.length) - 1] * 100) / 100 : null;
  const errors = {};
  for (const sample of samples) if (!sample.ok) errors[sample.code] = (errors[sample.code] || 0) + 1;
  return {
    attempts: samples.length, successes: samples.filter(s => s.ok).length,
    failures: samples.filter(s => !s.ok).length,
    attemptsPerSecond: Math.round(samples.length / seconds * 100) / 100,
    p50Ms: percentile(0.5), p95Ms: percentile(0.95), p99Ms: percentile(0.99), errors,
  };
}

// ---- SCL-01 scale test (scripts/load-test-scale.mjs) ----

const SCALE_LIMITS = { maxPlayers: 10000, maxSteps: 8, maxHoldSeconds: 900, maxRampPerSecond: 500,
  minSelectIntervalMs: 100, maxSelectIntervalMs: 10000, maxRestSeconds: 300, maxPoolSize: 64, maxServerCpus: 64 };

export function parseScaleOptions(args) {
  const options = { steps: [200], holdSeconds: 60, rampPerSecond: 50, playingPercent: 60, selectIntervalMs: 500,
    restSeconds: 8, poolSize: 4, serverCpus: 0, workers: 4, postgresSettings: null, redisSettings: null,
    psql: 'psql', reconnectStorm: false, reconnectJitterMs: 0, jfr: false, label: '', serverDist: null };
  const numeric = { '--hold-seconds': ['holdSeconds', 10, SCALE_LIMITS.maxHoldSeconds],
    '--ramp-per-second': ['rampPerSecond', 1, SCALE_LIMITS.maxRampPerSecond],
    '--playing-percent': ['playingPercent', 0, 100],
    '--select-interval-ms': ['selectIntervalMs', SCALE_LIMITS.minSelectIntervalMs, SCALE_LIMITS.maxSelectIntervalMs],
    '--rest-seconds': ['restSeconds', 0, SCALE_LIMITS.maxRestSeconds],
    '--pool-size': ['poolSize', 1, SCALE_LIMITS.maxPoolSize],
    '--server-cpus': ['serverCpus', 0, SCALE_LIMITS.maxServerCpus],
    '--workers': ['workers', 1, 16],
    '--reconnect-jitter-ms': ['reconnectJitterMs', 0, 60000] };
  const text = { '--postgres-settings': 'postgresSettings', '--redis-settings': 'redisSettings', '--psql': 'psql', '--label': 'label',
    '--server-dist': 'serverDist' };
  const flags = { '--reconnect-storm': 'reconnectStorm', '--jfr': 'jfr' };
  const seen = new Set();
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (seen.has(arg)) throw new Error('Duplicate load-test option');
    seen.add(arg);
    if (flags[arg]) { options[flags[arg]] = true; continue; }
    const value = args[++i];
    if (value === undefined || value.startsWith('--')) throw new Error('Unsupported or missing load-test option');
    if (arg === '--steps') {
      if (!/^\d+(,\d+)*$/.test(value)) throw new Error('Steps must be comma-separated whole numbers');
      const steps = value.split(',').map(Number);
      if (steps.length > SCALE_LIMITS.maxSteps || steps.some((s, j) => s < 2 || s > SCALE_LIMITS.maxPlayers || (j && s <= steps[j - 1])))
        throw new Error(`Steps must increase, 2..${SCALE_LIMITS.maxPlayers}, at most ${SCALE_LIMITS.maxSteps}`);
      options.steps = steps;
    } else if (numeric[arg]) {
      const [name, min, max] = numeric[arg];
      if (!/^\d+$/.test(value) || Number(value) < min || Number(value) > max) throw new Error(`${arg} must be ${min}..${max}`);
      options[name] = Number(value);
    } else if (text[arg]) {
      if (arg === '--label' && !/^[a-z0-9-]{1,40}$/.test(value)) throw new Error('Label must be lowercase a-z, 0-9, -');
      // A saved installDist copy under .artifacts/dist/<name>, for before/after comparisons of the same owned server.
      if (arg === '--server-dist' && !/^[a-z0-9][a-z0-9._-]{0,60}$/.test(value)) throw new Error('Server dist must be a name under .artifacts/dist');
      options[text[arg]] = value;
    } else throw new Error('Unsupported or missing load-test option');
  }
  return options;
}

/** Redis settings point only at a loopback container this machine owns; URLs live in local files. */
export function validateRedisSettings(settings, urls) {
  if (typeof settings?.container !== 'string' || !/^[a-z0-9][a-z0-9_.-]{0,62}$/.test(settings.container) ||
      typeof settings.rateLimitUrlFile !== 'string' || typeof settings.presenceUrlFile !== 'string' ||
      typeof settings.monitorPasswordFile !== 'string') throw new Error('Invalid local Redis settings');
  for (const raw of urls) {
    let url; try { url = new URL(raw); } catch { throw new Error('Only a loopback Redis is allowed'); }
    if (url.protocol !== 'redis:' || url.hostname !== '127.0.0.1' || !url.port || url.pathname.length > 1 || url.search || url.hash)
      throw new Error('Only a loopback Redis is allowed');
  }
}

/** Deterministic private source address for synthetic player i (sent as X-Forwarded-For to the owned server). */
export function syntheticAddress(index) {
  if (!Number.isInteger(index) || index < 0 || index >= 0xfffffe) throw new Error('Invalid synthetic player index');
  const n = index + 1;
  return `10.${(n >> 16) & 255}.${(n >> 8) & 255}.${n & 255}`;
}

// Log-bucketed latency histogram: ~2% relative error, mergeable across worker threads.
const HISTOGRAM_BASE = Math.log(1.02), HISTOGRAM_FLOOR_MS = 0.05;
export function newHistogram() { return { counts: {}, total: 0, failures: 0, errors: {}, maxMs: 0 }; }
export function recordSample(histogram, ms, ok = true, code) {
  const bucket = Math.max(0, Math.ceil(Math.log(Math.max(ms, HISTOGRAM_FLOOR_MS) / HISTOGRAM_FLOOR_MS) / HISTOGRAM_BASE));
  histogram.counts[bucket] = (histogram.counts[bucket] || 0) + 1;
  histogram.total++; histogram.maxMs = Math.max(histogram.maxMs, ms);
  if (!ok) { histogram.failures++; histogram.errors[code] = (histogram.errors[code] || 0) + 1; }
}
export function mergeHistogram(into, from) {
  for (const [bucket, count] of Object.entries(from.counts)) into.counts[bucket] = (into.counts[bucket] || 0) + count;
  into.total += from.total; into.failures += from.failures; into.maxMs = Math.max(into.maxMs, from.maxMs);
  for (const [code, count] of Object.entries(from.errors)) into.errors[code] = (into.errors[code] || 0) + count;
  return into;
}
export function summarizeHistogram(histogram, seconds) {
  const buckets = Object.keys(histogram.counts).map(Number).sort((a, b) => a - b);
  const percentile = p => {
    if (!histogram.total) return null;
    const rank = Math.ceil(p * histogram.total); let seen = 0;
    for (const bucket of buckets) {
      seen += histogram.counts[bucket];
      if (seen >= rank) return Math.round(Math.min(HISTOGRAM_FLOOR_MS * Math.exp(bucket * HISTOGRAM_BASE), histogram.maxMs) * 100) / 100;
    }
    return null;
  };
  return { attempts: histogram.total, failures: histogram.failures,
    errorRatePercent: histogram.total ? Math.round(histogram.failures / histogram.total * 10000) / 100 : 0,
    perSecond: Math.round(histogram.total / seconds * 100) / 100,
    p50Ms: percentile(0.5), p95Ms: percentile(0.95), p99Ms: percentile(0.99),
    maxMs: Math.round(histogram.maxMs * 100) / 100, errors: histogram.errors };
}
