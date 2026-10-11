import test from 'node:test';
import assert from 'node:assert/strict';
import { parseOptions, summarize, validateDatabaseSettings } from '../lib/load-test-utils.mjs';

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
