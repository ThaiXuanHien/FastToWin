@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import com.hienthai.fastowin.platform.AppPushBridge
import com.hienthai.fastowin.platform.AppPushStatus

internal class WebAppPushBridge : AppPushBridge {
    override val status: AppPushStatus
        get() = webPushStatus().toAppPushStatus()

    override fun observe(
        onStatusChanged: (AppPushStatus) -> Unit,
        onTokenChanged: (String) -> Unit
    ): () -> Unit {
        val statusAdapter: (String) -> Unit = { onStatusChanged(it.toAppPushStatus()) }
        val listenerId = addWebPushListeners(statusAdapter, onTokenChanged)
        return { removeWebPushListeners(listenerId) }
    }

    override fun enable() {
        enableWebPush()
    }

    override fun disable() {
        disableWebPush()
    }
}

private fun String.toAppPushStatus(): AppPushStatus =
    runCatching { AppPushStatus.valueOf(uppercase()) }.getOrDefault(AppPushStatus.ERROR)

private fun webPushStatus(): String = js(
    "(window.FASTTOWIN_PUSH && window.FASTTOWIN_PUSH.status()) || 'unsupported'"
)

private fun addWebPushListeners(
    onStatusChanged: (String) -> Unit,
    onTokenChanged: (String) -> Unit
): Int = js(
    """{
        const registry = window.__fastToWinPushListeners ||
            (window.__fastToWinPushListeners = { nextId: 1, handlers: new Map() });
        const id = registry.nextId++;
        const statusHandler = event => onStatusChanged(event.detail || 'error');
        const tokenHandler = event => onTokenChanged(event.detail || '');
        registry.handlers.set(id, { statusHandler, tokenHandler });
        window.addEventListener('fasttowin-push-status', statusHandler);
        window.addEventListener('fasttowin-push-token', tokenHandler);
        if (window.FASTTOWIN_PUSH) window.FASTTOWIN_PUSH.syncState();
        return id;
    }"""
)

private fun removeWebPushListeners(listenerId: Int): Unit = js(
    """{
        const registry = window.__fastToWinPushListeners;
        const handlers = registry && registry.handlers.get(listenerId);
        if (handlers) {
            window.removeEventListener('fasttowin-push-status', handlers.statusHandler);
            window.removeEventListener('fasttowin-push-token', handlers.tokenHandler);
            registry.handlers.delete(listenerId);
        }
    }"""
)

private fun enableWebPush(): Unit = js(
    "window.FASTTOWIN_PUSH && window.FASTTOWIN_PUSH.enable()"
)

private fun disableWebPush(): Unit = js(
    "window.FASTTOWIN_PUSH && window.FASTTOWIN_PUSH.disable()"
)
