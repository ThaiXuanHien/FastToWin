import { test, expect, login } from '../support/admin-fixture.mjs';
import { gifts, fillGift, giftId, player } from '../support/admin-gift-fixture.mjs';
async function open(page, fixture) { await login(page, fixture); await page.getByRole('button', { name: 'Quà admin', exact: true }).click(); await expect(page.getByRole('status')).toHaveText('0 chiến dịch.'); }
async function draft(page) { await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click(); await fillGift(page); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Quà kiểm thử', exact: true, level: 1 })).toBeVisible(); }
async function preview(page) { await page.getByRole('button', { name: 'Xem trước', exact: true }).click(); await expect(page.getByRole('dialog', { name: 'Xem trước chiến dịch' }).getByText('Tổng Vàng', { exact: true })).toBeVisible(); }
async function confirm(page) { await page.getByRole('button', { name: 'Tiếp tục công bố', exact: true }).click(); await expect(page.getByRole('dialog', { name: 'Xác nhận công bố' })).toBeVisible(); }

test('UUID list accepts one ID and reports format separately from maximum count', async ({ page, fixture }) => {
  const state = gifts(fixture); await open(page, fixture);
  await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click(); await fillGift(page);
  await page.getByLabel('Người nhận', { exact: true }).selectOption('SINGLE_PLAYER');
  await expect(page.getByLabel('ID người chơi', { exact: true })).toBeHidden();
  await expect(page.getByLabel('ID người chơi', { exact: true })).not.toHaveAttribute('required', '');
  await page.getByLabel('Người nhận', { exact: true }).selectOption('USER_IDS');
  const ids = page.getByLabel('ID người chơi', { exact: true }); await ids.fill(player.playerCode);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Một người chơi');
  await expect(page.getByRole('alert')).not.toContainText('10.000');
  await ids.fill(Array(10001).fill('x').join(','));
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('vượt giới hạn 10.000');
  await ids.fill(player.id); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Quà kiểm thử', exact: true, level: 1 })).toBeVisible();
  expect(state.detail.campaign.draft.userIds).toEqual([player.id]);
});

for (const width of [360, 1440]) for (const reference of [player.playerCode.toLowerCase(), player.email.toUpperCase()]) test(`individual recipient ${reference} at ${width}`, async ({ page, fixture }) => {
  const state = gifts(fixture);
  fixture.handlers.set('/admin/api/gift-recipient', async (call, send) => {
    expect(call.query.get('q')).toBe(reference); send(200, player); return true;
  });
  await page.setViewportSize({ width, height: 1000 }); await open(page, fixture);
  await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click(); await fillGift(page);
  await page.getByLabel('Người nhận', { exact: true }).selectOption('SINGLE_PLAYER');
  const input = page.getByLabel('Mã người chơi hoặc email', { exact: true }); await input.fill(reference);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Tra cứu');
  expect(fixture.calls.filter(c => c.route === '/admin/api/gift-campaigns' && c.method === 'POST')).toHaveLength(0);
  await page.getByRole('button', { name: 'Tra cứu người nhận', exact: true }).click();
  await expect(page.locator('.gift-recipient')).toContainText(player.playerCode);
  await expect(page.locator('.gift-recipient')).toContainText(player.email);
  await page.screenshot({ path: `../.artifacts/admin-player-code/recipient-${width}-${reference.includes('@') ? 'email' : 'code'}.png`, fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Quà kiểm thử', exact: true, level: 1 })).toBeVisible();
  expect(state.detail.campaign.draft.audience).toBe('USER_IDS');
  expect(state.detail.campaign.draft.userIds).toEqual([player.id]);
  await preview(page);
  await expect(page.getByRole('dialog').locator('.gift-recipient')).toContainText(player.playerCode);
  await confirm(page);
  await expect(page.getByRole('dialog').locator('.gift-recipient')).toContainText(player.email);
});

test('individual recipient must be looked up again after editing and lookup failure cannot save', async ({ page, fixture }) => {
  gifts(fixture); let calls = 0;
  fixture.handlers.set('/admin/api/gift-recipient', async (_, send) => {
    if (++calls === 1) send(200, player); else send(404, { code: 'NOT_FOUND', message: 'Không tìm thấy người nhận.' }); return true;
  });
  await open(page, fixture); await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click(); await fillGift(page);
  await page.getByLabel('Người nhận', { exact: true }).selectOption('SINGLE_PLAYER');
  await page.getByLabel('Mã người chơi hoặc email', { exact: true }).fill(player.playerCode);
  await page.getByRole('button', { name: 'Tra cứu người nhận', exact: true }).click();
  await expect(page.locator('.gift-recipient')).toContainText(player.playerCode);
  await page.getByLabel('Mã người chơi hoặc email', { exact: true }).fill('UNKNOWN');
  await expect(page.locator('.gift-recipient')).toBeEmpty();
  await page.getByRole('button', { name: 'Tra cứu người nhận', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Không tìm thấy');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  expect(fixture.calls.filter(c => c.route === '/admin/api/gift-campaigns' && c.method === 'POST')).toHaveLength(0);
});

test('late recipient lookup cannot restore selection after input changes', async ({ page, fixture }) => {
  gifts(fixture); let release, calls = 0;
  fixture.handlers.set('/admin/api/gift-recipient', async (_, send) => {
    if (++calls === 1) { await new Promise(resolve => release = resolve); send(200, player); }
    else send(404, { code: 'NOT_FOUND', message: 'Không tìm thấy người nhận mới.' }); return true;
  });
  await open(page, fixture); await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click(); await fillGift(page);
  await page.getByLabel('Người nhận', { exact: true }).selectOption('SINGLE_PLAYER');
  const input = page.getByLabel('Mã người chơi hoặc email', { exact: true }); await input.fill(player.playerCode);
  await page.getByRole('button', { name: 'Tra cứu người nhận', exact: true }).click();
  await expect.poll(() => Boolean(release)).toBe(true);
  await input.fill('UNKNOWN'); await page.getByRole('button', { name: 'Tra cứu người nhận', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Không tìm thấy người nhận mới'); release();
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.locator('.gift-recipient')).toBeEmpty();
  expect(fixture.calls.filter(c => c.route === '/admin/api/gift-campaigns' && c.method === 'POST')).toHaveLength(0);
});

test('editing an individual draft displays public code and requires recipient confirmation', async ({ page, fixture }) => {
  const state = gifts(fixture);
  await open(page, fixture); await draft(page);
  state.detail.campaign.draft.audience = 'USER_IDS'; state.detail.campaign.draft.userIds = [player.id];
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click();
  await expect(page.getByLabel('Người nhận', { exact: true })).toHaveValue('SINGLE_PLAYER');
  await expect(page.getByLabel('Mã người chơi hoặc email', { exact: true })).toHaveValue(player.playerCode);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Tra cứu');
  expect(fixture.calls.filter(c => c.route === '/admin/api/gift-campaigns/' + giftId && c.method === 'PUT')).toHaveLength(0);
});

for (const status of [401, 403]) test(`${status} recipient lookup clears sensitive modal data`, async ({ page, fixture }) => {
  gifts(fixture);
  fixture.handlers.set('/admin/api/gift-recipient', async (_, send) => {
    fixture.role = 'SUPPORT'; if (status === 401) fixture.signedIn = false;
    send(status, { code: status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN', message: 'Quyền đã thay đổi.' }); return true;
  });
  await open(page, fixture); await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click();
  await page.getByLabel('Người nhận', { exact: true }).selectOption('SINGLE_PLAYER');
  await page.getByLabel('Mã người chơi hoặc email', { exact: true }).fill(player.email);
  await page.getByRole('button', { name: 'Tra cứu người nhận', exact: true }).click();
  await expect(page.getByRole('heading', { name: status === 401 ? 'Đăng nhập quản trị' : 'Người chơi', exact: true })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByLabel('Mã người chơi hoặc email', { exact: true })).toHaveCount(0);
});

test('decimal string zero recipients cannot continue publication', async ({ page, fixture }) => {
  gifts(fixture); await open(page, fixture); await draft(page);
  fixture.handlers.set(`/admin/api/gift-campaigns/${giftId}/preview`, async (_, send) => {
    send(200, { campaignId: giftId, version: 1, recipientCount: '0', duplicateCount: 0,
      invalidUserIds: [], totalGold: '0', totalXp: '0', previewToken: 'zero' }); return true;
  });
  await preview(page);
  await expect(page.getByRole('button', { name: 'Tiếp tục công bố', exact: true })).toBeDisabled();
});

for (const width of [360, 1440]) test(`exact gift aggregates above safe integer at ${width}`, async ({ page, fixture }) => {
  const state = gifts(fixture);
  await page.setViewportSize({ width, height: 1000 });
  await open(page, fixture); await draft(page);
  const large = '9007199254740993', maximum = '9223372036854775807';
  const formatted = value => new Intl.NumberFormat('vi-VN').format(BigInt(value));
  fixture.handlers.set(`/admin/api/gift-campaigns/${giftId}/preview`, async (_, send) => {
    send(200, { campaignId: giftId, version: 1, recipientCount: large, duplicateCount: 0,
      invalidUserIds: [], totalGold: large, totalXp: maximum, previewToken: 'exact-preview' }); return true;
  });
  await preview(page);
  const assertTotals = async dialog => {
    await expect(dialog.locator('dl div').filter({ has: page.locator('dt').filter({ hasText: /^Tổng Vàng$/ }) }).locator('dd')).toHaveText(formatted(large));
    await expect(dialog.locator('dl div').filter({ has: page.locator('dt').filter({ hasText: /^Tổng XP$/ }) }).locator('dd')).toHaveText(formatted(maximum));
    for (const dd of await dialog.locator('.gift-totals dd').all()) {
      expect(await dd.evaluate(el => el.getBoundingClientRect().height <= parseFloat(getComputedStyle(el).lineHeight) * 1.1)).toBe(true);
    }
  };
  await assertTotals(page.getByRole('dialog')); await confirm(page); await assertTotals(page.getByRole('dialog'));
  await page.evaluate(() => Promise.all(document.getAnimations().map(animation => animation.finished.catch(() => {}))));
  await page.screenshot({ path: `../.artifacts/admin-gift-precision/confirm-${width}.png` });
  await page.getByRole('button', { name: 'Hủy', exact: true }).click();
  state.detail.counters = { ...state.detail.counters, claimedGold: large, claimedXp: maximum };
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  const report = page.locator('.gift-report');
  await expect(report.locator('div').filter({ has: page.locator('dt').filter({ hasText: /^Vàng đã nhận$/ }) }).locator('dd')).toHaveText(formatted(large));
  await expect(report.locator('div').filter({ has: page.locator('dt').filter({ hasText: /^XP đã nhận$/ }) }).locator('dd')).toHaveText(formatted(maximum));
  await report.scrollIntoViewIfNeeded();
  await page.evaluate(() => Promise.all(document.getAnimations().map(animation => animation.finished.catch(() => {}))));
  await page.screenshot({ path: `../.artifacts/admin-gift-precision/report-${width}.png` });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  fixture.handlers.set('/admin/api/gift-campaigns', async (_, send) => {
    send(200, { items: [state.detail], page: 0, pageSize: 25, total: large }); return true;
  });
  await page.getByRole('button', { name: 'Quay lại chiến dịch', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText(`${formatted(large)} chiến dịch.`);
  await expect(page.getByRole('group', { name: 'Phân trang' })).toContainText(formatted(large));
  await expect(page.getByRole('button', { name: 'Trang sau', exact: true })).toBeEnabled();
});

test('long draft keeps its heading and close control visible when fields scroll', async ({ page, fixture }) => {
  gifts(fixture); await page.setViewportSize({ width: 360, height: 640 }); await open(page, fixture);
  await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click();
  await page.getByLabel('Lý do chiến dịch', { exact: true }).fill('Local test');
  await expect(page.getByRole('button', { name: 'Đóng hộp thoại' })).toBeInViewport();
  await expect(page.getByRole('dialog').getByRole('heading')).toBeInViewport();
});

test('draft Bangkok dates → bilingual preview → exact confirmation → report → reasoned cancellation', async ({ page, fixture }) => {
  const state = gifts(fixture); await open(page, fixture); await draft(page);
  const create = fixture.calls.find(c => c.route === '/admin/api/gift-campaigns' && c.method === 'POST');
  expect(create.body.availableAtEpochMillis).toBe(Date.parse('2026-10-02T12:00:00+07:00'));
  expect(create.body.expiresAtEpochMillis - create.body.availableAtEpochMillis).toBe(30 * 86400000);
  expect(create.headers['x-admin-csrf']).toBe('fixture-csrf');
  await preview(page);
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('Test gift', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Tổng Vàng', { exact: true })).toBeVisible();
  await expect(dialog.getByText('1.000', { exact: true })).toBeVisible();
  await expect(dialog.getByText(/2 ID trùng/)).toBeVisible();
  await confirm(page);
  await expect(page.getByRole('dialog').getByText('Người nhận', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Công bố chiến dịch', exact: true }).click();
  await expect(page.locator('.confirmation')).toContainText('Đã công bố');
  await expect(page.getByText('Vàng đã nhận', { exact: true })).toBeVisible();
  await expect(page.getByText(/Các chỉ số có phần giao nhau/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sửa bản nháp', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Hủy chiến dịch', exact: true }).click();
  await page.getByLabel('Lý do hủy', { exact: true }).fill('Kết thúc kiểm thử');
  await page.getByRole('button', { name: 'Xác nhận hủy chiến dịch', exact: true }).click();
  await expect(page.locator('.confirmation')).toContainText('Đã hủy chiến dịch');
  expect(state.detail.campaign.cancellationReason).toBe('Kết thúc kiểm thử');
});

test('draft editing uses PUT current version and new request identity; audience validation and explicit unavailable activity', async ({ page, fixture }) => {
  gifts(fixture); await open(page, fixture); await draft(page);
  await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click();
  await expect(page.getByLabel('Thời gian phát · Giờ Việt Nam (UTC+7)', { exact: true })).toHaveValue('2026-10-02T12:00');
  await page.getByLabel('Người nhận', { exact: true }).selectOption('USER_IDS');
  await page.getByLabel('ID người chơi', { exact: true }).fill(`${player.id}\n${player.id}`);
  await expect(page.getByText(/Chưa có nguồn hoạt động gần nhất chính thức/)).toBeVisible();
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const edit = fixture.calls.find(c => c.method === 'PUT'); expect(edit.body.expectedVersion).toBe(1); expect(edit.body.userIds).toEqual([player.id, player.id]);
  expect(edit.body.requestId).not.toBe(fixture.calls.find(c => c.method === 'POST' && c.route === '/admin/api/gift-campaigns').body.requestId);
  await preview(page); await expect(page.getByRole('dialog').getByText(/2 ID trùng/)).toBeVisible();
});

test('stale preview conflict requires fresh preview before publishing again', async ({ page, fixture }) => {
  const state = gifts(fixture); await open(page, fixture); await draft(page); await preview(page); await confirm(page);
  const path = `/admin/api/gift-campaigns/${giftId}/publish`, normal = fixture.handlers.get(path);
  fixture.handlers.set(path, async (_, send) => { send(409, { code: 'CONFLICT', message: 'Dữ liệu xem trước đã thay đổi.' }); return true; });
  await page.getByRole('button', { name: 'Công bố chiến dịch', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Xem trước lại');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.confirmation')).not.toContainText('Đã công bố');
  fixture.handlers.set(path, normal); await preview(page); expect(state.previewCalls).toBe(2); await confirm(page);
  await page.getByRole('button', { name: 'Công bố chiến dịch', exact: true }).click(); await expect(page.locator('.confirmation')).toContainText('Đã công bố');
});

test('ambiguous publish preserves identical request body and blocks every dismissal while pending', async ({ page, fixture }) => {
  gifts(fixture); await open(page, fixture); await draft(page); await preview(page); await confirm(page);
  let release, attempt = 0; const path = `/admin/api/gift-campaigns/${giftId}/publish`, normal = fixture.handlers.get(path);
  fixture.handlers.set(path, async (call, send) => {
    attempt++; if (attempt === 1) { await new Promise(r => release = r); send(503, { code: 'STORAGE_UNAVAILABLE', message: 'Kết quả chưa xác nhận.' }); return true; } return normal(call, send);
  });
  await page.getByRole('button', { name: 'Công bố chiến dịch', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveAttribute('aria-busy', 'true');
  await expect(page.getByRole('button', { name: 'Đóng hộp thoại' })).toBeDisabled(); await expect(page.getByRole('button', { name: 'Hủy', exact: true })).toBeDisabled();
  await page.keyboard.press('Escape'); await page.mouse.click(1, 1); await expect(page.getByRole('dialog')).toBeVisible(); await expect.poll(() => typeof release).toBe('function'); release();
  await expect(page.getByRole('alert')).toContainText('Kết quả chưa xác nhận');
  await page.getByRole('button', { name: 'Công bố chiến dịch', exact: true }).click(); await expect(page.locator('.confirmation')).toContainText('Đã công bố');
  const calls = fixture.calls.filter(c => c.route === path); expect(calls[0].body).toEqual(calls[1].body);
});

test('unknown single UUID keeps the campaign report and invalid preview visible', async ({ page, fixture }) => {
  const state = gifts(fixture); await open(page, fixture); await draft(page);
  state.detail.campaign.draft.audience = 'USER_IDS'; state.detail.campaign.draft.userIds = [player.id];
  fixture.handlers.set(`/admin/api/users/${player.id}`, async (_, send) => { send(404, { code: 'NOT_FOUND', message: 'Không tìm thấy dữ liệu.' }); return true; });
  fixture.handlers.set(`/admin/api/gift-campaigns/${giftId}/preview`, async (_, send) => { send(200, { campaignId: giftId, version: 1, recipientCount: '0', duplicateCount: 0, invalidUserIds: [player.id], totalGold: '0', totalXp: '0', previewToken: 'invalid' }); return true; });
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Báo cáo chiến dịch' })).toBeVisible();
  await preview(page);
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('không đủ điều kiện');
  await expect(page.getByRole('dialog').getByText(player.id, { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tiếp tục công bố', exact: true })).toBeDisabled();
});

test('invalid recipients prevent confirmation and campaign content is text only', async ({ page, fixture }) => {
  gifts(fixture); await open(page, fixture); await draft(page);
  fixture.handlers.set(`/admin/api/gift-campaigns/${giftId}/preview`, async (_, send) => { send(200, { campaignId: giftId, version: 1, recipientCount: '0', duplicateCount: 0, invalidUserIds: [player.id], totalGold: '0', totalXp: '0', previewToken: 'invalid' }); return true; });
  await preview(page); await expect(page.getByRole('dialog').getByText(player.id, { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tiếp tục công bố', exact: true })).toBeDisabled();
});

test('zero rewards and invalid date bounds are rejected without mutation; registration bounds use Bangkok', async ({ page, fixture }) => {
  gifts(fixture); await open(page, fixture); await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click(); await fillGift(page);
  await page.getByLabel('Vàng mỗi người').fill('0'); await page.getByLabel('XP mỗi người').fill('0'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('ít nhất một phần thưởng');
  expect(fixture.calls.filter(c => c.route === '/admin/api/gift-campaigns' && c.method === 'POST')).toHaveLength(0);
  await page.getByLabel('Vàng mỗi người').fill('1'); await page.getByLabel('Người nhận', { exact: true }).selectOption('REGISTERED_BETWEEN');
  await page.getByLabel('Đăng ký từ (bao gồm) · Giờ Việt Nam (UTC+7)', { exact: true }).fill('2026-09-01T12:00');
  await page.getByLabel('Đăng ký đến (không bao gồm) · Giờ Việt Nam (UTC+7)', { exact: true }).fill('2026-08-01T12:00');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Khoảng đăng ký');
  await page.getByLabel('Đăng ký đến (không bao gồm) · Giờ Việt Nam (UTC+7)', { exact: true }).fill('2026-10-01T12:00');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0);
  const body = fixture.calls.find(c => c.route === '/admin/api/gift-campaigns' && c.method === 'POST').body;
  expect(body.registeredFromEpochMillis).toBe(Date.parse('2026-09-01T12:00:00+07:00')); expect(body.registeredUntilEpochMillis).toBe(Date.parse('2026-10-01T12:00:00+07:00')); expect(body.userIds).toEqual([]);
});

test('draft network retry retains request identity and pending fields; cancellation retry retains reason and ID', async ({ page, fixture }) => {
  gifts(fixture); await open(page, fixture);
  const base = '/admin/api/gift-campaigns', create = fixture.handlers.get(base); let release, first = true;
  fixture.handlers.set(base, async (call, send) => {
    if (call.method === 'POST' && first) { first = false; await new Promise(r => release = r); send(503, { code: 'STORAGE_UNAVAILABLE', message: 'Thử lại yêu cầu.' }); return true; } return create(call, send);
  });
  await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click(); await fillGift(page);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByLabel('Tiêu đề tiếng Việt')).toBeDisabled();
  await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toBeVisible(); await expect.poll(() => typeof release).toBe('function'); release();
  await expect(page.getByRole('alert')).toContainText('Thử lại yêu cầu'); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0);
  const calls = fixture.calls.filter(c => c.route === base && c.method === 'POST'); expect(calls[0].body).toEqual(calls[1].body);
  const cancelPath = `${base}/${giftId}/cancel`, cancel = fixture.handlers.get(cancelPath); let cancelFirst = true;
  fixture.handlers.set(cancelPath, async (call, send) => { if (cancelFirst) { cancelFirst = false; send(503, { code: 'STORAGE_UNAVAILABLE', message: 'Chưa xác nhận hủy.' }); return true; } return cancel(call, send); });
  await page.getByRole('button', { name: 'Hủy chiến dịch', exact: true }).click(); await page.getByLabel('Lý do hủy').fill('Local cancellation');
  await page.getByRole('button', { name: 'Xác nhận hủy chiến dịch', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Chưa xác nhận hủy');
  await page.getByRole('button', { name: 'Xác nhận hủy chiến dịch', exact: true }).click(); await expect(page.locator('.confirmation')).toContainText('Đã hủy');
  const cancellations = fixture.calls.filter(c => c.route === cancelPath); expect(cancellations[0].body).toEqual(cancellations[1].body);
});

for (const role of ['OPERATOR', 'SUPPORT']) test(`${role} role affordances preserve the permitted workflows`, async ({ page, fixture }) => {
  fixture.role = role; gifts(fixture); await login(page, fixture);
  if (role === 'SUPPORT') { await expect(page.getByRole('button', { name: 'Quà admin', exact: true })).toHaveCount(0); await page.getByRole('button', { name: 'Xem người chơi' }).first().click(); await expect(page.getByRole('button', { name: 'Nguồn quà', exact: true })).toBeVisible(); }
  else { await page.getByRole('button', { name: 'Quà admin', exact: true }).click(); await draft(page); await preview(page); await expect(page.getByRole('button', { name: 'Tiếp tục công bố', exact: true })).toHaveCount(0); await expect(page.getByRole('button', { name: 'Hủy chiến dịch', exact: true })).toHaveCount(0); }
});

for (const status of [401, 403]) test(`${status} during draft save clears gift dialogs and sensitive inputs`, async ({ page, fixture }) => {
  gifts(fixture); await open(page, fixture);
  fixture.handlers.set('/admin/api/gift-campaigns', async (_, send) => { fixture.role = 'SUPPORT'; if (status === 401) fixture.signedIn = false; send(status, { code: status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN', message: 'Quyền đã thay đổi.' }); return true; });
  await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click(); await fillGift(page); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: status === 401 ? 'Đăng nhập quản trị' : 'Người chơi', exact: true })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0); await expect(page.getByLabel('Nội dung tiếng Anh')).toHaveCount(0); await expect(page.getByText('Quà kiểm thử', { exact: true })).toHaveCount(0);
});

for (const width of [360, 768, 1024, 1440]) test(`gift surfaces at ${width}px contain controls and restore focus with reduced motion`, async ({ page, fixture }) => {
  gifts(fixture); await page.setViewportSize({ width, height: 1000 }); await page.emulateMedia({ reducedMotion: 'reduce' }); await open(page, fixture);
  await page.screenshot({ path: `../.artifacts/admin-gifts-${width}-list.png`, fullPage: true });
  await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click(); await fillGift(page);
  await page.getByLabel('Người nhận', { exact: true }).selectOption('USER_IDS'); await page.getByLabel('ID người chơi', { exact: true }).fill(player.id);
  await page.getByLabel('Tiêu đề tiếng Việt').focus();
  await page.screenshot({ path: `../.artifacts/admin-gifts-${width}-form.png`, fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await page.getByRole('button', { name: 'Quay lại chiến dịch', exact: true }).click(); await expect(page.getByRole('button', { name: 'Xem chiến dịch Quà kiểm thử', exact: true })).toBeVisible();
  await page.screenshot({ path: `../.artifacts/admin-gifts-${width}-list.png`, fullPage: true });
  await page.getByRole('button', { name: 'Trang sau', exact: true }).click(); await expect(page.getByText('Trang 2 · 26 mục', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xem chiến dịch Quà kiểm thử', exact: true }).click(); await preview(page);
  await page.screenshot({ path: `../.artifacts/admin-gifts-${width}-preview.png`, fullPage: true }); await confirm(page);
  await page.screenshot({ path: `../.artifacts/admin-gifts-${width}-confirm.png`, fullPage: true });
  await page.keyboard.press('Escape'); await expect(page.getByRole('dialog')).toHaveCount(0); await expect(page.getByRole('button', { name: 'Xem trước', exact: true })).toBeFocused();
});


for (const width of [360, 1440]) test(`Gem-only gift draft preview publication report and edit at ${width}`, async ({ page, fixture }) => {
  const state = gifts(fixture);
  await page.setViewportSize({ width, height: 1000 });
  await open(page, fixture);
  await page.getByRole('button', { name: 'Tạo chiến dịch quà', exact: true }).click();
  await fillGift(page);
  await page.getByLabel('Vàng mỗi người', { exact: true }).fill('0');
  await page.getByLabel('XP mỗi người', { exact: true }).fill('0');
  await page.getByLabel('Gem mỗi người', { exact: true }).fill('7');
  await page.evaluate(() => Promise.all(document.getAnimations().map(a => a.finished.catch(() => {}))));
  await page.screenshot({ path: `../.artifacts/admin-gift-gems/edit-${width}.png` });
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Quà kiểm thử', exact: true, level: 1 })).toBeVisible();
  expect(state.detail.campaign.draft).toMatchObject({ gold: 0, xp: 0, gems: 7 });
  await page.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click();
  await expect(page.getByLabel('Gem mỗi người', { exact: true })).toHaveValue('7');
  await page.getByRole('button', { name: 'Đóng hộp thoại' }).click();
  await preview(page);
  const total = page.getByRole('dialog').locator('dl div').filter({ has: page.locator('dt').filter({ hasText: /^Tổng Gem$/ }) }).locator('dd');
  await expect(total).toHaveText('70');
  await confirm(page);
  await expect(total).toHaveText('70');
  await page.getByRole('button', { name: 'Công bố chiến dịch', exact: true }).click();
  await expect(page.locator('.confirmation')).toContainText('Đã công bố');
  state.detail.counters.claimedGems = '9007199254740993';
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click();
  const report = page.locator('.gift-report');
  await expect(report.locator('div').filter({ has: page.locator('dt').filter({ hasText: /^Gem đã nhận$/ }) }).locator('dd')).toHaveText(new Intl.NumberFormat('vi-VN').format(9007199254740993n));
  await report.scrollIntoViewIfNeeded();
  await page.screenshot({ path: `../.artifacts/admin-gift-gems/report-${width}.png` });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
