@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.hienthai.fastowin.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.hienthai.fastowin.FastToWinApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "fastToWinRoot") {
        FastToWinApp(
            serverUrl = configuredServerUrl(),
            resumeTokenStore = WebResumeTokenStore(),
            authSessionStore = WebAuthSessionStore(),
            preferencesStore = WebAppPreferencesStore(),
            devicePlatform = "web"
        )
    }
}

private fun configuredServerUrl(): String = js(
    "(window.FASTTOWIN_CONFIG && window.FASTTOWIN_CONFIG.serverUrl) || 'ws://127.0.0.1:8080/game'"
)
