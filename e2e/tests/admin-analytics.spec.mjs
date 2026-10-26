import { test, expect, login } from '../support/admin-fixture.mjs';
import { analytics, analyticsPath, snapshot, group, coverage } from '../support/admin-analytics-fixture.mjs';
import { maintenance } from '../support/maintenance-fixture.mjs';
async function open(page, fixture) { await login(page, fixture); await page.getByRole('button', { name: 'Thống kê', exact: true }).click(); }
// The directory loads its own two-metric overview before console navigation.
// Inspect console requests independently when checking obsolete lazy imports.
const lastQuery = fixture => fixture.calls.filter(c => c.route === analyticsPath && c.query.get('metric') !== 'ACTIVE_PLAYERS,REGISTRATIONS').at(-1)?.query;

// Delay only the real module response; production navigation and module bodies run unchanged.
async function delayedImport(page, name, fail = false) {
  let unblock, requested = false;
  const gate = new Promise(resolve => unblock = resolve);
  await page.route('**/admin/' + name, async route => { requested = true; await gate; if (fail) await route.abort('failed'); else await route.continue(); });
  return { unblock, wait: () => expect.poll(() => requested).toBe(true), async release() {
    unblock(); await page.evaluate(({ name, fail }) => import('/admin/' + name).then(() => {}, error => { if (!fail) throw error; }), { name, fail });
  } };
}
for (const first of ['analytics', 'maintenance']) test(`latest lazy navigation intent survives ${first} import completing first`, async ({ page, fixture }) => {
  analytics(fixture); maintenance(fixture); await login(page, fixture);
  const imports = { analytics: await delayedImport(page, 'admin-analytics.js'), maintenance: await delayedImport(page, 'admin-maintenance.js') };
  try {
    await page.getByRole('button', { name: 'Thống kê', exact: true }).click(); await imports.analytics.wait();
    await page.getByRole('button', { name: 'Bảo trì', exact: true }).click(); await imports.maintenance.wait();
    await imports[first].release();
    await expect(page.getByRole('navigation').locator('[aria-current="page"]')).toHaveText('Bảo trì');
    await imports[first === 'analytics' ? 'maintenance' : 'analytics'].release();
    await expect(page.getByRole('button', { name: 'Cấu hình bảo trì', exact: true })).toBeVisible();
    await expect(page.locator('.analytics-results')).toHaveCount(0);
    expect(lastQuery(fixture)).toBeUndefined();
  } finally { imports.analytics.unblock(); imports.maintenance.unblock(); }
});
test('outgoing analytics snapshot clears immediately while another lazy destination imports', async ({ page, fixture }) => {
  analytics(fixture); maintenance(fixture); await open(page, fixture);
  await expect(page.locator('.analytics-summary')).toBeVisible(); await page.getByRole('button', { name: 'Điểm tiếp theo' }).hover();
  await expect(page.getByRole('button', { name: 'Điểm tiếp theo' }).locator('.tooltip')).toBeVisible();
  const pending = await delayedImport(page, 'admin-maintenance.js');
  try {
    await page.getByRole('button', { name: 'Bảo trì', exact: true }).click(); await pending.wait();
    await expect(page.locator('.analytics-summary, .analytics-chart, .analytics-point-value, .analytics-inspection, .analytics-results table, .page-analytics .tooltip')).toHaveCount(0);
    await expect(page.getByRole('navigation').locator('[aria-current="page"]')).toHaveText('Bảo trì');
    await pending.release(); await expect(page.getByRole('button', { name: 'Cấu hình bảo trì', exact: true })).toBeVisible();
  } finally { pending.unblock(); }
});
test('pending lazy navigation cannot revive a dashboard after logout and Operator login', async ({ page, fixture }) => {
  analytics(fixture); await login(page, fixture); const pending = await delayedImport(page, 'admin-analytics.js');
  try {
    await page.getByRole('button', { name: 'Thống kê', exact: true }).click(); await pending.wait();
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await expect(page.getByText('Đã đăng xuất.', { exact: true })).toBeVisible();
    fixture.role = 'OPERATOR'; await page.getByLabel('Email quản trị', { exact: true }).fill('operator@example.com'); await page.getByLabel('Mật khẩu', { exact: true }).fill('FixturePassword123!');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
    await pending.release(); await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
    await expect(page.locator('.analytics-results')).toHaveCount(0); expect(lastQuery(fixture)).toBeUndefined();
  } finally { pending.unblock(); }
});
for (const destination of ['Người chơi', 'Tài khoản quản trị', 'Nhật ký kiểm toán']) test(`pending lazy navigation cannot override immediate ${destination}`, async ({ page, fixture }) => {
  analytics(fixture); await login(page, fixture); const pending = await delayedImport(page, 'admin-analytics.js');
  try {
    await page.getByRole('button', { name: 'Thống kê', exact: true }).click(); await pending.wait();
    await page.getByRole('navigation').getByRole('button', { name: destination, exact: true }).click();
    await pending.release(); await expect(page.getByRole('heading', { name: destination, exact: true })).toBeVisible();
    await expect(page.getByRole('navigation').locator('[aria-current="page"]')).toHaveText(destination);
    await expect(page.locator('.analytics-results')).toHaveCount(0); expect(lastQuery(fixture)).toBeUndefined();
  } finally { pending.unblock(); }
});
for (const obsolete of [false, true]) test(`${obsolete ? 'obsolete' : 'current'} lazy navigation import error stays within its intent`, async ({ page, fixture }) => {
  analytics(fixture); maintenance(fixture); await login(page, fixture); const pending = await delayedImport(page, 'admin-analytics.js', true);
  try {
    await page.getByRole('button', { name: 'Thống kê', exact: true }).click(); await pending.wait();
    if (obsolete) { await page.getByRole('button', { name: 'Bảo trì', exact: true }).click(); await expect(page.getByRole('button', { name: 'Cấu hình bảo trì', exact: true })).toBeVisible(); }
    await pending.release();
    if (obsolete) { await expect(page.getByRole('heading', { name: 'Bảo trì', exact: true })).toBeVisible(); await expect(page.locator('main')).not.toContainText('Không thể tải thống kê'); }
    else { await expect(page.getByRole('alert')).toContainText('Không thể tải thống kê'); await expect(page.locator('main [role="status"]')).toBeEmpty(); }
    await expect(page.locator('.analytics-results')).toHaveCount(0); expect(lastQuery(fixture)).toBeUndefined();
  } finally { pending.unblock(); }
});

