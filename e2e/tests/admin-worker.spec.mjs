import { test, expect, login, player } from '../support/admin-fixture.mjs';

async function install(page, fixture, version = 'current') {
  fixture.worker = version;
  await page.goto(fixture.url + '/');
  await page.evaluate(async () => {
    await navigator.serviceWorker.register('/service-worker.js', { scope: '/', updateViaCache: 'none' });
    await navigator.serviceWorker.ready;
    if (!navigator.serviceWorker.controller) await new Promise(resolve => navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true }));
  });
}
async function adminCacheKeys(page) {
  return page.evaluate(async () => (await Promise.all((await caches.keys()).map(async name =>
    (await (await caches.open(name)).keys()).map(request => new URL(request.url).pathname)
  ))).flat().filter(path => path === '/admin' || path.startsWith('/admin/')));
}

test('worker controlled Owner logout Support login uses fresh masked reads without persisted admin responses', async ({ page, fixture }) => {
  await install(page, fixture);
  await login(page, fixture);
  await expect(page.getByText(player.email, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByText('Đã đăng xuất.', { exact: true })).toBeVisible();
  fixture.role = 'SUPPORT';
  await page.getByLabel('Email quản trị', { exact: true }).fill('support@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('FixturePassword123!');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByText('s***@example.com', { exact: true })).toBeVisible();
  await expect(page.getByText(player.email, { exact: true })).toHaveCount(0);
  expect(fixture.calls.filter(call => call.route === '/admin/api/users')).toHaveLength(2);
  expect(await adminCacheKeys(page)).toEqual([]);
});

for (const restore of [false, true]) test(`worker controlled expired session ${restore ? 'restore' : 'read'} reaches server and clears private view`, async ({ page, fixture }) => {
  await install(page, fixture);
  await login(page, fixture);
  await page.reload(); // Populate legacy session cache as well as the directory cache.
  await expect(page.getByText(player.email, { exact: true })).toBeVisible();
  const route = restore ? '/admin/api/session' : '/admin/api/users';
  const count = fixture.calls.filter(call => call.route === route).length;
  fixture.signedIn = false;
  if (restore) await page.reload();
  else await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị', exact: true })).toBeVisible();
  await expect(page.getByText(player.email, { exact: true })).toHaveCount(0);
  expect(fixture.calls.filter(call => call.route === route)).toHaveLength(count + 1);
  expect(await adminCacheKeys(page)).toEqual([]);
});

test('legacy controller and waiting worker upgrade before session read, purge old entries and retain game offline shell', async ({ page, fixture, context }) => {
  await install(page, fixture, 'legacy');
  fixture.signedIn = true;
  await page.evaluate(async () => {
    await fetch('/admin/api/session');
    await fetch('/admin/api/users');
    const cache = await caches.open('another-previous-cache');
    await cache.put('/admin', new Response('old admin navigation'));
    await cache.put('/admin/old.js', new Response('old admin script'));
  });
  fixture.signedIn = false;
  fixture.calls.length = 0;
  fixture.worker = 'current';
  await page.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration();
    await registration.update();
    if (!registration.waiting) await new Promise(resolve => registration.installing.addEventListener('statechange', () => { if (registration.waiting) resolve(); }));
  });
  await page.goto(fixture.url + '/admin/');
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị', exact: true })).toBeVisible({ timeout: 10000 });
  expect(fixture.calls.filter(call => call.route === '/admin/api/session')).toHaveLength(1);
  expect(await adminCacheKeys(page)).toEqual([]);
  await context.setOffline(true);
  await page.goto(fixture.url + '/');
  await expect(page.getByText('Game offline shell')).toBeVisible();
});

test('unupdatable legacy controller blocks all admin requests and offers retry', async ({ page, fixture }) => {
  await install(page, fixture, 'legacy');
  fixture.worker = 'unavailable';
  await page.goto(fixture.url + '/admin/');
  await expect(page.getByRole('button', { name: 'Thử lại', exact: true })).toBeVisible({ timeout: 10000 });
  expect(fixture.calls).toEqual([]);
  await expect(page.getByLabel('Email quản trị', { exact: true })).toHaveCount(0);
  fixture.worker = 'current';
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập quản trị', exact: true })).toBeVisible({ timeout: 10000 });
  expect(fixture.calls.filter(call => call.route === '/admin/api/session')).toHaveLength(1);
  expect(await adminCacheKeys(page)).toEqual([]);
});

