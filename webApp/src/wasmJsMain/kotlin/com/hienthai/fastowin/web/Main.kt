@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hienthai.fastowin.FastToWinApp
import com.hienthai.fastowin.data.network.AuthRequestConfigurator
import com.hienthai.fastowin.ui.components.ArcadeBackdrop
import io.ktor.client.fetchOptions
import io.ktor.client.request.header
import kotlin.js.toJsString

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "fastToWinRoot") {
        val safeArea = rememberWebSafeAreaInsets()
        ArcadeBackdrop(
            modifier = Modifier.fillMaxSize(),
            darkBackdrop = true
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = safeArea.left.dp,
                        top = safeArea.top.dp,
                        end = safeArea.right.dp,
                        bottom = safeArea.bottom.dp
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 430.dp)
                        .fillMaxSize()
                ) {
                    FastToWinApp(
                        serverUrl = configuredServerUrl(),
                        resumeTokenStore = WebResumeTokenStore(),
                        authSessionStore = WebAuthSessionStore(),
                        preferencesStore = WebAppPreferencesStore(),
                        devicePlatform = "web",
                        authRequestConfigurator = WebCookieAuthRequestConfigurator,
                        navigationBridge = remember { WebAppNavigationBridge() },
                        updateBridge = remember { WebAppUpdateBridge() },
                        installBridge = remember { WebAppInstallBridge() },
                        pushBridge = remember { WebAppPushBridge() }
                    )
                }
            }
        }
    }
}

private data class WebSafeAreaInsets(
    val top: Float,
    val right: Float,
    val bottom: Float,
    val left: Float
)

@Composable
private fun rememberWebSafeAreaInsets(): WebSafeAreaInsets {
    var insets by remember { mutableStateOf(readWebSafeAreaInsets()) }
    DisposableEffect(Unit) {
        val listenerId = addWebSafeAreaListener { insets = readWebSafeAreaInsets() }
        onDispose { removeWebSafeAreaListener(listenerId) }
    }
    return insets
}

private fun readWebSafeAreaInsets(): WebSafeAreaInsets = WebSafeAreaInsets(
    top = webSafeAreaTop().toFloat(),
    right = webSafeAreaRight().toFloat(),
    bottom = webSafeAreaBottom().toFloat(),
    left = webSafeAreaLeft().toFloat()
)

private fun webSafeAreaTop(): Double = js(
    "parseFloat(getComputedStyle(document.getElementById('fastToWinSafeAreaProbe')).paddingTop) || 0"
)

private fun webSafeAreaRight(): Double = js(
    "parseFloat(getComputedStyle(document.getElementById('fastToWinSafeAreaProbe')).paddingRight) || 0"
)

private fun webSafeAreaBottom(): Double = js(
    "parseFloat(getComputedStyle(document.getElementById('fastToWinSafeAreaProbe')).paddingBottom) || 0"
)

private fun webSafeAreaLeft(): Double = js(
    "parseFloat(getComputedStyle(document.getElementById('fastToWinSafeAreaProbe')).paddingLeft) || 0"
)

private fun addWebSafeAreaListener(onChanged: () -> Unit): Int = js(
    """{
        const registry = window.__fastToWinSafeAreaListeners ||
            (window.__fastToWinSafeAreaListeners = { nextId: 1, handlers: new Map() });
        const id = registry.nextId++;
        const handler = () => {
            onChanged();
            window.requestAnimationFrame(() => onChanged());
        };
        registry.handlers.set(id, handler);
        window.addEventListener('resize', handler);
        window.addEventListener('orientationchange', handler);
        window.addEventListener('pageshow', handler);
        window.addEventListener('focus', handler);
        window.addEventListener('fasttowin-viewport-change', handler);
        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', handler);
        }
        const visibilityHandler = () => {
            if (document.visibilityState === 'visible') handler();
        };
        registry.handlers.set('visibility-' + id, visibilityHandler);
        document.addEventListener('visibilitychange', visibilityHandler);
        return id;
    }"""
)

private fun removeWebSafeAreaListener(listenerId: Int): Unit = js(
    """{
        const registry = window.__fastToWinSafeAreaListeners;
        const handler = registry && registry.handlers.get(listenerId);
        if (handler) {
            window.removeEventListener('resize', handler);
            window.removeEventListener('orientationchange', handler);
            window.removeEventListener('pageshow', handler);
            window.removeEventListener('focus', handler);
            window.removeEventListener('fasttowin-viewport-change', handler);
            if (window.visualViewport) {
                window.visualViewport.removeEventListener('resize', handler);
            }
            const visibilityHandler = registry.handlers.get('visibility-' + listenerId);
            if (visibilityHandler) {
                document.removeEventListener('visibilitychange', visibilityHandler);
                registry.handlers.delete('visibility-' + listenerId);
            }
            registry.handlers.delete(listenerId);
        }
    }"""
)

private fun configuredServerUrl(): String = js(
    "(window.FASTTOWIN_CONFIG && window.FASTTOWIN_CONFIG.serverUrl) || 'ws://localhost:8080/game'"
)

private val WebCookieAuthRequestConfigurator = AuthRequestConfigurator { request ->
    request.header("X-FastToWin-Web-Session", "1")
    request.header("X-FastToWin-CSRF", "1")
    request.fetchOptions {
        credentials = "include".toJsString()
    }
}