test('Owner dashboard loads exact summary and comparison with default request', async ({ page, fixture }) => {
  analytics(fixture); await open(page, fixture);
  await expect(page.getByRole('heading', { name: 'Thống kê', exact: true })).toBeVisible();
  await expect(page.locator('.analytics-summary')).toContainText('17');
  await expect(page.locator('.analytics-summary')).toContainText('70%');
  await expect(page.getByRole('table')).toHaveCount(1);
  expect(Object.fromEntries(lastQuery(fixture))).toEqual({ metric: 'REGISTRATIONS', preset: '30', bucket: 'DAY' });
  await expect(page.locator('.analytics-range')).toContainText('Giờ Việt Nam (UTC+7)');
});
for (const role of ['OPERATOR', 'SUPPORT']) test(`${role} only sees permitted analytics`, async ({ page, fixture }) => {
  fixture.role = role; analytics(fixture); await login(page, fixture);
  if (role === 'SUPPORT') { await expect(page.getByRole('button', { name: 'Thống kê', exact: true })).toHaveCount(0); expect(lastQuery(fixture)).toBeUndefined(); }
  else { await page.getByRole('button', { name: 'Thống kê', exact: true }).click(); await expect(page.getByLabel('Chỉ số', { exact: true }).locator('option')).toHaveCount(6); await expect(page.getByLabel('Chỉ số', { exact: true }).locator('option[value="MONEY_PAID"]')).toHaveCount(0); }
});
test('presets, buckets and exclusive Bangkok custom dates drive actual query', async ({ page, fixture }) => {
  analytics(fixture); await open(page, fixture);
  for (const preset of ['7', '90']) { await page.getByLabel('Khoảng thời gian').selectOption(preset); await expect.poll(() => lastQuery(fixture)?.get('preset')).toBe(preset); }
  await page.getByLabel('Phân nhóm').selectOption('WEEK'); await expect.poll(() => lastQuery(fixture)?.get('bucket')).toBe('WEEK');
  await page.getByLabel('Phân nhóm').selectOption('MONTH');
  await page.getByLabel('Khoảng thời gian').selectOption('custom');
  await page.getByLabel('Từ ngày · Giờ Việt Nam (UTC+7)').fill('2026-09-01'); await page.getByLabel('Đến ngày, không gồm · Giờ Việt Nam (UTC+7)').fill('2026-10-01');
  await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();
  await expect.poll(() => lastQuery(fixture)?.get('from')).toBe(String(Date.parse('2026-09-01T00:00:00+07:00')));
  expect(lastQuery(fixture).get('until')).toBe(String(Date.parse('2026-10-01T00:00:00+07:00'))); expect(lastQuery(fixture).has('preset')).toBe(false);
});
test('context filters reset across metrics and validate range', async ({ page, fixture }) => {
  analytics(fixture, call => snapshot(call.query.get('metric'))); await open(page, fixture);
  await page.getByLabel('Chỉ số', { exact: true }).selectOption('MONEY_PAID');
  await page.getByRole('button', { name: 'Bộ lọc', exact: true }).click();
  await page.getByLabel('Cửa hàng').selectOption('GOOGLE_PLAY'); await page.getByLabel('Tiền tệ').fill('USD'); await page.getByLabel('Mã sản phẩm').fill('test.product'); await page.getByLabel('Môi trường').selectOption('SANDBOX');
  await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();
  await expect.poll(() => lastQuery(fixture)?.get('environment')).toBe('SANDBOX'); expect(lastQuery(fixture).get('currency')).toBe('USD');
  await page.getByLabel('Chỉ số', { exact: true }).selectOption('ITEM_SPEND'); await expect.poll(() => lastQuery(fixture)?.get('metric')).toBe('ITEM_SPEND'); expect(lastQuery(fixture).has('store')).toBe(false);
  await page.getByRole('button', { name: 'Bộ lọc', exact: true }).click(); await page.getByLabel('Đơn vị game').selectOption('GEMS'); await page.getByLabel('Mã vật phẩm').fill('hat'); await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();
  await expect.poll(() => lastQuery(fixture)?.get('item')).toBe('hat');
  await page.getByLabel('Khoảng thời gian').selectOption('custom'); await page.getByLabel('Từ ngày · Giờ Việt Nam (UTC+7)').fill('2020-01-01'); await page.getByLabel('Đến ngày, không gồm · Giờ Việt Nam (UTC+7)').fill('2026-10-01'); const count = fixture.calls.length;
  await page.getByRole('button', { name: 'Áp dụng', exact: true }).click(); await expect(page.getByRole('alert').filter({ hasText: '24 tháng' })).toBeVisible(); expect(fixture.calls.length).toBe(count);
});
test('money scale and sign stay exact above 2^53 and currencies stay separate', async ({ page, fixture }) => {
  analytics(fixture, snapshot('MONEY_NET', [group({ source: 'GOOGLE_PLAY', unit: 'MONEY_MINOR', currency: 'USD', currencyExponent: 2, summary: '900719925474099312', series: [], comparison: null }), group({ source: 'GOOGLE_PLAY', unit: 'MONEY_MINOR', currency: 'JPY', currencyExponent: 0, summary: '-9007199254740993', series: [], comparison: null }), group({ source: 'APP_STORE', unit: 'MONEY_MINOR', currency: 'KWD', currencyExponent: 3, summary: '-1', series: [], comparison: null })], [coverage('GOOGLE_PLAY'), coverage('APP_STORE')]));
  await open(page, fixture); await expect(page.locator('.analytics-summary')).toContainText(['9.007.199.254.740.993,12 USD', '-9.007.199.254.740.993 JPY', '-0,001 KWD']);
});
test('partial observed data, complete zero, unavailable and prior zero are distinct', async ({ page, fixture }) => {
  analytics(fixture, snapshot('ITEM_SPEND', [group({ source: 'ITEM_PURCHASE', currency: 'GOLD', unit: 'GOLD', summary: '0', comparison: { value: '0', absoluteChange: '0', percentageChange: null, coverage: coverage('ITEM_PURCHASE') } }), group({ source: 'OTHER', currency: 'GEMS', unit: 'GEMS', summary: null, comparison: { value: null, absoluteChange: null, percentageChange: null, coverage: coverage('OTHER', 'PARTIAL') } })], [coverage('ITEM_PURCHASE'), coverage('OTHER', 'PARTIAL'), coverage('APP_STORE', 'UNAVAILABLE')]));
  await open(page, fixture); await expect(page.locator('.analytics-summary').first()).toContainText('0 Vàng'); await expect(page.locator('.analytics-summary').nth(1)).toContainText('Chưa có dữ liệu'); await expect(page.locator('.analytics-summary')).not.toContainText(['Infinity']);
  await expect(page.locator('.analytics-coverage')).toContainText('Một phần'); await expect(page.locator('.analytics-coverage')).toContainText('Chưa có dữ liệu'); await expect(page.locator('.analytics-coverage')).toContainText('Thu thập từ');
});
test('top ten exact table and distinct summary are not derived from daily sums', async ({ page, fixture }) => {
  analytics(fixture, snapshot('TOP_ITEMS', [group({ unit: 'PURCHASES', currency: 'GOLD', summary: '3', top: [{ item: 'test-hat', purchases: '9007199254740993', spend: '9007199254740999', buyers: '2' }] })])); await open(page, fixture);
  await expect(page.getByRole('table').last()).toContainText('9.007.199.254.740.993'); await expect(page.getByRole('table').last()).toContainText('9.007.199.254.740.999 Vàng'); await expect(page.locator('.analytics-summary')).toContainText('3 lượt mua');
});
test('refresh clears stale chart while loading and on failure; late import uses source freshness', async ({ page, fixture }) => {
  analytics(fixture); await open(page, fixture); await expect(page.getByRole('table')).toBeVisible(); let release;
  fixture.handlers.set(analyticsPath, async (_, send) => { await new Promise(r => release = r); send(503, { message: 'Nguồn tạm gián đoạn.' }); return true; });
  await page.getByRole('button', { name: 'Làm mới thống kê' }).click(); await expect(page.getByRole('table')).toHaveCount(0); await expect(page.getByText('Đang tải thống kê…')).toBeVisible(); await expect.poll(() => typeof release).toBe('function'); release(); await expect(page.getByRole('alert')).toContainText('Nguồn tạm gián đoạn');
  const late = snapshot(); late.metrics[0].groups[0].summary = '19'; late.metrics[0].coverage[0].freshness += 30000; analytics(fixture, late);
  await page.getByRole('button', { name: 'Làm mới thống kê' }).click(); await expect(page.locator('.analytics-summary')).toContainText('19'); await expect(page.locator('.analytics-coverage')).toContainText('23:59:30');
});
for (const exit of ['view', 'logout', 'expiry', 'role']) test(`sensitive snapshot cleared and late response fenced on ${exit}`, async ({ page, fixture }) => {
  analytics(fixture); await open(page, fixture); await expect(page.getByRole('table')).toBeVisible();
  if (exit === 'expiry' || exit === 'role') {
    fixture.handlers.set(analyticsPath, async (_, send) => { fixture.role = 'SUPPORT'; send(exit === 'expiry' ? 401 : 403, { message: 'Quyền thay đổi.' }); return true; });
    await page.getByRole('button', { name: 'Làm mới thống kê' }).click(); await expect(page.getByRole('heading', { name: exit === 'expiry' ? 'Đăng nhập quản trị' : 'Người chơi', exact: true })).toBeVisible();
  } else {
    let release; fixture.handlers.set(analyticsPath, async (_, send) => { await new Promise(r => release = r); send(200, snapshot()); return true; }); await page.getByRole('button', { name: 'Làm mới thống kê' }).click(); await expect.poll(() => typeof release).toBe('function');
    await page.getByRole('button', { name: exit === 'view' ? 'Người chơi' : 'Đăng xuất', exact: true }).click(); release();
  }
  await expect(page.locator('.analytics-results')).toHaveCount(0); await expect(page.getByRole('table')).toHaveCount(0);
});
test('point inspection is keyboard accessible and table retains exact values', async ({ page, fixture }) => {
  analytics(fixture); await open(page, fixture); const selector = page.getByLabel('Điểm thời gian', { exact: true }); await selector.focus(); await page.keyboard.press('ArrowDown'); await expect(page.locator('.analytics-point-value')).toContainText('4 đăng ký');
  await page.getByRole('button', { name: 'Điểm tiếp theo' }).click(); await expect(page.locator('.analytics-point-value')).toContainText('0 đăng ký'); await expect(page.getByRole('table')).toContainText('0 đăng ký');
});
test('late Owner response cannot appear after a new Operator login', async ({ page, fixture }) => {
  analytics(fixture); await open(page, fixture); await expect(page.getByRole('table')).toBeVisible(); let release;
  fixture.handlers.set(analyticsPath, async (_, send) => { await new Promise(r => release = r); send(200, snapshot('MONEY_PAID', [group({ summary: '987654321' })])); return true; });
  await page.getByRole('button', { name: 'Làm mới thống kê' }).click(); await expect.poll(() => typeof release).toBe('function');
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await expect(page.getByText('Đã đăng xuất.', { exact: true })).toBeVisible();
  fixture.role = 'OPERATOR'; await page.getByLabel('Email quản trị', { exact: true }).fill('operator@example.com'); await page.getByLabel('Mật khẩu', { exact: true }).fill('FixturePassword123!'); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  release(); analytics(fixture); await page.getByRole('button', { name: 'Thống kê', exact: true }).click(); await expect(page.locator('.analytics-summary')).toContainText('17 đăng ký'); await expect(page.locator('main')).not.toContainText('987.654.321');
});
test('a changed metric fences older errors and custom selection clears pending state', async ({ page, fixture }) => {
  analytics(fixture); await open(page, fixture); await expect(page.getByRole('table')).toBeVisible(); let release;
  fixture.handlers.set(analyticsPath, async (call, send) => { if (call.query.get('metric') === 'REGISTRATIONS') { await new Promise(r => release = r); send(503, { message: 'OLD ERROR' }); } else send(200, snapshot('ITEM_BUYERS', [group({ unit: 'BUYERS', summary: '2' })])); return true; });
  await page.getByRole('button', { name: 'Làm mới thống kê' }).click(); await expect.poll(() => typeof release).toBe('function'); await page.getByLabel('Chỉ số', { exact: true }).selectOption('ITEM_BUYERS'); await expect(page.locator('.analytics-summary')).toContainText('2 người mua'); release(); await expect(page.locator('main')).not.toContainText('OLD ERROR');
  await page.getByLabel('Khoảng thời gian').selectOption('custom'); await expect(page.locator('.analytics-results')).toHaveAttribute('aria-busy', 'false'); await expect(page.getByRole('table')).toHaveCount(0); await expect(page.getByRole('button', { name: 'Làm mới thống kê' })).toBeEnabled();
});
test('calendar buckets stay concise, partial bounds precise, chart limits exact', async ({ page, fixture }) => {
  const data = snapshot(); data.metrics[0].groups[0].series[6].until -= 45000;
  analytics(fixture, data); await open(page, fixture);
  await expect(page.getByRole('table')).toContainText('29/9/26 → 30/9/26');
  await expect(page.getByRole('table')).toContainText('23:59:15');
  await expect(page.locator('.analytics-scale')).toContainText('5 đăng ký');
});
test('all unknown series never fabricates a zero scale; partial observed values suppress comparison', async ({ page, fixture }) => {
  const data = snapshot(); data.metrics[0].coverage[0].status = 'PARTIAL';
  data.metrics[0].groups[0].summary = '23'; data.metrics[0].groups[0].series.forEach(point => point.value = null);
  analytics(fixture, data); await open(page, fixture);
  await expect(page.locator('.analytics-summary')).toContainText('23 đăng ký');
  await expect(page.locator('.analytics-summary')).not.toContainText('70%');
  await expect(page.locator('.analytics-scale')).toHaveCount(0);
});
test('negative fractional comparison and huge series remain exact', async ({ page, fixture }) => {
  const data = snapshot(); const g = data.metrics[0].groups[0]; g.comparison.percentageChange = '-0.000001'; g.series[0].value = '9007199254740993';
  analytics(fixture, data); await open(page, fixture);
  await expect(page.locator('.analytics-summary')).toContainText('-0,000001%'); await expect(page.getByRole('table')).toContainText('9.007.199.254.740.993 đăng ký');
  await expect(page.locator('.analytics-point-value')).toContainText('9.007.199.254.740.993 đăng ký');
  expect(await page.locator('.analytics-line').getAttribute('d')).not.toMatch(/NaN|Infinity/);
});
test('touch landscape, enlarged text and no-storage policy', async ({ browser, fixture }) => {
  const context = await browser.newContext({ viewport: { width: 740, height: 360 }, hasTouch: true, reducedMotion: 'reduce' });
  const page = await context.newPage(); analytics(fixture); await open(page, fixture); await expect(page.getByRole('table')).toBeVisible();
  await page.getByRole('button', { name: 'Điểm tiếp theo' }).tap(); await expect(page.locator('.analytics-point-value')).toContainText('4 đăng ký');
  await page.screenshot({ path: '../.artifacts/admin-analytics-landscape.png', fullPage: true });
  await page.evaluate(() => document.documentElement.style.fontSize = '32px');
  expect(await page.locator('.page-analytics .field').evaluateAll(fields => fields.every(field => { const label = field.querySelector('label'), input = field.querySelector('select,input'); return label.getBoundingClientRect().bottom <= input.getBoundingClientRect().top + parseFloat(getComputedStyle(input).paddingTop); }))).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: '../.artifacts/admin-analytics-enlarged.png', fullPage: true });
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
  expect(fixture.calls.find(c => c.route === analyticsPath).headers['cache-control']).toBe('no-cache'); await context.close();
});
for (const width of [360, 768, 1024, 1440]) test(`responsive analytics ${width}`, async ({ page, fixture }) => {
  analytics(fixture); await page.setViewportSize({ width, height: 1000 }); await page.emulateMedia({ reducedMotion: 'reduce' }); await open(page, fixture); await expect(page.getByRole('table')).toBeVisible();
  if (width === 360) expect(await page.getByRole('navigation').getByRole('button', { name: 'Thống kê', exact: true }).evaluate(el => el.getBoundingClientRect().width)).toBeGreaterThan(95);
  expect(await page.evaluate(() => [...document.querySelectorAll('main *')].filter(el => el.getBoundingClientRect().right > innerWidth + 1).map(el => el.tagName + '.' + el.className))).toEqual([]);
  await page.screenshot({ path: `../.artifacts/admin-analytics-${width}.png`, fullPage: true });
  await page.getByLabel('Chỉ số', { exact: true }).selectOption('MONEY_PAID'); await page.getByRole('button', { name: 'Bộ lọc', exact: true }).click(); await expect(page.getByRole('dialog')).toBeVisible(); await page.screenshot({ path: `../.artifacts/admin-analytics-${width}-filters.png`, fullPage: true }); await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: 'Bộ lọc', exact: true })).toBeFocused();
});

