(function () {
    const pwa = window.FASTTOWIN_PWA = window.FASTTOWIN_PWA || {};
    pwa.updateAvailable = false;
    pwa.registration = null;
    pwa.isApplyingUpdate = false;
    const DEFERRED_UPDATE_KEY = 'fastToWinUpdateDeferredForSession';

    function updateDeferred() {
        try {
            return sessionStorage.getItem(DEFERRED_UPDATE_KEY) === 'true';
        } catch (_) {
            return false;
        }
    }

    function setUpdateDeferred(deferred) {
        try {
            if (deferred) sessionStorage.setItem(DEFERRED_UPDATE_KEY, 'true');
            else sessionStorage.removeItem(DEFERRED_UPDATE_KEY);
        } catch (_) {
            // Deferral is best-effort when session storage is unavailable.
        }
    }

    function announceUpdate(registration) {
        if (!registration.waiting) return;
        pwa.registration = registration;
        if (updateDeferred()) return;
        pwa.updateAvailable = true;
        window.dispatchEvent(new CustomEvent('fasttowin-update-available'));
    }

    pwa.deferUpdate = function () {
        pwa.updateAvailable = false;
        setUpdateDeferred(true);
    };

    pwa.applyUpdate = function () {
        const worker = pwa.registration && pwa.registration.waiting;
        pwa.updateAvailable = false;
        // Suppress the same waiting worker if activation times out and the fallback reload runs.
        setUpdateDeferred(true);
        if (!worker) {
            window.location.reload();
            return;
        }
        pwa.isApplyingUpdate = true;
        worker.postMessage({ type: 'SKIP_WAITING' });
        window.setTimeout(function () {
            if (pwa.isApplyingUpdate) window.location.reload();
        }, 4000);
    };

    if (!('serviceWorker' in navigator)) return;

    navigator.serviceWorker.addEventListener('controllerchange', function () {
        if (!pwa.isApplyingUpdate) return;
        pwa.isApplyingUpdate = false;
        pwa.updateAvailable = false;
        setUpdateDeferred(false);
        window.location.reload();
    });

    window.addEventListener('load', function () {
        navigator.serviceWorker.register('/service-worker.js', {
            scope: '/',
            updateViaCache: 'none'
        }).then(function (registration) {
            pwa.registration = registration;
            announceUpdate(registration);

            registration.addEventListener('updatefound', function () {
                const worker = registration.installing;
                if (!worker) return;
                worker.addEventListener('statechange', function () {
                    if (worker.state === 'installed' && navigator.serviceWorker.controller) {
                        announceUpdate(registration);
                    }
                });
            });

            document.addEventListener('visibilitychange', function () {
                if (document.visibilityState === 'visible') registration.update();
            });
            window.setInterval(function () { registration.update(); }, 60 * 60 * 1000);
        }).catch(function (error) {
            console.warn('[FastToWin] Không thể đăng ký service worker.', error);
        });
    });
})();

(function () {
    const install = window.FASTTOWIN_INSTALL = window.FASTTOWIN_INSTALL || {};
    let deferredPrompt = null;
    let currentStatus = 'manual';

    function isStandalone() {
        return window.matchMedia('(display-mode: standalone)').matches ||
            window.navigator.standalone === true;
    }

    function resolveStatus() {
        if (isStandalone()) return 'installed';
        if (deferredPrompt) return 'available';
        if ('serviceWorker' in navigator) return 'manual';
        return 'unsupported';
    }

    function emitStatus(status) {
        currentStatus = status || resolveStatus();
        window.dispatchEvent(new CustomEvent('fasttowin-install-status', {
            detail: currentStatus
        }));
    }

    install.status = function () {
        if (currentStatus === 'installing' || currentStatus === 'error') return currentStatus;
        currentStatus = resolveStatus();
        return currentStatus;
    };

    install.syncState = function () {
        emitStatus(resolveStatus());
    };

    install.install = async function () {
        if (isStandalone()) {
            emitStatus('installed');
            return;
        }
        if (!deferredPrompt) {
            emitStatus('manual');
            return;
        }

        const prompt = deferredPrompt;
        deferredPrompt = null;
        emitStatus('installing');
        try {
            await prompt.prompt();
            const choice = await prompt.userChoice;
            emitStatus(choice && choice.outcome === 'accepted' ? 'installed' : 'manual');
        } catch (error) {
            console.warn('[FastToWin] Không thể mở trình cài đặt PWA.', error);
            emitStatus('error');
        }
    };

    window.addEventListener('beforeinstallprompt', function (event) {
        event.preventDefault();
        deferredPrompt = event;
        emitStatus('available');
    });

    window.addEventListener('appinstalled', function () {
        deferredPrompt = null;
        emitStatus('installed');
    });

    const displayMode = window.matchMedia('(display-mode: standalone)');
    if (displayMode.addEventListener) {
        displayMode.addEventListener('change', install.syncState);
    }
    currentStatus = resolveStatus();
})();

