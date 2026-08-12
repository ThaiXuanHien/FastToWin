package com.hienthai.fastowin

import androidx.compose.ui.window.ComposeUIViewController
import com.hienthai.fastowin.data.network.IosResumeTokenStore

fun MainViewController(serverUrl: String) = ComposeUIViewController {
    FastToWinApp(
        serverUrl = serverUrl,
        resumeTokenStore = IosResumeTokenStore()
    )
}
