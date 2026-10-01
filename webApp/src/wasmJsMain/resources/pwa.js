(function () {
    const pwa = window.FASTTOWIN_PWA = window.FASTTOWIN_PWA || {};
    const standalone = (
        window.matchMedia('(display-mode: standalone)').matches ||
        window.navigator.standalone === true
    );
    const iosStandalone = standalone &&
        /iPad|iPhone|iPod/i.test(window.navigator.userAgent || '');
    // Android standalone already receives the usable app window from Chrome.
    // Browser tabs still report a larger 100vh that includes hidden browser UI,
    // while iOS standalone can publish a stale first-paint height.
    if (standalone && !iosStandalone) return;

    let pendingFrame = 0;
    let pendingSettle = 0;
    let recoveryTimer = 0;
    let recoveryFrame = 0;
    let recoveryDeadline = 0;
    let preKeyboardViewportHeight = 0;
    let recoveryStartedAt = 0;
    let recoveryLastHeight = 0;
    let recoveryLastSampleAt = 0;
    let lastPublishedViewportHeight = 0;

    function isNativeTextInput(element) {
        return element instanceof Element && element.matches('[data-fasttowin-native-input]');
    }

    function nativeTextInputFromEvent(event) {
        // Editors inside a shadow root are retargeted to its host at document
        // listeners; the composed path retains the original input/textarea.
        const path = typeof event.composedPath === 'function' ? event.composedPath() : [event.target];
        return path.find(isNativeTextInput);
    }

    function hasNativeTextInputFocus() {
        let element = document.activeElement;
        while (element && element.shadowRoot && element.shadowRoot.activeElement) {
            element = element.shadowRoot.activeElement;
        }
        return isNativeTextInput(element);
    }

    function iosStandaloneWindowHeight() {
        // A home-screen PWA has no browser chrome. On iOS 26 its innerHeight,
        // visualViewport and even 100dvh can exclude the status-bar height,
        // although our fixed canvas starts behind that status bar. outerHeight
        // describes the actual app window (also in iPad windowed mode).
        return Math.max(window.innerHeight, Number(window.outerHeight) || 0);
    }

    function iosStandaloneHasFullViewport() {
        if (!iosStandalone) return false;
        const viewport = window.visualViewport;
        if (viewport && Math.abs(viewport.scale - 1) > 0.01) return false;
        const probe = document.getElementById('fastToWinSafeAreaProbe');
        const safeTop = probe ? parseFloat(window.getComputedStyle(probe).paddingTop) || 0 : 0;
        const visibleBottom = viewport ? viewport.height + viewport.offsetTop : window.innerHeight;
        // While the keyboard is open, keep measuring its visible boundary.
        // Only expand to the full window once the status-bar-excluded viewport
        // has returned. Do not use screen.height: iPad PWAs can be windowed.
        return visibleBottom >= iosStandaloneWindowHeight() - safeTop - 1;
    }

    function currentViewportHeight() {
        const viewport = window.visualViewport;
        const height = iosStandaloneHasFullViewport()
            ? iosStandaloneWindowHeight()
            : viewport ? viewport.height + viewport.offsetTop : window.innerHeight;
        return Math.max(1, Math.round(height * 100) / 100);
    }

    function publishViewportHeight() {
        // Safari can leave the document itself scrolled after focusing an
        // offscreen native editor. Compose owns scrolling inside its canvas;
        // reset only the outer document after dismissal, never during typing.
        if (iosStandaloneHasFullViewport() && (window.scrollX || window.scrollY)) {
            window.scrollTo(0, 0);
        }
        const height = currentViewportHeight();
        lastPublishedViewportHeight = height;
        document.documentElement.style.setProperty(
            '--fast-to-win-viewport-height',
            `${height}px`
        );
        window.dispatchEvent(new CustomEvent('fasttowin-viewport-change'));
    }

    function syncViewportHeight() {
        const height = currentViewportHeight();
        if (iosStandalone && preKeyboardViewportHeight) {
            // iOS can dismiss its keyboard without blurring the DOM input. A
            // growing visual viewport is also a dismissal signal; keep sampling
            // beyond the last resize event until the animation has settled.
            if (height > lastPublishedViewportHeight + 0.5 && !recoveryStartedAt) {
                startViewportRecovery();
            } else if (height < lastPublishedViewportHeight - 0.5 && recoveryStartedAt && hasNativeTextInputFocus()) {
                // The same focused input can reopen the keyboard. Do not leave
                // the previous dismissal's timer competing with that opening.
                const baseline = preKeyboardViewportHeight;
                cancelViewportRecovery();
                preKeyboardViewportHeight = baseline;
            }
        }
        publishViewportHeight();
        if (pendingFrame) window.cancelAnimationFrame(pendingFrame);
        if (pendingSettle) window.clearTimeout(pendingSettle);
        pendingFrame = window.requestAnimationFrame(function () {
            publishViewportHeight();
            pendingFrame = window.requestAnimationFrame(function () {
                pendingFrame = 0;
                publishViewportHeight();
            });
        });
        pendingSettle = window.setTimeout(function () {
            pendingSettle = 0;
            publishViewportHeight();
        }, 160);
    }

    function cancelViewportRecovery() {
        if (recoveryTimer) window.clearTimeout(recoveryTimer);
        if (recoveryFrame) window.cancelAnimationFrame(recoveryFrame);
        if (recoveryDeadline) window.clearTimeout(recoveryDeadline);
        recoveryTimer = 0;
        recoveryFrame = 0;
        recoveryDeadline = 0;
        preKeyboardViewportHeight = 0;
        recoveryStartedAt = 0;
        recoveryLastHeight = 0;
        recoveryLastSampleAt = 0;
    }

    function finishViewportRecovery() {
        const baseline = preKeyboardViewportHeight;
        publishViewportHeight();
        cancelViewportRecovery();
        // A keyboard can be reopened on this input without another focusin.
        if (iosStandalone && hasNativeTextInputFocus()) {
            preKeyboardViewportHeight = Math.max(baseline, currentViewportHeight());
        }
    }

    function startViewportRecovery() {
        const baseline = preKeyboardViewportHeight;
        cancelViewportRecovery();
        preKeyboardViewportHeight = baseline;
        recoveryStartedAt = performance.now();
        recoveryLastHeight = currentViewportHeight();
        recoveryLastSampleAt = recoveryStartedAt;
        recoveryDeadline = window.setTimeout(function () {
            recoveryDeadline = 0;
            finishViewportRecovery();
        }, 1200);
        scheduleViewportRecoverySample();
    }

    function scheduleViewportRecoverySample() {
        recoveryFrame = window.requestAnimationFrame(function () {
            recoveryFrame = 0;
            publishViewportHeight();

            const height = currentViewportHeight();
            const now = performance.now();
            const returnedToBaseline = !preKeyboardViewportHeight ||
                height >= preKeyboardViewportHeight - 1;
            const stableForLongEnough = returnedToBaseline &&
                Math.abs(height - recoveryLastHeight) <= 0.5 &&
                now - recoveryLastSampleAt >= 100;

            if (Math.abs(height - recoveryLastHeight) > 0.5) {
                recoveryLastHeight = height;
                recoveryLastSampleAt = now;
            }

            const elapsed = now - recoveryStartedAt;
            if (stableForLongEnough || elapsed >= 1200) {
                finishViewportRecovery();
                return;
            }

            recoveryTimer = window.setTimeout(function () {
                recoveryTimer = 0;
                if (performance.now() - recoveryStartedAt >= 1200) {
                    finishViewportRecovery();
                    return;
                }
                scheduleViewportRecoverySample();
            }, 100);
        });
    }

    pwa.viewport = { sync: syncViewportHeight };

    window.addEventListener('resize', syncViewportHeight);
    window.addEventListener('pageshow', syncViewportHeight);
    window.addEventListener('focus', syncViewportHeight);
    document.addEventListener('focusin', function (event) {
        if (!nativeTextInputFromEvent(event)) return;
        const baseline = recoveryStartedAt
            ? preKeyboardViewportHeight
            : currentViewportHeight();
        cancelViewportRecovery();
        preKeyboardViewportHeight = baseline;
    }, true);
    document.addEventListener('focusout', function (event) {
        if (!nativeTextInputFromEvent(event)) return;
        startViewportRecovery();
    }, true);
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible') syncViewportHeight();
    });
    if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', syncViewportHeight);
        window.visualViewport.addEventListener('scroll', syncViewportHeight);
    }

    // Compose 1.11 measures its container only on window.resize. The visual
    // viewport/settle samples above can resize the root without that event,
    // leaving Home's canvas and bottom bar at the previous tutorial height.
    // Observe the actual container so CSS-only changes also resize Compose.
    if (typeof window.ResizeObserver === 'function') {
        const root = document.getElementById('fastToWinRoot');
        if (root) {
            let width = root.clientWidth;
            let height = root.clientHeight;
            let resizeFrame = 0;
            const observer = new window.ResizeObserver(function () {
                if (resizeFrame) return;
                // Resize listeners also publish CSS dimensions. Run outside
                // ResizeObserver delivery to avoid WebKit notification loops.
                resizeFrame = window.requestAnimationFrame(function () {
                    resizeFrame = 0;
                    const nextWidth = root.clientWidth;
                    const nextHeight = root.clientHeight;
                    if (nextWidth === width && nextHeight === height) return;
                    width = nextWidth;
                    height = nextHeight;
                    window.dispatchEvent(new Event('resize'));
                });
            });
            observer.observe(root);
        }
    }
    syncViewportHeight();
})();

