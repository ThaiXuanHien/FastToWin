@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import com.hienthai.fastowin.platform.AppInstallBridge
import com.hienthai.fastowin.platform.AppInstallStatus

internal class WebAppInstallBridge : AppInstallBridge {
    override val status: AppInstallStatus
        get() = webInstallStatus().toAppInstallStatus()

    override fun observe(onStatusChanged: (AppInstallStatus) -> Unit): () -> Unit {
        val adapter: (String) -> Unit = { onStatusChanged(it.toAppInstallStatus()) }
        val listenerId = addWebInstallListener(adapter)
        return { removeWebInstallListener(listenerId) }
    }

    override fun install() {
        requestWebInstall()
    }
}

private fun String.toAppInstallStatus(): AppInstallStatus =
    runCatching { AppInstallStatus.valueOf(uppercase()) }.getOrDefault(AppInstallStatus.ERROR)

private fun webInstallStatus(): String = js(
    "(window.FASTTOWIN_INSTALL && window.FASTTOWIN_INSTALL.status()) || 'unsupported'"
)

private fun addWebInstallListener(onStatusChanged: (String) -> Unit): Int = js(
    """{
        const registry = window.__fastToWinInstallListeners ||
            (window.__fastToWinInstallListeners = { nextId: 1, handlers: new Map() });
        const id = registry.nextId++;
        const handler = event => onStatusChanged(event.detail || 'error');
        registry.handlers.set(id, handler);
        window.addEventListener('fasttowin-install-status', handler);
        if (window.FASTTOWIN_INSTALL) onStatusChanged(window.FASTTOWIN_INSTALL.status());
        return id;
    }"""
)

private fun removeWebInstallListener(listenerId: Int): Unit = js(
    """{
        const registry = window.__fastToWinInstallListeners;
        const handler = registry && registry.handlers.get(listenerId);
        if (handler) {
            window.removeEventListener('fasttowin-install-status', handler);
            registry.handlers.delete(listenerId);
        }
    }"""
)

private fun requestWebInstall(): Unit = js(
    "window.FASTTOWIN_INSTALL && window.FASTTOWIN_INSTALL.install()"
)
