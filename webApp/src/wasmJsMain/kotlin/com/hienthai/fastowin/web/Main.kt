@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.runtime.remember
import com.hienthai.fastowin.FastToWinApp
import com.hienthai.fastowin.data.network.AuthRequestConfigurator
import io.ktor.client.fetchOptions
import io.ktor.client.request.header
import kotlin.js.toJsString

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "fastToWinRoot") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
