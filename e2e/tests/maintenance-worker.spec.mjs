import { test, expect, status } from '../support/maintenance-worker-fixture.mjs';
import { install } from '../support/gift-worker-fixture.mjs';
test('real v7 ACTIVE cache survives waiting upgrade but alias reads closure and failure; activation purges exact status paths from every cache', async ({ page, fixture, context }) => {
  await install(page, fixture);
  expect((await status(page, '/status')).body.state).toBe('ACTIVE'); fixture.maintenance = true;
  expect((await status(page, '/status')).body.state).toBe('ACTIVE');
  await page.evaluate(async () => {
    const cache = await caches.open('unrelated-cache');
    for (const path of ['/status?old=1', '/api/status?old=1', '/admin/api/session', '/gifts/mailbox?old=1']) await cache.put(path, new Response('legacy private'));
    for (const path of ['/statusish', '/api/status/history', 'https://other.invalid/status']) await cache.put(path, new Response('preserve'));
  });
  fixture.worker = 'current';
  await page.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration(); await registration.update();
    if (!registration.waiting) await new Promise(resolve => registration.installing.addEventListener('statechange', () => { if (registration.waiting) resolve(); }));
  });
  expect(await page.evaluate(async () => !!(await navigator.serviceWorker.getRegistration()).waiting)).toBe(true);
  expect((await status(page, '/api/status')).body.state).toBe('MAINTENANCE');
  fixture.unavailable = true; expect((await status(page, '/api/status')).status).toBe(503); fixture.unavailable = false;
  await context.setOffline(true); expect(await status(page, '/api/status')).toEqual({ offline: true }); await context.setOffline(false);
  await page.evaluate(async () => {
    const changed = new Promise(resolve => navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true }));
    (await navigator.serviceWorker.getRegistration()).waiting.postMessage({ type: 'SKIP_WAITING' }); await changed;
  });
  await expect.poll(() => page.evaluate(async () => (await navigator.serviceWorker.getRegistration()).active.state)).toBe('activated');
  expect(await page.evaluate(async () => (await Promise.all((await caches.keys()).map(async name => (await (await caches.open(name)).keys()).map(r => new URL(r.url))))).flat().filter(u => u.origin === location.origin && ['/status', '/api/status', '/admin/api/session', '/gifts/mailbox'].includes(u.pathname)).length)).toBe(0);
  expect(await page.evaluate(async () => { const c = await caches.open('unrelated-cache'); return Promise.all(['/statusish', '/api/status/history', 'https://other.invalid/status'].map(async p => (await c.match(p))?.text())); })).toEqual(['preserve', 'preserve', 'preserve']);
  for (const path of ['/status', '/api/status']) expect((await status(page, path)).body.state).toBe('MAINTENANCE');
  await context.setOffline(true);
  for (const path of ['/status', '/api/status']) expect(await status(page, path)).toEqual({ offline: true });
  await page.goto(fixture.url + '/gifts'); await expect(page.getByText('Game offline shell')).toBeVisible();
});