(function () {
    const push = window.FASTTOWIN_PUSH = window.FASTTOWIN_PUSH || {};
    const SDK_VERSION = '12.18.0';
    const ENABLED_KEY = 'fastToWinWebPushEnabled';
    let messaging = null;
    let loadingFirebase = null;
    let currentToken = '';

    function config() {
        return window.FASTTOWIN_CONFIG || {};
    }

    function isConfigured() {
        const firebaseConfig = config().firebase;
        return Boolean(
            firebaseConfig &&
            firebaseConfig.apiKey &&
            firebaseConfig.projectId &&
            firebaseConfig.messagingSenderId &&
            firebaseConfig.appId &&
            config().vapidKey
        );
    }

    function isSupported() {
        return 'Notification' in window &&
            'serviceWorker' in navigator &&
            'PushManager' in window;
    }

    push.status = function () {
        if (!isSupported()) return 'unsupported';
        if (!isConfigured()) return 'unconfigured';
        if (Notification.permission === 'denied') return 'denied';
        if (Notification.permission === 'granted') {
            return localStorage.getItem(ENABLED_KEY) === 'false' ? 'disabled' : 'enabled';
        }
        return 'prompt';
    };

    function emitStatus(status) {
        window.dispatchEvent(new CustomEvent('fasttowin-push-status', {
            detail: status || push.status()
        }));
    }

    function emitToken(token) {
        currentToken = token || '';
        window.dispatchEvent(new CustomEvent('fasttowin-push-token', {
            detail: currentToken
        }));
    }

    function loadScript(url) {
        return new Promise((resolve, reject) => {
            const existing = document.querySelector(`script[src="${url}"]`);
            if (existing) {
                if (window.firebase) resolve();
                else existing.addEventListener('load', resolve, { once: true });
                return;
            }
            const script = document.createElement('script');
            script.src = url;
            script.onload = resolve;
            script.onerror = () => reject(new Error(`Không tải được ${url}`));
            document.head.appendChild(script);
        });
    }

    async function ensureMessaging() {
        if (messaging) return messaging;
        if (!isConfigured()) throw new Error('Web Push chưa được cấu hình.');
        if (!loadingFirebase) {
            loadingFirebase = (async () => {
                await loadScript(`https://www.gstatic.com/firebasejs/${SDK_VERSION}/firebase-app-compat.js`);
                await loadScript(`https://www.gstatic.com/firebasejs/${SDK_VERSION}/firebase-messaging-compat.js`);
                if (!firebase.apps.length) firebase.initializeApp(config().firebase);
                messaging = firebase.messaging();
                messaging.onMessage(() => {
                    // WebSocket already updates the in-app notification UI while the tab is active.
                    window.dispatchEvent(new CustomEvent('fasttowin-push-foreground'));
                });
                return messaging;
            })().catch(error => {
                loadingFirebase = null;
                throw error;
            });
        }
        return loadingFirebase;
    }

    async function registration() {
        return (window.FASTTOWIN_PWA && window.FASTTOWIN_PWA.registration) ||
            navigator.serviceWorker.ready;
    }

    async function refreshToken() {
        const client = await ensureMessaging();
        const serviceWorkerRegistration = await registration();
        const token = await client.getToken({
            vapidKey: config().vapidKey,
            serviceWorkerRegistration
        });
        if (!token) throw new Error('Firebase không trả về token Web Push.');
        localStorage.setItem(ENABLED_KEY, 'true');
        emitToken(token);
        emitStatus('enabled');
    }

    push.syncState = function () {
        emitStatus();
        if (currentToken) emitToken(currentToken);
        if (
            push.status() === 'enabled' &&
            document.visibilityState === 'visible'
        ) {
            refreshToken().catch(error => {
                console.warn('[FastToWin] Không thể đồng bộ Web Push.', error);
                emitStatus('error');
            });
        }
    };

    push.enable = async function () {
        if (!isSupported() || !isConfigured()) {
            emitStatus(push.status());
            return;
        }
        emitStatus('requesting');
        try {
            const permission = await Notification.requestPermission();
            if (permission !== 'granted') {
                emitStatus(permission === 'denied' ? 'denied' : 'prompt');
                return;
            }
            localStorage.setItem(ENABLED_KEY, 'true');
            await refreshToken();
        } catch (error) {
            console.warn('[FastToWin] Không thể bật Web Push.', error);
            emitStatus('error');
        }
    };

    push.disable = async function () {
        localStorage.setItem(ENABLED_KEY, 'false');
        emitStatus('disabled');
        try {
            if (messaging || isConfigured()) {
                const client = await ensureMessaging();
                await client.deleteToken();
            }
        } catch (error) {
            console.warn('[FastToWin] Không thể xóa token Web Push trên trình duyệt.', error);
        } finally {
            emitToken('');
            emitStatus('disabled');
        }
    };

    // Site permissions can be changed while the tab remains open. Re-check when the
    // user returns so a newly granted permission registers its token immediately.
    window.addEventListener('focus', function () {
        push.syncState();
    });
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible') push.syncState();
    });
})();
