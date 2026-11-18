// Worker thread for scripts/load-test-scale.mjs. It only talks to the owned loopback server URL
// it is given by the parent, and reports aggregate histograms (never tokens, emails or passwords).
import { parentPort, workerData } from 'node:worker_threads';
import { monitorEventLoopDelay } from 'node:perf_hooks';
import { randomUUID } from 'node:crypto';
import { newHistogram, recordSample } from './lib/load-test-utils.mjs';

const { url, protocolVersion, selectIntervalMs, restSeconds } = workerData;
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(url)) throw new Error('Worker accepts only the owned loopback server');
const wsUrl = url.replace('http:', 'ws:') + '/game';
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const jitter = (ms, spread = 0.2) => ms * (1 - spread + Math.random() * 2 * spread);
const STOPPED = 'STOPPED';
const players = new Set(), loops = new Set();
let stopping = false, stats = new Map(), counters = newCounters();
const loopDelay = monitorEventLoopDelay({ resolution: 10 }); loopDelay.enable();

function newCounters() { return { matchesCompleted: 0, selectionsRejected: 0, loopFailures: 0, framesReceived: 0 }; }
function record(op, ms, ok, code) {
  if (!stats.has(op)) stats.set(op, newHistogram());
  recordSample(stats.get(op), ms, ok, code);
}
const errorCode = error => /^[A-Z0-9_]+$/.test(error?.message) ? error.message : 'ASSERTION_OR_TRANSPORT_FAILURE';
async function measured(op, action) {
  const start = performance.now();
  try { const value = await action(); record(op, performance.now() - start, true); return value; }
  catch (error) {
    if (error?.message !== STOPPED) record(op, performance.now() - start, false, errorCode(error));
    throw error;
  }
}

class Player {
  constructor(spec) { Object.assign(this, spec); this.ws = null; this.waiters = []; this.id = null; this.resumeToken = null; }

  async login() {
    const response = await fetch(url + '/auth/login', { method: 'POST', signal: AbortSignal.timeout(15000),
      headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': this.ip },
      body: JSON.stringify({ email: this.email, password: this.password, devicePlatform: 'ANDROID' }) });
    if (!response.ok) throw new Error('HTTP_' + response.status);
    this.accessToken = (await response.json()).accessToken;
  }

  async connect(resume = false) {
    if (stopping && !resume) throw new Error(STOPPED);
    const ws = new WebSocket(wsUrl, { headers: { 'X-Forwarded-For': this.ip } });
    this.ws = ws;
    ws.addEventListener('message', event => this.onFrame(event.data));
    ws.addEventListener('close', () => this.failAll(stopping ? STOPPED : 'SOCKET_CLOSED'));
    await new Promise((done, reject) => {
      const timer = setTimeout(() => { ws.close(); reject(new Error('SOCKET_OPEN_TIMEOUT')); }, 15000);
      ws.addEventListener('open', () => { clearTimeout(timer); done(); }, { once: true });
      ws.addEventListener('error', () => { clearTimeout(timer); reject(new Error('SOCKET_OPEN_ERROR')); }, { once: true });
    });
    const ready = await this.request({ type: 'connect_account', accessToken: this.accessToken, protocolVersion,
      ...(resume && this.resumeToken ? { resumeToken: this.resumeToken } : {}) }, ['session_ready']);
    this.id = ready.playerId; this.resumeToken = ready.resumeToken;
    players.add(this);
    return ready;
  }

  onFrame(data) {
    counters.framesReceived++;
    // Parse only frames somebody waits for; broadcasts (room lists, presence) are counted and dropped.
    const type = /^\{"type":"([a-z_]+)"/.exec(typeof data === 'string' ? data.slice(0, 64) : '')?.[1];
    if (!type) return;
    if (type === 'error') {
      let code = 'SERVER_ERROR';
      try { const parsed = JSON.parse(data); if (/^[A-Z0-9_]+$/.test(parsed.code)) code = parsed.code; } catch { /* keep generic */ }
      const waiter = this.waiters.shift();
      if (waiter) { clearTimeout(waiter.timer); waiter.reject(new Error(code)); }
      else record('server.unsolicited_error', 0, false, code);
      return;
    }
    let message;
    for (let i = 0; i < this.waiters.length; i++) {
      const waiter = this.waiters[i];
      if (!waiter.types.includes(type)) continue;
      message ??= JSON.parse(data);
      if (!waiter.predicate(message)) continue;
      this.waiters.splice(i, 1); clearTimeout(waiter.timer); waiter.resolve(message); return;
    }
  }

  request(body, types, predicate = () => true, timeoutMs = 15000) {
    return new Promise((resolve, reject) => {
      if (stopping && body.type !== 'connect_account') return reject(new Error(STOPPED));
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return reject(new Error('SOCKET_CLOSED'));
      const waiter = { types, predicate, resolve, reject };
      waiter.timer = setTimeout(() => {
        const index = this.waiters.indexOf(waiter); if (index >= 0) this.waiters.splice(index, 1);
        reject(new Error('SOCKET_EVENT_TIMEOUT'));
      }, timeoutMs);
      this.waiters.push(waiter);
      this.ws.send(JSON.stringify(body));
    });
  }

  send(body) { if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify(body)); }
  failAll(code) { for (const waiter of this.waiters.splice(0)) { clearTimeout(waiter.timer); waiter.reject(new Error(code)); } }
  close() {
    players.delete(this);
    const ws = this.ws; if (!ws || ws.readyState === WebSocket.CLOSED) return Promise.resolve();
    return new Promise(done => {
      const timer = setTimeout(done, 3000);
      ws.addEventListener('close', () => { clearTimeout(timer); done(); }, { once: true });
      ws.close();
    });
  }
}

