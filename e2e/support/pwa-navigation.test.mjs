import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const pwaScript = await readFile(
  new URL('../../webApp/src/wasmJsMain/resources/pwa.js', import.meta.url),
  'utf8',
);

function loadPwa({ standalone, userAgent }) {
  const historyCalls = [];
  const listeners = new Map();
  const documentListeners = new Map();
  const location = { pathname: '/', origin: 'https://fasttowin.example' };
  const history = {
    state: null,
    length: 1,
    replaceState(state, _unused, url) {
      this.state = state;
      location.pathname = new URL(url, location.origin).pathname;
      historyCalls.push({ type: 'replace', state, url });
    },
    pushState(state, _unused, url) {
      this.state = state;
      this.length += 1;
      location.pathname = new URL(url, location.origin).pathname;
      historyCalls.push({ type: 'push', state, url });
    },
    back() {
      historyCalls.push({ type: 'back' });
    },
  };
  const matchMedia = () => ({
    matches: standalone,
    addEventListener() {},
  });
  const rootStyle = new Map();
  const window = {
    innerWidth: 390,
    innerHeight: 844,
    history,
    location,
    navigator: { standalone, userAgent },
    matchMedia,
    addEventListener(type, listener) {
      const handlers = listeners.get(type) || [];
      handlers.push(listener);
      listeners.set(type, handlers);
    },
    removeEventListener(type, listener) {
      listeners.set(type, (listeners.get(type) || []).filter(handler => handler !== listener));
    },
    dispatchEvent(event) {
      for (const listener of listeners.get(event.type) || []) listener(event);
    },
    requestAnimationFrame(callback) {
      callback();
      return 1;
    },
    cancelAnimationFrame() {},
    setInterval() {},
    setTimeout() {},
  };
  const document = {
    visibilityState: 'visible',
    documentElement: {
      style: {
        setProperty(name, value) { rootStyle.set(name, value); },
      },
    },
    addEventListener(type, listener) {
      const handlers = documentListeners.get(type) || [];
      handlers.push(listener);
      documentListeners.set(type, handlers);
    },
    removeEventListener(type, listener) {
      documentListeners.set(
        type,
        (documentListeners.get(type) || []).filter(handler => handler !== listener),
      );
    },
    dispatchEvent(event) {
      for (const listener of documentListeners.get(event.type) || []) listener(event);
    },
    head: { appendChild() {} },
    querySelector() { return null; },
    createElement() { return {}; },
  };
  const context = vm.createContext({
    window,
    navigator: window.navigator,
    document,
    history,
    location,
    localStorage: { getItem() { return null; }, setItem() {} },
    sessionStorage: { getItem() { return null; }, setItem() {}, removeItem() {} },
    CustomEvent: class CustomEvent { constructor(type, init = {}) { this.type = type; this.detail = init.detail; } },
    URL,
    console,
  });
  vm.runInContext(pwaScript, context);
  return {
    navigation: window.FASTTOWIN_PWA.navigation,
    document,
    history,
    historyCalls,
    location,
  };
}

function dispatchTouch(document, type, clientX) {
  let prevented = false;
  document.dispatchEvent({
    type,
    touches: type === 'touchend' ? [] : [{ clientX }],
    changedTouches: [{ clientX }],
    preventDefault() { prevented = true; },
  });
  return prevented;
}

test('iOS standalone PWA replaces routes and declines native Back', () => {
  const browser = loadPwa({
    standalone: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
  });

  assert.ok(browser.navigation, 'PWA exposes its navigation policy');
  browser.navigation.prepare();
  browser.navigation.publish('/account');
  browser.navigation.publish('/settings');

  assert.equal(browser.location.pathname, '/settings');
  assert.equal(browser.history.length, 1);
  assert.equal(browser.history.state.fastToWinDepth, 0);
  assert.equal(browser.historyCalls.some(call => call.type === 'push'), false);
  assert.equal(browser.navigation.goBack(), false);
  assert.equal(browser.historyCalls.some(call => call.type === 'back'), false);
});

test('normal browser keeps native route history and Back', () => {
  const browser = loadPwa({
    standalone: false,
    userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 Safari/605.1.15',
  });

  assert.ok(browser.navigation, 'PWA exposes its navigation policy');
  browser.navigation.prepare();
  browser.navigation.publish('/leaderboard');

  assert.equal(browser.history.length, 2);
  assert.equal(browser.history.state.fastToWinDepth, 1);
  assert.equal(browser.navigation.goBack(), true);
  assert.equal(browser.historyCalls.at(-1).type, 'back');
});

test('iOS standalone PWA prevents navigation swipes from both screen edges', () => {
  const browser = loadPwa({
    standalone: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
  });

  assert.equal(dispatchTouch(browser.document, 'touchstart', 2), true);
  assert.equal(dispatchTouch(browser.document, 'touchmove', 48), true);
  assert.equal(dispatchTouch(browser.document, 'touchend', 48), false);

  assert.equal(dispatchTouch(browser.document, 'touchstart', 388), true);
  assert.equal(dispatchTouch(browser.document, 'touchmove', 340), true);
  assert.equal(dispatchTouch(browser.document, 'touchend', 340), false);

  assert.equal(dispatchTouch(browser.document, 'touchstart', 195), false);
  assert.equal(dispatchTouch(browser.document, 'touchmove', 210), false);
});

test('Safari tab keeps native edge gestures', () => {
  const browser = loadPwa({
    standalone: false,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 27_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
  });

  assert.equal(dispatchTouch(browser.document, 'touchstart', 2), false);
  assert.equal(dispatchTouch(browser.document, 'touchstart', 388), false);
});
