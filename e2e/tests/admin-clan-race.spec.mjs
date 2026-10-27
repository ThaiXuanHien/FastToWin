import { test, expect, login } from '../support/admin-fixture.mjs';
import { races, raceView, raceConfig } from '../support/admin-clan-race-fixture.mjs';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const artifacts = fileURLToPath(new URL('../../.artifacts/admin-clan-race/', import.meta.url));

async function open(page, fixture) { await page.clock.setFixedTime(new Date('2026-10-25T10:00:00+07:00')); await login(page, fixture); await page.getByRole('button', { name: 'Đua top bang', exact: true }).click(); }
async function detail(page, week = '2099-01-05') { await page.getByRole('button', { name: 'Xem tuần ' + week, exact: true }).click(); await expect(page.getByRole('button', { name: 'Xem trước', exact: true })).toBeVisible(); }
async function fill(page) {
  for (const [label, value] of [['Tuần bắt đầu (thứ Hai)', '2099-01-05'], ['Điểm / 1 Vàng', '0.01'], ['Điểm / 1 Gem', '10'], ['Lý do thay đổi', 'Lịch tuần mới']]) await page.getByLabel(label, { exact: true }).fill(value);
  for (const [rank, values] of [[1, ['30', '300', '3']], [2, ['20', '200', '2']], [3, ['10', '100', '1']]]) for (const [index, asset] of ['XP', 'Vàng', 'Gem'].entries()) await page.getByLabel(`Top ${rank} · ${asset}`, { exact: true }).fill(values[index]);
}
test('Owner previews exact per-member rewards and publishes a future week', async ({ page, fixture }) => {
  races(fixture, [raceView()]); await open(page, fixture);
  await page.getByRole('button', { name: 'Xem tuần 2099-01-05', exact: true }).click();
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('50'); await expect(page.getByRole('dialog')).toContainText('30.000');
  await page.getByRole('button', { name: 'Tiếp tục phát hành', exact: true }).click();
  await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Phê duyệt'); await page.getByRole('button', { name: 'Phát hành tuần đua', exact: true }).click();
  await expect(page.locator('main')).toContainText('Đã phát hành; cấu hình tuần được đóng băng.');
  const call = fixture.calls.find(c => c.route.endsWith('/publish')); expect(call.body).toMatchObject({ expectedVersion: 1, reason: 'Phê duyệt' }); expect(call.headers['x-admin-csrf']).toBe('fixture-csrf');
});

test('Operator clones unchanged economics only; Support has no destination', async ({ page, fixture }) => {
  fixture.role = 'OPERATOR'; races(fixture, [raceView({ state: 'PUBLISHED', publishedRevision: 2 })]); await open(page, fixture);
  await expect(page.getByRole('button', { name: 'Tạo tuần đua', exact: true })).toHaveCount(0); await detail(page);
  await expect(page.getByRole('button', { name: 'Sửa bản nháp', exact: true })).toHaveCount(0); await expect(page.getByRole('button', { name: 'Ngừng tuần tương lai', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Nhân bản sang tuần tương lai', exact: true }).click();
  await expect(page.getByLabel('Điểm / 1 Vàng', { exact: true })).toHaveAttribute('readonly', ''); await expect(page.getByLabel('Top 1 · Vàng', { exact: true })).toHaveValue('300');
  await page.getByLabel('Tuần bắt đầu (thứ Hai)', { exact: true }).fill('2099-01-12'); await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Sao chép nguyên cấu hình');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Tuần đua 2099-01-12', exact: true })).toBeVisible();
  expect(fixture.calls.find(c => c.route.endsWith('/clone')).body).toMatchObject({ weekStart: '2099-01-12', expectedVersion: 1, reason: 'Sao chép nguyên cấu hình' }); expect(fixture.calls.find(c => c.route.endsWith('/clone')).body).not.toHaveProperty('config');
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await expect(page.getByRole('dialog')).toContainText('30.000'); await expect(page.getByRole('button', { name: 'Tiếp tục phát hành', exact: true })).toHaveCount(0); await page.keyboard.press('Escape');
  fixture.role = 'SUPPORT'; await page.reload(); await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: 'Đua top bang', exact: true })).toHaveCount(0);
});

