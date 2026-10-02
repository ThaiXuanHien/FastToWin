import { test, expect, login, player, pageOf } from '../support/admin-fixture.mjs';
import { readFile } from 'node:fs/promises';

test('login errors focus feedback, pending submission is unique and passwords are cleared', async ({ page, fixture }) => {
  let release;
  fixture.handlers.set('/admin/api/login', async (call, send) => { await new Promise(r => release = r); send(401, { code: 'UNAUTHORIZED', message: 'Đăng nhập không thành công.' }); return true; });
  await page.goto(fixture.url + '/admin/');
  await page.getByLabel('Email quản trị', { exact: true }).fill('owner@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('FixturePassword123!');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đang đăng nhập…' })).toBeDisabled();
  await expect.poll(() => fixture.calls.filter(c => c.route.endsWith('/login')).length).toBe(1);
  release();
  await expect(page.getByRole('alert')).toBeFocused();
  await expect(page.getByLabel('Mật khẩu', { exact: true })).toHaveValue('');
  fixture.handlers.clear();
  await page.getByLabel('Mật khẩu', { exact: true }).fill('FixturePassword123!');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
});

test('directory search pagination and empty states', async ({ page, fixture }) => {
  await login(page, fixture);
  await expect(page.getByText('Đã đăng ký', { exact: true })).toBeVisible();
  await expect(page.getByText('Đang hoạt động', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click();
  await expect(page.getByText('Trang thứ hai', { exact: true })).toBeVisible();
  await page.getByLabel('Tìm người chơi').fill('empty');
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByText('Không có người chơi phù hợp.')).toBeVisible();
  const last = fixture.calls.filter(c => c.route === '/admin/api/users').at(-1);
  expect(last.query.get('q')).toBe('empty'); expect(last.query.get('page')).toBe('0');
});

test('details and independently paginated histories show safe text and null activity', async ({ page, fixture }) => {
  const malicious = '<img src=x onerror="window.injected=true">';
  fixture.handlers.set('/admin/api/users/' + player.id, async (_, send) => { send(200, { ...player, displayName: malicious }); return true; });
  await login(page, fixture);
  await page.getByRole('button', { name: 'Xem người chơi' }).first().click();
  await expect(page.getByRole('heading', { name: malicious, exact: true })).toBeVisible();
  await expect(page.getByText('Chưa có dữ liệu', { exact: true })).toBeVisible();
  await expect(page.getByText('Bang mẫu', { exact: true })).toBeVisible();
  await expect(page.getByText('Đấu hạng', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click();
  await page.getByRole('button', { name: 'Ví', exact: true }).click();
  await expect(page.getByText('Thưởng trận đấu', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Nguồn quà', exact: true }).click();
  await expect(page.getByText('Bồi hoàn', { exact: true })).toBeVisible();
  expect(fixture.calls.filter(c => c.route.endsWith('/matches')).at(-1).query.get('page')).toBe('1');
  expect(fixture.calls.filter(c => c.route.endsWith('/wallet')).at(-1).query.get('page')).toBe('0');
  expect(await page.evaluate(() => window.injected)).toBeUndefined();
  await expect(page.locator('main img')).toHaveCount(0);
});

for (const role of ['SUPPORT', 'OPERATOR']) test(`${role} hides owner controls and Support restricts email search`, async ({ page, fixture }) => {
  fixture.role = role; await login(page, fixture);
  await expect(page.getByRole('button', { name: 'Tài khoản quản trị', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Nhật ký kiểm toán', exact: true })).toHaveCount(0);
  if (role === 'SUPPORT') {
    await expect(page.getByText('s***@example.com', { exact: true })).toBeVisible();
    const before = fixture.calls.length;
    await page.getByLabel('Tìm người chơi').fill('player@example.com');
    await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('Support chỉ tìm bằng ID hoặc tên');
    expect(fixture.calls.length).toBe(before);
  }
});

test('refresh error clears stale detail, expiry clears every sensitive view', async ({ page, fixture }) => {
  await login(page, fixture); await page.getByRole('button', { name: 'Xem người chơi' }).first().click();
  await expect(page.getByText('seeded-player@example.com', { exact: true })).toBeVisible();
  fixture.handlers.set('/admin/api/users/' + player.id, async (_, send) => { send(503, { code: 'STORAGE_UNAVAILABLE', message: 'Kho dữ liệu người chơi chưa sẵn sàng.' }); return true; });
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Kho dữ liệu');
  await expect(page.getByText('seeded-player@example.com', { exact: true })).toHaveCount(0);
  fixture.handlers.set('/admin/api/users/' + player.id, async (_, send) => { send(401, { code: 'UNAUTHORIZED', message: 'Phiên đã hết hạn.' }); return true; });
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeVisible();
  await expect(page.getByText('owner@example.com', { exact: true })).toHaveCount(0);
  await expect(page.getByText('seeded-player@example.com', { exact: true })).toHaveCount(0);
});

for (const staleStatus of [200, 503]) test(`older search ${staleStatus} cannot replace current results or loading state`, async ({ page, fixture }) => {
  // Simulate a transport whose completion ignores cancellation, exercising the sequence fence.
  await page.addInitScript(() => { const original = window.fetch; window.fetch = (url, options) => original(url, { ...options, signal: undefined }); });
  await login(page, fixture); let release;
  fixture.handlers.set('/admin/api/users', async (call, send) => {
    if (call.query.get('q') !== 'old') return false;
    await new Promise(r => release = r); send(staleStatus, staleStatus === 200 ? pageOf([{ ...player, displayName: 'Kết quả cũ' }]) : { code: 'STORAGE_UNAVAILABLE', message: 'Lỗi tìm kiếm cũ' }); return true;
  });
  await page.getByLabel('Tìm người chơi').fill('old'); await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Đang tải');
  await page.getByLabel('Tìm người chơi').fill('new'); await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByText('Người chơi mẫu', { exact: true })).toBeVisible(); release();
  await page.waitForTimeout(100);
  await expect(page.getByText('Lỗi tìm kiếm cũ')).toHaveCount(0);
  await expect(page.getByText('Kết quả cũ')).toHaveCount(0);
  await expect(page.getByRole('status')).not.toContainText('Đang tải');
});

for (const event of ['navigation', 'logout', 'expiry']) test(`deferred callback after ${event} cannot repopulate player state`, async ({ page, fixture }) => {
  await page.addInitScript(() => { const original = window.fetch; window.fetch = (url, options) => original(url, { ...options, signal: undefined }); });
  await login(page, fixture); let release;
  fixture.handlers.set('/admin/api/users/' + player.id, async (_, send) => { await new Promise(r => release = r); send(200, player); return true; });
  await page.getByRole('button', { name: 'Xem người chơi' }).first().click();
  await expect(page.getByRole('status')).toContainText('Đang tải');
  if (event === 'navigation') await page.getByRole('button', { name: 'Nhật ký kiểm toán', exact: true }).click();
  else if (event === 'logout') await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  else {
    fixture.handlers.set('/admin/api/audit', async (_, send) => { send(401, { code: 'UNAUTHORIZED', message: 'Phiên đã hết hạn.' }); return true; });
    await page.getByRole('button', { name: 'Nhật ký kiểm toán', exact: true }).click();
  }
  await expect(page.getByRole('heading', { name: event === 'navigation' ? 'Nhật ký kiểm toán' : 'Đăng nhập quản trị', exact: true })).toBeVisible();
  release(); await page.waitForTimeout(100);
  await expect(page.getByText('seeded-player@example.com', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Người chơi mẫu', { exact: true })).toHaveCount(0);
});

test('server role changes remove Owner controls after forbidden response', async ({ page, fixture }) => {
  await login(page, fixture);
  fixture.role = 'SUPPORT';
  fixture.handlers.set('/admin/api/accounts', async (_, send) => { send(403, { code: 'FORBIDDEN', message: 'Bạn không có quyền thực hiện thao tác này.' }); return true; });
  await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Tài khoản quản trị', exact: true })).toHaveCount(0);
  await expect(page.getByText('s***@example.com', { exact: true })).toBeVisible();
});

test('pending logout disables sign-in until cookie clearing completes', async ({ page, fixture }) => {
  await login(page, fixture); let release;
  fixture.handlers.set('/admin/api/logout', async (_, send, state) => { await new Promise(r => release = r); state.signedIn = false; send(200, { ok: true }); return true; });
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeDisabled();
  await expect.poll(() => typeof release).toBe('function');
  release();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeEnabled();
});

test('Owner creates accounts with CSRF, prevents duplicates and reuses request IDs only for identical retries', async ({ page, fixture }) => {
  await login(page, fixture); await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await page.getByRole('button', { name: 'Cấp tài khoản', exact: true }).click();
  await page.getByLabel('Email quản trị mới').fill('new@example.com');
  await page.getByLabel('Mật khẩu tạm').fill('NewPassword123!');
  await page.getByLabel('Vai trò mới').selectOption('SUPPORT');
  await page.getByLabel('Lý do tạo').fill('Hỗ trợ khách hàng');
  let release;
  fixture.handlers.set('/admin/api/accounts', async (call, send) => {
    if (call.method === 'GET') return false;
    await new Promise(r => release = r); send(503, { code: 'STORAGE_UNAVAILABLE', message: 'Vui lòng thử lại.' }); return true;
  });
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đang lưu…' })).toBeDisabled();
  await expect.poll(() => typeof release).toBe('function');
  release();
  await expect(page.getByRole('alert')).toContainText('Vui lòng thử lại');
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect.poll(() => fixture.calls.filter(c => c.route === '/admin/api/accounts' && c.method === 'POST').length).toBe(2);
  release();
  await expect(page.getByRole('alert')).toContainText('Vui lòng thử lại');
  const posts = () => fixture.calls.filter(c => c.route === '/admin/api/accounts' && c.method === 'POST');
  expect(posts()).toHaveLength(2);
  expect(posts()[0].body).toEqual(posts()[1].body);
  expect(posts()[0].headers['x-admin-csrf']).toBe('fixture-csrf');
  await page.getByLabel('Lý do tạo').fill('Lý do khác');
  fixture.handlers.clear();
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã tạo' })).toBeVisible();
  expect(posts()[2].body.requestId).not.toBe(posts()[0].body.requestId);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Cấp tài khoản', exact: true }).click();
  await expect(page.getByLabel('Mật khẩu tạm')).toHaveValue('');
});

for (const action of ['replacement', 'cancel', 'refresh']) test(`pending access mutation blocks editor ${action}`, async ({ page, fixture }) => {
  await login(page, fixture); await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa quyền support@example.com', exact: true }).click();
  await page.getByLabel('Lý do thay đổi').fill('Pending support operation');
  let release;
  fixture.handlers.set('/admin/api/accounts/support-2/access', async (call, send) => {
    await new Promise(r => release = r);
    send(200, { id: 'support-2', email: 'support@example.com', role: call.body.role, active: call.body.active }); return true;
  });
  await page.getByRole('button', { name: 'Lưu quyền truy cập', exact: true }).click();
  await expect.poll(() => typeof release).toBe('function');
  const control = page.getByRole('button', { name: action === 'replacement' ? 'Sửa quyền owner@example.com' : action === 'cancel' ? 'Hủy' : 'Làm mới', exact: true, includeHidden: true });
  await expect(control).toBeDisabled();
  const accountReads = fixture.calls.filter(c => c.route === '/admin/api/accounts' && c.method === 'GET').length;
  await control.click({ force: true });
  await expect(page.getByLabel('Lý do thay đổi')).toHaveValue('Pending support operation');
  expect(fixture.calls.filter(c => c.route === '/admin/api/accounts' && c.method === 'GET')).toHaveLength(accountReads);
  release();
  await expect(page.getByLabel('Lý do thay đổi')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Sửa quyền owner@example.com', exact: true })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Làm mới', exact: true })).toBeEnabled();
});

test('successful account creation resets to Support and the next untouched role submits Support', async ({ page, fixture }) => {
  await login(page, fixture); await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await page.getByRole('button', { name: 'Cấp tài khoản', exact: true }).click();
  await expect(page.getByLabel('Vai trò mới')).toHaveValue('SUPPORT');
  await page.getByLabel('Email quản trị mới').fill('first-support@example.com');
  await page.getByLabel('Mật khẩu tạm').fill('NewPassword123!');
  await page.getByLabel('Lý do tạo').fill('First support account');
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Đã tạo' })).toBeVisible();
  await page.getByRole('button', { name: 'Cấp tài khoản', exact: true }).click();
  await expect(page.getByLabel('Vai trò mới')).toHaveValue('SUPPORT');
  await page.getByLabel('Email quản trị mới').fill('second-support@example.com');
  await page.getByLabel('Mật khẩu tạm').fill('NewPassword123!');
  await page.getByLabel('Lý do tạo').fill('Second support account');
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(fixture.calls.filter(c => c.route === '/admin/api/accounts' && c.method === 'POST').map(c => c.body.role)).toEqual(['SUPPORT', 'SUPPORT']);
});

test('Owner changes access with reason and self-change returns to login', async ({ page, fixture }) => {
  await login(page, fixture); await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa quyền owner@example.com', exact: true }).click();
  await page.getByLabel('Vai trò truy cập').selectOption('OPERATOR');
  await page.getByLabel('Lý do thay đổi').fill('Bàn giao quyền');
  await page.getByRole('button', { name: 'Lưu quyền truy cập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeVisible();
  const request = fixture.calls.find(c => c.route.endsWith('/owner-1/access'));
  expect(request.body).toMatchObject({ role: 'OPERATOR', active: true, reason: 'Bàn giao quyền' });
  expect(request.body.requestId).toMatch(/^[a-zA-Z0-9_-]{1,128}$/);
});

test('logout and browser Back do not restore sensitive content', async ({ page, fixture }) => {
  await login(page, fixture); await page.getByRole('button', { name: 'Xem người chơi' }).first().click();
  await expect(page.getByText('seeded-player@example.com', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await page.goBack();
  await expect(page.getByText('seeded-player@example.com', { exact: true })).toHaveCount(0);
});

for (const width of [360, 768, 1024, 1440]) test(`responsive ${width}px layout and keyboard focus`, async ({ page, fixture }) => {
  await page.setViewportSize({ width, height: 1000 }); await login(page, fixture);
  await page.keyboard.press('Tab');
  expect(await page.evaluate(() => document.activeElement.tagName)).not.toBe('BODY');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `.artifacts/admin-${width}.png`, fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: 'Xem người chơi' }).first().click();
  await expect(page.getByText('Bang mẫu', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `.artifacts/admin-detail-${width}.png`, fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await expect(page.getByLabel('Email quản trị mới')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Sửa quyền support@example.com', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `.artifacts/admin-accounts-${width}.png`, fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: 'Cấp tài khoản', exact: true }).click();
  await expect(page.getByLabel('Email quản trị mới')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `.artifacts/admin-create-${width}.png`, fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: 'Hủy', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa quyền support@example.com', exact: true }).click();
  await expect(page.getByLabel('Vai trò truy cập')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `.artifacts/admin-editor-${width}.png`, fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: 'Hủy', exact: true }).click();
  await page.getByRole('button', { name: 'Nhật ký kiểm toán', exact: true }).click();
  await expect(page.getByText('Đăng nhập quản trị', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `.artifacts/admin-audit-${width}.png`, fullPage: true, animations: 'disabled' });
});

test('account modal keeps keyboard focus inside, dismisses safely and clears abandoned secrets', async ({ page, fixture }) => {
  await login(page, fixture); await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await expect(page.getByLabel('Email quản trị mới')).toHaveCount(0);
  const trigger = page.getByRole('button', { name: 'Cấp tài khoản', exact: true });
  await trigger.click();
  const dialog = page.getByRole('dialog', { name: 'Cấp tài khoản mới', exact: true });
  await expect(dialog).toBeVisible();
  await expect(page.getByLabel('Email quản trị mới')).toBeFocused();
  await page.getByLabel('Mật khẩu tạm').fill('AbandonedPassword123!');
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => !!document.activeElement.closest('dialog'))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0); await expect(trigger).toBeFocused();
  await trigger.click();
  await expect(page.getByLabel('Mật khẩu tạm')).toHaveValue('');
  await page.getByRole('button', { name: 'Đóng hộp thoại', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const edit = page.getByRole('button', { name: 'Sửa quyền support@example.com', exact: true });
  await edit.click(); await expect(page.getByRole('dialog', { name: 'Quyền truy cập', exact: true })).toBeVisible();
  await page.getByRole('dialog').locator('.checkbox').click();
  await expect(page.getByLabel('Cho phép truy cập')).not.toBeChecked();
  await page.getByRole('button', { name: 'Hủy', exact: true }).click(); await expect(edit).toBeFocused();
});

test('pending modal blocks Escape, close and backdrop until the server responds', async ({ page, fixture }) => {
  await login(page, fixture); await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await page.getByRole('button', { name: 'Cấp tài khoản', exact: true }).click();
  await page.getByLabel('Email quản trị mới').fill('pending@example.com');
  await page.getByLabel('Mật khẩu tạm').fill('PendingPassword123!');
  await page.getByLabel('Lý do tạo').fill('Kiểm thử modal');
  let release;
  fixture.handlers.set('/admin/api/accounts', async (call, send) => {
    if (call.method === 'GET') return false;
    await new Promise(resolve => release = resolve); send(503, { message: 'Lưu chưa thành công.' }); return true;
  });
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect.poll(() => typeof release).toBe('function');
  await expect(page.getByRole('button', { name: 'Hủy', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Đóng hộp thoại', exact: true })).toBeDisabled();
  await page.keyboard.press('Escape'); await page.mouse.click(2, 2);
  await expect(page.getByRole('dialog')).toBeVisible();
  release(); await expect(page.getByRole('alert')).toBeFocused();
  await expect(page.getByLabel('Email quản trị mới')).toHaveValue('pending@example.com');
  await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(fixture.calls.filter(call => call.route === '/admin/api/accounts' && call.method === 'POST')).toHaveLength(1);
});

test('expired mutation removes the modal and every secret before returning to login', async ({ page, fixture }) => {
  await login(page, fixture); await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await page.getByRole('button', { name: 'Cấp tài khoản', exact: true }).click();
  await page.getByLabel('Email quản trị mới').fill('expired@example.com');
  await page.getByLabel('Mật khẩu tạm').fill('ExpiredPassword123!');
  await page.getByLabel('Lý do tạo').fill('Expired session');
  fixture.handlers.set('/admin/api/accounts', async (call, send) => {
    if (call.method === 'GET') return false;
    send(401, { message: 'Phiên hết hạn.' }); return true;
  });
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByLabel('Mật khẩu tạm')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeEnabled();
});

test('light Material console keeps text legible and controls usable', async ({ page, fixture }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto(fixture.url + '/admin/');
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeVisible();
  await page.screenshot({ path: '.artifacts/admin-login-1440.png', fullPage: true, animations: 'disabled' });
  await page.setViewportSize({ width: 360, height: 1000 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: '.artifacts/admin-login-360.png', fullPage: true, animations: 'disabled' });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await login(page, fixture);
  // The brighter hover state must remain legible too.
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).hover();
  const measurements = await page.evaluate(() => {
    const rgb = color => color.match(/[\d.]+/g).slice(0, 3).map(Number);
    const luminance = color => rgb(color).map(value => {
      const channel = value / 255;
      return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4;
    }).reduce((sum, value, index) => sum + value * [.2126, .7152, .0722][index], 0);
    const contrast = (foreground, background) => {
      const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
      return (values[0] + .05) / (values[1] + .05);
    };
    const background = getComputedStyle(document.documentElement).backgroundColor;
    const heading = getComputedStyle(document.querySelector('h1'));
    const help = getComputedStyle(document.getElementById('search-help'));
    const primary = getComputedStyle(document.querySelector('button.primary'));
    return {
      light: luminance(background) > .9,
      heading: contrast(heading.color, background),
      help: contrast(help.color, background),
      primary: contrast(primary.color, primary.backgroundColor),
      controls: [...document.querySelectorAll('button, input:not([type="checkbox"]), select')]
        .filter(element => element.getClientRects().length)
        .every(element => element.getBoundingClientRect().height >= 44),
    };
  });
  expect(measurements.light).toBe(true);
  expect(measurements.heading).toBeGreaterThanOrEqual(4.5);
  expect(measurements.help).toBeGreaterThanOrEqual(4.5);
  expect(measurements.primary).toBeGreaterThanOrEqual(4.5);
  expect(measurements.controls).toBe(true);
});

test.describe('live backend', () => {
test('real loopback backend auth, directory, audit and logout', async ({ page }) => {
  test.skip(!process.env.ADMIN_LIVE_SETTINGS, 'Set ADMIN_LIVE_SETTINGS to disposable loopback settings');
  const settings = JSON.parse(await readFile(process.env.ADMIN_LIVE_SETTINGS, 'utf8'));
  if (settings.url !== 'http://127.0.0.1:18082' || settings.databaseUrl !== 'jdbc:postgresql://127.0.0.1:15432/admin_ui') throw new Error('Unexpected live test target');
  const password = await readFile(settings.passwordFile, 'utf8');
  await page.goto(settings.url + '/admin/');
  await page.getByLabel('Email quản trị', { exact: true }).fill(settings.email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
  await page.getByLabel('Tìm người chơi').fill('player@admin-test.invalid');
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await page.getByRole('button', { name: 'Xem người chơi' }).first().click();
  await expect(page.getByText('player@admin-test.invalid', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Nhật ký kiểm toán', exact: true }).click();
  await expect(page.getByText('Tra cứu người chơi', { exact: true }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Tài khoản quản trị', exact: true }).click();
  await page.getByRole('button', { name: 'Cấp tài khoản', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Cấp tài khoản mới', exact: true })).toBeVisible();
  await expect(page.getByLabel('Email quản trị mới')).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByText('Đã đăng xuất.', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeVisible();
});
});
