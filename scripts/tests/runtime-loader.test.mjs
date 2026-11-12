import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const loader = readFileSync(new URL('../../webApp/src/compatibility/runtime-loader.js', import.meta.url), 'utf8');

for (const [userAgent, runtime] of [
    ['Chromium', 'originWasmFast-to-win.js'],
    ['iPhone', 'originJsFast-to-win.js'],
]) {
    test(`${userAgent} loads the runtime with the bootstrap's cache version`, () => {
        const loadedScripts = [];
        vm.runInNewContext(loader, {
            URL,
            WebAssembly: { validate: () => true },
            navigator: { userAgent },
            document: {
                currentScript: { src: 'https://example.test/fast-to-win.js?v=current-build' },
                readyState: 'complete',
                createElement: () => ({}),
                body: { appendChild: script => loadedScripts.push(script.src) },
            },
        });

        assert.deepEqual(loadedScripts, [`${runtime}?v=current-build`]);
    });
}
