import { test as base, expect } from 'playwright/test';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
export const test = base.extend({ fixture: async ({}, use) => {
  const state = { worker: 'v7', maintenance: false, unavailable: false };
  const server = createServer(async (req, res) => {
    const path = new URL(req.url, 'http://localhost').pathname;
    if (path === '/service-worker.js') {
      res.writeHead(200, { 'Content-Type': 'application/javascript', 'Cache-Control': 'no-store' });
      res.end((await readFile(new URL(state.worker === 'v7' ? './legacy-service-worker-v7.js' : '../../webApp/src/wasmJsMain/resources/service-worker.js', import.meta.url), 'utf8')) + (state.worker === 'v7' ? '\n// Previous deployment v7\n' : '')); return;
    }
    if (['/status', '/api/status'].includes(path)) {
      res.writeHead(state.unavailable ? 503 : 200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      res.end(JSON.stringify(state.unavailable ? { code: 'STORAGE_UNAVAILABLE' } : { maintenance: state.maintenance, state: state.maintenance ? 'MAINTENANCE' : 'ACTIVE' })); return;
    }
    const html = ['/', '/index.html', '/gifts'].includes(path);
    res.writeHead(200, { 'Content-Type': html ? 'text/html' : path.endsWith('.css') ? 'text/css' : 'application/javascript' });
    res.end(html ? '<!doctype html><title>Worker fixture</title><p>Game offline shell</p>' : '');
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); state.url = `http://127.0.0.1:${server.address().port}`;
  try { await use(state); } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
} });
export { expect };
export const status = (page, path) => page.evaluate(async path => { try { const response = await fetch(path, { cache: 'no-store' }); return { status: response.status, body: await response.json() }; } catch { return { offline: true }; } }, path);
