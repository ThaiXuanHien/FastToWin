import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const e2eRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const cli = resolve(e2eRoot, 'node_modules/playwright/cli.js');
const child = spawn(process.execPath, [cli, 'test'], {
  cwd: e2eRoot,
  stdio: 'inherit',
  windowsHide: true,
  env: {
    ...process.env,
    E2E_JS_FALLBACK: '1',
    E2E_WEB_TARGET: 'js',
    E2E_BASE_URL: process.env.E2E_JS_BASE_URL || 'http://127.0.0.1:18083',
    E2E_API_URL: process.env.E2E_JS_API_URL || 'http://127.0.0.1:18082',
  },
});

child.on('error', error => {
  console.error(error.message);
  process.exitCode = 1;
});
child.on('exit', code => { process.exitCode = code ?? 1; });
