@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import com.hienthai.fastowin.platform.AppUpdateBridge

internal class WebAppUpdateBridge : AppUpdateBridge {
    override val updateAvailable: Boolean
        get() = isWebUpdateAvailable()

    override fun observe(onUpdateAvailable: () -> Unit): () -> Unit {
        val listenerId = addWebUpdateListener(onUpdateAvailable)
        return { removeWebUpdateListener(listenerId) }
    }

    override fun applyUpdate() {
        applyWebUpdate()
    }

    override fun dismissUpdate() {
        dismissWebUpdate()
    }
}

private fun isWebUpdateAvailable(): Boolean = js(
    "Boolean(window.FASTTOWIN_PWA && window.FASTTOWIN_PWA.updateAvailable)"
)

private fun addWebUpdateListener(onUpdateAvailable: () -> Unit): Int = js(
    """{
        const registry = window.__fastToWinUpdateListeners ||
            (window.__fastToWinUpdateListeners = { nextId: 1, handlers: new Map() });
        const id = registry.nextId++;
        const handler = () => onUpdateAvailable();
        registry.handlers.set(id, handler);
        window.addEventListener('fasttowin-update-available', handler);
        if (window.FASTTOWIN_PWA && window.FASTTOWIN_PWA.updateAvailable) handler();
        return id;
    }"""
)

private fun removeWebUpdateListener(listenerId: Int): Unit = js(
    """{
        const registry = window.__fastToWinUpdateListeners;
        const handler = registry && registry.handlers.get(listenerId);
        if (handler) {
            window.removeEventListener('fasttowin-update-available', handler);
            registry.handlers.delete(listenerId);
        }
    }"""
)

private fun applyWebUpdate(): Unit = js(
    """{
        if (window.FASTTOWIN_PWA && window.FASTTOWIN_PWA.applyUpdate) {
            window.FASTTOWIN_PWA.applyUpdate();
        } else {
            window.location.reload();
        }
    }"""
)

private fun dismissWebUpdate(): Unit = js(
    "window.FASTTOWIN_PWA && window.FASTTOWIN_PWA.deferUpdate && window.FASTTOWIN_PWA.deferUpdate()"
)
