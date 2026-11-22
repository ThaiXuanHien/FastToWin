import { test, expect, login, pageOf } from '../support/admin-fixture.mjs';

test('Owner previews, publishes and stops a code for banked plays', async ({ page, fixture }, testInfo) => {
  const route = '/admin/api/gift-codes';
  const entries = [];
  fixture.handlers.set(route, (call, send) => {
    if (call.method === 'GET') send(200, pageOf(entries));
    else {
      const item = { ...call.body, claims: 0, stopped: false };
      entries.push(item);
      send(200, item);
    }
    return true;
  });
  fixture.handlers.set(route + '/PLAYWEEK/stop', (call, send) => {
    entries[0].stopped = true;
    send(200, { ok: true });
    return true;
  });
  await login(page, fixture);
  await page.getByRole('button', { name: 'Mã quà tặng', exact: true }).click();
  await expect(page.getByText('Chưa có mã quà tặng.')).toBeVisible();
  await page.getByRole('button', { name: 'Tạo mã', exact: true }).click();
  await page.getByLabel('Mã quà tặng', { exact: true }).fill('playweek');
  await page.getByLabel('Hết hạn (giờ Việt Nam)').fill('2099-12-31T23:00:01.125');
  await page.getByLabel('Lượt tích lũy', { exact: true }).fill('5');
  await page.getByLabel('Lý do phát hành').fill('Quà cuối tuần');
  for (const width of [375, 1440]) {
    await page.setViewportSize({ width, height: 1000 });
    await expect(page.getByRole('dialog')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('gift-code-form-' + width + '.png') });
  }
  await page.clock.setFixedTime(new Date('2100-01-01T00:00Z'));
  await page.getByRole('button', { name: 'Xem trước', exact: true }).click();
  await expect(page.getByRole('dialog').getByText('5 Lượt tích lũy', { exact: true })).toBeVisible();
  await expect(page.getByRole('dialog')).toContainText('23:00:01,125');
  await expect(page.getByRole('dialog')).toContainText('Giờ Việt Nam (UTC+7)');
  expect(entries).toHaveLength(0);
  await page.getByRole('button', { name: 'Phát hành', exact: true }).click();
  await expect(page.locator('main').getByText('PLAYWEEK', { exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('gift-code-published.png') });
  expect(entries[0].reward.plays).toBe(5);
  expect(entries[0].expiresAt).toBe(Date.parse('2099-12-31T16:00:01.125Z'));
  const publish = fixture.calls.find(call => call.route === route && call.method === 'POST');
  expect(publish.headers['x-admin-csrf']).toBe('fixture-csrf');
  await page.clock.setFixedTime(new Date('2099-12-30T00:00Z'));
  await page.getByRole('button', { name: 'Người chơi', exact: true }).click();
  await page.getByRole('button', { name: 'Mã quà tặng', exact: true }).click();
  await page.getByRole('button', { name: 'Ngừng mã', exact: true }).click();
  await page.getByLabel('Lý do ngừng mã').fill('Đã kết thúc');
  await page.getByRole('button', { name: 'Ngừng nhận mã', exact: true }).click();
  await expect(page.getByText(/^Đã ngừng/)).toBeVisible();
});

test('code list uses the server clock to show availability and the stop action', async ({ page, fixture }) => {
  const serverNowEpochMillis = Date.parse('2026-11-22T03:00Z');
  const reward = { gold: 5 };
  const items = [
    { code: 'ACTIVE', reward, expiresAt: serverNowEpochMillis + 60_000, claims: 0, stopped: false },
    { code: 'ENDED', reward, expiresAt: serverNowEpochMillis, claims: 0, stopped: false },
  ];
  fixture.handlers.set('/admin/api/gift-codes', (_, send) => {
    send(200, { ...pageOf(items), serverNowEpochMillis });
    return true;
  });
  await page.clock.setFixedTime(new Date('2026-11-23T03:00Z'));
  await login(page, fixture);
  await page.getByRole('button', { name: 'Mã quà tặng', exact: true }).click();
  const active = page.locator('.gift-code-row').filter({ hasText: 'ACTIVE' });
  const ended = page.locator('.gift-code-row').filter({ hasText: 'ENDED' });
  await expect(active).toContainText('Đang hoạt động');
  await expect(active).toContainText('10:01 ');
  await expect(active.getByRole('button', { name: 'Ngừng mã' })).toBeVisible();
  await expect(ended.locator('p').last()).toHaveText(/^Hết hạn ·/);
  await expect(ended.getByRole('button', { name: 'Ngừng mã' })).toHaveCount(0);
});
