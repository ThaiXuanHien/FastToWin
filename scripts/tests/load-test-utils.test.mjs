import test from 'node:test';
import assert from 'node:assert/strict';
import { parseOptions, summarize, validateDatabaseSettings, parseScaleOptions, validateRedisSettings, syntheticAddress,
  newHistogram, recordSample, mergeHistogram, summarizeHistogram } from '../lib/load-test-utils.mjs';

test('load test refuses external targets and unbounded workload', () => {
  for (const args of [['--url', 'https://fasttowin.up.railway.app'], ['--env', 'prod'],
    ['--pairs', '0'], ['--pairs', '17'], ['--rounds', '3'], ['--pairs', '1.5'],
    ['--pairs'], ['--unknown'], ['--target=http://127.0.0.1:8080']]) {
    assert.throws(() => parseOptions(args));
  }
  assert.deepEqual(parseOptions([]), { pairs: 4, rounds: 1, postgresSettings: null, psql: 'psql' });
  assert.equal(parseOptions(['--pairs', '2', '--rounds', '2']).pairs, 2);
});

test('database accepts only known local test DB, without embedded credentials or schema', () => {
  const good = { url: 'jdbc:postgresql://127.0.0.1:15432/admin_test', user: 'fasttowin', passwordFile: '/local/password' };
  assert.doesNotThrow(() => validateDatabaseSettings(good));
  for (const url of ['jdbc:postgresql://prod:5432/railway', 'jdbc:postgresql://127.0.0.1:15432/railway',
    good.url + '?currentSchema=public', good.url + '?user=owner']) {
    assert.throws(() => validateDatabaseSettings({ ...good, url }));
  }
  assert.throws(() => validateDatabaseSettings({ ...good, passwordFile: undefined }));
});

test('percentiles count failures and keep all latency samples', () => {
  const result = summarize([{ ms: 1, ok: true }, { ms: 2, ok: false, code: 'RATE_LIMITED' },
    { ms: 3, ok: true }, { ms: 100, ok: true }], 2);
  assert.deepEqual(result, { attempts: 4, successes: 3, failures: 1, attemptsPerSecond: 2,
    p50Ms: 2, p95Ms: 100, p99Ms: 100, errors: { RATE_LIMITED: 1 } });
  assert.equal(summarize([], 1).p95Ms, null);
});

test('scale test refuses targets, unbounded steps and unsafe names', () => {
  for (const args of [['--url', 'https://fasttowin.up.railway.app'], ['--target', 'http://127.0.0.1:8080'],
    ['--steps', '0'], ['--steps', '500,250'], ['--steps', '10001'], ['--steps', '1,2,3,4,5,6,7,8,9'],
    ['--hold-seconds', '5'], ['--ramp-per-second', '501'], ['--select-interval-ms', '50'], ['--steps'],
    ['--label', 'Prod'], ['--server-dist', '../server'], ['--server-dist', 'C:/prod'], ['--jfr', '--jfr'], ['--pool-size', '65']]) {
    assert.throws(() => parseScaleOptions(args), undefined, args.join(' '));
  }
  const parsed = parseScaleOptions(['--steps', '250,500,1000', '--reconnect-storm', '--server-dist', 'master-cfaf5fb7', '--label', 'ramp-1']);
  assert.deepEqual(parsed.steps, [250, 500, 1000]);
  assert.equal(parsed.reconnectStorm, true); assert.equal(parsed.poolSize, 4); assert.equal(parsed.serverDist, 'master-cfaf5fb7');
});

test('redis settings accept only a loopback container URL', () => {
  const settings = { container: 'ftw-loadtest-redis', rateLimitUrlFile: 'a', presenceUrlFile: 'b', monitorPasswordFile: 'c' };
  assert.doesNotThrow(() => validateRedisSettings(settings, ['redis://ftw_app:x@127.0.0.1:16379']));
  for (const url of ['rediss://u:p@127.0.0.1:16379', 'redis://u:p@redis.railway.internal:6379', 'redis://u:p@127.0.0.1',
    'redis://u:p@127.0.0.1:16379/0?x=1', 'not a url']) assert.throws(() => validateRedisSettings(settings, [url]), undefined, url);
  assert.throws(() => validateRedisSettings({ ...settings, container: 'x; rm -rf /' }, ['redis://u:p@127.0.0.1:16379']));
});

test('synthetic addresses are private, distinct and bounded', () => {
  assert.equal(syntheticAddress(0), '10.0.0.1');
  assert.equal(syntheticAddress(255), '10.0.1.0');
  assert.equal(new Set(Array.from({ length: 5000 }, (_, i) => syntheticAddress(i))).size, 5000);
  assert.throws(() => syntheticAddress(-1)); assert.throws(() => syntheticAddress(0xfffffe));
});

test('histograms merge across workers and keep percentiles within 2 percent', () => {
  const a = newHistogram(), b = newHistogram();
  for (let i = 1; i <= 1000; i++) recordSample(i % 2 ? a : b, i);
  recordSample(b, 5, false, 'SOCKET_EVENT_TIMEOUT');
  const merged = summarizeHistogram(mergeHistogram(mergeHistogram(newHistogram(), a), b), 10);
  assert.equal(merged.attempts, 1001); assert.equal(merged.failures, 1); assert.equal(merged.perSecond, 100.1);
  assert.deepEqual(merged.errors, { SOCKET_EVENT_TIMEOUT: 1 });
  for (const [p, expected] of [['p50Ms', 500], ['p95Ms', 950], ['p99Ms', 990]]) assert.ok(Math.abs(merged[p] - expected) / expected < 0.025, p);
  assert.equal(merged.maxMs, 1000); assert.equal(summarizeHistogram(newHistogram(), 1).p95Ms, null);
});
