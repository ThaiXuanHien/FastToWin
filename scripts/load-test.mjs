// Owned, isolated baseline only: no target URL option, provider credentials or existing server.
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdir, readFile, writeFile, readdir } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { createServer } from 'node:net';
import { cpus, totalmem, platform, release } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { parseOptions, summarize, validateDatabaseSettings } from './lib/load-test-utils.mjs';

const options = parseOptions(process.argv.slice(2));
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = join(root, '.artifacts', 'load-tests', new Date().toISOString().replace(/[:.]/g, '-'));
const safeEnvironment = Object.fromEntries(['PATH', 'Path', 'SystemRoot', 'WINDIR', 'TEMP', 'TMP']
  .filter(key => process.env[key]).map(key => [key, process.env[key]]));
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', platform() === 'win32' ? 'java.exe' : 'java') : 'java';
const runFile = promisify(execFile), sockets = new Set(), samples = new Map(), resources = [];
const schema = 'loadtest_' + randomUUID().replaceAll('-', '');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
let db, child, schemaAttempted = false, sampler, timedOut = false, interrupted = false;
const cancellation = new AbortController();
const ensureActive = () => {
  if (cancellation.signal.aborted) throw new Error(timedOut ? 'DEADLINE_EXCEEDED' : 'INTERRUPTED');
};
let url, measuredStart, measuredSeconds, databaseResources, setupFailure;
await readdir(join(root, 'server/build/install/server/lib')); // Require an explicit installDist build.
await mkdir(output, { recursive: true });
const socketTemp = join(root, '.tmp', 'jvm-sockets');
await mkdir(socketTemp, { recursive: true });
const logs = createWriteStream(join(output, 'server.log'));
const report = {
  createdAt: new Date().toISOString(), workload: { ...options, postgresSettings: undefined, psql: undefined },
  storage: options.postgresSettings ? 'postgresql-isolated-schema' : 'memory',
  hardware: { os: platform(), release: release(), cpu: cpus()[0]?.model, logicalCpus: cpus().length,
    totalMemoryBytes: totalmem(), node: process.version, jvmHeapMaxBytes: 512 * 1024 * 1024 },
  protocolVersion: 43, rateLimits: 'unchanged', clickIntervalMs: 200,
};

