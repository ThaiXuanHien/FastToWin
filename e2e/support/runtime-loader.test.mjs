import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

// The packaging check can point this same behavior suite at the emitted release loader.
const source = readFileSync(process.env.FASTTOWIN_LOADER_UNDER_TEST ||
  new URL('../../webApp/src/compatibility/runtime-loader.js', import.meta.url), 'utf8');
const iphone = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 Version/27.2 Mobile/15E148 Safari/604.1';

function run({ userAgent = '', platform = '', touchPoints = 0, wasm = true, readyState = 'complete' } = {}) {
  const scripts = [], listeners = [];
  const document = {
    readyState,
    body: { appendChild: element => scripts.push(element) },
    createElement: tag => { assert.equal(tag, 'script'); return {}; },
    addEventListener: (name, callback, options) => listeners.push({ name, callback, options }),
  };
  const context = { document, navigator: { userAgent, platform, maxTouchPoints: touchPoints }, Uint8Array, WebAssembly: undefined };
  if (wasm !== 'missing') context.WebAssembly = { validate: bytes => {
    if (wasm === 'throws') throw new Error('Validation unavailable');
    return wasm === true ? WebAssembly.validate(bytes) : wasm;
  } };
  vm.runInNewContext(source, context);
  return { scripts, listeners };
}

for (const [name, settings, expected] of [
  ['iPhone with validated Wasm uses working JS runtime', { userAgent: iphone, platform: 'iPhone', touchPoints: 5 }, 'js'],
  ['iPad mobile browser uses JS', { userAgent: 'Mozilla/5.0 (iPad; CPU OS 27_2 like Mac OS X)', platform: 'iPad', touchPoints: 5 }, 'js'],
  ['iPad desktop browser identity uses JS', { userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15)', platform: 'MacIntel', touchPoints: 5 }, 'js'],
  ['desktop Mac retains Wasm', { userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15)', platform: 'MacIntel' }, 'wasm'],
  ['touch Windows retains Wasm', { userAgent: 'Mozilla/5.0 (Windows NT 10.0)', platform: 'Win32', touchPoints: 10 }, 'wasm'],
  ['Android retains Wasm', { userAgent: 'Mozilla/5.0 (Linux; Android 17)', platform: 'Linux armv8l', touchPoints: 5 }, 'wasm'],
  ['unsupported Wasm uses JS', { wasm: false }, 'js'],
  ['missing Wasm uses JS', { wasm: 'missing' }, 'js'],
  ['throwing Wasm validation uses JS', { wasm: 'throws' }, 'js'],
]) {
  test(name, () => {
    const { scripts } = run(settings);
    assert.equal(scripts.length, 1);
    assert.equal(scripts[0].src, expected === 'js' ? 'originJsFast-to-win.js' : 'originWasmFast-to-win.js');
    assert.equal(scripts[0].type, 'application/javascript');
  });
}

test('loader waits until DOM content is ready before attaching one runtime', () => {
  const { scripts, listeners } = run({ userAgent: iphone, readyState: 'loading' });
  assert.equal(scripts.length, 0);
  assert.equal(listeners.length, 1);
  assert.equal(listeners[0].name, 'DOMContentLoaded');
  listeners[0].callback();
  assert.equal(scripts.length, 1);
  assert.equal(scripts[0].src, 'originJsFast-to-win.js');
});
