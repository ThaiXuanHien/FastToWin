import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { player, pageOf, securityHeaders } from '../e2e/support/admin-fixture.mjs';
import { analytics } from '../e2e/support/admin-analytics-fixture.mjs';
import { gifts } from '../e2e/support/admin-gift-fixture.mjs';
import { events } from '../e2e/support/admin-event-fixture.mjs';
import { announcements } from '../e2e/support/admin-announcement-fixture.mjs';
import { compensation } from '../e2e/support/admin-compensation-fixture.mjs';
import { maintenance } from '../e2e/support/maintenance-fixture.mjs';

const port = Number(process.env.PORT || 8080);
const assets = fileURLToPath(new URL('../server/src/main/resources/admin/', import.meta.url));
const identity = { id: 'owner-1', email: 'owner@fasttowin.vn', role: 'OWNER', active: true };

const state = {
  role: 'OWNER',
  signedIn: false,
  calls: [],
  handlers: new Map(),
  worker: 'current'
};

// Wire all functional modules with rich mock datasets
analytics({ handlers: state.handlers }, call => {
  const now = Date.now(), from = Number(call.query.get('from') || now - 7 * 86400000), until = Number(call.query.get('until') || now);
  const names = (call.query.get('metric') || 'REGISTRATIONS').split(',');
  const coverage = { source: 'SERVER', environment: 'LIVE', status: 'UNAVAILABLE', coverageStart: null, freshness: null };
  return { range: { from, until }, comparisonRange: { from: from - (until - from), until: from }, timezone: 'Asia/Bangkok', bucket: 'DAY', snapshotAt: now,
    metrics: names.map(metric => ({ metric, coverage: [coverage], groups: [] })) };
});
gifts({ handlers: state.handlers, role: state.role });
events({ handlers: state.handlers, role: state.role });
announcements({ handlers: state.handlers });
compensation({ handlers: state.handlers });
maintenance({ handlers: state.handlers });

// Additional mock players for rich directory display
const extraPlayers = [
  { ...player, id: 'user-001', playerCode: 'FTW001', displayName: 'Nguyễn Văn An', email: 'an.nguyen@example.com', level: 25, gold: 154000, gems: 850, elo: 1820, clanName: 'Rồng Vàng', status: 'ACTIVE' },
  { ...player, id: 'user-002', playerCode: 'FTW002', displayName: 'Trần Thị Bình', email: 'binh.tran@example.com', level: 14, gold: 42000, gems: 120, elo: 1350, clanName: 'Chiến Binh', status: 'ACTIVE' },
  { ...player, id: 'user-003', playerCode: 'FTW003', displayName: 'Lê Hoàng Long', email: 'long.le@example.com', level: 32, gold: 980000, gems: 2400, elo: 2150, clanName: 'Bất Bại', status: 'ACTIVE' },
  { ...player, id: 'user-004', playerCode: 'FTW004', displayName: 'Phạm Minh Quân', email: 'quan.pm@example.com', level: 5, gold: 8500, gems: 15, elo: 1100, clanName: null, status: 'ACTIVE' },
  { ...player, id: 'user-005', playerCode: 'FTW005', displayName: 'Tài khoản Vi Phạm', email: 'hacker@cheat.invalid', level: 99, gold: 9999999, gems: 99999, elo: 3000, clanName: null, status: 'BLOCKED' },
];

const allPlayers = [player, ...extraPlayers];

