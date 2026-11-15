import { test, expect, login, player, pageOf } from '../support/admin-fixture.mjs';

const day = 86400000;
const now = Date.parse('2026-10-15T12:00:00+07:00');
const today = Date.parse('2026-10-15T00:00:00+07:00');
const overview = {
  snapshotAt: now, todayFrom: today, todayUntil: now,
  totalPlayers: '42', registeredPlayers: '30', guestPlayers: '12', playableAccounts: '40', matchesToday: '9',
  recordedParticipations: '100', recordedWins: '45', recordedLosses: '45', recordedDraws: '10',
  playedProfiles: '20', clanCount: '3', clanMembers: '10', averageElo: '1456.5', maxElo: 2100,
  commerce: { from: today - 6 * day, until: now, iapPurchases: '7', iapBuyers: '4', rewardedAdViews: '14' },
};
function install(fixture, { status = 'COMPLETE', summary = '17', values = ['1', '2', '3', '4', '5', '6', '7'], registrationStatus = status } = {}) {
  fixture.handlers.set('/admin/api/player-overview', async (_, send) => { send(200, overview); return true; });
  fixture.handlers.set('/admin/api/analytics', async (call, send) => {
    const from = Number(call.query.get('from')), until = Number(call.query.get('until'));
    const coverage = { source: 'SERVER', environment: 'LIVE', status, coverageStart: from, freshness: now - 30000 };
    const group = (metric, points, total) => ({ metric, coverage: [{ ...coverage, status: metric === 'REGISTRATIONS' ? registrationStatus : status }], groups: [{
      source: 'SERVER', unit: metric === 'REGISTRATIONS' ? 'REGISTRATIONS' : 'PLAYERS', currency: null,
      summary: total, series: points.map((value, i) => ({ from: from + i * day, until: Math.min(from + (i + 1) * day, until), value })),
      comparison: { value: '20', percentageChange: status === 'COMPLETE' ? '-15' : null,
        absoluteChange: status === 'COMPLETE' ? '-3' : null, coverage, range: { from: from - (until - from), until: from } }, top: [],
    }] });
    send(200, { range: { from, until }, comparisonRange: { from: from - (until - from), until: from },
      timezone: 'Asia/Bangkok', bucket: 'DAY', snapshotAt: now,
      metrics: [group('ACTIVE_PLAYERS', values, summary), group('REGISTRATIONS', status === 'COMPLETE' ? ['1','2','0','1','2','1','3'] : values, '10')] }); return true;
  });
}
const metric = (page, label) => page.locator('.metric-card').filter({ has: page.locator('.metric-lbl', { hasText: label }) }).locator('.metric-val');
for (const width of [360, 1440]) test(`player dashboard uses real aggregates and preserves layout at ${width}`, async ({ page, fixture }) => {
  await page.setViewportSize({ width, height: 1000 }); await page.clock.setFixedTime(now); install(fixture); await login(page, fixture);
  await expect(metric(page, /^Tổng người chơi$/)).toHaveText('42');
  await expect(metric(page, /^Trận đấu hôm nay$/)).toHaveText('9');
  await expect(metric(page, /^Lượt mua IAP · 7 ngày$/)).toHaveText('7');
  await expect(metric(page, /^Người mua IAP · 7 ngày$/)).toHaveText('4');
  await expect(metric(page, /^Lượt xem quảng cáo thưởng · 7 ngày$/)).toHaveText('14');
  await expect(page.getByText(/Kết nối game|Điểm Elo trung bình|Tài khoản khách/)).toHaveCount(0);
  await expect(metric(page, /^Đăng ký mới hôm nay$/)).toHaveText('3');
  await expect(page.locator('.bar-chart-svg .chart-bar')).toHaveCount(7);
  await expect(page.getByText('VIP: 1.250', { exact: true })).toHaveCount(0);
  await page.getByLabel('Tìm người chơi', { exact: true }).fill('empty');
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByText('Không có người chơi phù hợp.')).toBeVisible();
  await expect(metric(page, /^Tổng người chơi$/)).toHaveText('42'); // Search/page results never become the system-wide total.
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `../.artifacts/admin-player-metrics-20261015/directory-${width}.png`, fullPage: true });
});
test('missing activity stays unknown while actual zero and large exact values remain distinguishable', async ({ page, fixture }) => {
  await page.clock.setFixedTime(now);
  install(fixture, { status: 'PARTIAL', summary: null, values: ['9007199254740993', '0', null, '4', '5', '6', '7'] });
  await login(page, fixture);
  await expect(metric(page, /^Lượt mua IAP · 7 ngày$/)).toHaveText('7');
  const chart = page.locator('.trend-chart-card');
  await chart.getByText('Dữ liệu theo ngày', { exact: true }).click();
  await expect(chart.getByText('9.007.199.254.740.993', { exact: true }).first()).toBeVisible();
  await expect(chart.getByText('Chưa có dữ liệu', { exact: true }).first()).toBeAttached();
  await expect(chart.locator('.bar-chart-svg .chart-bar')).toHaveCount(6);
  await expect(chart).toContainText('Một phần');
  await expect(chart.locator('.badge-growth')).not.toHaveText('+18.4%');
});
test('Support never requests or displays restricted aggregate statistics', async ({ page, fixture }) => {
  fixture.role = 'SUPPORT'; await login(page, fixture);
  expect(fixture.calls.some(c => ['/admin/api/player-overview','/admin/api/analytics'].includes(c.route))).toBe(false);
  await expect(page.locator('.dashboard-metrics-grid .metric-val')).toHaveText(['—', '—', '—', '—', '—', '—']);
});
test('profile and directory show persisted level/results and unknown playtime', async ({ page, fixture }) => {
  install(fixture);
  fixture.handlers.set('/admin/api/users/' + player.id, async (_, send) => { send(200, { ...player, totalMatches: 100, wins: 55, losses: 40, draws: 5 }); return true; });
  await login(page, fixture);
  await page.getByRole('button', { name: 'Xem người chơi', exact: true }).first().click();
  await expect(page.locator('.metric').filter({ has: page.getByText('Tỉ lệ thắng', { exact: true }) }).locator('dd')).toHaveText('55%');
  await expect(page.locator('.metric').filter({ has: page.getByText('Tổng số trận', { exact: true }) }).locator('dd')).toHaveText('100');
  await expect(page.getByText('142 giờ', { exact: true })).toHaveCount(0);
});

