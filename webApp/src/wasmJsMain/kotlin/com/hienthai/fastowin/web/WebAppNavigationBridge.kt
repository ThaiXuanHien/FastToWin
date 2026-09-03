@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import com.hienthai.fastowin.platform.AppNavigationBridge

internal class WebAppNavigationBridge : AppNavigationBridge {
    override val initialRoute: String = currentBrowserRoute()

    init {
        prepareBrowserHistory()
    }

    override fun observe(onRouteRequested: (String) -> Unit): () -> Unit {
        val listenerId = addBrowserRouteListener(onRouteRequested)
        return { removeBrowserRouteListener(listenerId) }
    }

    override fun publish(route: String) {
        publishBrowserRoute(normalizeRoute(route))
    }

    override fun goBack(): Boolean = goBackInBrowserHistory()

    override fun publicUrl(route: String): String = browserPublicUrl(normalizeRoute(route))
}

private fun normalizeRoute(route: String): String {
    val path = route.trim().substringBefore('?').substringBefore('#')
    return when {
        path.isBlank() || path == "/" -> "/"
        path.startsWith('/') -> path
        else -> "/$path"
    }
}

private fun currentBrowserRoute(): String = js(
    "window.location.pathname || '/'"
)

private fun prepareBrowserHistory(): Unit = js(
    """{
        const existing = history.state || {};
        if (typeof existing.fastToWinDepth !== 'number') {
            history.replaceState({ ...existing, fastToWinDepth: 0 }, '', window.location.href);
        }
    }"""
)

private fun addBrowserRouteListener(onRoute: (String) -> Unit): Int = js(
    """{
        const registry = window.__fastToWinRouteListeners ||
            (window.__fastToWinRouteListeners = { nextId: 1, handlers: new Map() });
        const id = registry.nextId++;
        const handler = () => onRoute(window.location.pathname || '/');
        registry.handlers.set(id, handler);
        window.addEventListener('popstate', handler);
        return id;
    }"""
)

private fun removeBrowserRouteListener(listenerId: Int): Unit = js(
    """{
        const registry = window.__fastToWinRouteListeners;
        const handler = registry && registry.handlers.get(listenerId);
        if (handler) {
            window.removeEventListener('popstate', handler);
            registry.handlers.delete(listenerId);
        }
    }"""
)

private fun publishBrowserRoute(route: String): Unit = js(
    """{
        const current = window.location.pathname || '/';
        if (current === route) return;
        const currentDepth = history.state && typeof history.state.fastToWinDepth === 'number'
            ? history.state.fastToWinDepth
            : 0;
        history.pushState({ fastToWinDepth: currentDepth + 1 }, '', route);
    }"""
)

private fun goBackInBrowserHistory(): Boolean = js(
    """{
        const depth = history.state && typeof history.state.fastToWinDepth === 'number'
            ? history.state.fastToWinDepth
            : 0;
        if (depth <= 0) return false;
        history.back();
        return true;
    }"""
)

private fun browserPublicUrl(route: String): String = js(
    "window.location.origin + route"
)