(function () {
    const pwa = window.FASTTOWIN_PWA = window.FASTTOWIN_PWA || {};
    const registry = { nextId: 1, handlers: new Map() };

    function isIosStandalone() {
        const standalone = window.matchMedia('(display-mode: standalone)').matches ||
            window.navigator.standalone === true;
        return standalone && /iPad|iPhone|iPod/i.test(window.navigator.userAgent || '');
    }

    function currentDepth() {
        return window.history.state && typeof window.history.state.fastToWinDepth === 'number'
            ? window.history.state.fastToWinDepth
            : 0;
    }

    function replace(route, depth = currentDepth()) {
        window.history.replaceState({
            ...(window.history.state || {}),
            fastToWinDepth: depth
        }, '', route);
        pwa.viewport?.sync?.();
    }

    function preventIosStandaloneEdgeNavigation() {
        if (!isIosStandalone()) return;
        const edgeWidth = 24;
        let edgeGestureActive = false;
        const firstTouch = event => event.touches && event.touches[0];
        const startsAtScreenEdge = touch => touch && (
            touch.clientX <= edgeWidth ||
            touch.clientX >= window.innerWidth - edgeWidth
        );
        const preventIfEdgeGesture = event => {
            if (edgeGestureActive && event.cancelable !== false) event.preventDefault();
        };

        document.addEventListener('touchstart', event => {
            edgeGestureActive = startsAtScreenEdge(firstTouch(event));
            preventIfEdgeGesture(event);
        }, { capture: true, passive: false });
        document.addEventListener('touchmove', preventIfEdgeGesture, {
            capture: true,
            passive: false
        });
        document.addEventListener('touchend', () => {
            edgeGestureActive = false;
        }, { capture: true, passive: true });
        document.addEventListener('touchcancel', () => {
            edgeGestureActive = false;
        }, { capture: true, passive: true });
    }

    preventIosStandaloneEdgeNavigation();

    pwa.navigation = {
        prepare() {
            if (typeof window.history.state?.fastToWinDepth !== 'number') {
                replace(window.location.href, 0);
            }
        },
        addRouteListener(onRoute) {
            const id = registry.nextId++;
            const handler = function () {
                window.__fastToWinBackPending = false;
                onRoute(window.location.pathname || '/');
            };
            registry.handlers.set(id, handler);
            window.addEventListener('popstate', handler);
            return id;
        },
        removeRouteListener(id) {
            const handler = registry.handlers.get(id);
            if (!handler) return;
            window.removeEventListener('popstate', handler);
            registry.handlers.delete(id);
        },
        publish(route) {
            const current = window.location.pathname || '/';
            if (current === route || window.__fastToWinBackPending) return;
            if (isIosStandalone()) {
                replace(route, 0);
                return;
            }
            window.history.pushState({
                ...(window.history.state || {}),
                fastToWinDepth: currentDepth() + 1
            }, '', route);
            pwa.viewport?.sync?.();
        },
        replace(route) {
            replace(route, isIosStandalone() ? 0 : currentDepth());
        },
        goBack() {
            if (isIosStandalone()) return false;
            if (currentDepth() <= 0) return false;
            if (window.__fastToWinBackPending) return true;
            window.__fastToWinBackPending = true;
            window.history.back();
            return true;
        },
        publicUrl(route) {
            return window.location.origin + route;
        }
    };
})();

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
