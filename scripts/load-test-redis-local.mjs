// Starts (or replaces) a loopback-only Redis container built from the production image definition
// deploy/railway/redis (same config: 64 MB maxmemory, noeviction, AOF everysec, ACL users), with
// freshly generated local secrets under .artifacts/loadtest-redis/. Prints the settings file path
// for `node scripts/load-test-scale.mjs --redis-settings <path>` and redis-benchmark.mjs.
//
//   node scripts/load-test-redis-local.mjs [--cpus 1] [--port 16379]
//   node scripts/load-test-redis-local.mjs --remove
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdir, writeFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const run = promisify(execFile);
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dir = join(root, '.artifacts', 'loadtest-redis');
const container = 'ftw-loadtest-redis', namespace = 'loadtest';
const args = process.argv.slice(2);
const option = (name, fallback) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : fallback; };
const port = Number(option('--port', '16379')), cpus = option('--cpus', '');
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid port');
if (cpus && !/^\d+(\.\d+)?$/.test(cpus)) throw new Error('Invalid --cpus');

await run('docker', ['rm', '-f', container]).catch(() => {});
if (args.includes('--remove')) { console.log('removed'); process.exit(0); }

await mkdir(dir, { recursive: true });
const secret = () => randomBytes(48).toString('hex');
const app = secret(), presence = secret(), monitor = secret(), backup = secret();
await run('docker', ['build', '-q', '-t', 'ftw-loadtest-redis:local', join(root, 'deploy', 'railway', 'redis')], { maxBuffer: 1 << 20 });
// Secrets reach the container through an env file inside the ignored artifacts folder, not the command line.
const envFile = join(dir, 'redis.env');
await writeFile(envFile, [`FASTTOWIN_RATE_LIMIT_NAMESPACE=${namespace}`, `FASTTOWIN_REDIS_APP_PASSWORD=${app}`,
  `FASTTOWIN_REDIS_MONITOR_PASSWORD=${monitor}`, `FASTTOWIN_REDIS_BACKUP_PASSWORD=${backup}`,
  `FASTTOWIN_REDIS_PRESENCE_PASSWORD=${presence}`, ''].join('\n'), { mode: 0o600 });
await run('docker', ['run', '-d', '--name', container, '-p', `127.0.0.1:${port}:6379`, '--env-file', envFile,
  ...(cpus ? ['--cpus', cpus] : []), 'ftw-loadtest-redis:local']);
await writeFile(join(dir, 'rate-limit.url'), `redis://ftw_app:${app}@127.0.0.1:${port}`, { mode: 0o600 });
await writeFile(join(dir, 'presence.url'), `redis://ftw_presence:${presence}@127.0.0.1:${port}`, { mode: 0o600 });
await writeFile(join(dir, 'monitor.password'), monitor, { mode: 0o600 });
const settings = join(dir, 'settings.json');
await writeFile(settings, JSON.stringify({ container, namespace, rateLimitUrlFile: join(dir, 'rate-limit.url'),
  presenceUrlFile: join(dir, 'presence.url'), monitorPasswordFile: join(dir, 'monitor.password') }, null, 2));
console.log(JSON.stringify({ container, port, cpus: cpus || 'unlimited', settings }));
