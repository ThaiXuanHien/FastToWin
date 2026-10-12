const FIREFOX_NAVIGATION_CANCELLATIONS = new Set([
  'NetworkError when attempting to fetch resource.',
  // Kotlin/Wasm 2.4 wraps the same cancelled fetch with this generic JsError
  // message. The time and browser checks below keep unrelated errors visible.
  'Exception was thrown while running JavaScript code',
]);

function isRecentLocalGetCancellation(request, loopbackOrigins, now, failureText = 'Load request cancelled') {
  if (!request || request.method !== 'GET' || request.failureText !== failureText ||
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
  if (browserName === 'firefox') {
    if (message === 'Fail to fetch') return isRecentLocalGetCancellation(
      cancelledRequest, loopbackOrigins, now, 'NS_BINDING_ABORTED'
    );
    return FIREFOX_NAVIGATION_CANCELLATIONS.has(message);
  }
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

// Firefox's Ktor/Wasm wrapper can precede the requestfailed event. Keep it
// pending briefly, but retain every wrapper without a matching local GET abort.
export function createFirefoxNavigationErrorFence({ loopbackOrigins, onError, now = Date.now }) {
  const pending = new Set();
  const cancellations = [];
  const consumeCancellation = (detail, expectedUntil) => {
    const at = now();
    for (let index = cancellations.length - 1; index >= 0; index--) {
      if (at - cancellations[index].at > 250) cancellations.splice(index, 1);
    }
    const index = cancellations.findIndex(request =>
      Math.abs(request.at - detail.at) <= 250 && isExpectedNavigationAbort({
        browserName: 'firefox', message: detail.message, now: at, expectedUntil,
        cancelledRequest: request, loopbackOrigins,
      })
    );
    if (index < 0) return false;
    cancellations.splice(index, 1);
    return true;
  };
  const finish = (entry, failed) => {
    clearTimeout(entry.timer);
    pending.delete(entry);
    if (failed) onError(entry.detail);
    entry.resolve();
  };
  return {
    capture(detail, expectedUntil) {
      if (detail.message !== 'Fail to fetch' || detail.at > expectedUntil) return false;
      if (consumeCancellation(detail, expectedUntil)) return true;
      let resolve;
      const promise = new Promise(done => { resolve = done; });
      const entry = { detail, expectedUntil, promise, resolve };
      entry.timer = setTimeout(() => finish(entry, true), 250);
      pending.add(entry);
      return true;
    },
    recordCancellation(request) {
      if (!isRecentLocalGetCancellation(request, loopbackOrigins, now(), 'NS_BINDING_ABORTED')) return;
      cancellations.push(request);
      if (cancellations.length > 8) cancellations.shift();
      for (const entry of pending) {
        if (consumeCancellation(entry.detail, entry.expectedUntil)) {
          finish(entry, false);
          break;
        }
      }
    },
    resetCancellation() { cancellations.length = 0; },
    async flush() {
      while (pending.size) await Promise.all([...pending].map(entry => entry.promise));
    },
  };
}