async function sql(query, cleanup = false) {
  // Password is passed only through the psql process environment, never arguments/logs.
  return new Promise((accept, reject) => {
    if (!cleanup) ensureActive();
    const p = spawn(options.psql, ['-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1',
      '-p', '15432', '-d', 'admin_test', '-U', db.user], {
      env: { ...safeEnvironment, PGPASSWORD: db.password, PGCONNECT_TIMEOUT: '5' }, windowsHide: true,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    let text = ''; p.stdout.on('data', chunk => { text += chunk; }); p.stderr.resume();
    const abort = () => { p.kill(); reject(new Error('DATABASE_OPERATION_CANCELLED')); };
    if (!cleanup) cancellation.signal.addEventListener('abort', abort, { once: true });
    const finished = () => {
      clearTimeout(timeout); cancellation.signal.removeEventListener('abort', abort);
    };
    const timeout = setTimeout(() => { p.kill(); reject(new Error('LOCAL_DATABASE_TIMEOUT')); }, 15000);
    p.on('error', () => { finished(); reject(new Error('LOCAL_DATABASE_PROCESS_ERROR')); });
    p.on('exit', code => { finished(); code === 0 ? accept(text.trim()) : reject(new Error('LOCAL_DATABASE_QUERY_FAILED')); });
    p.stdin.on('error', () => {}); p.stdin.end(query);
  });
}

async function http(path, body) {
  ensureActive();
  if (!child || child.exitCode !== null || child.signalCode !== null) throw new Error('OWN_SERVER_STOPPED');
  const response = await fetch(url + path, { signal: AbortSignal.any([cancellation.signal, AbortSignal.timeout(15000)]),
    method: body ? 'POST' : 'GET', headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined });
  if (!response.ok) throw new Error('HTTP_' + response.status);
  return response.json();
}

async function measured(name, action) {
  const start = performance.now(); let ok = false, code;
  try { const result = await action(); ok = true; return result; }
  catch (error) {
    code = /^[A-Z0-9_]+$/.test(error.message) ? error.message : 'ASSERTION_OR_TRANSPORT_FAILURE';
    throw error;
  } finally {
    if (!samples.has(name)) samples.set(name, []);
    samples.get(name).push({ ms: performance.now() - start, ok, ...(code ? { code } : {}) });
  }
}

async function connect(auth, resumeToken) {
  ensureActive();
  const ws = new WebSocket(url.replace('http:', 'ws:') + '/game'), messages = [];
  const actor = { ws, id: null, resumeToken: null, send: body => { ensureActive(); ws.send(JSON.stringify(body)); },
    clear(type) { for (let i = messages.length - 1; i >= 0; i--) if (messages[i].type === type) messages.splice(i, 1); },
    async wait(types, predicate = () => true) {
      const allowed = Array.isArray(types) ? types : [types], deadline = Date.now() + 15000;
      while (Date.now() < deadline) {
        ensureActive();
        const error = messages.find(m => m.type === 'error');
        if (error) throw new Error(/^[A-Z0-9_]+$/.test(error.code) ? error.code : 'SERVER_ERROR');
        const index = messages.findIndex(m => allowed.includes(m.type) && predicate(m));
        if (index >= 0) return messages.splice(index, 1)[0];
        if (ws.readyState === WebSocket.CLOSED) throw new Error('SOCKET_CLOSED');
        await delay(5);
      }
      throw new Error('SOCKET_EVENT_TIMEOUT');
    },
    async close() {
      if (ws.readyState === WebSocket.CLOSED) return;
      await new Promise((done, reject) => {
        const timer = setTimeout(() => reject(new Error('SOCKET_CLOSE_TIMEOUT')), 3000);
        ws.addEventListener('close', () => { clearTimeout(timer); done(); }, { once: true }); ws.close();
      });
    },
  };
  sockets.add(actor);
  ws.addEventListener('message', event => {
    try { messages.push(JSON.parse(event.data)); } catch { messages.push({ type: 'error', code: 'INVALID_FRAME' }); }
  });
  await new Promise((done, reject) => {
    const timer = setTimeout(() => { ws.close(); reject(new Error('SOCKET_OPEN_TIMEOUT')); }, 15000);
    ws.addEventListener('open', () => { clearTimeout(timer); done(); }, { once: true });
    ws.addEventListener('error', () => { clearTimeout(timer); reject(new Error('SOCKET_OPEN_ERROR')); }, { once: true });
  });
  actor.send({ type: 'connect_account', accessToken: auth.accessToken, protocolVersion: 43,
    ...(resumeToken ? { resumeToken } : {}) });
  const ready = await actor.wait('session_ready'); actor.id = ready.playerId; actor.resumeToken = ready.resumeToken;
  actor.currentGame = ready.currentGame; return actor;
}

async function account(index) {
  const email = `load-${randomUUID()}@example.invalid`, password = 'Load-' + randomUUID() + '9!';
  const auth = await http('/auth/register', { email, password, displayName: 'Load ' + index, devicePlatform: 'ANDROID' });
  const verification = await http('/auth/email-verification/request', { accessToken: auth.accessToken });
  assert.match(verification.devEmailVerificationCode || '', /^\d{6}$/);
  await http('/auth/email-verification/confirm', { accessToken: auth.accessToken,
    verificationCode: verification.devEmailVerificationCode });
  // HTTP login is measured; registration/verification is setup, not workload latency.
  return { email, password };
}

async function pair(index, credentials) {
  const auths = await Promise.all(credentials.map(c => measured('http.login', () =>
    http('/auth/login', { ...c, devicePlatform: 'ANDROID' }))));
  let [host, opponent] = await Promise.all(auths.map(a => measured('ws.connect', () => connect(a))));
  for (let round = 0; round < options.rounds; round++) {
    await measured('http.status', () => http('/api/status'));
    await measured('ws.list_rooms', async () => {
      host.clear('room_list'); host.send({ type: 'list_rooms' }); await host.wait('room_list');
    });
    const room = await measured('ws.create_room', async () => {
      host.send({ type: 'create_room', roomName: `Load ${index}-${round}`, password: '', gameMode: 'ORDER', matchType: 'CASUAL' });
      return (await host.wait('room_created')).game.roomId;
    });
    await measured('ws.join_room', async () => {
      opponent.send({ type: 'join_room', roomId: room, password: '', asSpectator: false });
      await opponent.wait('room_updated', m => m.game.roomId === room && m.game.players.length === 2);
    });
    await measured('ws.start_game', async () => {
      host.send({ type: 'set_ready', roomId: room, ready: true });
      opponent.send({ type: 'set_ready', roomId: room, ready: true });
      await host.wait('game_started', m => m.game.roomId === room);
    });
    for (let number = 1; number <= 50; number++) {
      if (number === 26) {
        const old = opponent;
        await measured('ws.disconnect_resume', async () => {
          await old.close(); await delay(200);
          opponent = await connect(auths[1], old.resumeToken);
          assert.equal(opponent.id, old.id); assert.equal(opponent.currentGame?.roomId, room);
        });
      }
      await measured('ws.select_number', async () => {
        host.send({ type: 'select_number', roomId: room, number, requestId: randomUUID() });
        const event = await host.wait(['game_state_updated', 'game_finished'], m =>
          m.game.roomId === room && (number === 50 ? m.game.phase === 'FINISHED' :
            m.acceptedNumber === number && m.selectionAccepted));
        if (number === 50) assert.equal(event.game.winnerPlayerId, host.id);
      });
      await delay(200);
    }
    await Promise.all([host, opponent].map(actor => measured('ws.leave_room', async () => {
      actor.send({ type: 'leave_room', roomId: room });
      await actor.wait('room_closed', m => m.roomId === room && m.code === 'RESULTS_LEFT');
    })));
    report.completedMatches = (report.completedMatches || 0) + 1;
  }
  await Promise.all([host.close(), opponent.close()]);
}

async function resourceSample() {
  const response = await fetch(url + '/internal/metrics', { signal: AbortSignal.timeout(3000) });
  if (!response.ok) throw new Error('METRICS_UNAVAILABLE');
  const metrics = Object.fromEntries((await response.text()).split('\n').filter(l => /^fasttowin_[a-z_]+ \d/.test(l))
    .map(l => { const [key, value] = l.split(' '); return [key, Number(value)]; }));
  let processStats;
  try {
    if (platform() === 'win32') {
      const { stdout } = await runFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
        `Get-Process -Id ${child.pid} | Select-Object CPU,WorkingSet64 | ConvertTo-Json -Compress`],
        { windowsHide: true, timeout: 3000, env: safeEnvironment });
      processStats = JSON.parse(stdout);
    } else {
      const { stdout } = await runFile('ps', ['-p', String(child.pid), '-o', '%cpu=,rss='],
        { timeout: 3000, env: safeEnvironment });
      const [cpuPercent, rssKb] = stdout.trim().split(/\s+/).map(Number);
      processStats = { cpuPercent, WorkingSet64: rssKb * 1024 };
    }
  } catch { processStats = { availability: 'UNAVAILABLE' }; }
  resources.push({ seconds: Math.round((performance.now() - measuredStart) / 1000), metrics, process: processStats });
}

