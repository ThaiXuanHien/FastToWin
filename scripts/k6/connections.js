// SCL-01: connection-capacity load test for a STAGING deployment you own.
//
//   k6 run -e TARGET=https://staging.example.com -e PEAK=500 scripts/k6/connections.js
//
// Each virtual user connects as a guest, waits for session_ready, keeps the socket open and
// sends a presence request every 30 s, like an idle player in the lobby. Stages ramp to PEAK,
// hold for HOLD (default 30m), then drop every socket at once and reconnect to rehearse a
// deploy. Never point TARGET at production.
//
// Server limits apply per source IP (60 new sockets/minute, FASTTOWIN_WS_MAX_PER_IP open
// sockets), so one machine can only ramp slowly. Spread VUs over several machines/IPs (for
// example k6 Cloud or a few VMs) and divide PEAK between them. See docs/load-testing.md.
import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const target = (__ENV.TARGET || '').replace(/\/$/, '');
if (!/^https:\/\//.test(target) && !/^http:\/\/(127\.0\.0\.1|localhost)(:\d+)?$/.test(target)) {
  throw new Error('Set TARGET to the https:// origin of a staging deployment (or a loopback dev server).');
}
const wsUrl = target.replace(/^http/, 'ws') + '/game';
const peak = Number(__ENV.PEAK || 200);
const hold = __ENV.HOLD || '30m';
const protocolVersion = Number(__ENV.PROTOCOL_VERSION || 44);

const sessionReady = new Trend('ftw_session_ready_ms', true);
const rejected = new Counter('ftw_rejected_sessions');

export const options = {
  scenarios: {
    lobby: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '10m', target: peak },
        { duration: hold, target: peak },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    ftw_session_ready_ms: ['p(95)<2000'],
    ftw_rejected_sessions: ['count<10'],
  },
};

export function setup() {
  const terms = http.get(`${target}/api/content-terms`);
  check(terms, { 'content terms available': r => r.status === 200 });
  return { termsVersion: terms.status === 200 ? terms.json('requiredVersion') : null };
}

export default function (data) {
  const started = Date.now();
  const response = ws.connect(wsUrl, {}, socket => {
    socket.on('open', () => {
      socket.send(JSON.stringify({
        type: 'connect_guest', displayName: `Load ${__VU}`, protocolVersion,
        acceptedContentTermsVersion: data.termsVersion,
      }));
    });
    socket.on('message', raw => {
      const message = JSON.parse(raw);
      if (message.type === 'session_ready') {
        sessionReady.add(Date.now() - started);
        socket.setInterval(() => {
          socket.send(JSON.stringify({ type: 'get_presence', requestId: `vu${__VU}-${Date.now()}` }));
        }, 30000);
      } else if (message.type === 'error' && !message.requestId) {
        rejected.add(1, { code: message.code });
        socket.close();
      }
    });
    // Hold the socket for most of the iteration; k6 starts a new iteration (a reconnect) after close.
    socket.setTimeout(() => socket.close(), Number(__ENV.SESSION_SECONDS || 600) * 1000);
  });
  check(response, { 'websocket upgraded': r => r && r.status === 101 });
  // Spread reconnects like the app does (random 0-2 s jitter on top of backoff).
  sleep(1 + Math.random() * 2);
}
