// SCL-01: many-player capacity test against an OWNED loopback server only. There is no target URL
// option: this script starts its own server JVM, optionally on an isolated schema of the local
// admin_test PostgreSQL and a loopback Redis container, ramps synthetic players in steps and
// reports per-step latency, throughput, errors and resources. See docs/load-testing.md.
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdir, readFile, writeFile, readdir } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { createServer } from 'node:net';
import { cpus, totalmem, platform, release } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID, randomInt } from 'node:crypto';
import { Worker } from 'node:worker_threads';
import { parseScaleOptions, validateDatabaseSettings, validateRedisSettings, syntheticAddress,
  newHistogram, mergeHistogram, summarizeHistogram } from './lib/load-test-utils.mjs';

const options = parseScaleOptions(process.argv.slice(2));
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const output = join(root, '.artifacts', 'load-tests', `scale-${stamp}${options.label ? '-' + options.label : ''}`);
const safeEnvironment = Object.fromEntries(['PATH', 'Path', 'SystemRoot', 'WINDIR', 'TEMP', 'TMP']
  .filter(key => process.env[key]).map(key => [key, process.env[key]]));
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', platform() === 'win32' ? 'java.exe' : 'java') : 'java';
const runFile = promisify(execFile);
const delay = ms => new Promise(done => setTimeout(done, ms));
const schema = 'loadtest_' + randomUUID().replaceAll('-', '');
const PROTOCOL_VERSION = 44;
const maxPlayers = options.steps.at(-1);
const addressOffset = randomInt(0, 0xfffffe - maxPlayers - 1); // Fresh per-IP rate-limit keys for every run.

let db, redis, child, url, schemaAttempted = false, timedOut = false, interrupted = false, setupFailure;
const cancellation = new AbortController();
const ensureActive = () => { if (cancellation.signal.aborted) throw new Error(timedOut ? 'DEADLINE_EXCEEDED' : 'INTERRUPTED'); };

const distribution = options.serverDist ? join(root, '.artifacts', 'dist', options.serverDist) : join(root, 'server/build/install/server');
await readdir(join(distribution, 'lib')); // Require an explicit installDist build (or a saved copy of one).
await mkdir(output, { recursive: true });
const socketTemp = join(root, '.tmp', 'jvm-sockets');
await mkdir(socketTemp, { recursive: true });
const serverCwd = join(output, 'server-cwd'); await mkdir(serverCwd, { recursive: true });
const logs = createWriteStream(join(output, 'server.log'));
const report = {
  createdAt: new Date().toISOString(), kind: 'scale', label: options.label || undefined,
  workload: { ...options, postgresSettings: undefined, redisSettings: undefined, psql: undefined },
  storage: options.postgresSettings ? 'postgresql-isolated-schema' : 'memory',
  redis: options.redisSettings ? 'local-container (rate limit + presence)' : 'none (in-memory rate limit, no presence)',
  hardware: { os: platform(), release: release(), cpu: cpus()[0]?.model, logicalCpus: cpus().length,
    totalMemoryBytes: totalmem(), node: process.version, jvm: '-Xms64m -Xmx512m (production flags)',
    serverCpus: options.serverCpus || 'all', serverDist: options.serverDist || 'server/build/install/server' },
  protocolVersion: PROTOCOL_VERSION, rateLimits: 'production defaults; one synthetic private IP per player via X-Forwarded-For',
  steps: [],
};