test('validation focuses linked summary and retains hints, exact tiny rates and maximum integers', async ({ page, fixture }) => {
  races(fixture); await open(page, fixture); await page.getByRole('button', { name: 'Tạo tuần đua', exact: true }).click();
  await fill(page); await page.getByLabel('Điểm / 1 Vàng', { exact: true }).fill('0'); await page.getByLabel('Top 3 · XP', { exact: true }).fill('40'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  const summary = page.getByRole('dialog').getByRole('alert'); await expect(summary).toBeFocused(); await summary.getByRole('link').first().click(); await expect(page.getByLabel('Điểm / 1 Vàng', { exact: true })).toBeFocused();
  await expect(page.getByLabel('Điểm / 1 Vàng', { exact: true })).toHaveAttribute('aria-describedby', 'race-rate-hint field-race-goldRate-error');
  await expect(page.getByLabel('Top 3 · XP', { exact: true })).toHaveAttribute('aria-describedby', 'race-reward-hint field-race-2-xp-error');
  expect(fixture.calls.filter(c => c.method === 'POST' && c.route === '/admin/api/clan-race')).toHaveLength(0);
  await page.getByLabel('Điểm / 1 Vàng', { exact: true }).fill('0.000001'); await page.getByLabel('Top 3 · XP', { exact: true }).fill('10');
  await expect(page.getByRole('dialog')).toContainText('100 Vàng = 0.0001 điểm'); await page.getByLabel('Lý do thay đổi', { exact: true }).fill(''); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByLabel('Điểm / 1 Vàng', { exact: true })).toHaveAttribute('aria-describedby', 'race-rate-hint'); await expect(page.getByLabel('Top 3 · XP', { exact: true })).toHaveAttribute('aria-describedby', 'race-reward-hint');
  await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Tỷ lệ nhỏ'); await page.getByLabel('Top 1 · Vàng', { exact: true }).fill('2147483647'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tuần đua 2099-01-05', exact: true })).toBeVisible(); const body = fixture.calls.find(c => c.route === '/admin/api/clan-race' && c.method === 'POST').body; expect(body.config.goldRate).toBe('0.000001'); expect(body.config.rewards[0].gold).toBe(2147483647);
});

