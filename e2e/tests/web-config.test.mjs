import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const configSource = await readFile(
  new URL('../../webApp/src/wasmJsMain/resources/config.js', import.meta.url),
  'utf8',
);

function loadConfig({ hostname, protocol = 'http:' }) {
  const context = vm.createContext({ location: { hostname, protocol } });
  vm.runInContext(configSource, context);
  return context.FASTTOWIN_CONFIG;
}

test('web development uses the page LAN host for its websocket', () => {
  assert.equal(
    loadConfig({ hostname: '192.168.1.141' }).serverUrl,
    'ws://192.168.1.141:8080/game',
  );
});

test('localhost development remains unchanged', () => {
  assert.equal(
    loadConfig({ hostname: 'localhost' }).serverUrl,
    'ws://localhost:8080/game',
  );
});