// ---------- local helpers (database, redis, resources) ----------
async function sql(query, cleanup = false) {
  return new Promise((accept, reject) => {
    if (!cleanup) ensureActive();
    // `--psql docker:<container>` runs psql inside the local test container (PGPASSWORD passes by name only).
    const inDocker = /^docker:([a-z0-9][a-z0-9_.-]*)$/.exec(options.psql);
    const psqlArgs = ['-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1',
      '-p', inDocker ? '5432' : '15432', '-d', 'admin_test', '-U', db.user];
    const p = spawn(inDocker ? 'docker' : options.psql, inDocker ? ['exec', '-i', '-e', 'PGPASSWORD', '-e', 'PGCONNECT_TIMEOUT',
      inDocker[1], 'psql', ...psqlArgs] : psqlArgs, {
      env: { ...safeEnvironment, PGPASSWORD: db.password, PGCONNECT_TIMEOUT: '5' }, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    let text = ''; p.stdout.on('data', chunk => { text += chunk; }); p.stderr.resume();
    const timeout = setTimeout(() => { p.kill(); reject(new Error('LOCAL_DATABASE_TIMEOUT')); }, 60000);
    p.on('error', () => { clearTimeout(timeout); reject(new Error('LOCAL_DATABASE_PROCESS_ERROR')); });
    p.on('exit', code => { clearTimeout(timeout); code === 0 ? accept(text.trim()) : reject(new Error('LOCAL_DATABASE_QUERY_FAILED')); });
    p.stdin.on('error', () => {}); p.stdin.end(query);
  });
}

async function redisInfo() {
  // The monitor password goes to redis-cli through stdin, never through arguments.
  return new Promise((accept, reject) => {
    const p = spawn('docker', ['exec', '-i', redis.container, 'sh', '-c',
      'REDISCLI_AUTH=$(cat) exec redis-cli --no-auth-warning --user ftw_monitor INFO all'],
    { env: safeEnvironment, windowsHide: true, stdio: ['pipe', 'pipe', 'ignore'] });
    let text = ''; p.stdout.on('data', chunk => { text += chunk; });
    const timeout = setTimeout(() => { p.kill(); reject(new Error('REDIS_INFO_TIMEOUT')); }, 5000);
    p.on('error', () => { clearTimeout(timeout); reject(new Error('REDIS_INFO_FAILED')); });
    p.on('exit', code => {
      clearTimeout(timeout);
      if (code !== 0) return reject(new Error('REDIS_INFO_FAILED'));
      const info = Object.fromEntries(text.split(/\r?\n/).filter(l => l.includes(':') && !l.startsWith('#')).map(l => {
        const i = l.indexOf(':'); return [l.slice(0, i), l.slice(i + 1)]; }));
      const evalCalls = /calls=(\d+),usec=(\d+)/.exec(info.cmdstat_eval || '') || [];
      accept({ commands: Number(info.total_commands_processed), usedMemoryBytes: Number(info.used_memory),
        connectedClients: Number(info.connected_clients), cpuSeconds: Number(info.used_cpu_sys) + Number(info.used_cpu_user),
        evalCalls: Number(evalCalls[1] || 0), evalUsec: Number(evalCalls[2] || 0), rejectedConnections: Number(info.rejected_connections) });
    });
    p.stdin.end(redis.monitorPassword);
  });
}

async function serverCpuSeconds() {
  if (platform() === 'win32') {
    const { stdout } = await runFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
      `(Get-Process -Id ${child.pid}).TotalProcessorTime.TotalSeconds`], { windowsHide: true, timeout: 5000, env: safeEnvironment });
    return Number(stdout.trim().replace(',', '.'));
  }
  const { stdout } = await runFile('ps', ['-p', String(child.pid), '-o', 'cputimes='], { timeout: 5000, env: safeEnvironment });
  return Number(stdout.trim());
}

async function containerStats() {
  const names = ['ftw-test-pg', ...(redis ? [redis.container] : [])];
  const { stdout } = await runFile('docker', ['stats', '--no-stream', '--format', '{{.Name}} {{.CPUPerc}}', ...names],
    { windowsHide: true, timeout: 8000, env: safeEnvironment });
  return Object.fromEntries(stdout.trim().split(/\r?\n/).map(l => { const [n, c] = l.split(' '); return [n, Number(c.replace('%', ''))]; }));
}

async function serverMetrics() {
  const response = await fetch(url + '/internal/metrics', { signal: AbortSignal.timeout(3000) });
  if (!response.ok) throw new Error('METRICS_UNAVAILABLE');
  return Object.fromEntries((await response.text()).split('\n')
    .map(l => /^(fasttowin_[a-z_]+(?:\{[^}]*\})?) (-?[\d.eE+]+)$/.exec(l.trim())).filter(Boolean).map(m => [m[1], Number(m[2])]));
}