test('unknown result retries identical body/id; changed input gets new id and pending dismiss is blocked', async ({ page, fixture }) => {
  races(fixture); let release; fixture.handlers.set('/admin/api/clan-race', async (call, send) => { if (call.method === 'GET') { send(200, { items: [], page: 0, pageSize: 25, total: 0 }); return true; } await new Promise(resolve => release = resolve); send(503, { message: 'Chưa biết kết quả' }); return true; });
  await open(page, fixture); await page.getByRole('button', { name: 'Tạo tuần đua', exact: true }).click(); await fill(page);
  for (let attempt = 0; attempt < 3; attempt++) {
    if (attempt === 2) await page.getByLabel('Điểm / 1 Gem', { exact: true }).fill('11'); release = null; await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect.poll(() => !!release).toBe(true);
    await expect(page.getByLabel('Điểm / 1 Vàng', { exact: true })).toBeDisabled(); await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toBeVisible(); release(); await expect(page.getByRole('alert')).toContainText('giữ nguyên mã yêu cầu'); await expect(page.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toBeEnabled();
  }
  const calls = fixture.calls.filter(c => c.route === '/admin/api/clan-race' && c.method === 'POST'); expect(calls[0].body).toEqual(calls[1].body); expect(calls[2].body.requestId).not.toBe(calls[1].body.requestId); expect(calls[2].body.config.gemRate).toBe('11');
});

test('stale version blocks a second mutation and reloads authoritative revision', async ({ page, fixture }) => {
  const rows = races(fixture, [raceView()]); fixture.handlers.set('/admin/api/clan-race/2099-01-05/draft', (call, send) => { rows.set('2099-01-05', raceView({ version: 8 })); send(409, { code: 'STALE_VERSION', message: 'Changed' }); return true; });
  await open(page, fixture); await detail(page); await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click(); await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Sửa'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toBeDisabled(); await page.getByRole('button', { name: 'Tải lại tuần đua', exact: true }).click(); await expect(page.locator('main')).toContainText('8'); expect(fixture.calls.filter(c => c.method === 'PUT')).toHaveLength(1);
});

test('current frozen prizes are distinct from future drafts and only future weeks archive', async ({ page, fixture }) => {
  races(fixture, [raceView({ weekStart: '2026-10-19', state: 'PUBLISHED', publishedRevision: 4, config: { ...raceConfig(), goldRate: '0.5' } }), raceView()]); await open(page, fixture);
  const current = page.getByRole('region', { name: 'Tuần hiện tại', exact: true }); await expect(current).toContainText('0.5'); await expect(current).toContainText('Thưởng đã đóng băng'); await expect(current).not.toContainText('0.01');
  await page.getByRole('button', { name: 'Xem tuần hiện tại', exact: true }).click(); await expect(page.getByRole('button', { name: 'Sửa bản nháp', exact: true })).toHaveCount(0); await expect(page.getByRole('button', { name: 'Ngừng tuần tương lai', exact: true })).toHaveCount(0); await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await expect(page.getByRole('dialog')).toContainText('Ngân sách tối đa (3 hạng)'); await expect(page.getByRole('button', { name: 'Tiếp tục phát hành', exact: true })).toHaveCount(0); await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Quay lại tuần đua', exact: true }).click(); await detail(page); await page.getByRole('button', { name: 'Ngừng tuần tương lai', exact: true }).click(); await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Hủy lịch'); await page.getByRole('button', { name: 'Ngừng tuần tương lai', exact: true }).click(); await expect(page.locator('main')).toContainText('Chưa mở đua · đã ngừng'); expect(fixture.calls.find(c => c.route.endsWith('/archive')).body.expectedVersion).toBe(1);
});

