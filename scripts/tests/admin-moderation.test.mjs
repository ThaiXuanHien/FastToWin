import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const source = await readFile(new URL('../../server/src/main/resources/admin/admin-moderation.js', import.meta.url), 'utf8');
const { createModerationSubmission } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
const versioned = { expectedVersion: 3, reason: 'Kiểm tra nội dung', status: 'RESOLVED', removeContent: true };

test('lost mutation response retries with same ID; edited payload gets new ID', async () => {
  const bodies = []; let next = 0;
  const submission = createModerationSubmission({ role: () => 'OPERATOR', current: () => true, uuid: () => 'request-' + ++next,
    send: async body => { bodies.push(body); if (bodies.length === 1) throw new Error('Connection lost'); return { ok: true }; } });
  await assert.rejects(submission.submit(versioned));
  await submission.submit(versioned);
  await submission.submit({ ...versioned, reason: 'Nội dung khác' });
  assert.equal(bodies[0].requestId, bodies[1].requestId);
  assert.notEqual(bodies[1].requestId, bodies[2].requestId);
});
test('SUPPORT can triage but cannot remove content or restrict users', async () => {
  const calls = [];
  const submission = createModerationSubmission({ role: () => 'SUPPORT', current: () => true, send: async body => calls.push(body) });
  await assert.rejects(submission.submit(versioned), /quyền/);
  await assert.rejects(submission.submit({ expectedVersion: 0, reason: 'review', blocked: true }), /quyền/);
  await submission.submit({ ...versioned, status: 'REVIEWING', removeContent: false });
  assert.equal(calls.length, 1);
});
test('stale-version conflict requires reloading instead of retrying destructive action', async () => {
  let calls = 0;
  const submission = createModerationSubmission({ role: () => 'OWNER', current: () => true,
    send: async () => { calls++; throw Object.assign(new Error('Conflict'), { status: 409 }); } });
  await assert.rejects(submission.submit(versioned));
  await assert.rejects(submission.submit(versioned), /Tải lại/);
  assert.equal(calls, 1);
});
test('double-submit and obsolete dialog cannot issue extra mutations', async () => {
  let finish, current = true, calls = 0;
  const submission = createModerationSubmission({ role: () => 'OWNER', current: () => current,
    send: () => { calls++; return new Promise(resolve => { finish = resolve; }); } });
  const pending = submission.submit(versioned);
  await assert.rejects(submission.submit(versioned), /xử lý/);
  finish({ ok: true }); await pending;
  current = false;
  await assert.rejects(submission.submit(versioned));
  assert.equal(calls, 1);
});