const samples = [];
let phase = 'setup', previousCpu, previousRedis, previousPg;
async function sampleResources() {
  const at = performance.now(), sample = { phase, t: Math.round(at / 100) / 10 };
  const tasks = [
    serverMetrics().then(m => { sample.metrics = m; }),
    serverCpuSeconds().then(cpu => {
      if (previousCpu) sample.serverCpuPercent = Math.round((cpu - previousCpu.cpu) / ((at - previousCpu.at) / 1000) * 1000) / 10;
      previousCpu = { cpu, at };
    }),
  ];
  if (redis) tasks.push(redisInfo().then(info => {
    if (previousRedis) {
      const seconds = (at - previousRedis.at) / 1000;
      sample.redisOpsPerSecond = Math.round((info.commands - previousRedis.info.commands) / seconds);
      sample.redisCpuPercent = Math.round((info.cpuSeconds - previousRedis.info.cpuSeconds) / seconds * 1000) / 10;
      const calls = info.evalCalls - previousRedis.info.evalCalls;
      sample.redisEvalAvgUsec = calls ? Math.round((info.evalUsec - previousRedis.info.evalUsec) / calls * 10) / 10 : null;
    }
    sample.redisMemoryBytes = info.usedMemoryBytes; sample.redisClients = info.connectedClients;
    previousRedis = { info, at };
  }));
  if (db) tasks.push(sql(`SELECT xact_commit + xact_rollback FROM pg_stat_database WHERE datname = 'admin_test';`).then(text => {
    const total = Number(text);
    if (previousPg) sample.pgTransactionsPerSecond = Math.round((total - previousPg.total) / ((at - previousPg.at) / 1000));
    previousPg = { total, at };
  }));
  tasks.push(containerStats().then(stats => { sample.containerCpuPercent = stats; }));
  const settled = await Promise.allSettled(tasks);
  sample.failures = settled.filter(s => s.status === 'rejected').length || undefined;
  samples.push(sample);
}

async function stopChild() {
  if (!child || child.exitCode !== null || child.signalCode !== null) return;
  const exited = new Promise(done => child.once('exit', done)); child.kill();
  await Promise.race([exited, delay(10000)]);
  if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await Promise.race([exited, delay(3000)]); }
  if (child.exitCode === null && child.signalCode === null) throw new Error('OWN_SERVER_DID_NOT_STOP');
}

// ---------- worker pool ----------
const workers = [], workerStatus = new Map();
let pendingReplies = new Map();
function startWorkers() {
  for (let i = 0; i < options.workers; i++) {
    const worker = new Worker(new URL('./load-test-scale-worker.mjs', import.meta.url), {
      workerData: { url, protocolVersion: PROTOCOL_VERSION, selectIntervalMs: options.selectIntervalMs, restSeconds: options.restSeconds } });
    worker.on('message', message => {
      if (message.type === 'status') workerStatus.set(i, message);
      else pendingReplies.get(i)?.(message);
    });
    worker.on('error', () => { report.workerFailures = (report.workerFailures || 0) + 1; });
    workers.push(worker);
  }
}
function askAll(message, timeoutMs = 120000) {
  return Promise.all(workers.map((worker, i) => new Promise((accept, reject) => {
    const timer = setTimeout(() => { pendingReplies.delete(i); reject(new Error('WORKER_REPLY_TIMEOUT')); }, timeoutMs);
    pendingReplies.set(i, reply => { clearTimeout(timer); pendingReplies.delete(i); accept(reply); });
    worker.postMessage(message);
  })));
}
const connectedPlayers = () => [...workerStatus.values()].reduce((sum, s) => sum + s.connected, 0);

function mergeWindow(replies, seconds) {
  const ops = {}, counters = {};
  for (const reply of replies) {
    for (const [op, histogram] of Object.entries(reply.ops)) mergeHistogram(ops[op] ??= newHistogram(), histogram);
    for (const [key, value] of Object.entries(reply.counters)) counters[key] = (counters[key] || 0) + value;
  }
  const all = Object.values(ops).reduce((acc, h) => mergeHistogram(acc, h), newHistogram());
  return { seconds: Math.round(seconds * 10) / 10, operations: Object.fromEntries(Object.entries(ops).map(([op, h]) => [op, summarizeHistogram(h, seconds)])),
    counters, allOperations: summarizeHistogram(all, seconds),
    clientLoopDelayP99Ms: Math.max(...replies.map(r => r.loopDelayP99Ms)), clientLoopDelayMaxMs: Math.max(...replies.map(r => r.loopDelayMaxMs)) };
}