async function startPlayers(group) {
  await Promise.all(group.map(p => measured('http.login', () => p.login())));
  await Promise.all(group.map(p => measured('ws.connect', () => p.connect())));
}

async function idleLoop(spec) {
  const player = new Player(spec);
  await startPlayers([player]);
  // Lobby/friends screen: presence every 15 s, room list every ~30 s (like the real client).
  let tick = 0;
  while (!stopping) {
    await delay(jitter(15000));
    if (stopping) break;
    player.send({ type: 'get_presence', requestId: String(++tick) });
    if (tick % 2 === 0) await measured('ws.list_rooms', () => player.request({ type: 'list_rooms' }, ['room_list']));
  }
}

async function pairLoop(specs) {
  let [host, guest] = specs.map(s => new Player(s));
  await startPlayers([host, guest]);
  let round = 0;
  while (!stopping) {
    const roomId = await measured('ws.create_room', async () => (await host.request({ type: 'create_room',
      roomName: `Load ${host.index}-${round++}`, password: '', gameMode: 'ORDER', matchType: 'CASUAL' }, ['room_created'])).game.roomId);
    await measured('ws.join_room', () => guest.request({ type: 'join_room', roomId, password: '', asSpectator: false },
      ['room_updated'], m => m.game.roomId === roomId && m.game.players.length === 2));
    await measured('ws.start_game', () => {
      const started = host.request({ type: 'set_ready', roomId, ready: true }, ['game_started'], m => m.game.roomId === roomId);
      guest.send({ type: 'set_ready', roomId, ready: true });
      return started;
    });
    for (let number = 1; number <= 50; number++) {
      await delay(jitter(selectIntervalMs));
      const actor = number % 2 ? host : guest;
      const event = await measured('ws.select_number', () => actor.request(
        { type: 'select_number', roomId, number, requestId: randomUUID() }, ['game_state_updated', 'game_finished'],
        m => m.game.roomId === roomId && (m.type === 'game_finished' || (m.acceptedNumber === number && m.selectedByPlayerId === actor.id))));
      if (event.type === 'game_state_updated' && !event.selectionAccepted) counters.selectionsRejected++;
      if (event.type === 'game_finished') break;
    }
    await Promise.all([host, guest].map(actor => measured('ws.leave_room', () =>
      actor.request({ type: 'leave_room', roomId }, ['room_closed'], m => m.roomId === roomId))));
    counters.matchesCompleted++;
    await delay(jitter(restSeconds * 1000, 0.5));
  }
}

function launch(unit) {
  const run = (unit.kind === 'pair' ? pairLoop(unit.players) : idleLoop(unit.players[0]))
    .catch(error => { if (error?.message !== STOPPED) counters.loopFailures++; });
  loops.add(run); run.finally(() => loops.delete(run));
}

function snapshot() {
  const result = { ops: Object.fromEntries(stats), counters, connected: players.size,
    loopDelayP99Ms: Math.round(loopDelay.percentile(99) / 1e4) / 100, loopDelayMaxMs: Math.round(loopDelay.max / 1e4) / 100 };
  stats = new Map(); counters = newCounters(); loopDelay.reset();
  return result;
}

async function reconnectStorm(jitterMs) {
  stopping = true;
  const connected = [...players];
  await Promise.allSettled(connected.map(p => p.close()));
  await Promise.allSettled([...loops]);
  stopping = false;
  const start = performance.now();
  await Promise.allSettled(connected.map(async p => {
    if (jitterMs) await delay(Math.random() * jitterMs);
    await measured('ws.reconnect', () => p.connect(true));
  }));
  return { stormSeconds: Math.round((performance.now() - start) / 10) / 100, attempted: connected.length };
}

setInterval(() => parentPort.postMessage({ type: 'status', connected: players.size, loops: loops.size }), 1000).unref();
parentPort.on('message', async message => {
  if (message.type === 'spawn') message.units.forEach(launch);
  else if (message.type === 'window') parentPort.postMessage({ type: 'stats', name: message.name, ...snapshot() });
  else if (message.type === 'storm') {
    const result = await reconnectStorm(message.jitterMs);
    parentPort.postMessage({ type: 'storm-done', ...result });
  } else if (message.type === 'stop') {
    stopping = true;
    await Promise.allSettled([...players].map(p => p.close()));
    await Promise.race([Promise.allSettled([...loops]), delay(5000)]);
    parentPort.postMessage({ type: 'stopped', ...snapshot() });
  }
});
