import { test as base, expect } from 'playwright/test';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';

// Browser transport fixture: actual current/v6 workers and CacheStorage, controllable HTTP truth.
export const test = base.extend({
  fixture: async ({}, use) => {
    const state = { worker: 'current', giftState: 'AVAILABLE', calls: [] };
    const server = createServer(async (req, res) => {
      const url = new URL(req.url, 'http://localhost');
      const path = url.pathname;
      if (path === '/service-worker.js') {
        const file = state.worker === 'v6' ? './legacy-service-worker-v6.js' : '../../webApp/src/wasmJsMain/resources/service-worker.js';
        res.writeHead(200, { 'Content-Type': 'application/javascript', 'Cache-Control': 'no-store' });
        res.end(await readFile(new URL(file, import.meta.url))); return;
      }
      if (path === '/gifts/mailbox' || path === '/api/gifts/mailbox') {
        state.calls.push({ path, token: req.headers.authorization });
        const account = req.headers.authorization?.replace('Bearer ', '');
        res.writeHead(account ? 200 : 401, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
        res.end(JSON.stringify(account ? { items: [{ campaignId: account, titleVi: 'Quà ' + account, titleEn: 'Gift ' + account,
          bodyVi: 'Riêng tư', bodyEn: 'Private', gold: 1, xp: 1, availableAtEpochMillis: 1700000000000,
          expiresAtEpochMillis: 253402300799999, state: state.giftState, claimedAtEpochMillis: state.giftState === 'CLAIMED' ? 1700000000001 : null }],
          page: 0, pageSize: 25, total: 1 } : { code: 'UNAUTHORIZED', message: '' })); return;
      }
      const html = ['/', '/index.html', '/gifts'].includes(path);
      res.writeHead(200, { 'Content-Type': html ? 'text/html' : path.endsWith('.css') ? 'text/css' : 'application/javascript' });
      res.end(html ? '<!doctype html><title>Game shell</title><p>Game offline shell</p>' : '');
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    state.url = `http://127.0.0.1:${server.address().port}`;
    try { await use(state); } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
  },
});
export { expect };

export async function install(page, fixture) {
  await page.goto(fixture.url);
  await page.evaluate(async () => {
    await navigator.serviceWorker.register('/service-worker.js', { scope: '/', updateViaCache: 'none' });
    await navigator.serviceWorker.ready;
    if (!navigator.serviceWorker.controller) await new Promise(resolve => navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true }));
  });
}
export async function mailbox(page, prefix, account) {
  return page.evaluate(async ({ prefix, account }) => {
    try {
      const response = await fetch(prefix + '/mailbox?page=0&pageSize=25', { headers: { Authorization: 'Bearer ' + account } });
      return { status: response.status, body: await response.json() };
    } catch { return { networkFailure: true }; }
  }, { prefix, account });
}
export async function giftCacheKeys(page) {
  return page.evaluate(async () => (await Promise.all((await caches.keys()).map(async name =>
    (await (await caches.open(name)).keys()).map(request => new URL(request.url))
  ))).flat().filter(url => url.origin === location.origin &&
    ['/gifts/mailbox', '/api/gifts/mailbox'].some(path => url.pathname === path || url.pathname.startsWith(path + '/'))).map(url => url.href));
}
