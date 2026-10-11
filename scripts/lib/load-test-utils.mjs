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
