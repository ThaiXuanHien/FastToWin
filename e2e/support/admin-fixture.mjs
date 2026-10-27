import { test as base, expect } from 'playwright/test';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

export const player = { id: '00000000-0000-0000-0000-000000000001', playerCode: 'FTW123AB', displayName: 'Người chơi mẫu', email: 'seeded-player@example.com', accountType: 'REGISTERED', status: 'ACTIVE', createdAtEpochMillis: 1700000000000, level: 8, xp: 430, gold: 1200, gems: 12, elo: 1450, clanId: 'clan-1', clanName: 'Bang mẫu', lastActivityAtEpochMillis: null };
const identity = { id: 'owner-1', email: 'owner@example.com', role: 'OWNER', active: true };
export const pageOf = (items, page = 0, total = items.length) => ({ items, page, pageSize: 25, total });
export const securityHeaders = { 'Content-Security-Policy': "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'", 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
const assets = fileURLToPath(new URL('../../server/src/main/resources/admin/', import.meta.url));
export const test = base.extend({
  fixture: async ({}, use) => {
    const state = { role: 'OWNER', signedIn: false, calls: [], handlers: new Map(), worker: 'current' };
    const server = createServer(async (req, res) => {
      const url = new URL(req.url, 'http://localhost');
      const route = url.pathname;
      if (route === '/health') {
        const call = { route, method: req.method, headers: req.headers };
        state.calls.push(call);
        const send = (status, body) => { if (!res.destroyed) { res.writeHead(status, { 'Content-Type': 'text/plain', 'Cache-Control': 'no-store' }); res.end(body); } };
        if (state.handlers.get(route) && await state.handlers.get(route)(call, send)) return;
        send(200, 'OK'); return;
      }
      if (route === '/service-worker.js') {
        if (state.worker === 'unavailable') { res.writeHead(503); res.end(); return; }
        const file = state.worker === 'legacy' ? './legacy-service-worker-v5.js' : '../../webApp/src/wasmJsMain/resources/service-worker.js';
        res.writeHead(200, { 'Content-Type': 'application/javascript', 'Cache-Control': 'no-store' });
        res.end((await readFile(new URL(file, import.meta.url), 'utf8')) + (state.worker === 'legacy' ? '\n// Previous deployment snapshot\n' : '') + (state.workerRevision || '')); return;
      }
      if (!route.startsWith('/admin')) {
        const html = route === '/' || route === '/index.html';
        res.writeHead(200, { 'Content-Type': html ? 'text/html' : route.endsWith('.css') ? 'text/css' : 'application/javascript' });
        res.end(html ? '<!doctype html><title>Game shell</title><p>Game offline shell</p>' : ''); return;
      }
      if (!route.startsWith('/admin/api/')) {
        const name = { '/admin/': 'index.html', '/admin/index.html': 'index.html', '/admin/admin.css': 'admin.css', '/admin/admin.js': 'admin.js', '/admin/admin-network.js': 'admin-network.js', '/admin/admin-gifts.js': 'admin-gifts.js', '/admin/admin-maintenance.js': 'admin-maintenance.js', '/admin/admin-analytics.js': 'admin-analytics.js', '/admin/admin-missions.js': 'admin-missions.js', '/admin/admin-clan-race.js': 'admin-clan-race.js', '/admin/admin-events.js': 'admin-events.js', '/admin/admin-compensation.js': 'admin-compensation.js', '/admin/admin-moderation.js': 'admin-moderation.js', '/admin/admin-announcements.js': 'admin-announcements.js' }[route];
        try {
          const body = name && await readFile(assets + name);
          if (!body) throw new Error('missing');
          res.writeHead(200, { ...securityHeaders, 'Content-Type': name.endsWith('.html') ? 'text/html; charset=utf-8' : name.endsWith('.css') ? 'text/css' : 'application/javascript' }); res.end(body);
        } catch { res.writeHead(404); res.end('Missing admin asset'); }
        return;
      }
      let raw = ''; for await (const chunk of req) raw += chunk;
      const call = { route, method: req.method, query: url.searchParams, headers: req.headers, body: raw ? JSON.parse(raw) : null };
      state.calls.push(call);
      const send = (status, body) => { if (!res.destroyed) { res.writeHead(status, { ...securityHeaders, 'Content-Type': 'application/json' }); res.end(JSON.stringify(body)); } };
      const override = state.handlers.get(route);
      if (override && await override(call, send, state)) return;
      const admin = { ...identity, role: state.role };
      if (route.endsWith('/login')) { state.signedIn = true; send(200, { admin, csrfToken: 'fixture-csrf' }); }
      else if (!state.signedIn) send(401, { code: 'UNAUTHORIZED', message: 'Phiên quản trị không hợp lệ hoặc đã hết hạn.' });
      else if (route.endsWith('/session')) send(200, { admin, csrfToken: 'fixture-csrf' });
      else if (route.endsWith('/logout')) { state.signedIn = false; send(200, { ok: true }); }
      else if (route === '/admin/api/users') {
        const p = Number(url.searchParams.get('page') || 0);
        const q = url.searchParams.get('q') || '';
        send(200, pageOf(q === 'empty' ? [] : [{ ...player, displayName: p ? 'Trang thứ hai' : player.displayName, email: state.role === 'SUPPORT' ? 's***@example.com' : player.email }], p, q === 'empty' ? 0 : 26));
      } else if (route === '/admin/api/users/' + player.id) send(200, { ...player, email: state.role === 'SUPPORT' ? 's***@example.com' : player.email });
      else if (route.endsWith('/matches')) send(200, pageOf([{ id: 'match-1', matchType: 'RANKED', outcome: 'WIN', score: 42, finishedAtEpochMillis: 1700000000000 }], Number(url.searchParams.get('page') || 0), 26));
      else if (route.endsWith('/wallet') || route.endsWith('/gifts')) send(200, pageOf([{ id: 'wallet-1', sourceType: route.endsWith('/gifts') ? 'COMPENSATION' : 'MATCH', sourceId: 'source-1', goldDelta: 100, gemsDelta: 1, xpDelta: 30, createdAtEpochMillis: 1700000000000 }], Number(url.searchParams.get('page') || 0), 26));
      else if (route === '/admin/api/accounts' && req.method === 'GET') send(200, pageOf([admin, { id: 'support-2', email: 'support@example.com', role: 'SUPPORT', active: true }], Number(url.searchParams.get('page') || 0), 26));
      else if (route === '/admin/api/accounts') send(200, { id: 'new-admin', email: call.body.email, role: call.body.role, active: true });
      else if (route.endsWith('/access')) send(200, { id: route.split('/').at(-2), email: 'support@example.com', role: call.body.role, active: call.body.active });
      else if (route === '/admin/api/audit') send(200, pageOf([{ id: 'audit-1', actorId: 'owner-1', action: 'ADMIN_LOGIN', targetId: null, requestId: null, reason: null, beforeSummary: null, afterSummary: null, createdAtEpochMillis: 1700000000000 }], Number(url.searchParams.get('page') || 0), 26));
      else send(404, { code: 'NOT_FOUND', message: 'Không tìm thấy dữ liệu.' });
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    state.url = `http://127.0.0.1:${server.address().port}`;
    try { await use(state); } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
  },
});
export { expect };
export async function login(page, fixture) {
  const response = await page.goto(fixture.url + '/admin/');
  expect(response.status()).toBe(200);
  await page.getByLabel('Email quản trị', { exact: true }).fill('owner@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('FixturePassword123!');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Người chơi', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Xem người chơi' }).first()).toBeVisible();
}