test('unavailable sources show unknown rather than zero or demo figures', async ({ page, fixture }) => {
  install(fixture, { status: 'UNAVAILABLE', summary: null, values: Array(7).fill(null) });
  fixture.handlers.set('/admin/api/player-overview', async (_, send) => { send(503, { code: 'STORAGE_UNAVAILABLE', message: 'Kho dữ liệu chưa sẵn sàng.' }); return true; });
  await login(page, fixture);
  await expect(page.locator('.dashboard-metrics-grid .metric-val')).toHaveText(['—', '—', '—', '—', '—', '—']);
  await expect(page.locator('.trend-chart-card')).toContainText('Chưa tải được tổng quan hồ sơ');
  await expect(page.locator('.bar-chart-svg .chart-bar')).toHaveCount(0);
  await expect(page.locator('.trend-chart-card .badge-growth')).toHaveText('—');
});
test('late aggregate response cannot repopulate another view or a logged out session', async ({ page, fixture }) => {
  await page.addInitScript(() => { const fetchOriginal = window.fetch; window.fetch = (url, options) => fetchOriginal(url, { ...options, signal: undefined }); });
  let release;
  fixture.handlers.set('/admin/api/player-overview', async (_, send) => { await new Promise(r => release = r); send(200, overview); return true; });
  await login(page, fixture); await expect.poll(() => typeof release).toBe('function');
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  release();
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị' })).toBeVisible();
  await expect(page.locator('.dashboard-metrics-grid')).toHaveCount(0);
  expect(fixture.calls.some(c => c.route === '/admin/api/analytics')).toBe(false);
});

test('registration and activity coverage remain independently visible', async ({ page, fixture }) => {
  install(fixture, { registrationStatus: 'PARTIAL' }); await login(page, fixture);
  await expect(metric(page, /^Đăng ký mới hôm nay$/)).toHaveText('3');
  await expect(page.locator('.trend-chart-card')).toContainText('Nguồn đăng ký: Một phần');
  await expect(page.locator('.trend-chart-card')).not.toContainText('Nguồn kết nối game');
});

