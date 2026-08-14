package com.hienthai.fastowin

import androidx.compose.ui.window.ComposeUIViewController
import com.hienthai.fastowin.data.network.IosResumeTokenStore
import com.hienthai.fastowin.data.network.IosAuthSessionStore
import com.hienthai.fastowin.data.preferences.IosAppPreferencesStore

fun MainViewController(serverUrl: String) = ComposeUIViewController {
    FastToWinApp(
        serverUrl = serverUrl,
        resumeTokenStore = IosResumeTokenStore(),
        authSessionStore = IosAuthSessionStore(),
        preferencesStore = IosAppPreferencesStore(),
        devicePlatform = "ios"
    )
}