test('loading, unavailable, no config, direct week lookup and paging use actual HTTP state', async ({ page, fixture }) => {
  races(fixture); let release; let first = true; fixture.handlers.set('/admin/api/clan-race', async (call, send) => { if (first) { first = false; await new Promise(resolve => release = resolve); send(503, { message: 'Kho tạm lỗi' }); } else send(200, { items: [], page: Number(call.query.get('page')), pageSize: 25, total: 26 }); return true; });
  await open(page, fixture); await expect(page.getByRole('status').filter({ hasText: 'Đang tải tuần đua' })).toBeVisible(); await expect(page.locator('main')).not.toContainText('Chưa có cấu hình tuần.'); release(); await expect(page.getByRole('alert')).toContainText('Kho tạm lỗi'); await expect(page.locator('main')).not.toContainText('Chưa mở đua tuần này');
  await page.getByRole('button', { name: 'Thử lại tải tuần đua', exact: true }).click(); await expect(page.locator('main')).toContainText('Chưa mở đua tuần này'); await page.getByRole('button', { name: 'Trang sau', exact: true }).click(); await expect.poll(() => fixture.calls.filter(c => c.route === '/admin/api/clan-race').at(-1).query.get('page')).toBe('1'); expect(Object.fromEntries(fixture.calls.filter(c => c.route === '/admin/api/clan-race').at(-1).query)).toEqual({ page: '1', pageSize: '25' });
  await page.getByLabel('Xem tuần theo ngày thứ Hai', { exact: true }).fill('2026-10-12'); await page.getByRole('button', { name: 'Xem tuần', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Tuần này chưa có cấu hình'); expect(fixture.calls.some(c => c.route === '/admin/api/clan-race/2026-10-12')).toBe(true);
});

test('settling, failed and settled snapshots preserve escaped exact scores and overlapping delivery counts', async ({ page, fixture }) => {
  const standing = { rank: 1, clanId: 'clan', name: '<img src=x onerror=alert(1)>', logoId: null, level: 4, scoreUnits: '1', points: '0.000001', reachedAtEpochMillis: 1792342800000 };
  const settlement = { state: 'SETTLED', attempts: 2, lastErrorCode: null, result: { weekStart: '2026-10-12', revision: 3, cutoffEpochMillis: 1792342800000, settledAtEpochMillis: 1792342810000, standings: [standing], winners: [{ standing, reward: { xp: 30, gold: 300, gems: 3 }, eligibleMembers: 2 }] }, delivery: { pending: 3, waitingRegistration: 1, failed: 1, delivered: 2, claimed: 1, accountUnavailable: 1 } };
  const rows = races(fixture, [raceView({ weekStart: '2026-10-12', state: 'PUBLISHED', publishedRevision: 3 })]); await open(page, fixture); await detail(page, '2026-10-12'); await expect(page.locator('main')).toContainText('Đang chốt');
  rows.set('2026-10-12', raceView({ weekStart: '2026-10-12', state: 'PUBLISHED', publishedRevision: 3, settlement: { state: 'FAILED', attempts: 1, lastErrorCode: 'SETTLEMENT_FAILED' } })); await page.getByRole('button', { name: 'Làm mới tuần đua', exact: true }).click(); await expect(page.locator('main')).toContainText('SETTLEMENT_FAILED');
  rows.set('2026-10-12', raceView({ weekStart: '2026-10-12', state: 'PUBLISHED', publishedRevision: 3, settlement })); await page.getByRole('button', { name: 'Làm mới tuần đua', exact: true }).click(); await expect(page.locator('main')).toContainText('0.000001 điểm'); await expect(page.locator('main')).toContainText('<img src=x onerror=alert(1)>'); await expect(page.locator('main img')).toHaveCount(0); await expect(page.locator('main')).toContainText('không cộng thành tổng'); await expect(page.getByRole('button', { name: 'Ngừng tuần tương lai', exact: true })).toHaveCount(0); await expect(page.getByRole('button', { name: 'Sửa bản nháp', exact: true })).toHaveCount(0);
});

for (const destination of ['Người chơi', 'Đăng xuất']) for (const action of ['preview', 'draft']) test(`late ${action} cannot render after ${destination}`, async ({ page, fixture }) => {
  races(fixture, [raceView()]); let release; fixture.handlers.set('/admin/api/clan-race/2099-01-05/' + action, async (call, send) => { await new Promise(resolve => release = resolve); send(200, action === 'preview' ? { race: raceView({ weekStart: '2099-02-02' }) } : raceView({ weekStart: '2099-02-02' })); return true; });
  await open(page, fixture); await detail(page);
  if (action === 'preview') await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); else { await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click(); await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Sửa'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); }
  await expect.poll(() => !!release).toBe(true); await page.evaluate(destination => [...document.querySelectorAll('nav button, .identity button')].find(button => (button.getAttribute('aria-label') || button.textContent) === destination).click(), destination); await expect(page.getByRole('dialog')).toHaveCount(0); release(); if (destination === 'Người chơi') await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible(); else await expect(page.getByLabel('Email quản trị', { exact: true })).toBeVisible(); expect(fixture.calls.some(c => c.route.endsWith('/2099-02-02'))).toBe(false);
});

for (const status of [401, 403]) test(`backend ${status} overrides stale UI role and CSRF authority`, async ({ page, fixture }) => {
  races(fixture, [raceView()]); fixture.handlers.set('/admin/api/clan-race/2099-01-05/draft', (call, send) => { if (status === 403) fixture.role = 'SUPPORT'; else fixture.signedIn = false; send(status, { message: 'Quyền hoặc CSRF không còn hợp lệ' }); return true; });
  await open(page, fixture); await detail(page); await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click(); await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Sửa'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0);
  if (status === 401) await expect(page.getByLabel('Email quản trị', { exact: true })).toBeVisible(); else { await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: 'Đua top bang', exact: true })).toHaveCount(0); } expect(fixture.calls.filter(c => c.method === 'PUT')).toHaveLength(1);
});

