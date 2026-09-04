package com.hienthai.fastowin

import androidx.compose.ui.window.ComposeUIViewController
import com.hienthai.fastowin.data.network.IosResumeTokenStore
import com.hienthai.fastowin.data.network.IosAuthSessionStore
import com.hienthai.fastowin.data.preferences.IosAppPreferencesStore
import com.hienthai.fastowin.platform.AppPushBridge
import com.hienthai.fastowin.platform.IosAppNavigationBridge

fun MainViewController(
    serverUrl: String,
    pushBridge: AppPushBridge
) = ComposeUIViewController {
    FastToWinApp(
        serverUrl = serverUrl,
        resumeTokenStore = IosResumeTokenStore(),
        authSessionStore = IosAuthSessionStore(),
        preferencesStore = IosAppPreferencesStore(),
        devicePlatform = "ios",
        navigationBridge = IosAppNavigationBridge,
        pushBridge = pushBridge
    )
}
