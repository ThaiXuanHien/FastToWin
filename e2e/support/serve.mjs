import { createServer } from 'node:http';
import { createReadStream, existsSync } from 'node:fs';
import { stat } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, extname, resolve, sep, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const webTarget = process.env.E2E_WEB_TARGET || 'wasmJs';
if (!['wasmJs', 'js'].includes(webTarget)) throw new Error('E2E_WEB_TARGET must be wasmJs or js.');
const webRoot = resolve(repo, process.env.E2E_WEB_ROOT || `webApp/build/kotlin-webpack/${webTarget}/developmentExecutable`);
const resourceRoot = resolve(repo, `webApp/build/processedResources/${webTarget}/main`);
const roots = [webRoot, resourceRoot];
const indexFile = roots.map(root => join(root, 'index.html')).find(existsSync);
const baseURL = new URL(process.env.E2E_BASE_URL || 'http://127.0.0.1:18081');
const apiURL = new URL(process.env.E2E_API_URL || 'http://127.0.0.1:18080');
for (const url of [baseURL, apiURL]) {
  if (url.protocol !== 'http:' || !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)) {
    throw new Error('Only loopback HTTP addresses are allowed for the E2E servers.');
  }
}
if (!indexFile || !existsSync(join(webRoot, 'fast-to-win.js'))) {
  const buildTask = webTarget === 'js' ? 'jsBrowserDevelopmentWebpack' : 'wasmJsBrowserDevelopmentWebpack';
  throw new Error(`Build :webApp:${buildTask} before running E2E.`);
}
const lib = join(repo, 'server/build/install/server/lib');
if (!existsSync(lib)) throw new Error('Build :server:installDist before running E2E.');
// Fail closed instead of silently reusing a developer's server/database.
try {
  await fetch(`${apiURL.origin}/health`, { signal: AbortSignal.timeout(1000) });
  throw new Error(`Port ${apiURL.port} is already occupied. Choose a different E2E_API_URL.`);
} catch (error) {
  if (!['TypeError', 'TimeoutError'].includes(error.name)) throw error;
}
const java = process.env.JAVA_HOME
  ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const serverEnv = {
  ...process.env,
  FASTTOWIN_ENV: 'dev',
  FASTTOWIN_WEB_ORIGINS: baseURL.origin,
  SERVER_HOST: apiURL.hostname,
  PORT: apiURL.port,
};
// A fresh in-memory backend; do not inherit production/dev DB or push credentials.
for (const key of Object.keys(serverEnv)) {
  if (/^(DATABASE_|TEST_DATABASE_|FIREBASE_|GOOGLE_APPLICATION_CREDENTIALS$|FASTTOWIN_MAINTENANCE)/.test(key)) {
    delete serverEnv[key];
  }
}
const backend = spawn(java, ['-cp', join(lib, '*'), 'com.hienthai.fastowin.server.MainKt'], {
  cwd: resolve(repo, 'e2e'), env: serverEnv, stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true,
});
backend.stdout.pipe(process.stdout);
backend.stderr.pipe(process.stderr);
let closing = false;
let ready = false;
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.mjs': 'text/javascript',
  '.wasm': 'application/wasm', '.css': 'text/css', '.json': 'application/json',
  '.png': 'image/png', '.webp': 'image/webp', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const frontend = createServer(async (req, res) => {
  try {
    const pathname = decodeURIComponent(new URL(req.url, baseURL).pathname);
    res.setHeader('Cache-Control', 'no-store');
    if (pathname === '/__e2e/ready') { res.writeHead(ready ? 200 : 503); return res.end(ready ? 'OK' : 'Starting'); }
    if (pathname === '/config.js') {
      res.setHeader('Content-Type', 'text/javascript');
      return res.end(`globalThis.FASTTOWIN_CONFIG=${JSON.stringify({ serverUrl: `${apiURL.origin.replace('http:', 'ws:')}/game` })};`);
    }
    let file;
    for (const root of roots) {
      const path = resolve(root, `.${pathname}`);
      if (path !== root && !path.startsWith(root + sep)) { res.writeHead(403); return res.end(); }
      if ((await stat(path).catch(() => null))?.isFile()) { file = path; break; }
    }
    if (!file && !extname(pathname)) file = indexFile;
    if (!file) { res.writeHead(404); return res.end('Not found'); }
    res.setHeader('Content-Type', mime[extname(file)] || 'application/octet-stream');
    createReadStream(file).on('error', () => res.destroy()).pipe(res);
  } catch { res.writeHead(400); res.end('Bad request'); }
});
function stop(code = 0) {
  if (closing) return;
  closing = true;
  frontend.close();
  backend.kill();
  process.exitCode = code;
  setTimeout(() => process.exit(code), 1000).unref();
}
process.on('SIGINT', () => stop());
process.on('SIGTERM', () => stop());
backend.on('error', error => { console.error(error.message); stop(1); });
backend.on('exit', code => { if (!closing) stop(code || 1); });
frontend.on('error', error => { console.error(error.message); stop(1); });
frontend.listen(Number(baseURL.port), baseURL.hostname);
for (let attempt = 0; attempt < 100 && !closing; attempt++) {
  const response = await fetch(`${apiURL.origin}/health`, { signal: AbortSignal.timeout(1000) }).catch(() => null);
  if (response?.ok) { ready = true; break; }
  await new Promise(resolve => setTimeout(resolve, 300));
}
if (!ready) { console.error('E2E backend did not become healthy.'); stop(1); }