test('uncontrolled admin startup removes orphaned admin cache entries', async ({ page, fixture }) => {
  await page.goto(fixture.url + '/');
  await page.evaluate(async () => {
    const cache = await caches.open('fast-to-win-shell-v5');
    await cache.put('/admin/api/session', new Response('old session and csrf'));
    await cache.put('/admin/api/users', new Response('old private email'));
  });
  await login(page, fixture);
  expect(await adminCacheKeys(page)).toEqual([]);
});

test('controller replacement clears private UI and blocks reads until the replacement is safe', async ({ page, fixture, context }) => {
  await install(page, fixture);
  await login(page, fixture);
  const before = fixture.calls.length;
  const game = await context.newPage();
  await game.goto(fixture.url + '/');
  fixture.worker = 'legacy';
  await game.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration();
    await registration.update();
    if (!registration.waiting) await new Promise(resolve => registration.installing.addEventListener('statechange', () => { if (registration.waiting) resolve(); }));
    registration.waiting.postMessage({ type: 'SKIP_WAITING' });
  });
  await expect(page.getByText(player.email, { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Thử lại', exact: true })).toBeVisible({ timeout: 10000 });
  expect(fixture.calls).toHaveLength(before);
});

test('late request waiting on worker policy cannot populate a replaced admin view', async ({ page, fixture }) => {
  await install(page, fixture);
  await login(page, fixture);
  // Hold a real worker's policy reply; no fake transport or production hook.
  await page.evaluate(() => {
    const send = ServiceWorker.prototype.postMessage;
    window.releasePolicy = null;
    ServiceWorker.prototype.postMessage = function (...args) {
      if (args[0]?.type === 'ADMIN_NETWORK_POLICY' && !window.releasePolicy) {
        window.releasePolicy = () => send.apply(this, args);
      } else send.apply(this, args);
    };
  });
  const before = fixture.calls.filter(call => call.route === '/admin/api/users').length;
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect.poll(() => page.evaluate(() => !!window.releasePolicy)).toBe(true);
  await page.getByRole('button', { name: 'Nhật ký kiểm toán', exact: true }).click();
  await page.evaluate(() => window.releasePolicy());
  await expect(page.getByRole('heading', { name: 'Nhật ký kiểm toán', exact: true })).toBeVisible();
  await expect(page.getByText('Đăng nhập quản trị', { exact: true })).toBeVisible();
  expect(fixture.calls.filter(call => call.route === '/admin/api/users')).toHaveLength(before);
  await expect(page.getByText(player.email, { exact: true })).toHaveCount(0);
});

test('worker transition during logout keeps sign-in blocked until the mutation finishes', async ({ page, fixture, context }) => {
  await install(page, fixture);
  await login(page, fixture);
  let finishLogout;
  fixture.handlers.set('/admin/api/logout', async (_call, send, state) => {
    await new Promise(resolve => { finishLogout = resolve; });
    state.signedIn = false;
    send(200, { ok: true }); return true;
  });
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect.poll(() => !!finishLogout).toBe(true);
  const game = await context.newPage();
  await game.goto(fixture.url + '/');
  fixture.workerRevision = '\n// Next deployment\n';
  await page.evaluate(() => { window.controllerChanged = false; navigator.serviceWorker.addEventListener('controllerchange', () => { window.controllerChanged = true; }); });
  await game.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration();
    await registration.update();
    if (!registration.waiting) await new Promise(resolve => registration.installing.addEventListener('statechange', () => { if (registration.waiting) resolve(); }));
    registration.waiting.postMessage({ type: 'SKIP_WAITING' });
  });
  await expect.poll(() => page.evaluate(() => window.controllerChanged)).toBe(true);
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeDisabled();
  finishLogout();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeEnabled();
  await expect(page.getByText(player.email, { exact: true })).toHaveCount(0);
});
