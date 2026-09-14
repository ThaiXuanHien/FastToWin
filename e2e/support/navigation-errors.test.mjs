import test from 'node:test';
import assert from 'node:assert/strict';
import { isExpectedNavigationAbort } from './navigation-errors.mjs';

const genericWasmError = 'Exception was thrown while running JavaScript code';

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
