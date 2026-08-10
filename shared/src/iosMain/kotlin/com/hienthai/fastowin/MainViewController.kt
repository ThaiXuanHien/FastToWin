package com.hienthai.fastowin

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController(serverUrl: String) = ComposeUIViewController {
    FastToWinApp(serverUrl = serverUrl)
}
