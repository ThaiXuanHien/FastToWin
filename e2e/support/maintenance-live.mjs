// Explicit local acceptance, no traces or secret output. Run from e2e with ADMIN_LIVE_SETTINGS.
import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { chromium, request } from 'playwright';
const settings = JSON.parse(await readFile(process.env.ADMIN_LIVE_SETTINGS, 'utf8'));
assert.equal(settings.url, 'http://127.0.0.1:18082');
assert.equal(settings.databaseUrl, 'jdbc:postgresql://127.0.0.1:15432/admin_ui');
const browser = await chromium.launch(), context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage(), guestHttp = await request.newContext({ baseURL: settings.url });
const createdAdmins = [], sockets = [], nativeAccounts = [], evidence = { target: settings.url, trace: 'off', stages: [] };
let headers;
const checked = async response => { assert.ok(response.ok(), `HTTP ${response.status()}`); return response.json(); };
const read = async () => checked(await context.request.get(settings.url + '/admin/api/maintenance'));
const post = async payload => checked(await context.request.post(settings.url + '/admin/api/maintenance', { headers, data: payload }));
async function waitState(expected, timeout = 75000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const response = await guestHttp.get('/api/status');
    if (response.ok() && (await response.json()).state === expected) { evidence.stages.push(expected); return; }
    await new Promise(r => setTimeout(r, 400));
  }
  throw new Error('Timed out awaiting ' + expected);
}
async function account(name) {
  const id = randomUUID().slice(0, 12), password = 'Local-' + randomUUID() + '9!';
  const auth = await checked(await guestHttp.post('/auth/register', { data: { email: `maintenance-${id}@example.invalid`, password, displayName: name, devicePlatform: 'ANDROID' } }));
  nativeAccounts.push(auth);
  const verification = await checked(await guestHttp.post('/auth/email-verification/request', { data: { accessToken: auth.accessToken } }));
  assert.match(verification.devEmailVerificationCode || '', /^\d{6}$/, 'Owned development preview must return its test verification code');
  await checked(await guestHttp.post('/auth/email-verification/confirm', { data: { accessToken: auth.accessToken, verificationCode: verification.devEmailVerificationCode } }));
  return auth;
}
async function connect(auth) {
  const ws = new WebSocket(settings.url.replace('http:', 'ws:') + '/game'), messages = [];
  const actor = { ws, messages, closed: null,
    send: body => ws.send(JSON.stringify(body)),
    async wait(type, predicate = () => true, timeout = 6000) {
      const deadline = Date.now() + timeout;
      while (Date.now() < deadline) {
        const index = messages.findIndex(m => m.type === type && predicate(m));
        if (index >= 0) return messages.splice(index, 1)[0];
        const failure = messages.find(m => m.type === 'error');
        if (failure && type !== 'error') throw new Error('Socket rejected: ' + failure.code);
        await new Promise(r => setTimeout(r, 30));
      } throw new Error('Missing socket event ' + type);
    } };
  ws.addEventListener('message', event => messages.push(JSON.parse(event.data)));
  ws.addEventListener('close', event => actor.closed = event.code); sockets.push(actor);
  await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, { once: true }); ws.addEventListener('error', reject, { once: true }); });
  actor.send({ type: 'connect_account', accessToken: auth.accessToken, protocolVersion: 43 }); await actor.wait('session_ready'); return actor;
}
async function configure(mode, warning = 1) {
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await page.getByRole('button', { name: 'Cấu hình bảo trì', exact: true }).click();
  await page.getByLabel('Chế độ').selectOption(mode);
  await page.getByLabel('Thông báo tiếng Việt').fill('Kiểm thử bảo trì cục bộ');
  await page.getByLabel('Thông báo tiếng Anh').fill('Local maintenance acceptance');
  await page.getByLabel('Báo trước (phút)').fill(String(warning));
  await page.getByLabel('Chờ trận kết thúc (phút)').fill('1');
  await page.getByLabel('Lý do nội bộ').fill('Task4 owned loopback acceptance');
  await page.getByRole('button', { name: 'Xem lại thay đổi', exact: true }).click();
  await page.getByRole('button', { name: 'Xác nhận lưu', exact: true }).click();
  await page.getByRole('dialog').waitFor({ state: 'detached' });
}
async function disable() {
  const current = await read();
  if (current.persisted.state !== 'ACTIVE') await post({ enabled: false, expectedVersion: current.persisted.version, requestId: randomUUID(), reason: 'Restore owned preview ACTIVE after local acceptance' });
  await waitState('ACTIVE', 8000);
}
try {
  assert.equal((await guestHttp.get('/admin/api/maintenance')).status(), 401);
  await page.goto(settings.url + '/admin/');
  await page.getByLabel('Email quản trị', { exact: true }).fill(settings.email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(await readFile(settings.passwordFile, 'utf8'));
  const login = page.waitForResponse(r => r.url().endsWith('/admin/api/login'));
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  const session = await (await login).json(); headers = { Origin: settings.url, 'X-Admin-CSRF': session.csrfToken };
  await disable();
  const people = await Promise.all(['Maint Host', 'Maint Opponent', 'Maint Idle'].map(account));
  const [host, opponent, idle] = await Promise.all(people.map(connect));
  host.send({ type: 'create_room', roomName: 'Local maintenance acceptance', password: '', gameMode: 'ORDER', matchType: 'CASUAL' });
  const room = (await host.wait('room_created')).game.roomId;
  opponent.send({ type: 'join_room', roomId: room, password: '' }); await opponent.wait('room_updated');
  host.send({ type: 'set_ready', roomId: room, ready: true }); opponent.send({ type: 'set_ready', roomId: room, ready: true });
  await host.wait('game_started'); await opponent.wait('game_started');
  await page.getByRole('button', { name: 'Bảo trì', exact: true }).click(); await configure('PLANNED');
  await waitState('ANNOUNCING', 8000);
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await page.screenshot({ path: '../.artifacts/admin-maintenance-live-announcing.png', fullPage: true });
  console.log('Live preview: ANNOUNCING observed; waiting for saved warning deadline.');
  await waitState('DRAINING');
  const drain = await read(); assert.equal(drain.runtime.activeMatches, 1);
  host.send({ type: 'select_number', roomId: room, number: 1, requestId: 'live-select-1' });
  await host.wait('game_state_updated', m => m.acceptedNumber === 1 && m.selectionAccepted);
  idle.send({ type: 'create_room', roomName: 'Must be blocked', password: '', gameMode: 'ORDER' });
  assert.equal((await idle.wait('error')).code, 'MAINTENANCE');
  evidence.existingMatchContinued = true; evidence.newRoomBlocked = true;
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await page.screenshot({ path: '../.artifacts/admin-maintenance-live-draining.png', fullPage: true });
  console.log('Live preview: DRAINING observed, existing match accepts input, new room rejected.');
  await waitState('MAINTENANCE');
  await new Promise(r => setTimeout(r, 1500)); assert.equal(host.closed, 1001); assert.equal(opponent.closed, 1001);
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await page.screenshot({ path: '../.artifacts/admin-maintenance-live-closed.png', fullPage: true });
  await page.getByRole('button', { name: 'Tắt bảo trì', exact: true }).click();
  await page.getByLabel('Lý do nội bộ').fill('Local planned acceptance complete');
  await page.getByRole('button', { name: 'Xác nhận lưu', exact: true }).click(); await page.getByRole('dialog').waitFor({ state: 'detached' });
  await waitState('ACTIVE', 8000);
  const old = await connect(people[2]), start = Date.now(); await configure('EMERGENCY', 0); await waitState('MAINTENANCE', 8000);
  while (old.closed == null && Date.now() - start < 8000) await new Promise(r => setTimeout(r, 50));
  assert.equal(old.closed, 1001); evidence.emergencyOldSocketClosed = true;
  await disable();
  evidence.roles = {};
  for (const role of ['OPERATOR', 'SUPPORT']) {
    const password = 'Local-' + randomUUID() + '9!', email = `maintenance-${role.toLowerCase()}-${Date.now()}@example.invalid`;
    const admin = await checked(await context.request.post(settings.url + '/admin/api/accounts', { headers, data: { email, password, role, reason: 'Local role acceptance', requestId: randomUUID() } })); createdAdmins.push(admin);
    const roleContext = await browser.newContext(), rolePage = await roleContext.newPage();
    try {
      await rolePage.goto(settings.url + '/admin/'); await rolePage.getByLabel('Email quản trị', { exact: true }).fill(email); await rolePage.getByLabel('Mật khẩu', { exact: true }).fill(password);
      const response = rolePage.waitForResponse(r => r.url().endsWith('/admin/api/login')); await rolePage.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); const logged = await (await response).json();
      await rolePage.getByRole('button', { name: 'Bảo trì', exact: true }).click(); await rolePage.getByText(/Chỉ Owner/).waitFor();
      assert.equal(await rolePage.getByRole('button', { name: 'Cấu hình bảo trì', exact: true }).count(), 0);
      const denied = await roleContext.request.post(settings.url + '/admin/api/maintenance', { headers: { Origin: settings.url, 'X-Admin-CSRF': logged.csrfToken }, data: { enabled: false, expectedVersion: (await read()).persisted.version, requestId: randomUUID(), reason: 'Must be denied' } }); assert.equal(denied.status(), 403);
      await context.request.post(settings.url + `/admin/api/accounts/${admin.id}/access`, { headers, data: { role, active: false, requestId: randomUUID(), reason: 'Finish owned local role acceptance' } });
      assert.equal((await roleContext.request.get(settings.url + '/admin/api/maintenance')).status(), 401);
      evidence.roles[role] = { readOnlyControls: true, mutation: 403, afterRevocation: 401 };
    } finally { await roleContext.close(); }
  }
  const audit = await checked(await context.request.get(settings.url + '/admin/api/audit?page=0&pageSize=100'));
  assert.ok(audit.items.some(a => a.action === 'MAINTENANCE_UPDATE')); assert.ok(audit.items.some(a => a.action === 'MAINTENANCE_TRANSITION'));
  evidence.auditUpdateAndTransition = true; evidence.status = 'PASS';
} finally {
  for (const actor of sockets) actor.ws.close();
  if (headers) {
    await disable();
    for (const admin of createdAdmins) await context.request.post(settings.url + `/admin/api/accounts/${admin.id}/access`, { headers, data: { role: admin.role, active: false, requestId: randomUUID(), reason: 'Ensure owned local test account disabled' } });
    evidence.restoredActive = (await read()).persisted.state === 'ACTIVE';
  }
  for (const auth of nativeAccounts) await guestHttp.post('/auth/logout', { data: { refreshToken: auth.refreshToken } });
  await writeFile('../.artifacts/admin-maintenance-live-result.json', JSON.stringify(evidence, null, 2));
  await context.close(); await browser.close(); await guestHttp.dispose();
}
console.log(JSON.stringify(evidence));
