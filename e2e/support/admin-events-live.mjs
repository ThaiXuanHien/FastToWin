// Owned loopback acceptance only. No traces, credentials or raw settings output.
import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID, createHash } from 'node:crypto';
import assert from 'node:assert/strict';
import { chromium, request } from 'playwright';
const settings = JSON.parse(await readFile(process.env.ADMIN_LIVE_SETTINGS, 'utf8'));
assert.equal(settings.url, 'http://127.0.0.1:18082');
assert.equal(settings.databaseUrl, 'jdbc:postgresql://127.0.0.1:15432/admin_ui');
const artifact = '../.artifacts/admin-reward-events-delivery/task4/';
const browser = await chromium.launch(), context = await browser.newContext(), page = await context.newPage();
const publicHttp = await request.newContext({ baseURL: settings.url });
const sockets = [], accounts = [], ownedEvents = [], evidence = { target: settings.url, traces: 'off', stages: [] };
let headers;
const checked = async response => { assert.ok([200, 201].includes(response.status()), 'Unexpected HTTP status ' + response.status()); return response.json(); };
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function account(name) {
  const auth = await checked(await publicHttp.post('/auth/register', { data: { email: `event-task4-${randomUUID()}@example.invalid`, password: 'Local-' + randomUUID() + '9!', displayName: name, devicePlatform: 'ANDROID' } })); accounts.push(auth);
  const verification = await checked(await publicHttp.post('/auth/email-verification/request', { data: { accessToken: auth.accessToken } }));
  assert.match(verification.devEmailVerificationCode || '', /^\d{6}$/);
  await checked(await publicHttp.post('/auth/email-verification/confirm', { data: { accessToken: auth.accessToken, verificationCode: verification.devEmailVerificationCode } })); return auth;
}
async function connect(auth) {
  const ws = new WebSocket(settings.url.replace('http:', 'ws:') + '/game'), messages = [];
  const actor = { ws, messages, send: body => ws.send(JSON.stringify(body)), async wait(type, predicate = () => true) {
    const deadline = Date.now() + 10000;
    while (Date.now() < deadline) {
      const index = messages.findIndex(m => m.type === type && predicate(m)); if (index >= 0) return messages.splice(index, 1)[0];
      await delay(20);
    } throw new Error('Missing socket event: ' + type + '; error codes: ' + messages.filter(m => m.type === 'error').map(m => m.code).join(','));
  } };
  ws.addEventListener('message', event => messages.push(JSON.parse(event.data))); sockets.push(actor);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  actor.send({ type: 'connect_account', accessToken: auth.accessToken, protocolVersion: 43 }); const ready = await actor.wait('session_ready'); actor.id = ready.playerId; return actor;
}
async function readEvent(id) { return checked(await context.request.get(settings.url + '/admin/api/events/' + id)); }
async function cancelOwned(id) {
  const current = await readEvent(id); if (['ENDED', 'CANCELLED'].includes(current.state)) return;
  await checked(await context.request.post(settings.url + '/admin/api/events/' + id + '/cancel', { headers, data: { expectedVersion: current.version, requestId: randomUUID(), reason: 'Owned Task4 loopback fixture complete' } }));
}
try {
  assert.equal((await publicHttp.get('/health')).status(), 200);
  assert.equal((await publicHttp.get('/api/status')).status(), 200);
  evidence.assets = {};
  for (const name of ['admin.js', 'admin.css', 'admin-events.js']) {
    const response = await publicHttp.get('/admin/' + name); assert.equal(response.status(), 200); assert.equal(response.headers()['cache-control'], 'no-store');
    const served = createHash('sha256').update(await response.body()).digest('hex');
    const source = createHash('sha256').update(await readFile('../server/src/main/resources/admin/' + name)).digest('hex'); assert.equal(served, source); evidence.assets[name] = served;
  }
  const people = await Promise.all([account('Task4 Event Host'), account('Task4 Event Guest')]);
  const [host, guest] = await Promise.all(people.map(connect));
  await page.goto(settings.url + '/admin/'); await page.getByLabel('Email quản trị', { exact: true }).fill(settings.email); await page.getByLabel('Mật khẩu', { exact: true }).fill(await readFile(settings.passwordFile, 'utf8'));
  const login = page.waitForResponse(r => r.url().endsWith('/admin/api/login')); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); const session = await (await login).json(); headers = { Origin: settings.url, 'X-Admin-CSRF': session.csrfToken };
  await page.getByRole('button', { name: 'Sự kiện thưởng', exact: true }).click(); await page.getByRole('button', { name: 'Tạo sự kiện', exact: true }).click();
  const title = 'LOCAL Task4 GOLD fixture ' + Date.now(), now = Date.now();
  for (const [label, value] of [['Tên tiếng Việt', title], ['Tên tiếng Anh', 'LOCAL Task4 GOLD fixture'], ['Nội dung tiếng Việt', 'Chỉ kiểm thử trên máy cục bộ.'], ['Nội dung tiếng Anh', 'Owned local integration fixture only.'], ['Múi giờ hiển thị (IANA)', 'America/New_York'], ['Bắt đầu (UTC, bao gồm)', new Date(now - 1000).toISOString().slice(0, 23)], ['Kết thúc (UTC, không bao gồm)', new Date(now + 300000).toISOString().slice(0, 23)], ['Lý do thay đổi', 'Owned Task4 loopback integration']]) await page.getByLabel(label, { exact: true }).fill(value);
  await page.getByLabel('Hệ số GOLD', { exact: true }).selectOption('2'); await page.getByLabel('Hệ số XP', { exact: true }).selectOption('1');
  const create = page.waitForResponse(r => r.url().endsWith('/admin/api/events') && r.request().method() === 'POST'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); const created = await checked(await create); ownedEvents.push(created.id); evidence.stages.push('browser-draft');
  assert.equal((await publicHttp.get('/api/events/' + created.id)).status(), 404);
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await page.getByRole('button', { name: 'Tiếp tục lên lịch', exact: true }).waitFor(); await page.getByRole('dialog').screenshot({ path: artifact + 'live-preview.png' });
  await page.getByRole('button', { name: 'Tiếp tục lên lịch', exact: true }).click(); await page.getByLabel('Lý do lên lịch', { exact: true }).fill('Owned Task4 loopback schedule');
  const scheduledResponse = page.waitForResponse(r => r.url().endsWith('/schedule')); await page.getByRole('button', { name: 'Lên lịch sự kiện', exact: true }).click(); const scheduledHttp = await scheduledResponse, scheduled = await checked(scheduledHttp); assert.equal(scheduled.state, 'ACTIVE'); evidence.stages.push('browser-preview-schedule');
  const replay = await checked(await context.request.post(settings.url + '/admin/api/events/' + created.id + '/schedule', { headers, data: scheduledHttp.request().postDataJSON() })); assert.deepEqual(replay, scheduled); evidence.scheduleReplay = true;
  // Independent XP event expires while the already captured match continues.
  const expiryAt = Date.now() + 12000;
  const xp = await checked(await context.request.post(settings.url + '/admin/api/events', { headers, data: { expectedVersion: 0, requestId: randomUUID(), reason: 'Owned Task4 expiry fixture', draft: { ...created.draft, nameVi: 'LOCAL Task4 XP expiry fixture', nameEn: 'LOCAL Task4 XP expiry fixture', goldMultiplier: 1, xpMultiplier: 2, startsAtEpochMillis: Date.now() - 1000, endsAtEpochMillis: expiryAt } } })); ownedEvents.push(xp.id);
  const xpPreview = await checked(await context.request.post(settings.url + '/admin/api/events/' + xp.id + '/preview', { headers, data: {} }));
  await checked(await context.request.post(settings.url + '/admin/api/events/' + xp.id + '/schedule', { headers, data: { expectedVersion: xp.version, requestId: randomUUID(), reason: 'Owned Task4 expiry fixture schedule', previewToken: xpPreview.previewToken } }));
  const safeResponse = await publicHttp.get('/api/events/' + created.id); evidence.publicCacheControl = safeResponse.headers()['cache-control']; assert.deepEqual([...new Set(evidence.publicCacheControl.split(/,\s*/))], ['no-store']);
  const safe = await checked(safeResponse); for (const key of ['actorId', 'creatorId', 'publisherId', 'reason', 'previewToken']) assert.ok(!(key in safe.event));
  host.send({ type: 'create_room', roomName: 'LOCAL Task4 event match', password: '', gameMode: 'ORDER', matchType: 'CASUAL' }); const room = (await host.wait('room_created')).game.roomId;
  guest.send({ type: 'join_room', roomId: room, password: '' }); await guest.wait('room_updated'); host.send({ type: 'set_ready', roomId: room, ready: true }); guest.send({ type: 'set_ready', roomId: room, ready: true });
  const started = (await host.wait('game_started')).game; await guest.wait('game_started'); assert.equal(started.eventRewards.goldMultiplier, 2); assert.equal(started.eventRewards.xpMultiplier, 2); assert.ok(started.eventRewards.sources.some(s => s.id === created.id)); assert.ok(started.eventRewards.sources.some(s => s.id === xp.id)); evidence.stages.push('actual-casual-start-independent-x2');
  await page.getByRole('button', { name: 'Hủy sự kiện', exact: true }).click(); await page.getByLabel('Lý do hủy', { exact: true }).fill('Owned Task4 cancellation after capture'); await page.getByRole('button', { name: 'Xác nhận hủy sự kiện', exact: true }).click(); await page.getByRole('dialog').waitFor({ state: 'detached' }); assert.equal((await readEvent(created.id)).state, 'CANCELLED');
  await delay(Math.max(0, expiryAt - Date.now() + 100)); assert.equal((await readEvent(xp.id)).state, 'ENDED'); evidence.stages.push('cancel-and-expiry-after-capture');
  // One wrong selection prevents a perfect-game achievement from disguising MATCH accounting.
  host.send({ type: 'select_number', roomId: room, number: 50, requestId: 'task4-wrong' }); await host.wait('game_state_updated', m => m.selectionAccepted === false);
  let finished;
  for (let number = 1; number <= 50; number++) { host.send({ type: 'select_number', roomId: room, number, requestId: 'task4-select-' + number }); const update = await host.wait('game_state_updated', m => m.acceptedNumber === number && m.selectionAccepted); if (number === 50) finished = update.game; }
  assert.equal(finished.phase, 'FINISHED'); assert.equal(finished.matchId, started.matchId); assert.deepEqual(finished.eventRewards, started.eventRewards);
  const expected = finished.rewardBreakdowns[host.id]; assert.equal(expected.baseGold, 80); assert.equal(expected.bonusGold, 80); assert.equal(expected.totalGold, 160); assert.equal(expected.baseXp, 24); assert.equal(expected.bonusXp, 24); assert.equal(expected.totalXp, 48);
  // Replay the exact last input and refresh committed history twice: no second award.
  host.send({ type: 'select_number', roomId: room, number: 50, requestId: 'task4-select-50' });
  let wallet, history;
  for (let attempt = 0; attempt < 30; attempt++) {
    host.send({ type: 'get_wallet_history' }); wallet = (await host.wait('wallet_history')).transactions; host.send({ type: 'get_profile' }); history = (await host.wait('profile_data')).profile.recentMatches.find(m => m.matchId === started.matchId); if (history?.rewardBreakdown) break; await delay(100);
  }
  assert.deepEqual(history.rewardBreakdown, expected); const entries = wallet.filter(t => t.sourceId === started.matchId && t.sourceType === 'MATCH'); assert.equal(entries.length, 1); assert.equal(entries[0].goldDelta, 160); assert.equal(entries[0].xpDelta, 48); assert.equal(wallet.filter(t => t.sourceType === 'EVENT_BONUS').length, 0);
  host.send({ type: 'get_match_detail', matchId: started.matchId }); const detail = await host.wait('match_detail_data'); assert.deepEqual(detail.detail.summary.rewardBreakdown, expected);
  const adminWallet = await checked(await context.request.get(settings.url + `/admin/api/users/${host.id}/wallet?page=0&pageSize=100`)); assert.equal(adminWallet.items.filter(t => t.sourceType === 'MATCH' && t.sourceId === started.matchId).length, 1);
  const adminMatches = await checked(await context.request.get(settings.url + `/admin/api/users/${host.id}/matches?page=0&pageSize=100`)); assert.equal(adminMatches.items.filter(m => m.id === started.matchId).length, 1);
  host.send({ type: 'get_wallet_history' }); assert.deepEqual((await host.wait('wallet_history')).transactions.filter(t => t.sourceId === started.matchId), wallet.filter(t => t.sourceId === started.matchId));
  evidence.matchId = started.matchId; evidence.eventIds = ownedEvents; evidence.breakdown = expected; evidence.matchWalletEntries = 1; evidence.duplicateInputNoExtraAward = true; evidence.playerAndAdminHistory = true; evidence.stages.push('committed-MATCH-history-and-duplicate-input');
  // A fresh post-cancel/expiry match is neutral, unlike the frozen first match.
  host.send({ type: 'request_rematch', roomId: room }); await guest.wait('rematch_status'); guest.send({ type: 'respond_rematch', roomId: room, accept: true }); const fresh = (await host.wait('game_started')).game; assert.equal(fresh.eventRewards.goldMultiplier, 1); assert.equal(fresh.eventRewards.xpMultiplier, 1); assert.equal(fresh.eventRewards.sources.length, 0);
  host.send({ type: 'leave_room', roomId: room }); guest.send({ type: 'leave_room', roomId: room }); evidence.freshRematchNeutral = true; evidence.status = 'PASS';
} finally {
  for (const socket of sockets) socket.ws.close();
  if (headers) { for (const id of ownedEvents) await cancelOwned(id); evidence.eventTerminalStates = await Promise.all(ownedEvents.map(async id => ({ id, state: (await readEvent(id)).state }))); await context.request.post(settings.url + '/admin/api/logout', { headers }); }
  for (const auth of accounts) await publicHttp.post('/auth/logout', { data: { refreshToken: auth.refreshToken } });
  await writeFile(artifact + 'live-integration.json', JSON.stringify(evidence, null, 2)); await context.close(); await browser.close(); await publicHttp.dispose();
}
console.log(JSON.stringify({ status: evidence.status, stages: evidence.stages, matchWalletEntries: evidence.matchWalletEntries, freshRematchNeutral: evidence.freshRematchNeutral }));
