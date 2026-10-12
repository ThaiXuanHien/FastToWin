import { test, expect, login } from '../support/admin-fixture.mjs';
import { events, eventView, eventPath } from '../support/admin-event-fixture.mjs';
import { readFile, writeFile } from 'node:fs/promises';
const artifacts = '../.artifacts/admin-reward-events-delivery/task4';
async function open(page, fixture) { await login(page, fixture); await page.getByRole('button', { name: 'Sự kiện thưởng', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Sự kiện thưởng', exact: true })).toBeVisible(); }
async function detail(page) { await page.getByRole('button', { name: /^Xem sự kiện / }).click(); await expect(page.getByRole('heading', { name: 'Cuối tuần nhân đôi', level: 1 })).toBeVisible(); }
async function fillDraft(page) {
  for (const [label, value] of [['Tên tiếng Việt', 'Sự kiện mẫu'], ['Tên tiếng Anh', 'Sample event'], ['Nội dung tiếng Việt', 'Trận trực tuyến'], ['Nội dung tiếng Anh', 'Online matches'], ['Múi giờ hiển thị (IANA)', 'America/New_York'], ['Bắt đầu (UTC, bao gồm)', '2026-10-31T12:00'], ['Kết thúc (UTC, không bao gồm)', '2026-11-01T12:00'], ['Lý do thay đổi', 'Local fixture verification']]) await page.getByLabel(label, { exact: true }).fill(value);
}
test('event navigation lists drafts and server filters / pagination', async ({ page, fixture }) => {
  events(fixture); await open(page, fixture); await expect(page.locator('.event-row')).toContainText('Cuối tuần nhân đôi');
  await page.getByLabel('Trạng thái sự kiện').selectOption('ENDED'); await expect(page.locator('.event-row')).toHaveCount(0);
  expect(fixture.calls.filter(c => c.route === eventPath).at(-1).query.get('state')).toBe('ENDED');
  await page.getByLabel('Trạng thái sự kiện').selectOption(''); await page.getByRole('searchbox', { name: 'Tìm sự kiện', exact: true }).fill('nhân đôi'); await page.getByRole('button', { name: 'Tìm sự kiện', exact: true }).click();
  await expect.poll(() => fixture.calls.filter(c => c.route === eventPath).at(-1).query.get('query')).toBe('nhân đôi');
  await page.getByRole('button', { name: 'Trang sau' }).click(); await expect.poll(() => fixture.calls.filter(c => c.route === eventPath).at(-1).query.get('page')).toBe('1');
});
test('bilingual text, immutable identities and legacy unknown truth', async ({ page, fixture }) => {
  events(fixture, eventView({ creatorId: null, publisherId: null, publishedAtEpochMillis: 1791417600000, state: 'ENDED' })); await open(page, fixture); await detail(page);
  await expect(page.locator('main')).toContainText('Không rõ (dữ liệu cũ)'); await expect(page.locator('main')).toContainText('<script>alert(1)</script>');
  await expect(page.locator('main script')).toHaveCount(0); await expect(page.getByRole('button', { name: 'Sửa bản nháp', exact: true })).toHaveCount(0); await expect(page.getByRole('button', { name: 'Hủy sự kiện', exact: true })).toHaveCount(0);
});
for (const role of ['OPERATOR', 'SUPPORT']) test(`permissions ${role}`, async ({ page, fixture }) => {
  events(fixture); fixture.role = role; await login(page, fixture);
  if (role === 'SUPPORT') { await expect(page.getByRole('button', { name: 'Sự kiện thưởng', exact: true })).toHaveCount(0); return; }
  await page.getByRole('button', { name: 'Sự kiện thưởng', exact: true }).click(); await detail(page); await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await expect(page.getByRole('dialog').getByRole('img', { name: 'Vàng (GOLD) ×2', exact: true }).first()).toBeVisible(); await expect(page.getByRole('button', { name: 'Tiếp tục lên lịch', exact: true })).toHaveCount(0); await expect(page.getByRole('button', { name: 'Hủy sự kiện', exact: true })).toHaveCount(0);
});
test('UTC entries remain exact with non-Bangkok display timezone and PUT current version', async ({ page, fixture }) => {
  events(fixture); await open(page, fixture); await detail(page); await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click();
  await expect(page.getByLabel('Bắt đầu (UTC, bao gồm)', { exact: true })).toHaveValue('2026-10-31T12:00'); await fillDraft(page); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0); const call = fixture.calls.find(c => c.method === 'PUT'); expect(call.route).toMatch(/\/draft$/); expect(call.body.expectedVersion).toBe(3); expect(call.body.draft.startsAtEpochMillis).toBe(Date.parse('2026-10-31T12:00:00Z')); expect(call.body.draft.timezone).toBe('America/New_York'); expect(call.headers['x-admin-csrf']).toBe('fixture-csrf');
});
test('preview POST binds exact server token/version and schedule confirmation', async ({ page, fixture }) => {
  const state = events(fixture); await open(page, fixture); await detail(page); await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await expect(page.getByRole('dialog')).toContainText('Sự kiện giao nhau');
  const preview = fixture.calls.find(c => c.route.endsWith('/preview')); expect(preview.method).toBe('POST'); expect(preview.body).toEqual({}); expect(preview.headers['x-admin-csrf']).toBe('fixture-csrf');
  await page.getByRole('button', { name: 'Tiếp tục lên lịch', exact: true }).click(); await page.getByLabel('Lý do lên lịch', { exact: true }).fill('Local schedule'); await page.getByRole('button', { name: 'Lên lịch sự kiện', exact: true }).click();
  await expect(page.locator('.confirmation')).toContainText('Đã lên lịch'); const call = fixture.calls.find(c => c.route.endsWith('/schedule')); expect(call.body).toMatchObject({ expectedVersion: 3, previewToken: state.token, reason: 'Local schedule' }); await expect(page.getByRole('button', { name: 'Sửa bản nháp', exact: true })).toHaveCount(0);
});
test('uncertain mutation retries exact payload and request ID while pending dismissal is blocked', async ({ page, fixture }) => {
  events(fixture); let release; let attempts = 0; const original = fixture.handlers.get(eventPath);
  fixture.handlers.set(eventPath, async (call, send) => { if (call.method !== 'POST') return original(call, send); attempts++; if (attempts === 1) { await new Promise(r => release = r); send(503, { message: 'Kết quả chưa xác nhận.' }); } else send(200, eventView({ draft: call.body.draft })); return true; });
  await open(page, fixture); await page.getByRole('button', { name: 'Tạo sự kiện', exact: true }).click(); await fillDraft(page); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect.poll(() => typeof release).toBe('function');
  await expect(page.getByLabel('Tên tiếng Việt', { exact: true })).toBeDisabled(); await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toBeVisible(); await expect(page.getByRole('button', { name: 'Hủy', exact: true })).toBeDisabled(); await expect(page.getByRole('button', { name: 'Đóng hộp thoại', exact: true })).toBeDisabled();
  release(); await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Kết quả chưa xác nhận'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0);
  const calls = fixture.calls.filter(c => c.route === eventPath && c.method === 'POST'); expect(calls[0].body).toEqual(calls[1].body);
});
test('version conflict requires reload and never repeats stale mutation', async ({ page, fixture }) => {
  events(fixture); fixture.handlers.set(eventPath + '/' + eventView().id + '/draft', async (_, send) => { send(409, { code: 'CONFLICT', message: 'Phiên bản đã thay đổi.' }); return true; });
  await open(page, fixture); await detail(page); await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click(); await page.getByLabel('Lý do thay đổi', { exact: true }).fill('Conflict'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('dialog')).toContainText('Tải lại'); await expect(page.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Tải lại sự kiện', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0);
});
test('cancel confirmation pending guard and retry preserve payload', async ({ page, fixture }) => {
  events(fixture); let release; const target = eventPath + '/' + eventView().id + '/cancel';
  fixture.handlers.set(target, async (_, send) => { await new Promise(r => release = r); send(503, { message: 'Chưa xác nhận' }); return true; });
  await open(page, fixture); await detail(page); await page.getByRole('button', { name: 'Hủy sự kiện', exact: true }).click(); await page.getByLabel('Lý do hủy', { exact: true }).fill('Local cancel'); await page.getByRole('button', { name: 'Xác nhận hủy sự kiện', exact: true }).click(); await expect.poll(() => typeof release).toBe('function'); await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toBeVisible(); release(); await expect(page.getByRole('alert')).toContainText('Chưa xác nhận');
  fixture.handlers.set(target, async (_, send) => { send(200, eventView({ state: 'CANCELLED', version: 4 })); return true; }); await page.getByRole('button', { name: 'Xác nhận hủy sự kiện', exact: true }).click(); await expect(page.locator('.confirmation')).toContainText('Đã hủy'); const calls = fixture.calls.filter(c => c.route === target); expect(calls[0].body).toEqual(calls[1].body);
});
test('eight holiday suggestions stay editable drafts with manual Gregorian year and lunar dates', async ({ page, fixture }) => {
  events(fixture); await open(page, fixture); await page.getByRole('button', { name: 'Tạo sự kiện', exact: true }).click();
  const preset = page.getByLabel('Gợi ý ngày lễ', { exact: true }); expect(await preset.locator('option').count()).toBe(9);
  await preset.selectOption('lunar'); await expect(page.getByLabel('Bắt đầu (UTC, bao gồm)', { exact: true })).toHaveValue(''); await expect(page.getByRole('dialog')).toContainText('Gregorian');
  await preset.selectOption('christmas'); await page.getByLabel('Năm ngày lễ', { exact: true }).fill('2028'); await page.getByRole('button', { name: 'Áp dụng gợi ý', exact: true }).click(); await expect(page.getByLabel('Bắt đầu (UTC, bao gồm)', { exact: true })).toHaveValue('2028-12-24T00:00'); await expect(page.getByLabel('Kết thúc (UTC, không bao gồm)', { exact: true })).toHaveValue('2028-12-26T00:00'); expect(fixture.calls.filter(c => c.method === 'POST' && c.route.startsWith(eventPath))).toHaveLength(0);
});
for (const exit of ['view', 'logout', 'role', 'expiry']) test(`late API/session fence ${exit}`, async ({ page, fixture }) => {
  events(fixture); await open(page, fixture); await expect(page.locator('.event-row')).toBeVisible(); let release;
  fixture.handlers.set(eventPath, async (_, send) => { if (exit === 'role' || exit === 'expiry') { fixture.role = 'SUPPORT'; send(exit === 'role' ? 403 : 401, { message: 'Changed session' }); } else { await new Promise(r => release = r); send(503, { message: 'OLD PRIVATE ERROR' }); } return true; });
  await page.getByRole('button', { name: 'Làm mới sự kiện', exact: true }).click();
  if (exit === 'view' || exit === 'logout') { await expect.poll(() => typeof release).toBe('function'); await page.getByRole('button', { name: exit === 'view' ? 'Người chơi' : 'Đăng xuất', exact: true }).click(); release(); }
  else await expect(page.getByRole('heading', { name: exit === 'role' ? 'Người chơi' : 'Đăng nhập quản trị', exact: true })).toBeVisible();
  await expect(page.locator('.event-row')).toHaveCount(0); await expect(page.locator('main')).not.toContainText('OLD PRIVATE ERROR');
});
test('late lazy import cannot replace newly selected destination', async ({ page, fixture }) => {
  events(fixture); let release; await page.route('**/admin/admin-events.js', async route => { await new Promise(r => release = r); await route.continue(); }); await login(page, fixture); await page.getByRole('button', { name: 'Sự kiện thưởng', exact: true }).click(); await expect.poll(() => typeof release).toBe('function'); await expect(page.locator('.user-row')).toHaveCount(0); await page.getByRole('button', { name: 'Người chơi', exact: true }).click(); release(); await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible(); await expect(page.locator('.event-row')).toHaveCount(0);
});
test('preview current definition/version supersedes detail and uncertain schedule retries exact token', async ({ page, fixture }) => {
  events(fixture); const id = eventView().id, previewEvent = eventView({ version: 7 }); previewEvent.draft.nameEn = 'Updated server definition';
  fixture.handlers.set(eventPath + '/' + id + '/preview', async (_, send) => { send(200, { event: previewEvent, previewToken: 'current-v7-token', policy: 'MAX_PER_ASSET', overlaps: [], serverNowEpochMillis: 1791417600000 }); return true; });
  let release; fixture.handlers.set(eventPath + '/' + id + '/schedule', async (_, send) => { await new Promise(r => release = r); send(503, { message: 'Uncertain schedule' }); return true; });
  await open(page, fixture); await detail(page); await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await expect(page.getByRole('dialog')).toContainText('Updated server definition'); await page.getByRole('button', { name: 'Tiếp tục lên lịch', exact: true }).click(); await page.getByLabel('Lý do lên lịch', { exact: true }).fill('Retry schedule'); await page.getByRole('button', { name: 'Lên lịch sự kiện', exact: true }).click(); await expect.poll(() => typeof release).toBe('function'); await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toBeVisible(); await expect(page.getByLabel('Lý do lên lịch', { exact: true })).toBeDisabled(); release(); await expect(page.getByRole('alert')).toContainText('Uncertain schedule');
  fixture.handlers.set(eventPath + '/' + id + '/schedule', async (_, send) => { send(200, eventView({ state: 'SCHEDULED', version: 8 })); return true; }); await page.getByRole('button', { name: 'Lên lịch sự kiện', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0); const calls = fixture.calls.filter(c => c.route.endsWith('/schedule')); expect(calls[0].body).toEqual(calls[1].body); expect(calls[0].body).toMatchObject({ expectedVersion: 7, previewToken: 'current-v7-token' });
});
for (const result of ['success', 'error']) test(`late modal ${result} cannot appear after logout and new session`, async ({ page, fixture }) => {
  events(fixture); const original = fixture.handlers.get(eventPath); let release;
  fixture.handlers.set(eventPath, async (call, send) => { if (call.method === 'GET') return original(call, send); await new Promise(r => release = r); send(result === 'success' ? 200 : 503, result === 'success' ? eventView({ draft: call.body.draft }) : { message: 'OLD MODAL ERROR' }); return true; });
  await open(page, fixture); await page.getByRole('button', { name: 'Tạo sự kiện', exact: true }).click(); await fillDraft(page); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect.poll(() => typeof release).toBe('function');
  // Native dialog makes the header inert; dispatch the app's existing logout handler as session invalidation would.
  await page.getByRole('button', { name: 'Đăng xuất', exact: true, includeHidden: true }).evaluate(el => el.click()); await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị', exact: true })).toBeVisible(); release(); fixture.role = 'SUPPORT'; await page.getByLabel('Email quản trị', { exact: true }).fill('support@example.com'); await page.getByLabel('Mật khẩu', { exact: true }).fill('FixturePassword123!'); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible(); await expect(page.getByRole('dialog')).toHaveCount(0); await expect(page.locator('main')).not.toContainText('OLD MODAL ERROR'); await expect(page.getByRole('button', { name: 'Sự kiện thưởng', exact: true })).toHaveCount(0);
});
test('newer filter request fences older list success and error', async ({ page, fixture }) => {
  events(fixture); await open(page, fixture); await expect(page.locator('.event-row')).toBeVisible(); let release;
  fixture.handlers.set(eventPath, async (call, send) => { if (call.query.get('state') === 'ENDED') { await new Promise(r => release = r); send(200, { items: [eventView({ draft: { ...eventView().draft, nameVi: 'OLD PRIVATE RESULT' } })], page: 0, pageSize: 25, total: 1 }); } else send(200, { items: [], page: 0, pageSize: 25, total: 0 }); return true; });
  await page.getByLabel('Trạng thái sự kiện').selectOption('ENDED'); await expect.poll(() => typeof release).toBe('function'); await page.getByLabel('Trạng thái sự kiện').selectOption('CANCELLED'); await expect(page.locator('main')).toContainText('Không có sự kiện phù hợp'); release(); await expect(page.locator('main')).not.toContainText('OLD PRIVATE RESULT');
});
for (const width of [360, 768, 1024, 1440]) test(`responsive events ${width}`, async ({ page, fixture }) => {
  events(fixture); await page.setViewportSize({ width, height: 1000 }); await page.emulateMedia({ reducedMotion: 'reduce' }); await open(page, fixture); await expect(page.locator('.event-row')).toBeVisible();
  expect(await page.evaluate(() => [...document.querySelectorAll('body *')].filter(el => el.getBoundingClientRect().right > innerWidth + 1).map(el => [el.tagName, el.className?.baseVal ?? el.className, el.getBoundingClientRect().right]))).toEqual([]); await page.screenshot({ path: `${artifacts}/events-${width}-list.png`, fullPage: true });
  const opener = page.getByRole('button', { name: 'Tạo sự kiện', exact: true }); await opener.click(); await expect(page.getByLabel('Tên tiếng Việt', { exact: true })).toBeFocused(); await page.screenshot({ path: `${artifacts}/events-${width}-form.png` });
  await page.getByLabel('Lý do thay đổi', { exact: true }).scrollIntoViewIfNeeded(); await page.screenshot({ path: `${artifacts}/events-${width}-form-end.png` });
  expect(await page.getByRole('button', { name: 'Đóng hộp thoại', exact: true }).evaluate(el => { const box = el.getBoundingClientRect(), hit = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2); return box.top >= 0 && box.bottom <= innerHeight && el.contains(hit); })).toBe(true);
  expect(await page.locator('dialog .icon-button').evaluateAll(nodes => nodes.every(n => n.getBoundingClientRect().width >= 48 && n.getBoundingClientRect().height >= 48))).toBe(true);
  await page.keyboard.press('Escape'); await expect(opener).toBeFocused(); await detail(page); await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await expect(page.getByRole('dialog')).toContainText('hệ số cao nhất'); await page.screenshot({ path: `${artifacts}/events-${width}-preview.png` });
});
test('enlarged phone text, long content, focus containment and no storage', async ({ page, fixture }) => {
  const row = eventView(); row.draft.nameVi = 'Sự kiện rất dài '.repeat(12); row.draft.contentEn = 'LongToken'.repeat(240); events(fixture, row); await page.setViewportSize({ width: 360, height: 780 }); await page.emulateMedia({ reducedMotion: 'reduce' }); await open(page, fixture); await page.evaluate(() => document.documentElement.style.fontSize = '32px');
  expect(await page.evaluate(() => [...document.querySelectorAll('body *')].filter(el => el.getBoundingClientRect().right > innerWidth + 1).map(el => [el.tagName, el.className?.baseVal ?? el.className, el.getBoundingClientRect().right]))).toEqual([]); await page.screenshot({ path: `${artifacts}/events-enlarged.png`, fullPage: true });
  const metrics = () => page.getByRole('dialog').evaluate(dialog => {
    const rect = el => { const b = el.getBoundingClientRect(); return { x: b.x, y: b.y, width: b.width, height: b.height, top: b.top, bottom: b.bottom }; };
    const close = dialog.querySelector('[aria-label="Đóng hộp thoại"]'), b = close.getBoundingClientRect(), hit = document.elementFromPoint(b.x + b.width / 2, b.y + b.height / 2);
    return { opacity: getComputedStyle(dialog).opacity, background: getComputedStyle(dialog).backgroundColor, animations: dialog.getAnimations().map(a => ({ state: a.playState, currentTime: a.currentTime })), viewport: { width: innerWidth, height: innerHeight }, dialog: rect(dialog), header: rect(dialog.querySelector('.modal-header')), body: rect(dialog.querySelector('.modal-form')), heading: rect(dialog.querySelector('h2')), close: rect(close), closeHit: close.contains(hit), submit: rect(dialog.querySelector('button[type="submit"]')) };
  });
  const evidence = {};
  await page.getByRole('button', { name: 'Tạo sự kiện', exact: true }).click(); const close = page.getByRole('button', { name: 'Đóng hộp thoại', exact: true });
  evidence.reducedTop = await metrics(); await page.screenshot({ path: `${artifacts}/events-enlarged-modal-top.png`, animations: 'disabled' });
  await close.focus(); await page.keyboard.press('Shift+Tab'); await expect(page.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toBeFocused(); evidence.reducedFooter = await metrics(); await page.screenshot({ path: `${artifacts}/events-enlarged-modal.png`, animations: 'disabled' }); expect(evidence.reducedFooter.closeHit).toBe(true);
  await page.keyboard.press('Escape'); await page.emulateMedia({ reducedMotion: 'no-preference' }); await page.getByRole('button', { name: 'Tạo sự kiện', exact: true }).click(); evidence.defaultEntry = await metrics();
  await expect.poll(async () => (await metrics()).opacity).toBe('1'); await expect.poll(() => page.getByRole('dialog').evaluate(el => el.getAnimations().every(a => a.playState === 'finished'))).toBe(true);
  evidence.defaultTop = await metrics(); await page.screenshot({ path: `${artifacts}/events-enlarged-default-settled-top.png` }); await close.focus(); await page.keyboard.press('Shift+Tab'); await expect(page.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toBeFocused(); evidence.defaultFooter = await metrics(); await page.screenshot({ path: `${artifacts}/events-enlarged-default-settled-footer.png` });
  for (const state of [evidence.reducedTop, evidence.reducedFooter, evidence.defaultTop, evidence.defaultFooter]) { expect(state.opacity).toBe('1'); expect(state.background).toBe('rgb(255, 255, 255)'); expect(state.closeHit).toBe(true); expect(state.close.top).toBeGreaterThanOrEqual(0); expect(state.close.bottom).toBeLessThanOrEqual(780); expect(state.body.top).toBeGreaterThanOrEqual(state.header.bottom); }
  await writeFile(`${artifacts}/modal-visibility.json`, JSON.stringify(evidence, null, 2)); expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
});
test('live local public event API is no-store and safe', async ({ request }) => {
  test.skip(!process.env.ADMIN_LIVE_SETTINGS, 'Local Ktor settings required'); const settings = JSON.parse(await readFile(process.env.ADMIN_LIVE_SETTINGS, 'utf8')); expect(settings.url).toBe('http://127.0.0.1:18082'); const response = await request.get(settings.url + '/api/events'); expect(response.status()).toBe(200); expect([...new Set(response.headers()['cache-control'].split(/,\s*/))]).toEqual(['no-store']); const body = await response.json(); expect(body.serverNowEpochMillis).toBeGreaterThan(0); for (const event of body.items) for (const key of ['actorId', 'creatorId', 'publisherId', 'reason', 'previewToken']) expect(event).not.toHaveProperty(key);
});
