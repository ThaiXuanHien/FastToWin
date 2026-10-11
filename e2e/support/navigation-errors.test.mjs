import test from 'node:test';
import assert from 'node:assert/strict';
import { isExpectedNavigationAbort } from './navigation-errors.mjs';

test('WebKit generic wrapper requires a recent cancelled local GET request during navigation', () => {
  const args = {
    browserName: 'webkit', message: 'Fail to fetch', now: 1_000, expectedUntil: 1_500,
    loopbackOrigins: ['http://127.0.0.1:18084', 'http://127.0.0.1:18085'],
    cancelledRequest: {
      url: 'http://127.0.0.1:18085/api/announcements/push-preferences',
      method: 'GET', failureText: 'Load request cancelled', at: 980,
    },
  };
  assert.equal(isExpectedNavigationAbort(args), true);
  for (const patch of [
    { browserName: 'chromium' },
    { browserName: 'firefox' },
    { expectedUntil: 999 },
    { loopbackOrigins: [] },
    { cancelledRequest: null },
    ...[
      { at: 749 }, { at: 1_001 }, { method: 'POST' },
      { failureText: 'Could not connect to the server.' },
      { url: 'http://127.0.0.1:19000/api/status' },
      { url: 'https://fasttowin.up.railway.app/api/status' },
      { url: 'malformed' },
    ].map(request => ({ cancelledRequest: { ...args.cancelledRequest, ...request } })),
    { loopbackOrigins: ['http://example.com'],
      cancelledRequest: { ...args.cancelledRequest, url: 'http://example.com/api/status' } },
  ]) {
    assert.equal(isExpectedNavigationAbort({ ...args, ...patch }), false, JSON.stringify(patch));
  }
});

const genericWasmError = 'Exception was thrown while running JavaScript code';

test('accepts the WebKit fetch wrapper only with a correlated navigation cancellation', () => {
  assert.equal(isExpectedNavigationAbort({
    browserName: 'webkit', message: 'Fail to fetch', now: 1_000,
    expectedUntil: 1_500, correlatedWebKitAbortUntil: 1_200,
  }), true);
});

test('keeps uncorrelated or expired WebKit fetch errors visible', () => {
  for (const args of [
    { now: 1_000, expectedUntil: 1_500 },
    { now: 1_201, expectedUntil: 1_500, correlatedWebKitAbortUntil: 1_200 },
    { now: 1_501, expectedUntil: 1_500, correlatedWebKitAbortUntil: 1_700 },
  ]) {
    assert.equal(isExpectedNavigationAbort({ browserName: 'webkit', message: 'Fail to fetch', ...args }), false);
  }
});

test('keeps unrelated errors and other browsers visible despite a WebKit cancellation', () => {
  for (const args of [
    { browserName: 'webkit', message: 'ReferenceError: broken is not defined' },
    { browserName: 'chromium', message: 'Fail to fetch' },
    { browserName: 'firefox', message: 'Fail to fetch' },
  ]) {
    assert.equal(isExpectedNavigationAbort({ ...args, now: 1_000, expectedUntil: 1_500, correlatedWebKitAbortUntil: 1_200 }), false);
  }
});

test('accepts the Kotlin/Wasm Firefox cancellation only during navigation', () => {
  assert.equal(isExpectedNavigationAbort({
    browserName: 'firefox', message: genericWasmError, now: 1_000, expectedUntil: 1_500,
  }), true);
});

test('does not hide the generic Kotlin/Wasm error after navigation', () => {
  assert.equal(isExpectedNavigationAbort({
    browserName: 'firefox', message: genericWasmError, now: 1_501, expectedUntil: 1_500,
  }), false);
});

test('does not hide the generic Kotlin/Wasm error in other browsers', () => {
  assert.equal(isExpectedNavigationAbort({
    browserName: 'chromium', message: genericWasmError, now: 1_000, expectedUntil: 1_500,
  }), false);
});

test('does not hide unrelated Firefox errors during navigation', () => {
  assert.equal(isExpectedNavigationAbort({
    browserName: 'firefox', message: 'ReferenceError: broken is not defined', now: 1_000, expectedUntil: 1_500,
  }), false);
});
