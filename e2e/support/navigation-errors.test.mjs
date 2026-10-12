import test from 'node:test';
import assert from 'node:assert/strict';
import { isExpectedNavigationAbort, createFirefoxNavigationErrorFence } from './navigation-errors.mjs';

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

test('Firefox generic fetch wrapper requires a recent cancelled loopback GET during navigation', () => {
  const args = {
    browserName: 'firefox', message: 'Fail to fetch', now: 1_000, expectedUntil: 1_500,
    loopbackOrigins: ['http://127.0.0.1:18080'],
    cancelledRequest: { url: 'http://127.0.0.1:18080/api/announcements/push-preferences',
      method: 'GET', failureText: 'NS_BINDING_ABORTED', at: 980 },
  };
  assert.equal(isExpectedNavigationAbort(args), true);
  for (const patch of [
    { browserName: 'chromium' }, { browserName: 'webkit' }, { expectedUntil: 999 },
    { cancelledRequest: null }, { loopbackOrigins: [] },
    ...[{ at: 749 }, { at: 1_001 }, { method: 'POST' },
      { failureText: 'NS_ERROR_CONNECTION_REFUSED' }, { failureText: 'Load request cancelled' },
      { url: 'http://127.0.0.1:19000/api/status' },
      { url: 'https://fonts.gstatic.com/s/notosanssymbols/font.woff2' },
      { url: 'malformed' }].map(request => ({ cancelledRequest: { ...args.cancelledRequest, ...request } })),
  ]) assert.equal(isExpectedNavigationAbort({ ...args, ...patch }), false, JSON.stringify(patch));
});

test('Firefox navigation fence correlates a cancellation arriving after its wrapper', async () => {
  const failures = [];
  let now = 1_000;
  const fence = createFirefoxNavigationErrorFence({ loopbackOrigins: ['http://127.0.0.1:18080'],
    onError: detail => failures.push(detail), now: () => now });
  assert.equal(fence.capture({ message: 'Fail to fetch', at: now }, 1_500), true);
  now = 1_100;
  fence.recordCancellation({ url: 'http://127.0.0.1:18080/api/status', method: 'GET',
    failureText: 'NS_BINDING_ABORTED', at: now });
  await fence.flush();
  assert.deepEqual(failures, []);
});

test('Firefox navigation fence flushes every uncorrelated wrapper and ignores unrelated errors', async () => {
  const failures = [];
  const fence = createFirefoxNavigationErrorFence({ loopbackOrigins: ['http://127.0.0.1:18080'],
    onError: detail => failures.push(detail), now: () => 1_000 });
  const detail = { message: 'Fail to fetch', at: 1_000 };
  assert.equal(fence.capture({ message: 'ReferenceError: broken', at: 1_000 }, 1_500), false);
  assert.equal(fence.capture(detail, 999), false);
  assert.equal(fence.capture(detail, 1_500), true);
  fence.recordCancellation({ url: 'https://fonts.gstatic.com/s/font.woff2', method: 'GET',
    failureText: 'NS_BINDING_ABORTED', at: 1_010 });
  await fence.flush();
  assert.deepEqual(failures, [detail]);
});

test('Firefox pending wrappers remain errors for late, expired, non-GET or nonlocal cancellations', async () => {
  await Promise.all([
    { at: 1_251 }, { expectedUntil: 1_050 }, { method: 'POST' },
    { url: 'https://fonts.gstatic.com/s/font.woff2' },
    { url: 'http://127.0.0.1:19000/api/status' },
    { failureText: 'NS_ERROR_CONNECTION_REFUSED' },
  ].map(async patch => {
    let now = 1_000;
    const failures = [];
    const fence = createFirefoxNavigationErrorFence({ loopbackOrigins: ['http://127.0.0.1:18080'],
      onError: detail => failures.push(detail), now: () => now });
    const detail = { message: 'Fail to fetch', at: now };
    assert.equal(fence.capture(detail, patch.expectedUntil ?? 1_500), true);
    now = patch.at ?? 1_100;
    fence.recordCancellation({ url: 'http://127.0.0.1:18080/api/status', method: 'GET',
      failureText: 'NS_BINDING_ABORTED', at: now, ...patch });
    await fence.flush();
    assert.deepEqual(failures, [detail], JSON.stringify(patch));
  }));
});

test('Firefox fence flush waits for wrappers captured while cleanup is already waiting', async () => {
  const failures = [];
  const fence = createFirefoxNavigationErrorFence({ loopbackOrigins: ['http://127.0.0.1:18080'],
    onError: detail => failures.push(detail), now: () => 1_000 });
  const first = { message: 'Fail to fetch', at: 1_000, label: 'first' };
  const second = { message: 'Fail to fetch', at: 1_000, label: 'during cleanup' };
  fence.capture(first, 1_500);
  setTimeout(() => fence.capture(second, 1_500), 10);
  await fence.flush();
  assert.deepEqual(failures, [first, second]);
});

test('one Firefox cancelled GET cannot hide two wrappers when the request event arrives first', async () => {
  const failures = [];
  const fence = createFirefoxNavigationErrorFence({ loopbackOrigins: ['http://127.0.0.1:18080'],
    onError: detail => failures.push(detail), now: () => 1_000 });
  fence.recordCancellation({ url: 'http://127.0.0.1:18080/api/status', method: 'GET',
    failureText: 'NS_BINDING_ABORTED', at: 980 });
  const first = { message: 'Fail to fetch', at: 1_000, label: 'cancelled request' };
  const excess = { message: 'Fail to fetch', at: 1_000, label: 'unrelated wrapper' };
  fence.capture(first, 1_500);
  fence.capture(excess, 1_500);
  await fence.flush();
  assert.deepEqual(failures, [excess]);
});

test('one Firefox cancelled GET cannot hide two wrappers when the errors arrive first', async () => {
  let now = 1_000;
  const failures = [];
  const fence = createFirefoxNavigationErrorFence({ loopbackOrigins: ['http://127.0.0.1:18080'],
    onError: detail => failures.push(detail), now: () => now });
  const first = { message: 'Fail to fetch', at: now, label: 'cancelled request' };
  const excess = { message: 'Fail to fetch', at: now, label: 'unrelated wrapper' };
  fence.capture(first, 1_500);
  fence.capture(excess, 1_500);
  now = 1_100;
  fence.recordCancellation({ url: 'http://127.0.0.1:18080/api/status', method: 'GET',
    failureText: 'NS_BINDING_ABORTED', at: now });
  await fence.flush();
  assert.deepEqual(failures, [excess]);
});

test('two distinct Firefox cancelled GETs account for two wrappers and leave an excess wrapper visible', async () => {
  const failures = [];
  const fence = createFirefoxNavigationErrorFence({ loopbackOrigins: ['http://127.0.0.1:18080'],
    onError: detail => failures.push(detail), now: () => 1_000 });
  for (const [path, at] of [['status', 980], ['announcements/push-preferences', 990]]) {
    fence.recordCancellation({ url: `http://127.0.0.1:18080/api/${path}`, method: 'GET',
      failureText: 'NS_BINDING_ABORTED', at });
  }
  fence.capture({ message: 'Fail to fetch', at: 1_000, label: 'first request' }, 1_500);
  fence.capture({ message: 'Fail to fetch', at: 1_000, label: 'second request' }, 1_500);
  const excess = { message: 'Fail to fetch', at: 1_000, label: 'third wrapper' };
  fence.capture(excess, 1_500);
  await fence.flush();
  assert.deepEqual(failures, [excess]);
});
