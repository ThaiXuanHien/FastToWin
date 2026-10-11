const FIREFOX_NAVIGATION_CANCELLATIONS = new Set([
  'NetworkError when attempting to fetch resource.',
  // Kotlin/Wasm 2.4 wraps the same cancelled fetch with this generic JsError
  // message. The time and browser checks below keep unrelated errors visible.
  'Exception was thrown while running JavaScript code',
]);

function isRecentLocalGetCancellation(request, loopbackOrigins, now) {
  if (!request || request.method !== 'GET' || request.failureText !== 'Load request cancelled' ||
      !Number.isFinite(request.at) || request.at > now || now - request.at > 250) return false;
  try {
    const url = new URL(request.url);
    return url.protocol === 'http:' &&
      ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) &&
      loopbackOrigins.includes(url.origin);
  } catch { return false; }
}

export function isExpectedNavigationAbort({ browserName, message, now, expectedUntil,
  correlatedWebKitAbortUntil = 0, cancelledRequest = null, loopbackOrigins = [] }) {
  if (now > expectedUntil) return false;
  if (browserName === 'firefox') return FIREFOX_NAVIGATION_CANCELLATIONS.has(message);
  if (browserName !== 'webkit') return false;
  // WebKit can publish the actual request cancellation without its separate
  // access-control error. Correlate its exact local GET cancellation too;
  // a generic fetch failure alone must remain visible.
  if (message === 'Fail to fetch') return (
    (correlatedWebKitAbortUntil > 0 && now <= correlatedWebKitAbortUntil) ||
    isRecentLocalGetCancellation(cancelledRequest, loopbackOrigins, now)
  );
  return ['Load failed', 'The I/O read operation failed.'].includes(message) ||
    (/^(?:https?:\/\/|ttps?:\/\/|\/)(?:localhost|127\.0\.0\.1):\d+\/.+ due to access control checks\.$/.test(message)) ||
    (message.startsWith('Fatal exception in coroutines machinery for AwaitContinuation(') &&
      message.includes('{Cancelled}'));
}