for (const status of ['COMPLETE', 'PARTIAL', 'UNAVAILABLE']) test(`connected activity exact chart table ${status}`, async ({ page, fixture }) => {
  const values = ['9007199254740993', null, '0', '4', null, '2', '3'];
  const active = group({ unit: 'PLAYERS', summary: status === 'UNAVAILABLE' ? null : '9007199254740993', series: group().series.map((p, i) => ({ ...p, value: status === 'UNAVAILABLE' ? null : values[i] })), comparison: { ...group().comparison, value: null, absoluteChange: null, percentageChange: null, coverage: coverage('SERVER', 'UNAVAILABLE') } });
  analytics(fixture, call => call.query.get('metric') === 'ACTIVE_PLAYERS' ? snapshot('ACTIVE_PLAYERS', [active], [coverage('SERVER', status)]) : snapshot());
  await open(page, fixture); await page.getByLabel('Chỉ số', { exact: true }).selectOption('ITEM_SPEND'); await page.getByRole('button', { name: 'Bộ lọc', exact: true }).click(); await page.getByLabel('Đơn vị game').selectOption('GOLD'); await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();
  await page.getByLabel('Chỉ số', { exact: true }).selectOption('ACTIVE_PLAYERS');
  await expect(page.getByRole('button', { name: 'Bộ lọc', exact: true })).toBeHidden();
  await expect(page.locator('.analytics-results')).toContainText('90 ngày'); await expect(page.locator('.analytics-results')).toContainText('kể cả khi chờ ở sảnh');
  await expect(page.locator('.analytics-results')).toContainText('không cộng các điểm theo thời gian');
  await expect(page.locator('.analytics-summary')).toContainText(status === 'UNAVAILABLE' ? 'Chưa có dữ liệu' : '9.007.199.254.740.993 người chơi');
  await expect(page.getByRole('table')).toContainText('Trạng thái dữ liệu'); await expect(page.getByRole('table')).toContainText('Chưa có dữ liệu');
  if (status === 'PARTIAL') await expect(page.locator('.analytics-summary')).toContainText('một phần');
  const query = lastQuery(fixture); expect(query.get('metric')).toBe('ACTIVE_PLAYERS'); for (const key of ['currency', 'item', 'store', 'product', 'environment']) expect(query.has(key)).toBe(false);
  expect(await page.locator('.analytics-chart .analytics-marker').count()).toBe(status === 'UNAVAILABLE' ? 1 : 6);
});
for (const width of [360, 1440]) test(`connected activity captures ${width}`, async ({ page, fixture }) => {
  const points = group().series.map((p, i) => ({ ...p, value: i === 2 ? null : String(i + 1) }));
  analytics(fixture, snapshot('ACTIVE_PLAYERS', [group({ unit: 'PLAYERS', summary: '9', series: points, comparison: { ...group().comparison, value: null, absoluteChange: null, percentageChange: null, coverage: coverage('SERVER', 'UNAVAILABLE') } })], [coverage('SERVER', 'PARTIAL')]));
  await page.setViewportSize({ width, height: 1000 }); await page.emulateMedia({ reducedMotion: 'reduce' }); await open(page, fixture); await page.getByLabel('Chỉ số', { exact: true }).selectOption('ACTIVE_PLAYERS'); await expect(page.locator('.analytics-summary')).toContainText('9 người chơi');
  await page.screenshot({ path: `../.artifacts/connected-activity/analytics-${width}-partial.png`, fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