function resourceSummary(name) {
  const window = samples.filter(s => s.phase === name);
  const values = pick => window.map(pick).filter(v => typeof v === 'number' && Number.isFinite(v));
  const avg = list => list.length ? Math.round(list.reduce((a, b) => a + b, 0) / list.length * 10) / 10 : null;
  const max = list => list.length ? Math.max(...list) : null;
  const metric = (s, name) => s.metrics?.[name];
  const heap = values(s => metric(s, 'fasttowin_jvm_memory_used_bytes'));
  const delta = name => {
    const list = window.map(s => metric(s, name)).filter(v => v !== undefined);
    return list.length > 1 ? list.at(-1) - list[0] : null;
  };
  return {
    serverCpuPercentAvg: avg(values(s => s.serverCpuPercent)), serverCpuPercentMax: max(values(s => s.serverCpuPercent)),
    heapUsedMiBMax: heap.length ? Math.round(max(heap) / 1048576) : null,
    wsConnectionsMax: max(values(s => metric(s, 'fasttowin_websocket_connections'))),
    dbPoolPendingMax: max(values(s => metric(s, 'fasttowin_db_pool_pending_threads'))),
    dbPoolActiveMax: max(values(s => metric(s, 'fasttowin_db_pool_connections{state="active"}'))),
    pgTransactionsPerSecondAvg: avg(values(s => s.pgTransactionsPerSecond)),
    postgresContainerCpuPercentAvg: avg(values(s => s.containerCpuPercent?.['ftw-test-pg'])),
    redisOpsPerSecondAvg: avg(values(s => s.redisOpsPerSecond)), redisOpsPerSecondMax: max(values(s => s.redisOpsPerSecond)),
    redisCpuPercentAvg: avg(values(s => s.redisCpuPercent)), redisEvalAvgUsec: avg(values(s => s.redisEvalAvgUsec)),
    redisMemoryMiBMax: (() => { const m = max(values(s => s.redisMemoryBytes)); return m === null ? null : Math.round(m / 104857.6) / 10; })(),
    rateLimitUnavailableDelta: delta('fasttowin_rate_limit_shared_decisions_total{outcome="unavailable"}'),
    rateLimitTimeoutDelta: delta('fasttowin_rate_limit_shared_failures_total{reason="timeout"}'),
    rateLimitOverloadedDelta: delta('fasttowin_rate_limit_shared_failures_total{reason="overloaded"}'),
    presenceReadFailuresDelta: delta('fasttowin_presence_redis_failures_total{operation="read"}'),
    presenceWriteFailuresDelta: delta('fasttowin_presence_redis_failures_total{operation="write"}'),
    wsRateLimitedDelta: delta('fasttowin_websocket_rate_limited_total'),
    wsLimitRejectedDelta: delta('fasttowin_websocket_connection_limit_rejected_total'),
    samples: window.length,
  };
}

// ---------- run ----------
const plannedSeconds = 180 + maxPlayers / 15 + options.steps.reduce((sum, step, i) =>
  sum + (step - (options.steps[i - 1] || 0)) / options.rampPerSecond * 2 + 60 + options.holdSeconds, 0) + (options.reconnectStorm ? 150 : 0);
report.deadlineSeconds = Math.min(Math.round(plannedSeconds * 1.5), 7200);
const deadline = setTimeout(() => { timedOut = true; cancellation.abort(); }, report.deadlineSeconds * 1000);
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { interrupted = true; cancellation.abort(); });
let sampler, samplingChain = Promise.resolve();

