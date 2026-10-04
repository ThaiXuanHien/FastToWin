import { test, expect, login } from '../support/admin-fixture.mjs';
import { maintenance, maintenancePath, openMaintenance, fillMaintenance } from '../support/maintenance-fixture.mjs';
const open = (p, f) => openMaintenance(p, f, login);
const edit = p => p.getByRole('button', { name: 'Cấu hình bảo trì', exact: true }).click();
const review = p => p.getByRole('button', { name: 'Xem lại thay đổi', exact: true }).click();
const save = p => p.getByRole('button', { name: 'Xác nhận lưu', exact: true }).click();

test('Owner planned, emergency and disable submit exact versioned DTOs and show observed runtime separately', async ({ page, fixture }) => {
  maintenance(fixture); await open(page, fixture);
  await expect(page.getByText('3 trận', { exact: true })).toBeVisible();
  await expect(page.getByText(/Chưa xác nhận tất cả kết nối đã đóng/)).toBeVisible();
  await edit(page); await fillMaintenance(page);
  await page.getByLabel('Kết thúc dự kiến (Bangkok)').fill('2026-10-04T16:30');
  await review(page); await save(page);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const first = fixture.calls.find(c => c.method === 'POST' && c.route === maintenancePath);
  expect(first.body).toEqual({ enabled: true, expectedVersion: 7, requestId: expect.any(String), mode: 'PLANNED', cause: 'UPDATE',
    messageVi: 'Cập nhật máy chủ', messageEn: 'Server update', reason: 'Kiểm thử cục bộ', warningMinutes: 5, drainMinutes: 15,
    expectedEndEpochMillis: Date.parse('2026-10-04T16:30:00+07:00') });
  expect(first.headers['x-admin-csrf']).toBe('fixture-csrf');
  await edit(page); await page.getByLabel('Chế độ').selectOption('EMERGENCY'); await review(page);
  await expect(page.getByRole('dialog').getByText(/Trận chưa hoàn tất sẽ dừng/)).toBeVisible(); await save(page);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Tắt bảo trì', exact: true }).click();
  await page.getByLabel('Lý do nội bộ').fill('Hoàn tất'); await save(page);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(fixture.calls.filter(c => c.method === 'POST' && c.route === maintenancePath).at(-1).body).toEqual({ enabled: false, expectedVersion: 9, requestId: expect.any(String), reason: 'Hoàn tất' });
});
for (const role of ['OPERATOR', 'SUPPORT']) test(`${role} can observe but cannot edit maintenance`, async ({ page, fixture }) => {
  fixture.role = role; maintenance(fixture); await open(page, fixture);
  await expect(page.getByText('3 trận', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cấu hình bảo trì', exact: true })).toHaveCount(0);
  await expect(page.getByText(/Chỉ Owner/)).toBeVisible();
});
test('environment override prevents mutations and names effective source honestly', async ({ page, fixture }) => {
  maintenance(fixture, 'ACTIVE', 'ENVIRONMENT'); await open(page, fixture);
  await expect(page.getByText(/FASTTOWIN_MAINTENANCE/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cấu hình bảo trì', exact: true })).toBeDisabled();
});
test('enabled schedule stays readonly and exact saved durations survive edits and failed retry', async ({ page, fixture }) => {
  const data = maintenance(fixture, 'DRAINING'); data.persisted.warningMinutes = 9; data.persisted.drainMinutes = 23;
  await open(page, fixture); await edit(page);
  await expect(page.getByLabel('Báo trước (phút)')).toHaveAttribute('readonly', '');
  await expect(page.getByLabel('Chờ trận kết thúc (phút)')).toHaveValue('23');
  await page.getByLabel('Thông báo tiếng Việt').fill('Thông báo mới'); await review(page);
  const normal = fixture.handlers.get(maintenancePath); let release, first = true;
  fixture.handlers.set(maintenancePath, async (call, send) => {
    if (call.method === 'POST' && first) { first = false; await new Promise(r => release = r); send(503, { code: 'STORAGE_UNAVAILABLE', message: 'Kết quả chưa xác nhận.' }); return true; } return normal(call, send);
  });
  await save(page); await expect(page.getByRole('dialog')).toHaveAttribute('aria-busy', 'true');
  await page.keyboard.press('Escape'); await page.mouse.click(1, 1);
  await expect(page.getByRole('button', { name: 'Hủy', exact: true })).toBeDisabled();
  await expect.poll(() => typeof release).toBe('function'); release();
  await expect(page.getByRole('alert')).toContainText('Kết quả chưa xác nhận'); await save(page);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const calls = fixture.calls.filter(c => c.method === 'POST' && c.route === maintenancePath);
  expect(calls[0].body).toEqual(calls[1].body); expect(calls[0].body.warningMinutes).toBe(9); expect(calls[0].body.drainMinutes).toBe(23);
  expect(data.persisted.drainDeadlineEpochMillis).toBe(1791001200000);
});
for (const code of ['CONFLICT', 'MODE_LOCKED']) test(`${code} requires reload and never retries stale data`, async ({ page, fixture }) => {
  maintenance(fixture); await open(page, fixture); await edit(page); await fillMaintenance(page); await review(page);
  fixture.handlers.set(maintenancePath, async (_, send) => { send(409, { code, message: 'Conflict' }); return true; });
  await save(page); await expect(page.getByRole('alert')).toContainText('Tải lại');
  await expect(page.getByRole('button', { name: 'Xác nhận lưu', exact: true })).toBeDisabled();
});
test('native range validation prevents invalid schedule and emergency cannot become planned', async ({ page, fixture }) => {
  const data = maintenance(fixture); await open(page, fixture); await edit(page); await fillMaintenance(page);
  for (const [label, value] of [['Báo trước (phút)', '61'], ['Chờ trận kết thúc (phút)', '0']]) {
    await page.getByLabel(label).fill(value); await review(page); await expect(page.getByLabel(label)).toBeVisible();
    expect(fixture.calls.filter(c => c.route === maintenancePath && c.method === 'POST')).toHaveLength(0);
    await page.getByLabel(label).fill('5');
  }
  await page.keyboard.press('Escape'); data.persisted.state = 'MAINTENANCE'; data.persisted.mode = 'EMERGENCY';
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click(); await edit(page);
  await expect(page.getByLabel('Chế độ').locator('option[value="PLANNED"]')).toBeDisabled();
});
for (const status of [401, 403]) test(`${status} clears pending maintenance and sensitive content`, async ({ page, fixture }) => {
  maintenance(fixture); await open(page, fixture); await edit(page); await fillMaintenance(page); await review(page);
  fixture.handlers.set(maintenancePath, async (_, send) => { fixture.role = 'SUPPORT'; if (status === 401) fixture.signedIn = false; send(status, { message: 'Quyền đã thay đổi.' }); return true; });
  await save(page); await expect(page.getByRole('heading', { name: status === 401 ? 'Đăng nhập quản trị' : 'Người chơi', exact: true })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});
for (const width of [360, 768, 1024, 1440]) test(`maintenance controls, focus and Material screenshots at ${width}`, async ({ page, fixture }) => {
  maintenance(fixture); await page.setViewportSize({ width, height: 1000 }); await page.emulateMedia({ reducedMotion: 'reduce' }); await open(page, fixture);
  await page.screenshot({ path: `../.artifacts/admin-maintenance-${width}-list.png`, fullPage: true });
  await edit(page); await fillMaintenance(page);
  await page.getByLabel('Chế độ').focus();
  await expect(page.getByLabel('Chế độ')).toBeInViewport();
  await page.screenshot({ path: `../.artifacts/admin-maintenance-${width}-form.png` });
  await page.getByRole('button', { name: 'Xem lại thay đổi', exact: true }).focus();
  await expect(page.getByRole('button', { name: 'Xem lại thay đổi', exact: true })).toBeInViewport();
  await page.screenshot({ path: `../.artifacts/admin-maintenance-${width}-form-end.png` });
  await review(page); await page.screenshot({ path: `../.artifacts/admin-maintenance-${width}-confirm.png`, fullPage: true });
  for (let i = 0; i < 8; i++) { await page.keyboard.press('Tab'); expect(await page.evaluate(() => !!document.activeElement.closest('dialog'))).toBe(true); }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.keyboard.press('Escape'); await expect(page.getByRole('button', { name: 'Cấu hình bảo trì', exact: true })).toBeFocused();
});

test('late response cannot resurrect abandoned maintenance view and runtime messages stay text only', async ({ page, fixture }) => {
  const data = maintenance(fixture); data.persisted.messageVi = '<img src=x onerror=alert(1)>';
  await open(page, fixture); await expect(page.getByText('<img src=x onerror=alert(1)>', { exact: true })).toBeVisible();
  expect(await page.locator('.maintenance-message img').count()).toBe(0);
  let release; fixture.handlers.set(maintenancePath, async (_, send) => { await new Promise(r => release = r); send(200, data); return true; });
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect.poll(() => typeof release).toBe('function');
  await page.getByRole('button', { name: 'Người chơi', exact: true }).click(); release();
  await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
  await expect(page.locator('.maintenance-status')).toHaveCount(0);
});
test('unavailable observation does not fabricate active matches or confirmed closure', async ({ page, fixture }) => {
  const data = maintenance(fixture, 'DRAINING'); data.runtime = { available: false, version: null, activeMatches: null, observedAtEpochMillis: null };
  data.effective = { source: 'UNAVAILABLE', state: null };
  await open(page, fixture); await expect(page.getByRole('heading', { name: 'Chưa xác định', exact: true })).toBeVisible();
  await expect(page.getByText(/Quan sát máy chủ đang không khả dụng/)).toBeVisible();
  await expect(page.getByText('0 trận', { exact: true })).toHaveCount(0);
});
test('touch and landscape modal scroll exposes first field, reason, and footer without horizontal overflow', async ({ browser, fixture }) => {
  maintenance(fixture);
  const context = await browser.newContext({ viewport: { width: 844, height: 390 }, hasTouch: true, reducedMotion: 'reduce' });
  const page = await context.newPage();
  try {
    await open(page, fixture); await edit(page);
    await expect(page.getByLabel('Chế độ')).toBeInViewport();
    await page.getByLabel('Lý do nội bộ').fill('Landscape verification');
    await expect(page.getByLabel('Lý do nội bộ')).toBeInViewport();
    await page.getByRole('button', { name: 'Xem lại thay đổi', exact: true }).focus();
    await expect(page.getByRole('button', { name: 'Xem lại thay đổi', exact: true })).toBeInViewport();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: '../.artifacts/admin-maintenance-landscape.png' });
    await page.getByRole('button', { name: 'Hủy', exact: true }).tap(); await expect(page.getByRole('dialog')).toHaveCount(0);
  } finally { await context.close(); }
});
