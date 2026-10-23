import { test } from 'node:test';
import assert from 'node:assert/strict';
import { finishModerationAcceptance } from '../../e2e/support/admin-moderation-cleanup.mjs';

function fixture(overrides = {}) {
  const calls = [], evidence = { status: 'PASS' }; let recorded;
  const operation = name => async () => { calls.push(name); };
  const options = {
    evidence,
    closeSockets: operation('sockets'), closeContext: operation('context'), closeBrowser: operation('browser'),
    removePassword: operation('password'), stopBackend: operation('backend'), dropSchema: operation('schema'),
    writeEvidence: async value => { calls.push('evidence'); recorded = structuredClone(value); },
    ...overrides,
  };
  return { calls, evidence, options, get recorded() { return recorded; } };
}

test('browser close rejection still removes password stops backend drops schema and records FAIL', async () => {
  const rejected = Error('Injected browser close failure');
  const f = fixture(); f.options.closeBrowser = async () => { f.calls.push('browser'); throw rejected; };
  await assert.rejects(finishModerationAcceptance(f.options), error => error instanceof AggregateError && error.errors.includes(rejected));
  assert.deepEqual(f.calls, ['sockets', 'context', 'browser', 'password', 'backend', 'schema', 'evidence']);
  assert.equal(f.recorded.status, 'FAIL'); assert.equal(f.recorded.cleanup.browser, 'failed');
  assert.equal(f.recorded.cleanup.backend, 'done'); assert.equal(f.recorded.cleanup.schema, 'done');
});

test('context rejection and password unlink failure are preserved while backend cleanup still runs', async () => {
  const contextFailure = Error('Injected context close failure');
  const unlinkFailure = Object.assign(Error('Injected password unlink failure'), { code: 'EACCES' });
  const f = fixture();
  f.options.closeContext = async () => { f.calls.push('context'); throw contextFailure; };
  f.options.removePassword = async () => { f.calls.push('password'); throw unlinkFailure; };
  await assert.rejects(finishModerationAcceptance(f.options), error => error instanceof AggregateError && error.errors.includes(contextFailure) && error.errors.includes(unlinkFailure));
  assert.deepEqual(f.calls, ['sockets', 'context', 'browser', 'password', 'backend', 'schema', 'evidence']);
  assert.equal(f.recorded.status, 'FAIL'); assert.equal(f.recorded.cleanup.password, 'failed');
  assert.equal(f.recorded.cleanup.backend, 'done'); assert.equal(f.recorded.cleanup.schema, 'done');
});

test('failed backend stop retains schema and records failure rather than successful cleanup', async () => {
  const failure = Error('Injected backend stop failure'); const f = fixture();
  f.options.stopBackend = async () => { f.calls.push('backend'); throw failure; };
  await assert.rejects(finishModerationAcceptance(f.options), error => error instanceof AggregateError && error.errors.includes(failure));
  assert.equal(f.calls.includes('schema'), false); assert.equal(f.recorded.status, 'FAIL');
  assert.equal(f.recorded.cleanup.schema, 'retained-backend-not-stopped');
});

test('an absent password is safe while the original acceptance error remains a failing result', async () => {
  const original = Error('Injected bootstrap failure'); const f = fixture({ failure: original });
  f.options.removePassword = async () => { f.calls.push('password'); throw Object.assign(Error('Absent'), { code: 'ENOENT' }); };
  await assert.rejects(finishModerationAcceptance(f.options), error => error instanceof AggregateError && error.errors.includes(original) && error.errors.length === 1);
  assert.equal(f.recorded.status, 'FAIL'); assert.equal(f.recorded.cleanup.password, 'already-absent');
  assert.equal(f.recorded.cleanup.schema, 'done');
});

test('successful acceptance becomes PASS only after all cleanup and evidence succeed', async () => {
  const f = fixture(); await finishModerationAcceptance(f.options);
  assert.equal(f.recorded.status, 'PASS');
  assert.deepEqual(f.recorded.cleanup, { sockets: 'done', context: 'done', browser: 'done', password: 'done', backend: 'done', schema: 'done' });
});

test('evidence write failure preserves both it and the original acceptance error', async () => {
  const original = Error('Injected acceptance failure'), writeFailure = Error('Injected evidence write failure');
  const f = fixture({ failure: original }); f.options.writeEvidence = async () => { f.calls.push('evidence'); throw writeFailure; };
  await assert.rejects(finishModerationAcceptance(f.options), error => error instanceof AggregateError && error.errors.includes(original) && error.errors.includes(writeFailure));
  assert.equal(f.evidence.status, 'FAIL'); assert.equal(f.calls.includes('schema'), true);
});