const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  const route = url.pathname;

  if (route === '/' || route === '/index.html') {
    res.writeHead(302, { Location: '/admin/' });
    res.end();
    return;
  }

  if (route === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Cache-Control': 'no-store' });
    res.end('OK');
    return;
  }

  if (route === '/service-worker.js') {
    try {
      const sw = await readFile(new URL('../webApp/src/wasmJsMain/resources/service-worker.js', import.meta.url), 'utf8');
      res.writeHead(200, { 'Content-Type': 'application/javascript', 'Cache-Control': 'no-store' });
      res.end(sw);
    } catch {
      res.writeHead(200, { 'Content-Type': 'application/javascript' });
      res.end('// Service worker placeholder');
    }
    return;
  }

  // Admin Assets (HTML, CSS, JS)
  if (!route.startsWith('/admin/api/')) {
    const assetMap = {
      '/admin': 'index.html',
      '/admin/': 'index.html',
      '/admin/index.html': 'index.html',
      '/admin/admin.css': 'admin.css',
      '/admin/admin.js': 'admin.js',
      '/admin/admin-network.js': 'admin-network.js',
      '/admin/admin-gifts.js': 'admin-gifts.js',
      '/admin/admin-maintenance.js': 'admin-maintenance.js',
      '/admin/admin-analytics.js': 'admin-analytics.js',
      '/admin/admin-events.js': 'admin-events.js',
      '/admin/admin-compensation.js': 'admin-compensation.js',
      '/admin/admin-announcements.js': 'admin-announcements.js',
    };
    const name = assetMap[route];
    if (name) {
      try {
        const body = await readFile(assets + name);
        res.writeHead(200, {
          ...securityHeaders,
          'Content-Type': name.endsWith('.html') ? 'text/html; charset=utf-8' : name.endsWith('.css') ? 'text/css' : 'application/javascript',
        });
        res.end(name.endsWith('.html') ? body.toString('utf8').replace('<title>', '<title>[DỮ LIỆU MẪU] ') : body);
        return;
      } catch (err) {
        res.writeHead(404);
        res.end('Missing admin asset: ' + err.message);
        return;
      }
    }
  }

  // Read Request Body
  let raw = '';
  for await (const chunk of req) raw += chunk;
  const call = { route, method: req.method, query: url.searchParams, headers: req.headers, body: raw ? JSON.parse(raw) : null };
  const send = (status, body) => {
    if (!res.destroyed) {
      res.writeHead(status, { ...securityHeaders, 'Content-Type': 'application/json' });
      res.end(JSON.stringify(body));
    }
  };

  // Check custom module handlers
  const handler = state.handlers.get(route);
  if (handler) {
    const handled = await handler(call, send, state);
    if (handled) return;
  }

  // Core API Endpoints
  const admin = { ...identity, role: state.role };

  if (route.endsWith('/login')) {
    state.signedIn = true;
    const email = call.body?.email || 'owner@fasttowin.vn';
    admin.email = email;
    send(200, { admin, csrfToken: 'preview-csrf-token' });
    return;
  }

  if (route.endsWith('/session')) {
    if (state.signedIn) {
      send(200, { admin, csrfToken: 'preview-csrf-token' });
    } else {
      send(401, { code: 'UNAUTHORIZED', message: 'Phiên quản trị đã hết hạn.' });
    }
    return;
  }

  if (route.endsWith('/logout')) {
    state.signedIn = false;
    send(200, { ok: true });
    return;
  }

  if (!state.signedIn) {
    send(401, { code: 'UNAUTHORIZED', message: 'Vui lòng đăng nhập.' });
    return;
  }

  if (route === '/admin/api/player-overview') {
    const now = Date.now(), offset = 7 * 3600000;
    const today = Math.floor((now + offset) / 86400000) * 86400000 - offset;
    // Preview-only aggregate fixture; this server never connects to production data.
    send(200, { snapshotAt: now, todayFrom: today, todayUntil: now,
      totalPlayers: String(allPlayers.length), registeredPlayers: String(allPlayers.filter(p => p.accountType === 'REGISTERED').length),
      guestPlayers: String(allPlayers.filter(p => p.accountType === 'GUEST').length), playableAccounts: String(allPlayers.filter(p => p.status === 'ACTIVE').length),
      matchesToday: null, recordedParticipations: null, recordedWins: null, recordedLosses: null, recordedDraws: null,
      playedProfiles: null, clanCount: String(new Set(allPlayers.map(p => p.clanName).filter(Boolean)).size),
      clanMembers: String(allPlayers.filter(p => p.clanName).length),
      averageElo: String(allPlayers.reduce((sum, p) => sum + p.elo, 0) / allPlayers.length), maxElo: Math.max(...allPlayers.map(p => p.elo)) });
    return;
  }

  if (route === '/admin/api/users') {
    const p = Number(url.searchParams.get('page') || 0);
    const q = (url.searchParams.get('q') || '').toLowerCase();
    const filtered = allPlayers.filter(u =>
      !q || u.displayName.toLowerCase().includes(q) || u.playerCode.toLowerCase().includes(q) || (u.email && u.email.toLowerCase().includes(q))
    );
    send(200, pageOf(filtered, p, filtered.length));
    return;
  }

  if (route.startsWith('/admin/api/users/')) {
    const parts = route.split('/');
    const userId = parts[4];
    const sub = parts[5];
    const u = allPlayers.find(p => p.id === userId) || player;

    if (sub === 'matches') {
      send(200, pageOf([
        { id: 'match-1', matchType: 'RANKED', outcome: 'WIN', score: 150, finishedAtEpochMillis: Date.now() - 3600000 },
        { id: 'match-2', matchType: 'CASUAL', outcome: 'WIN', score: 90, finishedAtEpochMillis: Date.now() - 7200000 },
        { id: 'match-3', matchType: 'RANKED', outcome: 'LOSS', score: 40, finishedAtEpochMillis: Date.now() - 14400000 },
        { id: 'match-4', matchType: 'RANKED', outcome: 'DRAW', score: 85, finishedAtEpochMillis: Date.now() - 86400000 },
      ], 0, 4));
      return;
    }

    if (sub === 'wallet') {
      send(200, pageOf([
        { id: 'wallet-1', sourceType: 'ADMIN_GIFT', sourceId: 'gift-01', goldDelta: 1000, gemsDelta: 10, xpDelta: 200, createdAtEpochMillis: Date.now() - 3600000 },
        { id: 'wallet-2', sourceType: 'MATCH', sourceId: 'match-1', goldDelta: 150, gemsDelta: 0, xpDelta: 50, createdAtEpochMillis: Date.now() - 7200000 },
        { id: 'wallet-3', sourceType: 'DAILY_CHECK_IN', sourceId: 'checkin-today', goldDelta: 500, gemsDelta: 5, xpDelta: 30, createdAtEpochMillis: Date.now() - 86400000 },
      ], 0, 3));
      return;
    }

    if (sub === 'gifts') {
      send(200, pageOf([
        { id: 'wallet-g1', sourceType: 'ADMIN_GIFT', sourceId: 'gift-tet-2026', goldDelta: 5000, gemsDelta: 50, xpDelta: 500, createdAtEpochMillis: Date.now() - 86400000 * 5 },
        { id: 'wallet-g2', sourceType: 'COMPENSATION', sourceId: 'incident-network', goldDelta: 2000, gemsDelta: 20, xpDelta: 200, createdAtEpochMillis: Date.now() - 86400000 * 12 },
      ], 0, 2));
      return;
    }

    send(200, u);
    return;
  }

  if (route === '/admin/api/accounts') {
    if (req.method === 'GET') {
      send(200, pageOf([
        admin,
        { id: 'op-1', email: 'operator@fasttowin.vn', role: 'OPERATOR', active: true },
        { id: 'supp-1', email: 'support@fasttowin.vn', role: 'SUPPORT', active: true },
        { id: 'supp-old', email: 'old-support@fasttowin.vn', role: 'SUPPORT', active: false },
      ], 0, 4));
    } else {
      send(200, { id: 'new-admin-' + Date.now(), email: call.body.email, role: call.body.role, active: true });
    }
    return;
  }

  if (route.endsWith('/access')) {
    send(200, { id: route.split('/').at(-2), email: 'support@fasttowin.vn', role: call.body.role, active: call.body.active });
    return;
  }

  if (route === '/admin/api/audit') {
    send(200, pageOf([
      { id: 'audit-1', actorId: 'owner-1', action: 'ADMIN_LOGIN', targetId: null, requestId: 'req-01', reason: 'Đăng nhập phiên làm việc', beforeSummary: null, afterSummary: null, createdAtEpochMillis: Date.now() - 1000 * 60 * 5 },
      { id: 'audit-2', actorId: 'owner-1', action: 'USER_DETAIL', targetId: 'user-001', requestId: 'req-02', reason: 'Kiểm tra thông tin tài khoản', beforeSummary: null, afterSummary: null, createdAtEpochMillis: Date.now() - 1000 * 60 * 25 },
      { id: 'audit-3', actorId: 'owner-1', action: 'GIFT_PUBLISH', targetId: 'gift-01', requestId: 'req-03', reason: 'Phát thưởng sự kiện', beforeSummary: 'DRAFT', afterSummary: 'PUBLISHED', createdAtEpochMillis: Date.now() - 1000 * 60 * 60 },
      { id: 'audit-4', actorId: 'owner-1', action: 'MAINTENANCE_UPDATE', targetId: null, requestId: 'req-04', reason: 'Thiết lập bảo trì định kỳ', beforeSummary: 'ACTIVE', afterSummary: 'PLANNED', createdAtEpochMillis: Date.now() - 1000 * 3600 * 24 },
    ], 0, 4));
    return;
  }

  send(404, { code: 'NOT_FOUND', message: 'Không tìm thấy endpoint: ' + route });
});

server.listen(port, '127.0.0.1', () => {
  console.log(`================================================================`);
  console.log(` FastToWin Admin Preview — DỮ LIỆU MẪU, CHỈ LOCAL`);
  console.log(` Đang chạy tại: http://localhost:${port}/admin/`);
  console.log(` Hoặc:          http://127.0.0.1:${port}/admin/`);
  console.log(` Bạn có thể đăng nhập bằng bất kỳ email/mật khẩu nào!`);
  console.log(` (Ví dụ: owner@fasttowin.vn / Mật khẩu bất kỳ)`);
  console.log(`================================================================`);
});