test('Owner creates a player through the support form with a stable retry key', async ({ page, fixture }) => {
  const requests = [];
  fixture.handlers.set('/admin/api/users', async (call, send) => {
    if (call.method !== 'POST') return false;
    requests.push(call);
    if (requests.length === 1) {
      send(503, { code: 'STORAGE_UNAVAILABLE', message: 'Kho dữ liệu chưa sẵn sàng.' });
    } else {
      send(200, { ...player, emailVerified: false, displayName: 'Người chơi hỗ trợ' });
    }
    return true;
  });
  await login(page, fixture);
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Tạo tài khoản người chơi' });
  await dialog.getByLabel('Email').fill('supported@example.test');
  await dialog.getByLabel('Tên hiển thị').fill('Người chơi hỗ trợ');
  await dialog.getByLabel('Mật khẩu ban đầu').fill('Only-A-Local-Fixture-123!');
  await dialog.getByLabel('Lý do hỗ trợ').fill('Người chơi không nhận được mã.');
  await dialog.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('Kho dữ liệu chưa sẵn sàng.');
  await dialog.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByText(/Chưa xác minh email/)).toBeVisible();
  expect(requests[0].headers['x-admin-csrf']).toBe('fixture-csrf');
  expect(requests[0].body.requestId).toMatch(/^[0-9a-f-]{36}$/);
  expect(requests[1].body.requestId).toBe(requests[0].body.requestId);
});

for (const width of [360, 1440]) test(`support player form preserves focus, secrets and pending guards at ${width}`, async ({ page, fixture }) => {
  await page.setViewportSize({ width, height: 800 }); install(fixture);
  let release;
  fixture.handlers.set('/admin/api/users', async (call, send) => {
    if (call.method !== 'POST') return false;
    await new Promise(resolve => { release = resolve; });
    send(200, { ...player, emailVerified: false, supportEmailVerificationExempt: true }); return true;
  });
  await login(page, fixture);
  const opener = page.getByRole('button', { name: 'Tạo tài khoản', exact: true });
  await opener.click();
  let dialog = page.getByRole('dialog', { name: 'Tạo tài khoản người chơi' });
  await expect(dialog.getByLabel('Email', { exact: true })).toBeFocused();
  await dialog.getByLabel('Mật khẩu ban đầu').fill('Abandoned-Fixture-123!');
  await page.keyboard.press('Escape'); await expect(dialog).toHaveCount(0); await expect(opener).toBeFocused();
  await opener.click(); dialog = page.getByRole('dialog', { name: 'Tạo tài khoản người chơi' });
  await expect(dialog.getByLabel('Mật khẩu ban đầu')).toBeEmpty();
  await dialog.getByLabel('Email', { exact: true }).fill('supported@example.test');
  await dialog.getByLabel('Tên hiển thị').fill('Người chơi hỗ trợ');
  await dialog.getByLabel('Mật khẩu ban đầu').fill('Only-A-Local-Fixture-123!');
  await dialog.getByLabel('Lý do hỗ trợ').fill('Người chơi không nhận được mã.');
  await dialog.getByRole('button', { name: 'Tạo tài khoản', exact: true }).scrollIntoViewIfNeeded();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: `../.artifacts/admin-player-metrics-20261015/support-form-${width}.png` });
  await dialog.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click();
  await expect.poll(() => typeof release).toBe('function');
  await expect(dialog.getByLabel('Email', { exact: true })).toBeDisabled();
  await expect(dialog.getByRole('button', { name: 'Hủy', exact: true })).toBeDisabled();
  await page.keyboard.press('Escape'); await expect(dialog).toBeVisible();
  release(); await expect(dialog).toHaveCount(0);
});

for (const role of ['OPERATOR', 'SUPPORT']) test(`${role} cannot open support player provisioning`, async ({ page, fixture }) => {
  fixture.role = role; install(fixture); await login(page, fixture);
  await expect(page.getByRole('button', { name: 'Tạo tài khoản', exact: true })).toHaveCount(0);
  expect(fixture.calls.some(call => call.method === 'POST' && call.route === '/admin/api/users')).toBe(false);
});
