const CACHE_PREFIX = 'fast-to-win-';
const SHELL_CACHE = `${CACHE_PREFIX}shell-v5`;
const APP_SHELL = [
    '/',
    '/index.html',
    '/styles.css',
    '/config.js',
    '/pwa.js',
    '/fast-to-win.js',
    '/manifest.webmanifest',
    '/app-icon.webp',
    '/icons/icon-192.png',
    '/icons/icon-512.png',
    '/icons/icon-maskable-512.png'
];

// Firebase Messaging uses this same worker for notifications received while the app is closed.
// The SDK is loaded only after Firebase Web config has been supplied in config.js.
try {
    importScripts('/config.js');
    const firebaseConfig = self.FASTTOWIN_CONFIG && self.FASTTOWIN_CONFIG.firebase;
    if (firebaseConfig && firebaseConfig.apiKey && firebaseConfig.messagingSenderId) {
        importScripts('https://www.gstatic.com/firebasejs/12.18.0/firebase-app-compat.js');
        importScripts('https://www.gstatic.com/firebasejs/12.18.0/firebase-messaging-compat.js');
        firebase.initializeApp(firebaseConfig);
        firebase.messaging();
    }
} catch (error) {
    console.warn('[FastToWin] Không thể khởi tạo Web Push trong service worker.', error);
}

self.addEventListener('install', event => {
    event.waitUntil(caches.open(SHELL_CACHE).then(cache => cache.addAll(APP_SHELL)));
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(names => Promise.all(
                names
                    .filter(name => name.startsWith(CACHE_PREFIX) && name !== SHELL_CACHE)
                    .map(name => caches.delete(name))
            ))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('message', event => {
    if (event.data && event.data.type === 'SKIP_WAITING') {
        event.waitUntil(self.skipWaiting());
    }
});

self.addEventListener('fetch', event => {
    const request = event.request;
    if (request.method !== 'GET') return;

    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;
    if (url.pathname.startsWith('/api/') || url.pathname === '/health') return;

    if (request.mode === 'navigate') {
        event.respondWith(networkFirst(request, '/index.html'));
        return;
    }

    // Always check the live backend URL first; the cached copy is only an offline fallback.
    if (url.pathname === '/config.js') {
        event.respondWith(networkConfig(request));
        return;
    }

    if (
        url.pathname === '/fast-to-win.js' ||
        request.destination === 'script' ||
        request.destination === 'style'
    ) {
        event.respondWith(networkFirst(request));
        return;
    }

    event.respondWith(cacheFirst(request));
});

async function networkFirst(request, fallbackPath) {
    const cache = await caches.open(SHELL_CACHE);
    try {
        const response = await fetch(request);
        if (response && response.ok) await cache.put(request, response.clone());
        return response;
    } catch (error) {
        const cached = await cache.match(request);
        if (cached) return cached;
        if (fallbackPath) {
            const fallback = await cache.match(fallbackPath);
            if (fallback) return fallback;
        }
        throw error;
    }
}

async function networkConfig(request) {
    const cache = await caches.open(SHELL_CACHE);
    try {
        const response = await fetch(request, { cache: 'no-store' });
        if (response && response.ok) await cache.put(request, response.clone());
        return response;
    } catch (error) {
        const cached = await cache.match(request);
        if (cached) return cached;
        throw error;
    }
}

async function cacheFirst(request) {
    const cache = await caches.open(SHELL_CACHE);
    const cached = await cache.match(request);
    if (cached) return cached;
    const response = await fetch(request);
    if (response && response.ok) await cache.put(request, response.clone());
    return response;
}