for (const width of [375, 1440]) test(`fixture visual and native focus at ${width}`, async ({ page, fixture }) => {
  await mkdir(artifacts, { recursive: true }); await page.setViewportSize({ width, height: 900 }); await page.emulateMedia({ reducedMotion: 'reduce', colorScheme: 'dark' }); races(fixture, [raceView()]); await open(page, fixture); await page.getByRole('heading', { name: 'Cấu hình các tuần và lịch sử', exact: true }).scrollIntoViewIfNeeded(); await page.screenshot({ path: `${artifacts}/race-${width}-list.png` });
  await detail(page); await page.screenshot({ path: `${artifacts}/race-${width}-detail.png` }); await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await page.getByRole('button', { name: 'Tiếp tục phát hành', exact: true }).waitFor(); await page.screenshot({ path: `${artifacts}/race-${width}-preview.png` });
  const next = page.getByRole('button', { name: 'Tiếp tục phát hành', exact: true }); await next.focus(); await page.screenshot({ path: `${artifacts}/race-${width}-preview-footer.png` }); await next.click(); await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Kiểm tra'); await page.getByRole('button', { name: 'Phát hành tuần đua', exact: true }).focus(); await page.screenshot({ path: `${artifacts}/race-${width}-confirm-footer.png` }); await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click(); await expect(page.getByLabel('Tuần bắt đầu (thứ Hai)', { exact: true })).toBeFocused(); await page.screenshot({ path: `${artifacts}/race-${width}-form.png` });
  expect(await page.getByRole('dialog').evaluate(dialog => ({ white: getComputedStyle(dialog).backgroundColor, overflow: dialog.scrollWidth > dialog.clientWidth, controls: [...dialog.querySelectorAll('button,input,textarea')].every(control => control.getBoundingClientRect().height >= 48) }))).toEqual({ white: 'rgb(255, 255, 255)', overflow: false, controls: true });
  const close = page.getByRole('button', { name: 'Đóng hộp thoại', exact: true }); await close.focus(); await page.keyboard.press('Shift+Tab'); await expect(page.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toBeFocused(); await page.screenshot({ path: `${artifacts}/race-${width}-focus-footer.png` }); await page.keyboard.press('Escape'); await expect(page.getByRole('button', { name: 'Sửa bản nháp', exact: true })).toBeFocused();
});

test('phone 200% text and landscape keep modal close, footer and errors reachable', async ({ page, fixture }) => {
  await mkdir(artifacts, { recursive: true }); races(fixture, [raceView()]); await page.setViewportSize({ width: 375, height: 900 }); await page.emulateMedia({ reducedMotion: 'reduce' }); await open(page, fixture); await page.evaluate(() => document.documentElement.style.fontSize = '32px'); await detail(page); await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click(); await page.screenshot({ path: `${artifacts}/race-large-font-top.png` });
  await page.getByRole('button', { name: 'Đóng hộp thoại', exact: true }).focus(); await page.keyboard.press('Shift+Tab'); await expect(page.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toBeFocused(); await page.screenshot({ path: `${artifacts}/race-large-font-focus-footer.png` });
  expect(await page.getByRole('dialog').evaluate(dialog => { const close = dialog.querySelector('.modal-header button').getBoundingClientRect(); return { closeVisible: close.top >= 0 && close.bottom <= innerHeight, overflow: dialog.scrollWidth > dialog.clientWidth }; })).toEqual({ closeVisible: true, overflow: false });
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('dialog').getByRole('alert')).toBeFocused(); await page.screenshot({ path: `${artifacts}/race-large-font-error.png` });
  expect(await page.getByRole('button', { name: 'Đóng hộp thoại', exact: true }).evaluate(control => { const bounds = control.getBoundingClientRect(); return bounds.top >= 0 && bounds.bottom <= innerHeight; })).toBe(true); await page.keyboard.press('Escape');
  await page.evaluate(() => document.documentElement.style.fontSize = '16px'); await page.setViewportSize({ width: 812, height: 375 }); await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click(); await page.screenshot({ path: `${artifacts}/race-landscape-top.png` }); await page.getByRole('button', { name: 'Đóng hộp thoại', exact: true }).focus(); await page.keyboard.press('Shift+Tab'); await page.screenshot({ path: `${artifacts}/race-landscape-focus-footer.png` }); expect(await page.getByRole('dialog').evaluate(dialog => dialog.scrollWidth > dialog.clientWidth)).toBe(false); expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
});