async function stopChild() {
  if (!child || child.exitCode !== null || child.signalCode !== null) return;
  const exited = new Promise(resolve => child.once('exit', resolve)); child.kill();
  await Promise.race([exited, delay(5000)]);
  if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await Promise.race([exited, delay(3000)]); }
  if (child.exitCode === null && child.signalCode === null) throw new Error('OWN_SERVER_DID_NOT_STOP');
}

const timeout = setTimeout(() => { timedOut = true; cancellation.abort(); child?.kill(); }, 240000);
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => {
  interrupted = true; cancellation.abort(); child?.kill();
});
try {
  const javaVersion = await runFile(java, ['-version'], { env: safeEnvironment, timeout: 10000, signal: cancellation.signal });
  ensureActive();
  report.hardware.java = javaVersion.stderr.split('\n').slice(0, 2).join(' ').trim();
  if (options.postgresSettings) {
    const settings = JSON.parse(await readFile(options.postgresSettings, 'utf8'));
    validateDatabaseSettings(settings);
    db = { ...settings, password: (await readFile(settings.passwordFile, 'utf8')).trim() };
    schemaAttempted = true; await sql(`CREATE SCHEMA ${schema};`);
  }
  const port = await new Promise((accept, reject) => {
    const socket = createServer(); socket.on('error', reject);
    socket.listen(0, '127.0.0.1', () => { const p = socket.address().port; socket.close(() => accept(p)); });
  });
  url = `http://127.0.0.1:${port}`;
  ensureActive();
  const env = { ...safeEnvironment, FASTTOWIN_ENV: 'dev', SERVER_HOST: '127.0.0.1', PORT: String(port),
    ...(db ? { DATABASE_URL: db.url + '?currentSchema=' + schema, DATABASE_USER: db.user,
      DATABASE_PASSWORD: db.password, DATABASE_POOL_SIZE: '10' } : {}) };
  child = spawn(java, ['-Xms128m', '-Xmx512m', `-Djdk.net.unixdomain.tmpdir=${socketTemp}`,
    '-cp', join(root, 'server/build/install/server/lib/*'),
    'com.hienthai.fastowin.server.MainKt'], { cwd: output, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(logs, { end: false }); child.stderr.pipe(logs, { end: false });
  report.ownedServerPid = child.pid;
  let startupText = '', ownedListenerReady = false;
  child.stdout.on('data', chunk => {
    startupText = (startupText + chunk.toString()).slice(-8192);
    ownedListenerReady ||= startupText.includes(`Responding at ${url}`);
  });
  let spawnError = false; child.on('error', () => { spawnError = true; });
  const startup = performance.now();
  while (true) {
    ensureActive();
    if (spawnError || child.exitCode !== null || child.signalCode !== null ||
        performance.now() - startup > 60000) throw new Error('OWN_SERVER_START_FAILED');
    try {
      if (!ownedListenerReady) { await delay(100); continue; }
      const health = await fetch(url + '/health', { signal: AbortSignal.any([cancellation.signal, AbortSignal.timeout(1000)]) });
      if (health.ok && (await health.text()).trim() === 'OK') break;
    } catch { /* Startup listener is not ready yet. */ }
    await delay(100);
  }
  report.startupSeconds = Math.round((performance.now() - startup) / 10) / 100;
  const credentials = [];
  for (let i = 0; i < options.pairs; i++) credentials.push([await account(i * 2), await account(i * 2 + 1)]);
  measuredStart = performance.now();
  await resourceSample();
  let sampling = Promise.resolve();
  sampler = setInterval(() => { sampling = sampling.then(resourceSample).catch(() => {
    report.resourceSampleFailures = (report.resourceSampleFailures || 0) + 1;
  }); }, 2000);
  const outcomes = await Promise.allSettled(credentials.map((c, i) => pair(i, c)));
  measuredSeconds = (performance.now() - measuredStart) / 1000;
  clearInterval(sampler); await sampling; await resourceSample();
  report.failedPairs = outcomes.filter(o => o.status === 'rejected').length;
  // Capture committed match/profile table sizes and schema-scoped buffer counters.
  if (db) {
    const settlementDeadline = Date.now() + 15000;
    do {
      report.persistedMatches = Number(await sql(`SELECT count(*) FROM ${schema}.matches;`));
      if (report.persistedMatches === (report.completedMatches || 0)) break;
      await delay(200);
    } while (Date.now() < settlementDeadline);
    if (report.persistedMatches !== (report.completedMatches || 0)) throw new Error('DATABASE_MATCH_SETTLEMENT_INCOMPLETE');
    // Closing the pool flushes connection-local PostgreSQL statistics before inspection.
    await stopChild();
    await delay(1100);
    databaseResources = JSON.parse(await sql(`SELECT COALESCE(json_agg(row_to_json(t)), '[]'::json) FROM (
      SELECT s.relname, s.n_live_tup, s.n_tup_ins, s.n_tup_upd, i.heap_blks_read, i.heap_blks_hit,
        pg_total_relation_size(s.relid) AS total_bytes
      FROM pg_stat_user_tables s JOIN pg_statio_user_tables i ON i.relid=s.relid
      WHERE s.schemaname='${schema}' ORDER BY s.relname) t;`));
  }
} catch (error) {
  setupFailure = /^[A-Z0-9_]+$/.test(error.message) ? error.message : 'SETUP_OR_HARNESS_FAILURE';
} finally {
  clearTimeout(timeout); clearInterval(sampler);
  await Promise.allSettled([...sockets].map(s => s.close()));
  try { await stopChild(); } catch { report.cleanupFailure = 'OWN_SERVER_DID_NOT_STOP'; }
  report.ownedServerStopped = !child || child.exitCode !== null || child.signalCode !== null;
  if (schemaAttempted && !report.cleanupFailure) {
    try { await sql(`DROP SCHEMA IF EXISTS ${schema} CASCADE;`, true); report.schemaRemoved = true; }
    catch { report.cleanupFailure = 'OWN_SCHEMA_DROP_FAILED'; }
  }
  logs.end();
  report.durationSeconds = Math.round((measuredSeconds || 0) * 100) / 100;
  report.operations = Object.fromEntries([...samples].map(([name, values]) => [name, summarize(values, measuredSeconds || 1)]));
  report.resources = resources; report.databaseResources = databaseResources || { availability: 'UNAVAILABLE', reason: 'No PostgreSQL run' };
  report.setupFailure = setupFailure; report.timedOut = timedOut; report.interrupted = interrupted;
  report.status = !setupFailure && !report.failedPairs && !timedOut && !interrupted && !report.cleanupFailure ? 'PASS' : 'FAIL';
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({ status: report.status, report: join(output, 'report.json'), completedMatches: report.completedMatches || 0,
    failedPairs: report.failedPairs, setupFailure, cleanupFailure: report.cleanupFailure }));
  if (report.status !== 'PASS') process.exitCode = 1;
}
