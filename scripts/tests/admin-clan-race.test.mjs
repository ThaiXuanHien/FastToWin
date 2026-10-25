import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { races } from '../../e2e/support/admin-clan-race-fixture.mjs';
import { raceConfig } from '../../e2e/support/admin-clan-race-fixture.mjs';
const require = createRequire(new URL('../../e2e/package.json', import.meta.url));
const { chromium } = require('playwright');
const source = await readFile(new URL('../../server/src/main/resources/admin/admin-clan-race.js', import.meta.url), 'utf8');
const { validateRace, raceWeek, createRaceSubmission } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
const now = Date.parse('2026-10-25T10:00:00+07:00');

test('rate and reward boundaries preserve tiny positive points and reject overflow and non-monotonic economics', () => {
  assert.deepEqual(validateRace('2026-10-26', { ...raceConfig(), goldRate: '0.000001' }, now), {});
  for (const value of ['0', '-1', '1e2', ' 0.1', '0.0000001', '1000000000000000000000000000000']) assert.ok(validateRace('2026-10-26', { ...raceConfig(), goldRate: value }, now).goldRate, value);
  assert.ok(validateRace('2026-10-26', { ...raceConfig(), goldRate: '1000000000000000000000000000000' }, now).goldRate);
  assert.deepEqual(validateRace('2026-10-26', { ...raceConfig(), goldRate: '999999999999999999999999999999.999999' }, now), {});
  assert.ok(validateRace('2026-10-26', { ...raceConfig(), rewards: [{ xp: 2147483648, gold: 300, gems: 3 }, ...raceConfig().rewards.slice(1)] }, now)['0-xp']);
  assert.ok(validateRace('2026-10-26', { ...raceConfig(), rewards: [{ xp: 30, gold: 199, gems: 3 }, ...raceConfig().rewards.slice(1)] }, now)['1-xp']);
  assert.ok(validateRace('2026-10-26', { ...raceConfig(), rewards: [raceConfig().rewards[0], raceConfig().rewards[0], raceConfig().rewards[2]] }, now)['1-xp']);
  assert.ok(validateRace('2026-10-26', { ...raceConfig(), rewards: [...raceConfig().rewards.slice(0, 2), { xp: 0, gold: 0, gems: 0 }] }, now)['2-xp']);
});
test('Bangkok Monday boundary and valid future date are independent of host timezone', () => {
  assert.equal(raceWeek(Date.parse('2026-10-25T16:59:59.999Z')), '2026-10-19');
  assert.equal(raceWeek(Date.parse('2026-10-25T17:00:00Z')), '2026-10-26');
  for (const date of ['2026-10-19', '2026-10-25', '2026-02-30', '1969-12-29']) assert.ok(validateRace(date, raceConfig(), now).weekStart);
});
test('submission checks live role, view, duplicate submit and conflict before issuing writes', async () => {
  let role = 'OWNER', current = true, resolve; const calls = [];
  const submission = createRaceSubmission({ role: () => role, current: () => current, ownerOnly: true, send: body => { calls.push(body); return new Promise(done => resolve = done); } });
  const pending = submission.submit({ reason: 'Publish', expectedVersion: 1 }); await assert.rejects(submission.submit({}), /xử lý/); resolve({}); await pending;
  role = 'OPERATOR'; await assert.rejects(submission.submit({}), /quyền/); role = 'OWNER'; current = false; await assert.rejects(submission.submit({}), /hết hiệu lực/); assert.equal(calls.length, 1);
  const conflict = createRaceSubmission({ role: () => 'OWNER', current: () => true, send: async () => { throw Object.assign(new Error('changed'), { status: 409 }); } });
  await assert.rejects(conflict.submit({})); await assert.rejects(conflict.submit({}), /Tải lại/);
});

// The actual browser DOM and production admin modules produce the asserted JSON.
test('Owner creates, previews and publishes exact future race economics through native DOM', async () => {
  const fixture = { handlers: new Map(), calls: [] }; races(fixture);
  const server = createServer(async (req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const send = (status, body) => { res.writeHead(status, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)); };
    if (!url.pathname.startsWith('/admin/api/')) {
      const name = url.pathname === '/admin/' ? 'index.html' : url.pathname.split('/').at(-1);
      try { const body = await readFile(new URL('../../server/src/main/resources/admin/' + name, import.meta.url)); res.writeHead(200, { 'Content-Type': name.endsWith('.html') ? 'text/html' : name.endsWith('.css') ? 'text/css' : 'application/javascript' }); res.end(body); } catch { res.writeHead(404); res.end(); } return;
    }
    let raw = ''; for await (const chunk of req) raw += chunk;
    const call = { route: url.pathname, method: req.method, query: url.searchParams, headers: req.headers, body: raw ? JSON.parse(raw) : null }; fixture.calls.push(call);
    if (fixture.handlers.get(call.route)?.(call, send)) return;
    if (call.route.endsWith('/session') || call.route.endsWith('/login')) send(200, { admin: { id: 'owner', role: 'OWNER', email: 'owner@example.com', active: true }, csrfToken: 'dom-csrf' });
    else if (call.route.endsWith('/users')) send(200, { items: [], page: 0, pageSize: 25, total: 0 });
    else send(404, { message: 'missing fixture' });
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage(); await page.goto(`http://127.0.0.1:${server.address().port}/admin/`);
    const nav = page.getByRole('button', { name: 'Đua top bang', exact: true });
    await page.getByRole('heading', { name: 'Người chơi', exact: true }).waitFor();
    assert.equal(await nav.count(), 1, 'race destination is registered in the actual admin DOM');
    await nav.click(); await page.getByRole('button', { name: 'Tạo tuần đua', exact: true }).click();
    for (const [label, value] of [['Tuần bắt đầu (thứ Hai)', '2099-01-05'], ['Điểm / 1 Vàng', '0.01'], ['Điểm / 1 Gem', '10'], ['Lý do thay đổi', 'Lịch tương lai']]) await page.getByLabel(label, { exact: true }).fill(value);
    for (const [rank, values] of [[1, ['30', '300', '3']], [2, ['20', '200', '2']], [3, ['10', '100', '1']]]) for (const [index, asset] of ['XP', 'Vàng', 'Gem'].entries()) await page.getByLabel(`Top ${rank} · ${asset}`, { exact: true }).fill(values[index]);
    await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
    await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
    await page.getByRole('button', { name: 'Tiếp tục phát hành', exact: true }).click();
    await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Duyệt tương lai');
    await page.getByRole('button', { name: 'Phát hành tuần đua', exact: true }).click();
    await page.getByText('Đã phát hành; cấu hình tuần được đóng băng.', { exact: true }).waitFor();
    const create = fixture.calls.find(c => c.route === '/admin/api/clan-race' && c.method === 'POST');
    assert.deepEqual(create.body.config, { goldRate: '0.01', gemRate: '10', rewards: [{ xp: 30, gold: 300, gems: 3 }, { xp: 20, gold: 200, gems: 2 }, { xp: 10, gold: 100, gems: 1 }] });
    assert.equal(create.body.weekStart, '2099-01-05'); assert.equal(create.body.expectedVersion, 0); assert.equal(create.headers['x-admin-csrf'], 'dom-csrf'); assert.ok(create.body.requestId);
    const publish = fixture.calls.find(c => c.route.endsWith('/publish')); assert.equal(publish.body.expectedVersion, 1); assert.equal(publish.body.reason, 'Duyệt tương lai'); assert.notEqual(publish.body.requestId, create.body.requestId);
  } finally { await browser.close(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});
