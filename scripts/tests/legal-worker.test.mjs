import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
const listeners = {};
vm.runInNewContext(readFileSync(new URL('../../webApp/src/wasmJsMain/resources/service-worker.js', import.meta.url), 'utf8'), {
  URL, caches: { async open() { return { async match() {}, async put() {} }; } }, fetch: async () => ({ ok: false }), console: { warn() {} }, importScripts() {},
  self: { location: { origin: 'https://game.example' }, addEventListener(type, handler) { listeners[type] = handler; } },
});
for (const path of ['/privacy', '/privacy/', '/privacy/en', '/terms', '/terms/en/', '/delete-account', '/delete-account/en', '/legal/legal.css']) {
  test(`legal URL ${path} never falls back to a cached game shell`, () => {
    let intercepted = false;
    listeners.fetch({ request: { method: 'GET', url: 'https://game.example' + path, mode: 'navigate' }, respondWith() { intercepted = true; } });
    assert.equal(intercepted, false);
  });
}
