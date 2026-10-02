// Older game workers cached admin responses despite HTTP no-store. Verify the
// actual controller's network-only policy and cleanup before every admin request.
const workers = navigator.serviceWorker;
const supportedScript = new URL('/service-worker.js', location.origin).href;
let upgrading;

function policy(worker) {
  if (!worker || worker.scriptURL !== supportedScript) return Promise.resolve(false);
  return new Promise(resolve => {
    const channel = new MessageChannel();
    const finish = safe => { clearTimeout(timer); channel.port1.close(); channel.port2.close(); resolve(safe); };
    const timer = setTimeout(() => finish(false), 400);
    channel.port1.onmessage = event => finish(event.data?.adminNetworkOnly === 1);
    try { worker.postMessage({ type: 'ADMIN_NETWORK_POLICY' }, [channel.port2]); }
    catch { finish(false); }
  });
}

async function purgeOrphans() {
  if (!('caches' in window)) return;
  for (const name of await caches.keys()) {
    const cache = await caches.open(name);
    for (const request of await cache.keys()) {
      const url = new URL(request.url);
      if (url.origin === location.origin && (url.pathname === '/admin' || url.pathname.startsWith('/admin/'))) await cache.delete(request);
    }
  }
}

async function upgradeController() {
  const deadline = Date.now() + 6000;
  const registration = await workers.getRegistration();
  if (!registration || registration.active?.scriptURL !== supportedScript) throw new Error('Unsafe controller');
  // update() bypasses the old worker's fetch handler. Game updates normally wait
  // for SKIP_WAITING; admin access requires that activation to finish first.
  await registration.update();
  while (Date.now() < deadline) {
    const controller = workers.controller;
    if (await policy(controller) && controller === workers.controller) return;
    const waiting = registration.waiting;
    if (await policy(waiting)) waiting.postMessage({ type: 'SKIP_WAITING' });
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Worker update incomplete');
}

function bounded(operation) {
  let timer;
  return Promise.race([operation, new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error('Worker update timeout')), 8000);
  })]).finally(() => clearTimeout(timer));
}

export async function ensureAdminNetwork() {
  const controller = workers?.controller ?? null;
  if (!controller) {
    await purgeOrphans();
    if (!workers?.controller) return null;
    return ensureAdminNetwork();
  }
  if (await policy(controller) && workers.controller === controller) return controller;
  if (!upgrading) upgrading = bounded(upgradeController()).finally(() => { upgrading = null; });
  await upgrading;
  const current = workers.controller;
  if (!await policy(current) || workers.controller !== current) throw new Error('Controller changed');
  return current;
}
