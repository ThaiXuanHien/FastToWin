// Separate owned loopback/schema acceptance: no production provider, credentials or traces.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { randomUUID, createHash } from 'node:crypto';
import assert from 'node:assert/strict';
import { chromium, request } from 'playwright';
const artifact = fileURLToPath(new URL('../../.artifacts/admin-announcements-delivery/task4/', import.meta.url));
const settings = JSON.parse((await readFile(process.env.ADMIN_ANNOUNCEMENT_LIVE_SETTINGS, 'utf8')).replace(/^\uFEFF/, ''));
assert.equal(settings.url, 'http://127.0.0.1:18083'); assert.match(settings.schema, /^ann_task4_[0-9a-f]{32}$/);
assert.equal(settings.databaseUrl, 'jdbc:postgresql://127.0.0.1:15432/admin_ui?currentSchema=' + settings.schema);
const browser = await chromium.launch(), context = await browser.newContext(), page = await context.newPage(), http = await request.newContext({ baseURL: settings.url });
const evidence = { status: 'RUNNING', url: settings.url, schema: settings.schema, traces: 'off', provider: 'none', assets: {}, stages: [], announcementIds: [] }, accounts = [];
let headers;
const checked = async response => { assert.ok([200, 201].includes(response.status()), 'Unexpected local HTTP status ' + response.status()); return response.json(); };
const playerHeaders = auth => ({ Authorization: 'Bearer ' + auth.accessToken });
const adminGet = suffix => context.request.get(settings.url + '/admin/api' + suffix).then(checked);
const playerGet = (auth, suffix) => http.get('/api/announcements' + suffix, { headers: playerHeaders(auth) }).then(checked);
async function account(name) {
  const auth = await checked(await http.post('/auth/register', { data: { email: `ann-task4-${randomUUID()}@example.invalid`, password: 'Local-' + randomUUID() + '9!', displayName: name, devicePlatform: 'ANDROID' } })); accounts.push(auth);
  const verification = await checked(await http.post('/auth/email-verification/request', { data: { accessToken: auth.accessToken } })); assert.match(verification.devEmailVerificationCode || '', /^\d{6}$/);
  await checked(await http.post('/auth/email-verification/confirm', { data: { accessToken: auth.accessToken, verificationCode: verification.devEmailVerificationCode } }));
  const ws = new WebSocket(settings.url.replace('http:', 'ws:') + '/game');
  const ready = new Promise((resolve, reject) => { const timer = setTimeout(() => reject(new Error('Owned session handshake timed out')), 10000); ws.addEventListener('message', event => { const body = JSON.parse(event.data); if (body.type === 'session_ready') { clearTimeout(timer); resolve(body); } }); ws.addEventListener('error', reject); });
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); }); ws.send(JSON.stringify({ type: 'connect_account', accessToken: auth.accessToken, protocolVersion: 43 })); auth.id = (await ready).playerId; ws.close(); return auth;
}
async function draft(title, start, end, selected = null) {
  await page.getByRole('button', { name: 'Thông báo', exact: true }).click(); await page.getByRole('button', { name: 'Tạo thông báo', exact: true }).click();
  for (const [label, value] of [['Tiêu đề (VI)', title], ['Title (EN)', title + ' English'], ['Nội dung (VI)', 'Thông báo cục bộ. Không trao thưởng hoặc bật bảo trì.'], ['Body (EN)', 'Owned local announcement. No rewards or maintenance changes.'], ['Bắt đầu (UTC, bao gồm)', new Date(start).toISOString().slice(0, 23)], ['Kết thúc (UTC, không bao gồm)', new Date(end).toISOString().slice(0, 23)], ['Lý do bản nháp nội bộ', 'PRIVATE_LOCAL_DRAFT']]) await page.getByLabel(label, { exact: true }).fill(String(value));
  if (selected) { await page.getByLabel('Nhóm người nhận').selectOption('USER_IDS'); await page.getByLabel('UUID người chơi (mỗi dòng hoặc dấu phẩy)').fill(selected.join('\n')); }
  await page.getByLabel('Hiện banner trong ứng dụng', { exact: true }).check(); await assert.equal(await page.getByLabel('Gửi push tùy chọn').isChecked(), false);
  const created = page.waitForResponse(r => r.url().endsWith('/admin/api/announcements') && r.request().method() === 'POST'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); const row = await checked(await created); evidence.announcementIds.push(row.announcement.id); await page.getByRole('button', { name: 'Xem trước', exact: true }).waitFor(); return row;
}
async function publish(expected) {
  const previewResponse = page.waitForResponse(r => r.url().endsWith('/preview')); await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); const preview = await checked(await previewResponse); assert.equal(preview.recipientCount, expected); assert.equal(preview.pushConfigured, false);
  await page.getByRole('button', { name: 'Tiếp tục công bố' }).click(); await page.getByLabel('Lý do công bố nội bộ').fill('PRIVATE_LOCAL_PUBLISH');
  const publishResponse = page.waitForResponse(r => r.url().endsWith('/publish')); await page.getByRole('button', { name: 'Công bố thông báo' }).click(); const response = await publishResponse, row = await checked(response); assert.equal(row.announcement.status, 'PUBLISHED'); assert.equal(row.counts.frozen, expected); await page.getByRole('dialog').waitFor({ state: 'detached' }); return { row, body: response.request().postDataJSON() };
}
async function awaitItem(auth, id, state) {
  for (let attempt = 0; attempt < 120; attempt++) { const box = await playerGet(auth, '?page=0&pageSize=100'), item = box.items.find(i => i.announcement.id === id); if (item && (!state || item.announcement.state === state)) return item; await new Promise(resolve => setTimeout(resolve, 100)); }
  throw new Error('Owned durable announcement/state missing');
}
try {
  assert.equal((await http.get('/health')).status(), 200);
  for (const name of ['admin.js', 'admin.css', 'admin-announcements.js']) { const response = await http.get('/admin/' + name); assert.equal(response.status(), 200); assert.equal(response.headers()['cache-control'], 'no-store'); const source = createHash('sha256').update(await readFile(new URL('../../server/src/main/resources/admin/' + name, import.meta.url))).digest('hex'), served = createHash('sha256').update(await response.body()).digest('hex'); assert.equal(source, served); evidence.assets[name] = { source, served }; }
  const players = [await account('LOCAL announcement VI'), await account('LOCAL announcement EN')]; evidence.playerIds = players.map(a => a.id);
  await page.goto(settings.url + '/admin/'); await page.getByLabel('Email quản trị', { exact: true }).fill(settings.email); await page.getByLabel('Mật khẩu', { exact: true }).fill((await readFile(settings.passwordFile, 'utf8')).trim());
  const loginResponse = page.waitForResponse(r => r.url().endsWith('/admin/api/login')); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); const session = await checked(await loginResponse); headers = { Origin: settings.url, 'X-Admin-CSRF': session.csrfToken };
  const unchangedBefore = { wallets: await Promise.all(players.map(a => adminGet('/users/' + a.id + '/wallet?page=0&pageSize=100'))), gifts: await Promise.all(players.map(a => adminGet('/users/' + a.id + '/gifts?page=0&pageSize=100'))), maintenance: await adminGet('/maintenance') };
  const start = Date.now() + 12000; await draft('LOCAL scheduled announcement', start, start + 60000); const first = await publish('2'), id = first.row.announcement.id;
  assert.equal(first.row.announcement.pushEnabled, false); assert.equal(first.row.counts.stored, '0'); evidence.stages.push('browser-scheduled-publication-with-push-off');
  const replay = await checked(await context.request.post(settings.url + '/admin/api/announcements/' + id + '/publish', { headers, data: first.body })); assert.deepEqual(replay, first.row); evidence.identicalPublishRetry = true; evidence.publishPayloadHash = createHash('sha256').update(JSON.stringify(first.body)).digest('hex');
  const late = await account('LOCAL late announcement account'); evidence.latePlayerId = late.id;
  const items = await Promise.all(players.map(a => awaitItem(a, id, 'ACTIVE'))); evidence.stages.push('offline-materialization-two-authenticated-clients');
  for (const [index, auth] of players.entries()) { const banners = await playerGet(auth, '/banners'); assert.ok(banners.items.some(i => i.announcement.id === id)); await checked(await http.post('/api/announcements/' + id + '/read', { headers: playerHeaders(auth) })); await checked(await http.post('/api/announcements/' + id + '/hide-banner', { headers: playerHeaders(auth) })); assert.equal((await playerGet(auth, '/banners')).items.some(i => i.announcement.id === id), false); const persisted = await playerGet(auth, '/' + id); assert.equal(persisted.notification.isRead, true); assert.equal(persisted.bannerHidden, true); items[index] = persisted; }
  assert.equal((await playerGet(late, '?page=0&pageSize=100')).items.some(i => i.announcement.id === id), false); evidence.lateRegistrationExcluded = true;
  const publicJson = JSON.stringify(items, null, 2); for (const field of ['selectedUserIds', 'audience', 'reason', 'requestId', 'previewToken', 'pushEnabled', 'pushAccepted', 'createdByAdminId']) assert.ok(!publicJson.includes('"' + field + '"')); assert.ok(!publicJson.includes('PRIVATE_LOCAL_')); await writeFile(artifact + 'live-public-player.json', publicJson); evidence.publicJsonHash = createHash('sha256').update(publicJson).digest('hex');
  const now = Date.now(); await draft('LOCAL expiring announcement', now - 1000, now + 10000, players.map(a => a.id)); const second = await publish('2'); await Promise.all(players.map(a => awaitItem(a, second.row.announcement.id, 'EXPIRED'))); evidence.stages.push('expired-history-no-active-banner');
  await page.getByRole('button', { name: 'Thông báo', exact: true }).click(); await page.getByRole('button', { name: 'Xem thông báo LOCAL scheduled announcement', exact: true }).click(); await page.getByRole('button', { name: 'Hủy thông báo', exact: true }).click(); await page.getByLabel('Lý do hủy nội bộ').fill('PRIVATE_LOCAL_CANCEL'); await page.getByRole('button', { name: 'Xác nhận hủy', exact: true }).click(); await page.getByRole('dialog').waitFor({ state: 'detached' });
  for (const auth of players) { const cancelled = await awaitItem(auth, id, 'CANCELLED'); assert.equal(cancelled.notification.isRead, true); assert.equal(cancelled.bannerHidden, true); }
  const terminal = await adminGet('/announcements/' + id); assert.equal(terminal.counts.stored, '2'); assert.equal(terminal.counts.read, '2'); assert.equal(terminal.counts.bannerHidden, '2'); assert.equal(terminal.counts.pushAccepted, '0'); assert.equal(terminal.counts.pushUnknown, '0'); evidence.providerFacts = { stored: terminal.counts.stored, read: terminal.counts.read, accepted: terminal.counts.pushAccepted, unknown: terminal.counts.pushUnknown, configured: false }; evidence.stages.push('browser-cancelled-history-preserved');
  const unchangedAfter = { wallets: await Promise.all(players.map(a => adminGet('/users/' + a.id + '/wallet?page=0&pageSize=100'))), gifts: await Promise.all(players.map(a => adminGet('/users/' + a.id + '/gifts?page=0&pageSize=100'))), maintenance: await adminGet('/maintenance') };
  assert.deepEqual(unchangedAfter.wallets, unchangedBefore.wallets); assert.deepEqual(unchangedAfter.gifts, unchangedBefore.gifts); assert.deepEqual(unchangedAfter.maintenance.persisted, unchangedBefore.maintenance.persisted); assert.deepEqual(unchangedAfter.maintenance.effective, unchangedBefore.maintenance.effective); evidence.walletGiftMaintenanceUnchanged = true; evidence.status = 'PASS';
} finally {
  if (headers) { for (const id of evidence.announcementIds) { const row = await adminGet('/announcements/' + id); if (row.announcement.status !== 'CANCELLED') await checked(await context.request.post(settings.url + '/admin/api/announcements/' + id + '/cancel', { headers, data: { expectedVersion: row.announcement.version, requestId: randomUUID(), reason: 'Owned local fixture completion' } })); } await context.request.post(settings.url + '/admin/api/logout', { headers }); }
  for (const auth of accounts) await http.post('/auth/logout', { data: { refreshToken: auth.refreshToken } }); evidence.ownedCleanup = 'Admin and player sessions revoked; owned definitions cancelled; schema retained';
  await writeFile(artifact + 'live-integration.json', JSON.stringify(evidence, null, 2)); await context.close(); await browser.close(); await http.dispose();
}
console.log(JSON.stringify({ status: evidence.status, stages: evidence.stages, provider: evidence.provider, lateRegistrationExcluded: evidence.lateRegistrationExcluded, walletGiftMaintenanceUnchanged: evidence.walletGiftMaintenanceUnchanged }));