try {
  const javaVersion = await runFile(java, ['-version'], { env: safeEnvironment, timeout: 10000 });
  report.hardware.java = javaVersion.stderr.split('\n').slice(0, 2).join(' ').trim();
  if (options.postgresSettings) {
    const settings = JSON.parse(await readFile(options.postgresSettings, 'utf8'));
    validateDatabaseSettings(settings);
    db = { ...settings, password: (await readFile(settings.passwordFile, 'utf8')).trim() };
    schemaAttempted = true; await sql(`CREATE SCHEMA ${schema};`);
  }
  if (options.redisSettings) {
    const settings = JSON.parse(await readFile(options.redisSettings, 'utf8'));
    const rateLimitUrl = (await readFile(settings.rateLimitUrlFile, 'utf8')).trim();
    const presenceUrl = (await readFile(settings.presenceUrlFile, 'utf8')).trim();
    validateRedisSettings(settings, [rateLimitUrl, presenceUrl]);
    if (!/^[a-z0-9][a-z0-9_-]{0,47}$/.test(settings.namespace || '')) throw new Error('INVALID_REDIS_NAMESPACE');
    redis = { ...settings, monitorPassword: (await readFile(settings.monitorPasswordFile, 'utf8')).trim() };
    await redisInfo();
  }
  const port = await new Promise((accept, reject) => {
    const socket = createServer(); socket.on('error', reject);
    socket.listen(0, '127.0.0.1', () => { const p = socket.address().port; socket.close(() => accept(p)); });
  });
  url = `http://127.0.0.1:${port}`;
  const env = { ...safeEnvironment, FASTTOWIN_ENV: 'dev', SERVER_HOST: '127.0.0.1', PORT: String(port),
    // Like production behind Caddy: the server trusts X-Forwarded-For, so every synthetic player has its own address.
    FASTTOWIN_TRUST_PROXY_HEADERS: 'true',
    ...(db ? { DATABASE_URL: db.url + '?currentSchema=' + schema, DATABASE_USER: db.user,
      DATABASE_PASSWORD_FILE: resolve(db.passwordFile), DATABASE_POOL_SIZE: String(options.poolSize) } : {}),
    ...(redis ? { FASTTOWIN_RATE_LIMIT_STORE: 'redis', FASTTOWIN_RATE_LIMIT_NAMESPACE: redis.namespace,
      FASTTOWIN_RATE_LIMIT_REDIS_URL_FILE: resolve(redis.rateLimitUrlFile), FASTTOWIN_PRESENCE_STORE: 'redis',
      FASTTOWIN_PRESENCE_NAMESPACE: redis.namespace, FASTTOWIN_PRESENCE_REDIS_URL_FILE: resolve(redis.presenceUrlFile) } : {}) };
  const jvmFlags = ['-Xms64m', '-Xmx512m', '-XX:MaxMetaspaceSize=192m', '-XX:+ExitOnOutOfMemoryError',
    `-Djdk.net.unixdomain.tmpdir=${socketTemp}`,
    ...(options.serverCpus ? [`-XX:ActiveProcessorCount=${options.serverCpus}`] : []),
    ...(options.jfr ? [`-XX:StartFlightRecording=filename=${join(output, 'server.jfr')},settings=profile,dumponexit=true`] : [])];
  child = spawn(java, [...jvmFlags, '-cp', join(distribution, 'lib', '*'), 'com.hienthai.fastowin.server.MainKt'],
    { cwd: serverCwd, env, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(logs, { end: false }); child.stderr.pipe(logs, { end: false });
  if (options.serverCpus) {
    const mask = (2 ** options.serverCpus - 1);
    if (platform() === 'win32') await runFile('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
      `(Get-Process -Id ${child.pid}).ProcessorAffinity = ${mask}`], { windowsHide: true, timeout: 10000, env: safeEnvironment });
    else await runFile('taskset', ['-p', mask.toString(16), String(child.pid)], { timeout: 5000, env: safeEnvironment });
  }
  let startupText = '', listenerReady = false, spawnError = false;
  child.stdout.on('data', chunk => { startupText = (startupText + chunk).slice(-8192); listenerReady ||= startupText.includes(`Responding at ${url}`); });
  child.on('error', () => { spawnError = true; });
  const startup = performance.now();
  while (true) {
    ensureActive();
    if (spawnError || child.exitCode !== null || performance.now() - startup > 90000) throw new Error('OWN_SERVER_START_FAILED');
    if (listenerReady) {
      try { const health = await fetch(url + '/health', { signal: AbortSignal.timeout(1000) });
        if (health.ok && (await health.text()).trim() === 'OK') break; } catch { /* not ready */ }
    }
    await delay(100);
  }
  report.startupSeconds = Math.round((performance.now() - startup) / 10) / 100;

  // Synthetic accounts: setup only, outside every measured window. Verification codes come from the dev server; no email is sent.
  phase = 'accounts';
  const setupStart = performance.now(), credentials = new Array(maxPlayers);
  const post = async (path, body, ip) => {
    ensureActive();
    const response = await fetch(url + path, { method: 'POST', signal: AbortSignal.any([cancellation.signal, AbortSignal.timeout(30000)]),
      headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': ip }, body: JSON.stringify(body) });
    if (!response.ok) throw new Error('SETUP_HTTP_' + response.status);
    return response.json();
  };
  let nextAccount = 0;
  await Promise.all(Array.from({ length: 24 }, async () => {
    while (nextAccount < maxPlayers) {
      const index = nextAccount++, ip = syntheticAddress(addressOffset + index);
      const email = `load-${randomUUID()}@example.invalid`, password = 'Load-' + randomUUID() + '9!';
      const auth = await post('/auth/register', { email, password, displayName: `Load ${index}`, devicePlatform: 'ANDROID' }, ip);
      const verification = await post('/auth/email-verification/request', { accessToken: auth.accessToken }, ip);
      if (!/^\d{6}$/.test(verification.devEmailVerificationCode || '')) throw new Error('DEV_VERIFICATION_CODE_MISSING');
      await post('/auth/email-verification/confirm', { accessToken: auth.accessToken, verificationCode: verification.devEmailVerificationCode }, ip);
      credentials[index] = { index, email, password, ip };
    }
  }));
  // Daily quota is 10 matches; give the isolated schema's synthetic players enough banked credits for long holds.
  if (db) report.creditedPlayers = Number(await sql(`WITH u AS (INSERT INTO ${schema}.player_stats (user_id, updated_at, play_credits)
    SELECT id, now(), 100000 FROM ${schema}.users ON CONFLICT (user_id) DO UPDATE SET play_credits = 100000 RETURNING 1) SELECT count(*) FROM u;`));
  report.accountSetupSeconds = Math.round((performance.now() - setupStart) / 100) / 10;

  startWorkers();
  phase = 'warmup';
  await sampleResources();
  sampler = setInterval(() => { samplingChain = samplingChain.then(sampleResources).catch(() => {}); }, 5000);

  let used = 0, pairsSoFar = 0, idleSoFar = 0, nextWorker = 0;
  for (const [stepIndex, target] of options.steps.entries()) {
    ensureActive();
    const wantedPairs = Math.floor(target * options.playingPercent / 200), wantedIdle = target - wantedPairs * 2;
    const units = [];
    for (; pairsSoFar < wantedPairs; pairsSoFar++) { units.push({ kind: 'pair', players: [credentials[used], credentials[used + 1]] }); used += 2; }
    for (; idleSoFar < wantedIdle; idleSoFar++) units.push({ kind: 'idle', players: [credentials[used++]] });
    units.sort(() => Math.random() - 0.5); // Mix playing pairs and lobby players during the ramp.
    phase = `ramp-${target}`;
    await askAll({ type: 'window', name: 'pre-' + phase });
    const rampStart = performance.now();
    let sentPlayers = 0;
    while (units.length) {
      ensureActive();
      const due = Math.floor((performance.now() - rampStart) / 1000 * options.rampPerSecond) - sentPlayers;
      const batch = [];
      while (units.length && batch.reduce((n, u) => n + u.players.length, 0) < due) batch.push(units.shift());
      for (const unit of batch) { workers[nextWorker++ % workers.length].postMessage({ type: 'spawn', units: [unit] }); sentPlayers += unit.players.length; }
      await delay(100);
    }
    const settleDeadline = performance.now() + 60000;
    while (connectedPlayers() < target * 0.99 && performance.now() < settleDeadline) { ensureActive(); await delay(500); }
    const rampSeconds = (performance.now() - rampStart) / 1000;
    const ramp = mergeWindow(await askAll({ type: 'window', name: phase }), rampSeconds);
    await delay(3000);
    phase = `hold-${target}`;
    await askAll({ type: 'window', name: 'settle' });
    const holdStart = performance.now();
    while (performance.now() - holdStart < options.holdSeconds * 1000) { ensureActive(); await delay(500); }
    const hold = mergeWindow(await askAll({ type: 'window', name: phase }), (performance.now() - holdStart) / 1000);
    await samplingChain;
    const step = { targetPlayers: target, playingPlayers: wantedPairs * 2, lobbyPlayers: wantedIdle,
      connectedAtHoldEnd: connectedPlayers(), rampSeconds: Math.round(rampSeconds * 10) / 10,
      ramp: { operations: ramp.operations, allOperations: ramp.allOperations, counters: ramp.counters },
      hold, resources: resourceSummary(phase), rampResources: resourceSummary(`ramp-${target}`) };
    const select = hold.operations['ws.select_number'];
    step.healthy = hold.allOperations.errorRatePercent <= 1 && (!select || select.p95Ms <= 250) &&
      step.connectedAtHoldEnd >= target * 0.99 && hold.clientLoopDelayP99Ms < 100;
    report.steps.push(step);
    console.log(JSON.stringify({ step: target, connected: step.connectedAtHoldEnd, healthy: step.healthy,
      selectsPerSecond: select?.perSecond, selectP95Ms: select?.p95Ms, selectP99Ms: select?.p99Ms,
      errorRatePercent: hold.allOperations.errorRatePercent, serverCpuPercentAvg: step.resources.serverCpuPercentAvg,
      heapMiBMax: step.resources.heapUsedMiBMax, dbPoolPendingMax: step.resources.dbPoolPendingMax,
      redisOpsPerSecondAvg: step.resources.redisOpsPerSecondAvg, clientLoopDelayP99Ms: hold.clientLoopDelayP99Ms }));
    if (hold.allOperations.errorRatePercent > 25 || step.connectedAtHoldEnd < target * 0.5) { report.stoppedEarly = `step ${stepIndex + 1}`; break; }
  }

  if (options.reconnectStorm && !report.stoppedEarly) {
    phase = 'storm';
    await askAll({ type: 'window', name: 'pre-storm' });
    const storm = await askAll({ type: 'storm', jitterMs: options.reconnectJitterMs }, 180000);
    const stormSeconds = Math.max(...storm.map(s => s.stormSeconds));
    await delay(5000);
    await samplingChain;
    report.reconnectStorm = { jitterMs: options.reconnectJitterMs, players: storm.reduce((n, s) => n + s.attempted, 0),
      allReconnectedWithinSeconds: stormSeconds, ...mergeWindow(await askAll({ type: 'window', name: 'storm' }), stormSeconds),
      resources: resourceSummary('storm') };
  }
  phase = 'stop';
  report.final = mergeWindow(await askAll({ type: 'stop' }, 60000), 1);
  if (db) {
    report.persistedMatches = Number(await sql(`SELECT count(*) FROM ${schema}.matches;`));
    // Schema-scoped write/read counters show which tables the workload hits (no row contents).
    report.databaseTables = JSON.parse(await sql(`SELECT COALESCE(json_agg(row_to_json(t)), '[]'::json) FROM (
      SELECT relname, n_tup_ins, n_tup_upd, n_tup_del, n_tup_hot_upd, seq_scan, idx_scan, n_live_tup,
        pg_total_relation_size(relid) AS total_bytes FROM pg_stat_user_tables WHERE schemaname = '${schema}'
        AND (n_tup_ins + n_tup_upd + n_tup_del + COALESCE(idx_scan, 0) + seq_scan) > 0 ORDER BY n_tup_upd + n_tup_ins DESC) t;`));
  }
} catch (error) {
  setupFailure = /^[A-Z0-9_]+$/.test(error.message) ? error.message : 'SETUP_OR_HARNESS_FAILURE';
  report.setupFailureDetail = error.message.slice(0, 200).replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+/g, '<redacted>');
} finally {
  clearTimeout(deadline); clearInterval(sampler);
  await Promise.allSettled(workers.map(w => w.terminate()));
  try { await stopChild(); } catch { report.cleanupFailure = 'OWN_SERVER_DID_NOT_STOP'; }
  report.ownedServerStopped = !child || child.exitCode !== null || child.signalCode !== null;
  if (schemaAttempted && !report.cleanupFailure) {
    try { await sql(`DROP SCHEMA IF EXISTS ${schema} CASCADE;`, true); report.schemaRemoved = true; }
    catch { report.cleanupFailure = 'OWN_SCHEMA_DROP_FAILED'; }
  }
  logs.end();
  report.resourceSamples = samples;
  report.setupFailure = setupFailure; report.timedOut = timedOut; report.interrupted = interrupted;
  report.ceiling = report.steps.filter(s => s.healthy).at(-1)?.targetPlayers ?? null;
  report.status = !setupFailure && !timedOut && !interrupted && !report.cleanupFailure && report.steps.length ? 'PASS' : 'FAIL';
  await writeFile(join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({ status: report.status, report: join(output, 'report.json'), healthyCeiling: report.ceiling,
    setupFailure, cleanupFailure: report.cleanupFailure }));
  if (report.status !== 'PASS') process.exitCode = 1;
}
