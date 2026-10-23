// Opt-in, owned loopback HTTP -> production admin assets -> Ktor -> PostgreSQL.
// No provider credentials, traces, production URLs or pre-existing schemas.
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import { createWriteStream } from 'node:fs';
import { readFile, writeFile, mkdir, unlink } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID, createHash } from 'node:crypto';
import { chromium, expect } from 'playwright/test';
import { openLocalPostgres } from './local-postgres.mjs';

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const artifact = resolve(process.env.UGC_E2E_ARTIFACT_DIR || join(repo, '.artifacts/admin-moderation-live'));
const url = 'http://127.0.0.1:18084';
const psql = process.env.E2E_PSQL || 'psql';
assert.ok(process.env.E2E_POSTGRES_SETTINGS, 'E2E_POSTGRES_SETTINGS required; no skipped live coverage');
assert.ok(process.env.JAVA_HOME, 'JAVA_HOME required');
const java = join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
await mkdir(artifact, { recursive: true });
// Binding the port guards against accidentally sharing an existing backend.
await new Promise((yes, no) => { const probe = createServer(); probe.once('error', () => no(Error('Owned backend port18084 occupied'))); probe.listen(18084, '127.0.0.1', () => probe.close(yes)); });
const database = await openLocalPostgres(process.env.E2E_POSTGRES_SETTINGS, psql);
const schema = new URL(database.serverEnvironment.DATABASE_URL.slice(5)).searchParams.get('currentSchema');
assert.match(schema, /^webe2e_[a-f0-9]{32}$/);
const environment = {
  ...Object.fromEntries(['PATH', 'Path', 'SystemRoot', 'WINDIR', 'TEMP', 'TMP', 'JAVA_TOOL_OPTIONS'].filter(k => process.env[k]).map(k => [k, process.env[k]])),
  ...database.serverEnvironment, FASTTOWIN_ENV: 'dev', SERVER_HOST: '127.0.0.1', PORT: '18084',
  FASTTOWIN_ADMIN_ENABLED: 'true', FASTTOWIN_ADMIN_ORIGIN: url, FASTTOWIN_WEB_ORIGINS: url,
  FASTTOWIN_CONTENT_TERMS_ENFORCED: 'false', FASTTOWIN_AVATAR_STORAGE: 'database',
};
const passwordFile = join(artifact, 'owned-owner-password.txt');
const ownerPassword = randomUUID() + 'Aa9!';
const email = `ugc-owner-${randomUUID()}@example.invalid`;
const lib = join(repo, 'server/build/install/server/lib/*');
const evidence = { status: 'RUNNING', browserFlows: 1, traces: 'off', provider: 'none', database: 'owned loopback PostgreSQL/UUID schema', schema, stages: [] };
let backend, browser, context;
const sockets = [];
function run(executable, args, env = environment, input) {
  return new Promise((yes, no) => {
    const child = spawn(executable, args, { cwd: repo, env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let stdout = ''; child.stdout.on('data', b => stdout += b); child.stderr.resume();
    const timer = setTimeout(() => { child.kill(); no(Error('Owned subprocess timed out')); }, 30_000);
    child.on('error', () => { clearTimeout(timer); no(Error('Owned subprocess failed to start')); });
    child.on('exit', code => { clearTimeout(timer); code === 0 ? yes(stdout.trim()) : no(Error('Owned subprocess failed: exit' + code)); });
    child.stdin.on('error', () => {}); child.stdin.end(input);
  });
}
async function start() {
  const log = createWriteStream(join(artifact, 'backend-' + (evidence.stages.includes('browser-removal') ? 'restart' : 'initial') + '.log'));
  backend = spawn(java, ['-cp', lib, 'com.hienthai.fastowin.server.MainKt'], { cwd: repo, env: environment, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  backend.stdout.pipe(log, { end: false }); backend.stderr.pipe(log, { end: false }); backend.once('exit', () => log.end());
  for (let i = 0; i < 150; i++) {
    if (backend.exitCode !== null) throw Error('Owned backend exited before readiness');
    const response = await fetch(url + '/health', { signal: AbortSignal.timeout(500) }).catch(() => null);
    if (response?.ok) return;
    await new Promise(r => setTimeout(r, 200));
  }
  throw Error('Owned backend readiness timeout');
}
async function stop() {
  if (!backend || backend.exitCode !== null || backend.signalCode !== null) return;
  await new Promise((yes, no) => {
    const timer = setTimeout(() => no(Error('Owned backend did not stop; schema retained')), 10_000);
    backend.once('exit', () => { clearTimeout(timer); yes(); }); backend.kill();
  });
}
async function guest(name, resumeToken) {
  const ws = new WebSocket(url.replace('http:', 'ws:') + '/game'); sockets.push(ws);
  const queue = [], waiters = [];
  ws.addEventListener('message', event => { const value = JSON.parse(event.data); const waiter = waiters.shift(); waiter ? waiter(value) : queue.push(value); });
  await new Promise((yes, no) => { ws.addEventListener('open', yes, { once: true }); ws.addEventListener('error', () => no(Error('Owned websocket open failed')), { once: true }); });
  async function next(type) {
    for (let i = 0; i < 50; i++) {
      const value = queue.length ? queue.shift() : await new Promise((yes, no) => {
        const timer = setTimeout(() => { const index = waiters.indexOf(receive); if (index >= 0) waiters.splice(index, 1); no(Error('Owned websocket message timeout: ' + type)); }, 10_000);
        function receive(value) { clearTimeout(timer); yes(value); } waiters.push(receive);
      });
      assert.notEqual(value.type, 'error', 'Unexpected owned websocket error ' + value.code);
      if (value.type === type) return value;
    }
    throw Error('Owned websocket expected type missing: ' + type);
  }
  const send = body => ws.send(JSON.stringify(body));
  send({ type: 'connect_guest', displayName: name, protocolVersion: 44, ...(resumeToken ? { resumeToken } : { acceptedContentTermsVersion: '2026-10-20' }) });
  const session = await next('session_ready'); await next('room_list');
  assert.match(session.playerId, /^[a-f0-9-]{36}$/);
  return { send, next, id: session.playerId, resumeToken: session.resumeToken };
}
async function sql(query) {
  return run(psql, ['-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', '15432', '-d', 'admin_test', '-U', environment.DATABASE_USER], { ...environment, PGPASSWORD: environment.DATABASE_PASSWORD }, query);
}
async function checked(response) { assert.equal(response.status(), 200, 'Unexpected owned HTTP response'); return response.json(); }
try {
  await writeFile(passwordFile, ownerPassword);
  await run(java, ['-cp', lib, 'com.hienthai.fastowin.server.admin.AdminBootstrapKt'], { ...environment, FASTTOWIN_ADMIN_BOOTSTRAP_EMAIL: email, FASTTOWIN_ADMIN_BOOTSTRAP_PASSWORD_FILE: passwordFile });
  await unlink(passwordFile);
  await start();
  browser = await chromium.launch(); context = await browser.newContext({ viewport: { width: 1280, height: 900 } }); const page = await context.newPage();
  for (const name of ['admin.js', 'admin.css', 'admin-moderation.js']) {
    const response = await context.request.get(url + '/admin/' + name); assert.equal(response.status(), 200);
    const hash = value => createHash('sha256').update(value).digest('hex');
    assert.equal(hash(await response.body()), hash(await readFile(join(repo, 'server/src/main/resources/admin', name))));
  }
  const owner = await guest('LOCAL avatar target'), reporter = await guest('LOCAL report sender');
  const image = await page.evaluate(() => { const c = document.createElement('canvas'); c.width = c.height = 32; const g = c.getContext('2d'); g.fillStyle = '#2463eb'; g.fillRect(0, 0, 32, 32); return c.toDataURL('image/png'); });
  owner.send({ type: 'update_avatar', base64Data: image }); await owner.next('profile_data');
  reporter.send({ type: 'update_avatar', base64Data: image }); await reporter.next('profile_data');
  const profilesQuery = `SELECT p.user_id,p.display_name,s.gold,s.gems,p.avatar_revision FROM ${schema}.profiles p LEFT JOIN ${schema}.player_stats s USING(user_id) ORDER BY p.user_id;`;
  const profilesBefore = await sql(profilesQuery);
  const before = await context.request.get(url + '/api/avatar/' + owner.id); assert.equal(before.status(), 200); assert.match(before.headers()['content-type'], /image\/jpeg/);
  reporter.send({ type: 'get_content_terms' }); const terms = await reporter.next('content_terms_data'); assert.equal(terms.status.enforced, false);
  const request = { targetType: 'PLAYER_AVATAR', targetId: owner.id, reason: 'OTHER', requestId: randomUUID() };
  reporter.send({ type: 'report_content', request }); const result = await reporter.next('content_report_result'); assert.ok(result.receipt); const reportId = result.receipt.reportId;
  assert.match(reportId, /^[a-f0-9-]{36}$/); evidence.stages.push('synthetic-socket-report-receipt');
  await page.goto(url + '/admin/'); await page.getByLabel('Email quản trị', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(ownerPassword); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Kiểm duyệt', exact: true }).click(); await page.getByRole('button', { name: 'Xem báo cáo ' + reportId }).click();
  await expect(page.getByRole('dialog', { name: 'Chi tiết báo cáo' })).toBeVisible();
  await page.getByRole('button', { name: 'Xem ảnh hiện tại', exact: true }).click(); await expect(page.locator('dialog img')).toBeVisible(); await expect.poll(() => page.locator('dialog img').evaluate(image => image.complete && image.naturalWidth > 0)).toBe(true);
  for (const width of [375, 1280]) {
    await page.setViewportSize({ width, height: 900 }); await page.getByRole('dialog').locator(':scope > .stack').evaluate(body => body.scrollTop = 0);
    await page.screenshot({ path: join(artifact, `avatar-detail-${width}.png`) });
    const resolve = page.getByRole('button', { name: 'Xử lý báo cáo', exact: true }); await resolve.scrollIntoViewIfNeeded(); await expect(resolve).toBeInViewport();
    await page.screenshot({ path: join(artifact, `avatar-detail-footer-${width}.png`) }); assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  }
  await page.getByRole('button', { name: 'Xử lý báo cáo', exact: true }).click(); await page.getByLabel('Gỡ nội dung đã được báo cáo').check(); await page.getByLabel('Lý do xử lý nội bộ', { exact: true }).fill('Owned local synthetic moderation acceptance');
  const resolved = page.waitForResponse(r => r.url().endsWith('/reports/' + reportId + '/resolve') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Lưu xử lý', exact: true }).click(); const view = await checked(await resolved); assert.equal(view.report.status, 'RESOLVED'); assert.equal(view.report.version, 2); await expect(page.getByRole('dialog')).toHaveCount(0); evidence.stages.push('browser-removal');
  const invalidated = await reporter.next('content_moderation_changed'); assert.equal(invalidated.targetType, 'PLAYER_AVATAR'); assert.equal(invalidated.targetId, owner.id);
  async function absentAvatar() { const response = await context.request.get(url + '/api/avatar/' + owner.id); assert.equal(response.status(), 404); assert.equal(response.headers()['cache-control'], 'no-store, max-age=0'); assert.equal(response.headers()['x-content-type-options'], 'nosniff'); }
  await absentAvatar();
  const db = await sql(`SELECT r.status || ':' || r.version || ':' || count(s.*) FROM ${schema}.ugc_reports r JOIN ${schema}.ugc_content_suppressions s ON s.target_type=r.target_type AND s.target_id=r.target_id AND s.revision=r.target_revision WHERE r.id='${reportId}' GROUP BY r.status,r.version;`);
  assert.equal(db, 'RESOLVED:2:1'); assert.equal(await sql(profilesQuery), profilesBefore); assert.equal((await context.request.get(url + '/api/avatar/' + reporter.id)).status(), 200); evidence.stages.push('profile-wallets-and-unrelated-avatar-unchanged'); evidence.stages.push('sql-exact-revision-persisted', 'socket-invalidation', 'avatar-http404');
  sockets.forEach(ws => ws.close()); await stop(); await start();
  const resumed = await guest('LOCAL report sender', reporter.resumeToken); assert.equal(resumed.id, reporter.id); resumed.send({ type: 'get_content_terms' }); assert.equal((await resumed.next('content_terms_data')).status.acceptedVersion, '2026-10-20'); evidence.stages.push('guest-resume-after-backend-restart-consent-persisted');
  await page.reload(); await page.getByRole('button', { name: 'Kiểm duyệt', exact: true }).click(); await page.getByLabel('Trạng thái báo cáo').selectOption('RESOLVED'); await page.getByRole('button', { name: 'Xem báo cáo ' + reportId }).click(); await expect(page.getByRole('dialog').getByText('Đã xử lý', { exact: true })).toBeVisible(); await absentAvatar(); evidence.stages.push('backend-restart-browser-reload-persistence');
  assert.deepEqual(await page.evaluate(() => [localStorage.length, sessionStorage.length]), [0, 0]); evidence.status = 'PASS';
} finally {
  sockets.forEach(ws => ws.close()); await context?.close(); await browser?.close();
  await unlink(passwordFile).catch(() => {});
  await stop(); await database.close(); evidence.cleanup = 'owned backend stopped; UUID schema dropped; owner password file removed';
  await writeFile(join(artifact, 'acceptance.json'), JSON.stringify(evidence, null, 2));
}
console.log(JSON.stringify(evidence));
