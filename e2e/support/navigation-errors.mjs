const FIREFOX_NAVIGATION_CANCELLATIONS = new Set([
  'NetworkError when attempting to fetch resource.',
  // Kotlin/Wasm 2.4 wraps the same cancelled fetch with this generic JsError
  // message. The time and browser checks below keep unrelated errors visible.
  'Exception was thrown while running JavaScript code',
]);

export function isExpectedNavigationAbort({ browserName, message, now, expectedUntil }) {
  if (now > expectedUntil) return false;
  if (browserName === 'firefox') return FIREFOX_NAVIGATION_CANCELLATIONS.has(message);
  if (browserName !== 'webkit') return false;
  return ['Load failed', 'The I/O read operation failed.'].includes(message) ||
    (/^(?:https?:\/\/|ttps?:\/\/|\/)(?:localhost|127\.0\.0\.1):\d+\/.+ due to access control checks\.$/.test(message)) ||
    (message.startsWith('Fatal exception in coroutines machinery for AwaitContinuation(') &&
      message.includes('{Cancelled}'));
}
