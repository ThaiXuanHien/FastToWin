import { test, expect, install, mailbox, giftCacheKeys } from '../support/gift-worker-fixture.mjs';

for (const prefix of ['/gifts', '/api/gifts']) test(`worker ${prefix} reads current account and claim state, never returns offline mailbox`, async ({ page, fixture, context }) => {
  await install(page, fixture);
  expect((await mailbox(page, prefix, 'A')).body.items[0].campaignId).toBe('A');
  expect((await mailbox(page, prefix, 'B')).body.items[0].campaignId).toBe('B');
  fixture.giftState = 'CLAIMED';
  expect((await mailbox(page, prefix, 'B')).body.items[0].state).toBe('CLAIMED');
  expect(fixture.calls).toHaveLength(3);
  expect(await giftCacheKeys(page)).toEqual([]);
  await context.setOffline(true);
  expect(await mailbox(page, prefix, 'B')).toEqual({ networkFailure: true });
  await page.goto(fixture.url + '/gifts');
  await expect(page.getByText('Game offline shell')).toBeVisible();
});

test('old active v6 bypasses cached account A for preferred API before waiting worker activates, upgrade purges every cache', async ({ page, fixture, context }) => {
  fixture.worker = 'v6';
  await install(page, fixture);
  // Reproduce persisted private data using the actual shipped v6 controller.
  expect((await mailbox(page, '/gifts', 'A')).body.items[0].campaignId).toBe('A');
  expect(await giftCacheKeys(page)).toHaveLength(1);
  await page.evaluate(async () => {
    for (const name of ['fast-to-win-shell-v4', 'unrelated-cache']) {
      const cache = await caches.open(name);
      for (const path of ['/gifts/mailbox?old=1', '/gifts/mailbox/id/claim', '/api/gifts/mailbox?old=1', '/api/gifts/mailbox/claim-all'])
        await cache.put(path, new Response('old account A'));
    }
    const unrelated = await caches.open('unrelated-cache');
    await unrelated.put('/gifts', new Response('SPA marker'));
    await unrelated.put('/gifts/mailboxish', new Response('unrelated path'));
    await unrelated.put('https://other.invalid/gifts/mailbox', new Response('foreign origin marker'));
  });
  fixture.worker = 'current';
  await page.evaluate(async () => {
    const registration = await navigator.serviceWorker.getRegistration();
    await registration.update();
    if (!registration.waiting) await new Promise(resolve => registration.installing.addEventListener('statechange', () => { if (registration.waiting) resolve(); }));
  });
  // New application scripts arrive via networkFirst while v6 still controls the page.
  await page.evaluate(() => fetch('/fast-to-win.js'));
  expect(await page.evaluate(async () => !!(await navigator.serviceWorker.getRegistration()).waiting)).toBe(true);
  expect((await mailbox(page, '/api/gifts', 'B')).body.items[0].campaignId).toBe('B');
  fixture.giftState = 'CANCELLED';
  expect((await mailbox(page, '/api/gifts', 'B')).body.items[0].state).toBe('CANCELLED');
  await context.setOffline(true);
  expect(await mailbox(page, '/api/gifts', 'B')).toEqual({ networkFailure: true });
  await context.setOffline(false);
  await page.evaluate(async () => {
    const changed = new Promise(resolve => navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true }));
    (await navigator.serviceWorker.getRegistration()).waiting.postMessage({ type: 'SKIP_WAITING' });
    await changed;
  });
  await expect.poll(() => page.evaluate(async () => (await navigator.serviceWorker.getRegistration()).active.state)).toBe('activated');
  expect(await giftCacheKeys(page)).toEqual([]);
  expect(await page.evaluate(async () => {
    const cache = await caches.open('unrelated-cache');
    return Promise.all(['/gifts', '/gifts/mailboxish', 'https://other.invalid/gifts/mailbox'].map(async key => (await cache.match(key))?.text()));
  })).toEqual(['SPA marker', 'unrelated path', 'foreign origin marker']);
  expect((await mailbox(page, '/gifts', 'B')).body.items[0].state).toBe('CANCELLED');
  expect(await giftCacheKeys(page)).toEqual([]);
  await context.setOffline(true);
  expect(await mailbox(page, '/gifts', 'B')).toEqual({ networkFailure: true });
  await page.goto(fixture.url + '/gifts');
  await expect(page.getByText('Game offline shell')).toBeVisible();
});
