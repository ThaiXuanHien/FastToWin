(function () {
    function prefersJavaScript() {
        // iOS may validate the Wasm feature probe yet leave the Compose canvas blank.
        // iPadOS desktop mode identifies itself as a Mac with multiple touch points.
        return /iPhone|iPad|iPod/i.test(navigator.userAgent || '') ||
            (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    }

    function supportsWasm() {
        try {
            return typeof WebAssembly !== 'undefined' &&
                typeof WebAssembly.validate === 'function' &&
                WebAssembly.validate(new Uint8Array([
                    0, 97, 115, 109, 1, 0, 0, 0, 1, 8, 2, 95,
                    1, 120, 0, 96, 0, 0, 3, 3, 2, 1, 1, 10,
                    14, 2, 6, 0, 6, 64, 25, 11, 11, 5, 0, 208,
                    112, 26, 11, 0, 45, 4, 110, 97, 109, 101, 1, 15,
                    2, 0, 5, 102, 117, 110, 99, 48, 1, 5, 102, 117,
                    110, 99, 49, 4, 8, 1, 0, 5, 116, 121, 112, 101,
                    48, 10, 11, 1, 0, 1, 0, 6, 102, 105, 101, 108,
                    100, 48
                ]));
        } catch (_) {
            return false;
        }
    }

    function loadApp() {
        const script = document.createElement('script');
        script.src = prefersJavaScript() || !supportsWasm()
            ? 'originJsFast-to-win.js'
            : 'originWasmFast-to-win.js';
        script.type = 'application/javascript';
        document.body.appendChild(script);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', loadApp, { once: true });
    } else {
        loadApp();
    }
})();
